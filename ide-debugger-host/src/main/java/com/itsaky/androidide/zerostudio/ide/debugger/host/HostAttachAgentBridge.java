/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostAttachAgentBridge: 给 HostAttachAgentBootstrap 用的轻量字节桥。
 *  (单独的 Java 类, 不依赖 Kotlin object, 方便 ContentProvider 早期调用)
 *
 *  行为跟 HostAttachAgent.bridgeBytes 一样, 不过是 Java 版本。
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host;

import android.net.LocalSocket;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

final class HostAttachAgentBridge {
    private static final String TAG = "HostAttachAgentBridge";

    static void bridge(LocalSocket ide, LocalSocket jdwp) throws Exception {
        final InputStream ideIn = ide.getInputStream();
        final OutputStream ideOut = ide.getOutputStream();
        final InputStream jdwpIn = jdwp.getInputStream();
        final OutputStream jdwpOut = jdwp.getOutputStream();

        Thread a = new Thread(() -> pump(ideIn, jdwpOut, "ide->jdwp"), "haab-ide2jdwp");
        Thread b = new Thread(() -> pump(jdwpIn, ideOut, "jdwp->ide"), "haab-jdwp2ide");
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();
        a.join();
        try { ide.close(); } catch (Throwable t) { /* ignore */ }
        try { jdwp.close(); } catch (Throwable t) { /* ignore */ }
        b.join(2000);
    }

    private static void pump(InputStream in, OutputStream out, String name) {
        byte[] buf = new byte[8192];
        try {
            while (true) {
                int n = in.read(buf);
                if (n < 0) {
                    Log.i(TAG, name + " EOF");
                    return;
                }
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Throwable t) {
            Log.w(TAG, name + " ended: " + t.getMessage());
        }
    }

    private HostAttachAgentBridge() { /* utility */ }
}
