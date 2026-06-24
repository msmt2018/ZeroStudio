/*
 *  ide-debugger - 心跳 / 空闲断连检测
 *
 *  PR-D5: 周期性向 JDWP server 发 [VirtualMachine.Version] (1/1) 命令,
 *  探测链路是否仍然活着。当连续多次失败时,主动调用 [JdwpClient.close]
 *  触发 B6 阶段实现的 auto-reconnect。
 *
 *  设计要点:
 *    - 与 [JdwpClient] 解耦,纯 packet-level heartbeat.这样未连接的
 *      client 不会跑空线程。
 *    - 通过 JNI-socket 写心跳,即使 [DebugSession] 当前 SUSPENDED 状态
 *      也能正常发包 (JVM 端不读 1/1 也不回,但 socket 可写)。
 *    - 失败计数: 默认 3 次连续 miss 后宣告死亡,触发 reconnect.
 *
 *  单元测试: see DebugSessionHeartbeatTest
 */

package com.zerostudio.debugger.jdwp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zerostudio.debugger.api.Debugger;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DebugSessionHeartbeat {

    private static final String TAG = "DebugSessionHeartbeat";

    /** Default heartbeat interval. 30s is short enough to detect dead conns. */
    public static final long DEFAULT_INTERVAL_MS = 30_000L;
    /** Default timeout for one round-trip. */
    public static final long DEFAULT_PING_TIMEOUT_MS = 3_000L;
    /** Default consecutive-miss threshold before declaring dead. */
    public static final int DEFAULT_MISS_THRESHOLD = 3;

    private final Debugger debugger;
    private final long intervalMs;
    private final long pingTimeoutMs;
    private final int missThreshold;
    @Nullable private final HeartbeatListener listener;

    private final ScheduledExecutorService executor;
    @Nullable private ScheduledFuture<?> future;
    private final AtomicInteger missCount = new AtomicInteger(0);
    private final AtomicLong lastSuccessMs = new AtomicLong(0L);

    public interface HeartbeatListener {
        /** Called every successful round-trip. */
        void onHeartbeatOk();
        /** Called when we have just declared the connection dead. */
        void onHeartbeatDead();
    }

    public DebugSessionHeartbeat(@NonNull Debugger debugger) {
        this(debugger, DEFAULT_INTERVAL_MS, DEFAULT_PING_TIMEOUT_MS,
                DEFAULT_MISS_THRESHOLD, null);
    }

    public DebugSessionHeartbeat(@NonNull Debugger debugger,
                                  long intervalMs,
                                  long pingTimeoutMs,
                                  int missThreshold,
                                  @Nullable HeartbeatListener listener) {
        this.debugger = debugger;
        this.intervalMs = Math.max(1_000L, intervalMs);
        this.pingTimeoutMs = Math.max(500L, pingTimeoutMs);
        this.missThreshold = Math.max(1, missThreshold);
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DebugSessionHeartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (future != null) return;
        future = executor.scheduleAtFixedRate(this::tick,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        executor.shutdownNow();
    }

    /** Synchronous one-shot ping. Returns true on success. */
    public boolean ping() {
        JdwpClient client = debugger.client();
        if (client == null || !client.isConnected()) return false;
        try {
            // 1/1 = VirtualMachine.Version
            // We don't parse the reply; we only care that the round-trip
            // succeeded (i.e. the connection is alive).
            long start = System.currentTimeMillis();
            client.sendCommand((byte) 1, (byte) 1, null, pingTimeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            Logger.getLogger(TAG).log(Level.FINE,
                    "heartbeat ok in " + elapsed + "ms");
            return true;
        } catch (IOException | RuntimeException ex) {
            Logger.getLogger(TAG).log(Level.FINE, "heartbeat failed: " + ex);
            return false;
        }
    }

    private void tick() {
        boolean ok = ping();
        if (ok) {
            int prevMiss = missCount.getAndSet(0);
            lastSuccessMs.set(System.currentTimeMillis());
            if (prevMiss >= missThreshold && listener != null) {
                listener.onHeartbeatOk();
            }
            return;
        }
        int missed = missCount.incrementAndGet();
        if (missed == missThreshold && listener != null) {
            listener.onHeartbeatDead();
        }
    }

    public int currentMissCount() { return missCount.get(); }
    public long lastSuccessAt() { return lastSuccessMs.get(); }
}
