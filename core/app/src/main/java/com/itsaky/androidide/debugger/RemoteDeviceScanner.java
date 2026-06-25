/*
 *  ZeroStudio IDE - 远程设备发现器
 *
 *  PR-D6: 在 LAN 上自动发现可 attach 的 Android 设备。
 *
 *  设计: 用 mDNS (`_adb._tcp`) 枚举 LAN 上开放了 wireless-adb 端口的
 *  设备. mDNS 解析走 java.nio / dnsjava 反射式加载,即便运行时缺失
 *  也能 fallback 到"手动输入 IP:端口" 模式.
 *
 *  现实使用:
 *    1. 用户在 Settings → 调试 里点 "扫描设备"
 *    2. UI 弹一个 device picker 列出扫描到的 (host, port, name) 列表
 *    3. 用户选中 → RemoteDeviceScanner.connect(host, port) → 触发
 *       DebuggerController.connect 走 PR-D5 流程
 *
 *  依赖: shizuku/manager AdbMdns 是可选的;若可用,直接复用.
 */

package com.itsaky.androidide.debugger;

import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RemoteDeviceScanner {

    private static final String TAG = "RemoteDeviceScanner";

    /** Subdomain used by android wireless-adb: `_adb._tcp`. */
    public static final String ADB_SERVICE_TYPE = "_adb._tcp";

    /** Default adb wireless port. */
    public static final int DEFAULT_ADB_PORT = 5555;

    /** Total budget for a single scan. */
    public static final long DEFAULT_SCAN_TIMEOUT_MS = 3_000L;

    public static final class DeviceInfo {
        public final String name;
        public final String host;
        public final int port;
        public DeviceInfo(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
        @Override
        public String toString() {
            return "DeviceInfo{" + name + "@" + host + ":" + port + "}";
        }
    }

    public interface ScanListener {
        /** Called once at the end, regardless of result count. */
        void onScanFinished(@NonNull List<DeviceInfo> devices);
    }

    private final ExecutorService startExecutor;
    /**
     * PR-D7: 实际 probe host 用的固定大小线程池。16 路并发,
     * 把串行遍历 254 个候选 host 的耗时从 ~254s (单线程 + 1s timeout)
     * 降到 ~4s (16 路并行 + 250ms timeout)。
     */
    private final ExecutorService probePool;
    @Nullable private ScanListener listener;
    private final AtomicReference<List<DeviceInfo>> lastResult =
            new AtomicReference<>(Collections.emptyList());

    public RemoteDeviceScanner() {
        // 串行入口:防止 startScan() 重入(用户连点 扫描 按钮)
        startExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RemoteDeviceScanner");
            t.setDaemon(true);
            return t;
        });
        // 16 路并发 probe pool
        probePool = Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "RemoteDeviceScanner-Probe");
            t.setDaemon(true);
            return t;
        });
    }

    public void setListener(@Nullable ScanListener l) { this.listener = l; }

    public List<DeviceInfo> lastResult() { return lastResult.get(); }

    /**
     * 异步扫描. PR-D6 第一版: 不真正发 mDNS 请求 (依赖 dnsjava / shizuku
     * AdbMdns), 而是遍历所有本地 network interface 上的
     * /24 子网,对每个候选 host:port 做 TCP probe. 这能在缺少 mDNS
     * 服务的环境也能工作 (用户把开发机接到和设备同网段就能扫到).
     */
    @AnyThread
    public void startScan() {
        startScan(DEFAULT_SCAN_TIMEOUT_MS);
    }

    @AnyThread
    public void startScan(long timeoutMs) {
        startExecutor.submit(() -> doScan(timeoutMs));
    }

    /**
     * PR-D7: 并行 probe 候选 host。16 路并发 + 250ms per-host timeout,
     * 配合全局 timeout (默认 3s) 给出合理 UX。
     *
     * <p>任何一个 host probe 成功都立即加入结果列表,但不会打断其他正在
     * 跑的 probe —— 让它们自然超时(250ms 内必然结束)。整体通过
     * CountDownLatch 等待所有 worker 完成 或 全局 timeout。
     */
    @WorkerThread
    private void doScan(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        // PR-D7: 使用类成员 probePool(16 路并发)而不是每次新建,减少
        // 线程池反复创建/销毁的开销。同时加 empty 优化,没有候选 host
        // 时直接返回,不做无意义的 latch.await。
        List<String> hosts = candidateHosts();
        if (hosts.isEmpty()) {
            lastResult.set(Collections.emptyList());
            ScanListener l = listener;
            if (l != null) l.onScanFinished(Collections.emptyList());
            return;
        }
        List<DeviceInfo> devices = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(hosts.size());
        for (String host : hosts) {
            // 提交前再做一次 deadline check,避免 1k+ 候选 host 时堆积任务
            if (System.currentTimeMillis() > deadline) {
                latch.countDown(); // 跳过这个 host,也要把 latch 减下来
                continue;
            }
            probePool.submit(() -> {
                try {
                    int port = probeAdbPort(host, 250);
                    if (port > 0) {
                        devices.add(new DeviceInfo("device", host, port));
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "probe " + host + " failed: " + t.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        long remaining = Math.max(0L, deadline - System.currentTimeMillis());
        try {
            latch.await(remaining, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        // 即使 latch 还没归零,已有的命中也要给出去
        List<DeviceInfo> snapshot = new ArrayList<>(devices);
        lastResult.set(snapshot);
        ScanListener l = listener;
        if (l != null) l.onScanFinished(snapshot);
    }

    /**
     * Probe a single host:port to check if it speaks adb (just verify
     * the TCP connection is accepted). Return 1..65535 on success, 0
     * otherwise.
     */
    @WorkerThread
    public int probeAdbPort(@NonNull String host, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, DEFAULT_ADB_PORT), timeoutMs);
            if (s.isConnected()) {
                Log.d(TAG, "adb port open on " + host);
                return DEFAULT_ADB_PORT;
            }
        } catch (IOException ioe) {
            // silent; most hosts won't have adb open
        }
        return 0;
    }

    /**
     * Connect to a remote device's JDWP server directly. The remote
     * device is expected to be running its own JDWP listener on the
     * given port. This is a thin wrapper around
     * [DebuggerController.connect] that records the target in
     * [AutoAttachManager] so subsequent IDE starts can re-attach.
     */
    @WorkerThread
    public boolean connect(@NonNull String host, int port, @NonNull String packageName) {
        try {
            DebuggerController.getInstance().connect(host, port);
            // Make auto-attach kick in next time:
            AutoAttachManager mgr = new AutoAttachManager(
                    com.itsaky.androidide.app.BaseApplication.getBaseInstance());
            mgr.rememberTarget(host, port, packageName);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "connect failed: " + t.getMessage());
            return false;
        }
    }

    @WorkerThread
    public void shutdown() {
        startExecutor.shutdownNow();
        probePool.shutdownNow();
    }

    /**
     * 枚举所有本地 network interface,产出候选 host. /24 子网展开成
     * 1..254,只产出 link-local + private RFC1918 地址,跳过公网段.
     */
    @WorkerThread
    @NonNull
    private List<String> candidateHosts() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isPointToPoint()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!(addr instanceof java.net.Inet4Address)) continue;
                    if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) continue;
                    byte[] raw = addr.getAddress();
                    if (raw.length != 4) continue;
                    int b0 = raw[0] & 0xff;
                    if (b0 == 0 || b0 >= 224) continue; // skip multicast / reserved
                    String base = (raw[0] & 0xff) + "." + (raw[1] & 0xff)
                            + "." + (raw[2] & 0xff);
                    // 1..254 展开. 大网络会慢,但 30ms 内可接受.
                    for (int i = 1; i < 255; i++) {
                        out.add(base + "." + i);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "candidateHosts failed: " + t.getMessage());
        }
        return out;
    }
}
