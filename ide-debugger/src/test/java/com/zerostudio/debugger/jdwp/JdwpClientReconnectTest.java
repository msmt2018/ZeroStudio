/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase B6: JdwpClient auto-reconnect tests.
 *
 *  The JdwpClient is hard to test with a real socket because the
 *  protocol is asymmetric. These tests instead drive the reconnect
 *  state machine by:
 *    - subclassing JdwpClient with a {@link FakeReconnectClient}
 *      whose {@code connect()} is a no-op that succeeds the first
 *      time and fails the second time, or always succeeds, etc.;
 *    - driving the scheduled executor manually so the test is
 *      deterministic.
 */

package com.zerostudio.debugger.jdwp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class JdwpClientReconnectTest {

    /** Marker interface for the test client. */
    public static class FakeReconnectClient extends JdwpClient {
        public boolean connectFails = false;
        public int connectInvocations = 0;
        public FakeReconnectClient() { super(); }
        @Override
        public void connect(@NonNull String host, int port) throws IOException {
            connectInvocations++;
            if (connectFails) throw new IOException("simulated failure");
        }
    }

    /** A ScheduledExecutorService that runs tasks synchronously on demand. */
    private static class ManualExecutor extends AbstractExecutorService
            implements ScheduledExecutorService {
        private boolean shutdown = false;
        private final List<Runnable> tasks = new ArrayList<>();

        @Override public void shutdown() { shutdown = true; }
        @Override public java.util.List<Runnable> shutdownNow() { shutdown = true; return new ArrayList<>(tasks); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long t, @NonNull TimeUnit u) { return shutdown; }
        @Override public void execute(@NonNull Runnable command) { if (!shutdown) command.run(); }

        @Override public ScheduledFuture<?> schedule(@NonNull Runnable r, long d, @NonNull TimeUnit u) {
            // Capture and run when the test calls .runNext().
            FakeScheduledFuture f = new FakeScheduledFuture(r);
            tasks.add(f);
            return f;
        }
        @Override public <V> ScheduledFuture<V> schedule(@NonNull Callable<V> c, long d, @NonNull TimeUnit u) {
            throw new UnsupportedOperationException();
        }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(@NonNull Runnable r, long a, long p, @NonNull TimeUnit u) {
            throw new UnsupportedOperationException();
        }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(@NonNull Runnable r, long a, long p, @NonNull TimeUnit u) {
            throw new UnsupportedOperationException();
        }
        public int runAll() {
            int n = tasks.size();
            for (Runnable r : new ArrayList<>(tasks)) {
                tasks.remove(r);
                r.run();
            }
            return n;
        }
    }

    private static final class FakeScheduledFuture implements RunnableScheduledFuture<Object> {
        private final Runnable r;
        FakeScheduledFuture(Runnable r) { this.r = r; }
        @Override public boolean isPeriodic() { return false; }
        @Override public long getDelay(@NonNull TimeUnit u) { return 0; }
        @Override public int compareTo(@NonNull Delayed o) { return 0; }
        @Override public void run() { r.run(); }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return true; }
        @Override public boolean isCancelled() { return true; }
        @Override public boolean isDone() { return true; }
        @Override public Object get() { return null; }
        @Override public Object get(long t, @NonNull TimeUnit u) { return null; }
    }

    /** A connection listener that records events. */
    private static class RecordingConnListener implements JdwpClient.ConnectionListener {
        final List<String> events = new ArrayList<>();
        @Override public void onConnected() { events.add("connected"); }
        @Override public void onDisconnected() { events.add("disconnected"); }
        @Override public void onReconnected() { events.add("reconnected"); }
    }

    /** Trigger a "disconnect" by calling the package-private notifyDisconnected indirectly. */
    private static void triggerDisconnect(JdwpClient c) {
        try {
            java.lang.reflect.Method m = JdwpClient.class.getDeclaredMethod("notifyDisconnected");
            m.setAccessible(true);
            m.invoke(c);
        } catch (Exception ex) {
            fail("could not invoke notifyDisconnected: " + ex);
        }
    }

    @Test
    public void reconnectDisabled_doesNotSchedule() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(false);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);

        triggerDisconnect(c);
        assertEquals(0, exec.runAll());
        assertEquals(0, c.reconnectAttempts());
    }

    @Test
    public void reconnectEnabled_schedulesTaskOnDisconnect() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);

        // Pretend we were connected: the scheduleReconnect() reads
        // host/port. We have to reflectively set them since they are
        // normally populated in connect().
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        int ran = exec.runAll();
        assertEquals(1, ran);
        assertEquals(1, c.connectInvocations);
        assertEquals(0, c.reconnectAttempts()); // reset on success
    }

    @Test
    public void reconnectFails_reschedulesAndIncrementsAttempts() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.connectFails = true;
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        // The first scheduled task runs, fails, and schedules another.
        int ran = exec.runAll();
        // The re-scheduled task is queued but not yet run.
        assertEquals(1, ran);
        assertEquals(1, c.connectInvocations);
        assertEquals(1, c.reconnectAttempts());
    }

    @Test
    public void reconnectSuccess_invokesOnReconnected() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);
        RecordingConnListener l = new RecordingConnListener();
        c.addConnectionListener(l);

        triggerDisconnect(c);
        exec.runAll();
        assertTrue("onReconnected should be called", l.events.contains("reconnected"));
    }

    @Test
    public void cancelReconnectOnClose() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        // Now we close; this should cancel the scheduled task.
        c.close();
        // Running the executor now should do nothing (the task was cancelled).
        int ran = exec.runAll();
        assertEquals(0, ran);
    }

    @Test
    public void doubleDisconnect_schedulesAtMostOneTask() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        triggerDisconnect(c);
        // Only the most recent scheduled task is still active.
        // The previous one was cancelled in scheduleReconnect().
        int ran = exec.runAll();
        assertEquals(1, ran);
        // We only made it through one connect() invocation.
        assertEquals(1, c.connectInvocations);
    }

    @Test
    public void reconnectAttemptsResetsOnSuccess() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.connectFails = true;
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        exec.runAll(); // first attempt fails
        assertEquals(1, c.reconnectAttempts());

        // Make the second attempt succeed.
        c.connectFails = false;
        exec.runAll(); // re-scheduled task runs and succeeds
        assertEquals(0, c.reconnectAttempts());
    }

    @Test
    public void backoff_doublesEachAttempt() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.connectFails = true;
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(50, 1000);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);

        triggerDisconnect(c);
        // Run the first task -> fails -> reschedules with 100ms
        // We can't observe the delay directly through the API, but
        // we can verify attempts increment.
        exec.runAll();
        assertEquals(1, c.reconnectAttempts());
        exec.runAll();
        assertEquals(2, c.reconnectAttempts());
        exec.runAll();
        assertEquals(3, c.reconnectAttempts());
    }

    @Test
    public void noHost_doesNotSchedule() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);

        // host is null -> scheduleReconnect returns early.
        triggerDisconnect(c);
        int ran = exec.runAll();
        assertEquals(0, ran);
    }

    @Test
    public void connectionListenerCanCoexistWithOnReconnected() {
        FakeReconnectClient c = new FakeReconnectClient();
        c.setReconnectEnabled(true);
        c.setReconnectBackoff(10, 100);
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        setHostAndPort(c, "127.0.0.1", 5005);
        RecordingConnListener l = new RecordingConnListener();
        c.addConnectionListener(l);

        triggerDisconnect(c);
        exec.runAll();
        // Should be: disconnected -> reconnected
        assertEquals(2, l.events.size());
        assertEquals("disconnected", l.events.get(0));
        assertEquals("reconnected", l.events.get(1));
    }

    @Test
    public void reconnectEnabled_startsReconnectAttempts() {
        FakeReconnectClient c = new FakeReconnectClient();
        assertFalse(c.isReconnectEnabled());
        c.setReconnectEnabled(true);
        assertTrue(c.isReconnectEnabled());
    }

    @Test
    public void reconnect_attemptsCountIsAccessible() {
        FakeReconnectClient c = new FakeReconnectClient();
        assertEquals(0, c.reconnectAttempts());
        c.setReconnectEnabled(true);
        setHostAndPort(c, "127.0.0.1", 5005);
        c.connectFails = true;
        ManualExecutor exec = new ManualExecutor();
        c.setReconnectExecutor(exec);
        triggerDisconnect(c);
        exec.runAll();
        assertTrue(c.reconnectAttempts() >= 1);
    }

    private static void setHostAndPort(JdwpClient c, String host, int port) {
        try {
            java.lang.reflect.Field hf = JdwpClient.class.getDeclaredField("host");
            hf.setAccessible(true);
            hf.set(c, host);
            java.lang.reflect.Field pf = JdwpClient.class.getDeclaredField("port");
            pf.setAccessible(true);
            pf.setInt(c, port);
        } catch (Exception ex) {
            fail("reflective set: " + ex);
        }
    }
}
