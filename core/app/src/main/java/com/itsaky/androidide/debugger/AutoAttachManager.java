/*
 *  ZeroStudio IDE - 调试会话自动 attach 管理器
 *
 *  PR-D5: 用户第一次成功 attach 调试器后,把 (host, port, packageName)
 *  存到 SharedPreferences。下次打开同一个项目时,IDE 会自动在
 *  200ms 之后尝试重新 attach,避免用户每次都点 "🪲 开始调试"。
 *
 *  存储格式: <prefs> =
 *      jdwp_target_host : "127.0.0.1"
 *      jdwp_target_port : 5005
 *      jdwp_target_pkg  : "com.example.myapp"
 *
 *  行为:
 *    - 同 packageName 才触发自动 attach (避免 project 切换错乱)
 *    - 用户主动 disconnect 后 60s 内不再自动 attach
 *    - 用户可以在 Settings 里全局关闭
 *
 *  与 JdwpClient 的 auto-reconnect (B6) 协作: 后者处理"已 attach 但
 *  连接被挂" 的场景;本类处理"启动 IDE 时尚未 attach" 的场景.
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public final class AutoAttachManager {

    private static final String TAG = "AutoAttachManager";
    private static final String PREFS_NAME = "zerostudio_debugger";
    private static final String KEY_HOST = "jdwp_target_host";
    private static final String KEY_PORT = "jdwp_target_port";
    private static final String KEY_PKG = "jdwp_target_pkg";
    private static final String KEY_ENABLED = "jdwp_auto_attach_enabled";
    private static final String KEY_LAST_DISCONNECT_MS = "jdwp_last_disconnect_ms";
    private static final long DEFAULT_DISCONNECT_BACKOFF_MS = 60_000L;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1_500L;
    private static final long DEFAULT_PROBE_TIMEOUT_MS = 1_000L;

    private final Context appContext;
    private final SharedPreferences prefs;
    @Nullable private Handler mainHandler;
    @Nullable private Runnable pendingAttach;

    public AutoAttachManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Check if the user has globally enabled auto-attach. */
    @AnyThread
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    @AnyThread
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Persist the most-recently-attached host/port/package. */
    @AnyThread
    public void rememberTarget(@NonNull String host, int port, @NonNull String packageName) {
        prefs.edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .putString(KEY_PKG, packageName)
                .apply();
    }

    @AnyThread
    public void clear() {
        prefs.edit()
                .remove(KEY_HOST)
                .remove(KEY_PORT)
                .remove(KEY_PKG)
                .apply();
    }

    /**
     * 标记用户刚刚主动 disconnect,触发 backoff.
     */
    @AnyThread
    public void recordUserDisconnect() {
        prefs.edit()
                .putLong(KEY_LAST_DISCONNECT_MS, System.currentTimeMillis())
                .apply();
    }

    /**
     * 启动器 (Activity.onCreate 等) 应当调用本方法,IDE 会在延迟
     * [DEFAULT_INITIAL_DELAY_MS] 之后尝试 attach 上次记录的目标。
     *
     * @param currentPackage 当前 Editor 中打开的 app package,null 时跳过
     * @return true 表示确实调度了一次自动 attach;false 表示无目标 / 已被禁用 / 在 backoff 期内
     */
    @MainThread
    public boolean maybeAutoAttach(@Nullable String currentPackage) {
        if (!isEnabled()) {
            Log.d(TAG, "auto-attach disabled by user");
            return false;
        }
        String host = prefs.getString(KEY_HOST, null);
        int port = prefs.getInt(KEY_PORT, 0);
        String pkg = prefs.getString(KEY_PKG, null);
        if (host == null || port <= 0 || pkg == null) {
            Log.d(TAG, "no saved target, skipping auto-attach");
            return false;
        }
        if (currentPackage != null && !pkg.equals(currentPackage)) {
            Log.d(TAG, "current package (" + currentPackage + ") != saved ("
                    + pkg + "), skipping auto-attach");
            return false;
        }
        long lastDisconnect = prefs.getLong(KEY_LAST_DISCONNECT_MS, 0L);
        if (System.currentTimeMillis() - lastDisconnect < DEFAULT_DISCONNECT_BACKOFF_MS) {
            Log.d(TAG, "within disconnect backoff, skipping auto-attach");
            return false;
        }
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        Runnable r = () -> doAutoAttach(host, port, pkg);
        pendingAttach = r;
        mainHandler.postDelayed(r, DEFAULT_INITIAL_DELAY_MS);
        return true;
    }

    @MainThread
    public void cancelPending() {
        if (mainHandler != null && pendingAttach != null) {
            mainHandler.removeCallbacks(pendingAttach);
            pendingAttach = null;
        }
    }

    @WorkerThread
    private void doAutoAttach(@NonNull String host, int port, @NonNull String pkg) {
        // PR-D5: 在后台线程做一次 TCP probe,只有 server 接受连接时才
        // 真正调用 DebuggerController.connect. 这避免了 IDE 一开就
        // 弹 "connect refused" 错误.
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), (int) DEFAULT_PROBE_TIMEOUT_MS);
            if (s.isConnected()) {
                Log.i(TAG, "auto-attaching to " + host + ":" + port + " for " + pkg);
                DebuggerController.getInstance().connect(host, port);
            }
        } catch (Throwable t) {
            Log.w(TAG, "auto-attach probe failed: " + t.getMessage());
        }
    }
}
