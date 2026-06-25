/*
 *  ZeroStudio IDE - run-as 备选路径
 *
 *  PR-D4: 在 shizuku 不可用 / 设备没有 root 权限时,仍然可以 attach
 *  一个 debuggable 目标 app —— 通过 `run-as <pkg> ...` 在目标进程的
 *  uid 下执行 shell 命令。
 *
 *  主要用途:
 *    1. 探测目标 app 是否已经 install: `run-as <pkg> id -u`
 *    2. 把 JDWP-bootstrap 的 jar 注入到目标 data 目录
 *    3. 启动一个 helper 进程做端口转发 (当 ContentProvider.call() 拿不到
 *       端口的时候,作为备选方案)
 *
 *  注意: run-as 仅对 `android:debuggable="true"` 的包起作用,我们的
 *  ide-log-plugin 在 init script 阶段已经把 debuggable 强制开启,所以
 *  默认能走通。
 */

package com.itsaky.androidide.debugger;

import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class RunAsBridge {

    private static final String TAG = "RunAsBridge";

    /** run-as 的最大允许等待时间 (毫秒). */
    public static final long DEFAULT_TIMEOUT_MS = 5_000L;

    /**
     * 通过 `run-as <pkg> id -u` 探测目标 app 是否已安装且为 debuggable.
     * 成功时返回 uid,失败时返回 -1。
     */
    @WorkerThread
    public int probeUid(@NonNull String packageName) {
        if (!CommandValidator.isSafePackageName(packageName)) {
            Log.w(TAG, "probeUid: refusing unsafe package name");
            return -1;
        }
        String out = exec(packageName, "id -u", DEFAULT_TIMEOUT_MS);
        if (out.isEmpty()) return -1;
        try {
            return Integer.parseInt(out.trim());
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "probeUid: bad output: " + out);
            return -1;
        }
    }

    /**
     * 在目标 app 的 uid 下跑一条命令,同步返回 stdout (空字符串 = 失败).
     *
     * 实现: `ProcessBuilder("run-as", pkg, "sh", "-c", command)`.
     * 注意 run-as 在 SELinux 严格的设备上可能被拒;遇到失败时上层会
     * 继续 fallback 到 shizuku.
     *
     * PR-D9.4 (#47) 安全审计: pkg 用 [CommandValidator.isSafePackageName]
     * 校验包名格式, command 用 [CommandValidator.isSafeArg] 拒掉
     * shell 元字符。返回的 String 是空时 caller 应视为失败, 此时
     * 错误详情已 logcat;本方法不抛异常 (worker 线程友好).
     */
    @WorkerThread
    @NonNull
    public String exec(@NonNull String packageName,
                       @NonNull String command,
                       long timeoutMs) {
        if (!CommandValidator.isSafePackageName(packageName)
                || !CommandValidator.isSafeArg(command)) {
            Log.w(TAG, "exec: refusing unsafe package name or command");
            return "";
        }
        if (packageName.isEmpty() || command.isEmpty()) return "";
        Process p = null;
        try {
            p = new ProcessBuilder("run-as", packageName, "sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            // read in a thread so we can apply timeout
            final Process fp = p;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = fp.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
                } catch (Throwable t) {
                    Log.w(TAG, "read failed: " + t.getMessage());
                }
            }, "RunAsBridge-reader");
            reader.setDaemon(true);
            reader.start();
            reader.join(Math.max(0L, timeoutMs));
            if (reader.isAlive()) {
                Log.w(TAG, "exec timed out for " + packageName + ": " + command);
                p.destroy();
                return "";
            }
            int exit = p.waitFor();
            if (exit != 0) {
                Log.w(TAG, "run-as exited " + exit + " for " + packageName);
                return "";
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            Log.w(TAG, "exec failed: " + t.getMessage());
            return "";
        } finally {
            if (p != null && p.isAlive()) p.destroy();
        }
    }

    /**
     * 探测目标 app 的某个文件是否在 data 目录中存在。
     * 用 run-as 实现,比直接 `File.exists()` 在 data 目录上更可靠。
     */
    @AnyThread
    public boolean fileExists(@NonNull String packageName, @NonNull String relativePath) {
        if (!CommandValidator.isSafePackageName(packageName)
                || !CommandValidator.isSafePath(relativePath)) {
            Log.w(TAG, "fileExists: refusing unsafe package or path");
            return false;
        }
        String out = exec(packageName, "test -e " + relativePath + " && echo yes", DEFAULT_TIMEOUT_MS);
        return "yes".equals(out);
    }
}
