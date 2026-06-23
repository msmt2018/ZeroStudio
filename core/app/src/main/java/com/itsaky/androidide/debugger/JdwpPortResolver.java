/*
 *  ZeroStudio IDE - 调试器 / JDWP 端口发现
 *
 *  PR-D2: 通过 ContentProvider.call() 拿到目标 App 的 JDWP 监听端口。
 *
 *  ide-log-plugin 的 DebuggerBootstrapProvider 在 onCreate() 中
 *  启了一个 ServerSocket(port=0 → OS 分配),并通过 call("getJdwpPort", ...)
 *  暴露该端口。IDE 端在 launch 完目标 App 之后,会用这个工具类去
 *  poll,直到返回有效 port,然后再 connect 到 127.0.0.1:<port>。
 *
 *  注意: ContentProvider.call() 是同步调用,这里用一个小线程池 + 1s
 *  间隔做 retry,直到 timeout。
 */

package com.itsaky.androidide.debugger;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdwpPortResolver {

    private static final String TAG = "JdwpPortResolver";

    /** Authority must match IdeDebuggerInitScriptPlugin.BOOTSTRAP_AUTHORITY / DebuggerBootstrapProvider.AUTHORITY. */
    public static final String AUTHORITY = "com.zerostudio.debugger.bootstrap";

    public static final String METHOD_GET_JDWP_PORT = "getJdwpPort";
    public static final String METHOD_GET_LOGCAT_PORT = "getLogcatPort";
    public static final String KEY_PORT = "port";

    /** 单次 call() 的默认 poll 间隔 (毫秒). */
    public static final long DEFAULT_POLL_INTERVAL_MS = 800L;
    /** 默认总等待 timeout (毫秒). */
    public static final long DEFAULT_TIMEOUT_MS = 15_000L;

    private final ContentResolver resolver;
    private final ExecutorService executor;

    public JdwpPortResolver(@NonNull Context context) {
        this.resolver = context.getApplicationContext().getContentResolver();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "JdwpPortResolver");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Poll the target app's ContentProvider for the JDWP port.
     * Returns the port (1..65535) on success, or 0 on timeout.
     */
    @WorkerThread
    public int awaitJdwpPort(@NonNull String packageName,
                             long timeoutMs,
                             long pollIntervalMs) {
        if (packageName.isEmpty()) {
            Log.w(TAG, "awaitJdwpPort: empty packageName");
            return 0;
        }
        AtomicInteger lastSeen = new AtomicInteger(0);
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            int port = callOnce(METHOD_GET_JDWP_PORT, packageName);
            if (port > 0) {
                Log.i(TAG, "Resolved JDWP port=" + port + " for " + packageName
                        + " (attempt " + attempt + ")");
                return port;
            }
            lastSeen.set(port);
            try {
                Thread.sleep(Math.max(50L, pollIntervalMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
        Log.w(TAG, "awaitJdwpPort timed out after " + timeoutMs + "ms for " + packageName
                + " (lastSeen=" + lastSeen.get() + ", attempts=" + attempt + ")");
        return 0;
    }

    /**
     * Single-shot call (no retry, no blocking). Returns the port int
     * or 0 if the call failed / the provider returned no port.
     */
    @WorkerThread
    public int callOnce(@NonNull String method, @NonNull String packageName) {
        final Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(packageName)
                .build();
        try {
            // Run the synchronous call() in the executor so we can
            // apply a hard timeout — in pathological cases (target
            // app is hung) the call() can block for a long time.
            Future<Bundle> f = executor.submit(new PortCall(resolver, method, uri));
            Bundle b = f.get(2L, TimeUnit.SECONDS);
            if (b == null) return 0;
            return b.getInt(KEY_PORT, 0);
        } catch (TimeoutException te) {
            Log.w(TAG, "call() timed out: method=" + method + " pkg=" + packageName);
            return 0;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException ee) {
            Log.w(TAG, "call() failed: " + ee.getCause());
            return 0;
        } catch (Throwable t) {
            Log.w(TAG, "call() threw: " + t.getMessage());
            return 0;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Result of the build → install → launch flow. */
    public static final class LaunchResult {
        public final boolean launched;
        @Nullable public final String packageName;
        @Nullable public final String error;
        public LaunchResult(boolean launched, @Nullable String packageName, @Nullable String error) {
            this.launched = launched;
            this.packageName = packageName;
            this.error = error;
        }
    }

    /** Callable that wraps ContentResolver.call() so we can apply timeouts. */
    private static final class PortCall implements Callable<Bundle> {
        private final ContentResolver resolver;
        private final String method;
        private final Uri uri;
        PortCall(ContentResolver resolver, String method, Uri uri) {
            this.resolver = resolver;
            this.method = method;
            this.uri = uri;
        }
        @Override
        public Bundle call() {
            try {
                return resolver.call(AUTHORITY, method, null, null);
            } catch (SecurityException se) {
                // Target app not yet installed / wrong authority / etc.
                Log.w(TAG, "ContentResolver.call denied: " + se.getMessage());
                return null;
            } catch (Throwable t) {
                Log.w(TAG, "ContentResolver.call failed: " + t.getMessage());
                return null;
            }
        }
    }
}
