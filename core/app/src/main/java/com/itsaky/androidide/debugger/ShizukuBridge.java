/*
 *  ZeroStudio IDE - shizuku 桥
 *
 *  PR-D3: 通过 shizuku 走 ADB 等价权限,完成 install / run-as / 端口转发
 *  等特权操作,避免用户每次都用 USB 调试。
 *
 *  设计要点:
 *    1. 静态外观,无外部依赖。`isAvailable()` / `hasPermission()` 用
 *       反射检测,即使 shizuku-api 没有打包到 classpath 也不会崩。
 *    2. 一旦拿到 binder,就可以走 Shizuku.newProcess() / Shizuku
 *       .transactRemote() 在 shell/root 用户下执行命令。
 *    3. 默认失败回退到普通 PackageInstaller (PR-D2 的 runInstall 已经
 *       实现),所以 D3 的失败永远不会卡住流程。
 *
 *  PR-D4 (run-as 备选路径) 与 D6 (远程 adb) 会复用这里的执行语义。
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ShizukuBridge {

    private static final String TAG = "ShizukuBridge";

    /** shizuku 提供的特殊 binder/uid;0 表示未授权 / 未运行. */
    public static final int SHIZUKU_PERMISSION_NONE = -1;
    public static final int SHIZUKU_PERMISSION_NOT_GRANTED = 0;
    public static final int SHIZUKU_PERMISSION_GRANTED = 1;

    private final Context appContext;
    private final boolean apiAvailable;

    public ShizukuBridge(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.apiAvailable = detectApiClass();
    }

    /**
     * shizuku API 是否在本 APK 中可用 (classpath).
     * 即便 shizuku app 没有运行,这里也返回 true;只有当 shizuku-api
     * 根本没被打包进来时才返回 false。
     */
    public boolean isApiAvailable() { return apiAvailable; }

    /** shizuku binder 是否就绪 (shizuku app 在跑 + 我们能拿到 binder). */
    @AnyThread
    public boolean isBinderReady() {
        if (!apiAvailable) return false;
        try {
            Class<?> cls = Class.forName("rikka.shizuku.Shizuku");
            Method m = cls.getMethod("getBinder");
            IBinder b = (IBinder) m.invoke(null);
            return b != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 检查当前 app 是否拿到 shizuku ADB 等价权限。
     * 返回 [SHIZUKU_PERMISSION_NONE]/-1 表示 shizuku API 不可用,
     * [SHIZUKU_PERMISSION_NOT_GRANTED]/0 表示 API 在但没授权,
     * [SHIZUKU_PERMISSION_GRANTED]/1 表示已授权。
     */
    @AnyThread
    public int checkPermission() {
        if (!apiAvailable) return SHIZUKU_PERMISSION_NONE;
        try {
            Class<?> cls = Class.forName("rikka.shizuku.Shizuku");
            Method m = cls.getMethod("checkSelfPermission");
            int result = (int) m.invoke(null);
            return result == PackageManager.PERMISSION_GRANTED
                    ? SHIZUKU_PERMISSION_GRANTED
                    : SHIZUKU_PERMISSION_NOT_GRANTED;
        } catch (Throwable t) {
            Log.w(TAG, "checkPermission failed: " + t.getMessage());
            return SHIZUKU_PERMISSION_NONE;
        }
    }

    /** 当前 shizuku 服务进程的 UID (-1 表示不可用). */
    @AnyThread
    public int getServerUid() {
        if (!apiAvailable) return -1;
        try {
            Class<?> cls = Class.forName("rikka.shizuku.Shizuku");
            Method m = cls.getMethod("getUid");
            Object r = m.invoke(null);
            return r instanceof Integer ? (Integer) r : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 通过 shizuku binder 跑一个 shell 命令并返回 stdout。
     * 失败时返回 "" 并记录到 logcat。
     *
     * 实现:走 [IShizukuService] `newProcess` transaction,以
     * "sh -c <cmd>" 启动子进程并同步读 stdout。shizuku 本身并没有
     * 直接的 `exec` API,但它会通过 binder 给我们一个可以 newProcess
     * 的接口,而 newProcess 走的是 `java.lang.ProcessBuilder` 等价
     * 的实现。
     */
    @WorkerThread
    @NonNull
    public String exec(@NonNull String command) {
        if (!isBinderReady()) {
            Log.w(TAG, "exec: binder not ready");
            return "";
        }
        try {
            Class<?> cls = Class.forName("rikka.shizuku.Shizuku");
            Method m = cls.getMethod("newProcess", String[].class, String[].class, String.class);
            Object process = m.invoke(null,
                    new String[]{"sh", "-c", command},
                    null,
                    null);
            if (process == null) return "";
            return readProcessStdout(process);
        } catch (Throwable t) {
            Log.w(TAG, "exec failed: " + t.getMessage());
            return "";
        }
    }

    /** 尝试通过 shizuku 静默 install (走 `pm install` 而不是 PackageInstaller). */
    @WorkerThread
    public boolean installApk(@NonNull String apkPath) {
        String out = exec("pm install -r -t " + apkPath);
        boolean ok = out.contains("Success") || out.contains("success");
        if (!ok) {
            Log.w(TAG, "installApk via shizuku failed: " + out);
        }
        return ok;
    }

    /** 尝试通过 shizuku 跑 `run-as <pkg> <cmd>` (debug 包内部使用). */
    @WorkerThread
    @NonNull
    public String runAs(@NonNull String pkg, @NonNull String cmd) {
        return exec("run-as " + pkg + " " + cmd);
    }

    private static boolean detectApiClass() {
        try {
            Class.forName("rikka.shizuku.Shizuku");
            return true;
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
    }

    private static String readProcessStdout(Object process) {
        try {
            // 反射: process.getInputStream()
            Method getInput = process.getClass().getMethod("getInputStream");
            InputStream in = (InputStream) getInput.invoke(process);
            Method waitFor = process.getClass().getMethod("waitFor");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
            waitFor.invoke(process);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            Log.w(TAG, "readProcessStdout failed: " + t.getMessage());
            return "";
        }
    }

    /**
     * 反射调用工具: 给一个无法 import 的类,做一次 "method-invoke" 工具。
     */
    static Object invokeStatic(@NonNull String clsName,
                               @NonNull String methodName,
                               @Nullable Object receiver,
                               @NonNull Class<?>[] paramTypes,
                               @NonNull Object[] args) {
        try {
            Class<?> cls = Class.forName(clsName);
            Method m = cls.getMethod(methodName, paramTypes);
            return m.invoke(receiver, args);
        } catch (Throwable t) {
            Log.w(TAG, "invokeStatic " + clsName + "#" + methodName
                    + " failed: " + t.getMessage());
            return null;
        }
    }
}
