/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostAttachAgentBootstrap: 子项目 9c 的一部分。
 *
 *  host 端 ContentProvider, 在 host app 启动时 (早于 Application.onCreate)
 *  被 Android framework 实例化, 启动一个反连线程:
 *    1) 读 Manifest placeholder "ide_local_server_name" (build-time 注入)
 *    2) 反向连 IDE 的 LocalServerSocket (走 HostAttachAgent.connectToIdeLocalServer)
 *    3) 打开本进程的 localabstract:jdwp (走 HostAttachAgent.openLocalAbstractJdwpSocket)
 *    4) 在两 socket 之间做字节桥 (走 HostAttachAgent.bridgeBytes)
 *    5) 收到 HELLO 不需要 IDE 端回复, 字节直接 JDWP 握手
 *
 *  这个 provider 跟 ide-log-plugin 的 DebuggerBootstrapProvider 共存 (各管一摊):
 *    - DebuggerBootstrapProvider (ide-log-plugin): TCP ServerSocket on 127.0.0.1:random,
 *      处理"IDE 通过 hostLauncher.launch 主动拉起"场景
 *    - HostAttachAgentBootstrap (ide-debugger-host): 反向连 IDE LocalServerSocket,
 *      处理"用户手动启动 host app"场景 (子项目 9 的核心)
 *
 *  注册: IdeDebuggerInitScriptPlugin 把 ide-debugger-host AAR 注入到 host app,
 *  合并 AndroidManifest 后这个 provider 会被自动注册。
 *
 *  安全: 不申请任何权限, 只走 abstract namespace 套接字, 在 host 进程内执行。
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 子项目 9c: host 端 ContentProvider, 启动时反连 IDE LocalServerSocket。
 *
 * Manifest placeholder 约定 (由 IdeDebuggerInitScriptPlugin 注入):
 *   - {@code ide_local_server_name}:  IDE 端 LocalServerSocket 名字 (abstract)
 *
 * 工作流:
 *   1) 读 placeholder "ide_local_server_name" (AndroidManifest <meta-data>)
 *   2) 反向连 (走 HostAttachAgent 的 connectToIdeLocalServer)
 *   3) 打开 localabstract:jdwp
 *   4) 字节桥
 *   5) Provider 返回 true; 后续 bridge 跑在 daemon thread
 *
 * 失败处理: 占位符缺失 / 反连失败 -> log + 不抛, 让 host app 正常启动。
 */
public final class HostAttachAgentBootstrap extends ContentProvider {

    private static final String TAG = "HostAttachAgentBootstrap";

    /** Manifest meta-data key: IDE LocalServerSocket 名字 */
    public static final String META_IDE_SOCKET_NAME = "ide_local_server_name";

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return true;
        String socketName = readManifestPlaceholder(ctx, META_IDE_SOCKET_NAME);
        if (socketName == null || socketName.isEmpty()) {
            Log.i(TAG, "no 'ide_local_server_name' placeholder; reverse-connect disabled");
            return true;
        }
        Log.i(TAG, "starting reverse-connect to IDE LocalServerSocket '" + socketName + "'");
        startBridgeThread(ctx, socketName);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    // ---- 私有 ----

    /**
     * 读 Manifest 的 <meta-data> 占位符。
     * 注: <meta-data> 在 merge 后的 AndroidManifest 里, 通过 PackageManager 读。
     */
    @Nullable
    private String readManifestPlaceholder(Context ctx, String key) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(
                    ctx.getPackageName(), PackageManager.GET_META_DATA);
            if (ai.metaData == null) return null;
            Object v = ai.metaData.get(key);
            if (v == null) return null;
            return v.toString();
        } catch (Throwable t) {
            Log.w(TAG, "failed to read meta-data " + key + ": " + t.getMessage());
            return null;
        }
    }

    private void startBridgeThread(Context ctx, String socketName) {
        Thread t = new Thread(() -> {
            try {
                // 1) 反向连 IDE LocalServerSocket
                LocalSocket ide = new LocalSocket();
                ide.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
                // 2) HELLO 头
                String hello = "HELLO pkg=" + ctx.getPackageName()
                        + " pid=" + android.os.Process.myPid()
                        + " sdk=" + Build.VERSION.SDK_INT + "\n";
                ide.getOutputStream().write(hello.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ide.getOutputStream().flush();

                // 3) 打开 localabstract:jdwp
                LocalSocket jdwp = new LocalSocket();
                jdwp.connect(new LocalSocketAddress("jdwp", LocalSocketAddress.Namespace.ABSTRACT));

                Log.i(TAG, "reverse-connect ok; bridging IDE <-> jdwp");

                // 4) 字节桥 - 复用 HostAttachAgent 的双线程 forward
                HostAttachAgentBridge.bridge(ide, jdwp);
            } catch (Throwable th) {
                Log.w(TAG, "reverse-connect / bridge failed: " + th.getMessage());
            }
        }, "HostAttachAgentBootstrap-bridge");
        t.setDaemon(true);
        t.start();
    }
}
