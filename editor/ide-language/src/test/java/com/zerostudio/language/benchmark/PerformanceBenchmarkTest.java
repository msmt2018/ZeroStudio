package com.zerostudio.language.benchmark;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceBenchmarkTest {

    @Test
    public void runsAllBenchmarks() {
        // 跑全部基准，不抛异常即可
        java.util.List<PerformanceBenchmark.Result> results = PerformanceBenchmark.runAll();
        assertFalse("expected non-empty results", results.isEmpty());
        for (PerformanceBenchmark.Result r : results) {
            assertNotNull(r.name);
            assertTrue("elapsed should be non-negative: " + r.name, r.elapsedMs >= 0);
            assertTrue("ops should be positive: " + r.name, r.ops > 0);
        }
    }

    @Test
    public void mainRunsWithoutException() {
        // 重定向 stdout 防止污染测试输出
        java.io.PrintStream orig = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            PerformanceBenchmark.main(new String[]{});
        } finally {
            System.setOut(orig);
        }
    }

    @Test
    public void resultToString() {
        PerformanceBenchmark.Result r = new PerformanceBenchmark.Result("test", 100, 1000);
        String s = r.toString();
        assertTrue(s.contains("test"));
        assertTrue(s.contains("1000"));
        assertTrue(s.contains("100"));
    }

    @Test
    public void opsPerSecondCalculation() {
        // 1000 ops in 1000 ms = 1000 ops/sec
        PerformanceBenchmark.Result r = new PerformanceBenchmark.Result("a", 1000, 1000);
        assertEquals(1000, r.opsPerSecond);
    }

    @Test
    public void zeroElapsed() {
        PerformanceBenchmark.Result r = new PerformanceBenchmark.Result("a", 0, 5);
        // elapsed=0 时 opsPerSecond=0
        assertEquals(0, r.opsPerSecond);
    }
}
