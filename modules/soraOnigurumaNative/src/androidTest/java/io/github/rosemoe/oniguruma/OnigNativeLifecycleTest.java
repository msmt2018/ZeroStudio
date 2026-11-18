package io.github.rosemoe.oniguruma;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OnigNativeLifecycleTest {

    @Test
    public void releaseIsIdempotentAndInvalidatesHandle() {
        long handle = OnigNative.newRegex("(foo)", false);
        assertTrue("regex compilation failed", handle != 0L);

        byte[] source = "xxfooyy".getBytes(StandardCharsets.UTF_8);
        int[] match = OnigNative.regexSearch(handle, 1L, source, 0, source.length);
        assertNotNull(match);
        assertTrue(match.length >= 2);
        assertArrayEquals(new int[]{2, 5}, new int[]{match[0], match[1]});

        OnigNative.releaseRegex(handle);
        OnigNative.releaseRegex(handle);

        assertNull(OnigNative.regexSearch(handle, 1L, source, 0, source.length));
    }

    @Test
    public void releasingDuringSearchDoesNotFreeAnInUseRegex() throws Exception {
        final long handle = OnigNative.newRegex("foo|bar", false);
        assertTrue("regex compilation failed", handle != 0L);

        final byte[] source = "prefix-foo-suffix".getBytes(StandardCharsets.UTF_8);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> search = executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        int[] result = OnigNative.regexSearch(handle, i + 1L, source, 0, source.length);
                        if (result != null) {
                            assertTrue(result.length >= 2);
                            assertEqualsRange(result[0], result[1], 7, 10);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });

            start.countDown();
            OnigNative.releaseRegex(handle);
            search.get(10, TimeUnit.SECONDS);

            assertNull(OnigNative.regexSearch(handle, 2001L, source, 0, source.length));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertEqualsRange(int actualStart, int actualEnd,
                                          int expectedStart, int expectedEnd) {
        assertTrue("unexpected match start: " + actualStart, actualStart == expectedStart);
        assertTrue("unexpected match end: " + actualEnd, actualEnd == expectedEnd);
    }
}
