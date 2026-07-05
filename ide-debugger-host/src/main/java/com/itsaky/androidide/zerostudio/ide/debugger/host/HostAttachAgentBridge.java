/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostAttachAgentBridge: 给 HostAttachAgentBootstrap 用的轻量字节桥。
 *  (单独的 Java 类, 不依赖 Kotlin object, 方便 ContentProvider 早期调用)
 *
 *  行为跟 HostAttachAgent.bridgeBytes 一样, 不过是 Java 版本, 同步 fix:
 *    - pump 响应 Thread.interrupt
 *    - 任一 pump 结束 -> close 两边 stream -> interrupt 另一 thread
 *    - 用 CountDownLatch 同步, join 带超时
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host;

import android.net.LocalSocket;
import android.util.Log;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class HostAttachAgentBridge {
    private static final String TAG = "HostAttachAgentBridge";

    /** Maximum wait for the second pump thread to drain after the first one ended. */
    private static final long DRAIN_JOIN_TIMEOUT_MS = 2_000L;

    static void bridge(LocalSocket ide, LocalSocket jdwp) throws Exception {
        final InputStream ideIn = ide.getInputStream();
        final OutputStream ideOut = ide.getOutputStream();
        final InputStream jdwpIn = jdwp.getInputStream();
        final OutputStream jdwpOut = jdwp.getOutputStream();
        bridge(ideIn, ideOut, jdwpIn, jdwpOut);
    }

    static void bridge(InputStream ideIn, OutputStream ideOut,
                       InputStream jdwpIn, OutputStream jdwpOut) throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        Thread a = new Thread(() -> {
            try {
                pump(ideIn, jdwpOut, "ide->jdwp");
            } finally {
                latch.countDown();
            }
        }, "haab-ide2jdwp");
        Thread b = new Thread(() -> {
            try {
                pump(jdwpIn, ideOut, "jdwp->ide");
            } finally {
                latch.countDown();
            }
        }, "haab-jdwp2ide");
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();

        // 等任一 pump 先结束
        latch.await();
        // 关两侧 output stream 触发另一 pump 从 read 阻塞退出
        try { ideOut.close(); } catch (Throwable ignored) { }
        try { jdwpOut.close(); } catch (Throwable ignored) { }
        // interrupt 兜底
        a.interrupt();
        b.interrupt();
        // 等第二 pump 收尾, 带超时
        latch.await(DRAIN_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        a.join(DRAIN_JOIN_TIMEOUT_MS);
        b.join(DRAIN_JOIN_TIMEOUT_MS);
    }

    private static void pump(InputStream in, OutputStream out, String name) {
        byte[] buf = new byte[8192];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int n;
                try {
                    n = in.read(buf);
                } catch (InterruptedIOException ie) {
                    Log.i(TAG, name + " interrupted");
                    return;
                }
                if (n < 0) {
                    Log.i(TAG, name + " EOF");
                    return;
                }
                out.write(buf, 0, n);
                out.flush();
            }
            Log.i(TAG, name + " loop exit (interrupted)");
        } catch (Throwable t) {
            Log.w(TAG, name + " ended: " + t.getMessage());
        }
    }

    private HostAttachAgentBridge() { /* utility */ }
}
