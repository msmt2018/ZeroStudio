package com.zerostudio.language.eval;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class EvalHistoryTest {

    @Test
    public void recordsExpression() {
        EvalHistory h = new EvalHistory();
        h.record("1 + 1", new EvalEngine.Result(2L, null));
        assertEquals(1, h.size());
    }

    @Test
    public void recentInReverseOrder() {
        EvalHistory h = new EvalHistory();
        h.record("a", new EvalEngine.Result(1L, null));
        h.record("b", new EvalEngine.Result(2L, null));
        List<EvalHistory.EvalRecord> r = h.recent(10);
        assertEquals(2, r.size());
        assertEquals("b", r.get(0).expression);
        assertEquals("a", r.get(1).expression);
    }

    @Test
    public void enforcesMaxRecords() {
        EvalHistory h = new EvalHistory(3, 10);
        for (int i = 0; i < 10; i++) h.record("e" + i, new EvalEngine.Result((long) i, null));
        assertEquals(3, h.size());
    }

    @Test
    public void uniqueExpressions() {
        EvalHistory h = new EvalHistory();
        h.record("a", new EvalEngine.Result(1L, null));
        h.record("b", new EvalEngine.Result(2L, null));
        h.record("a", new EvalEngine.Result(3L, null));
        List<String> u = h.uniqueExpressions();
        // a, b（最新 a 在前）
        assertEquals(2, u.size());
        assertEquals("a", u.get(0));
        assertEquals("b", u.get(1));
    }

    @Test
    public void recordNullExpressionNoOp() {
        EvalHistory h = new EvalHistory();
        h.record(null, null);
        h.record("", null);
        assertEquals(0, h.size());
    }

    @Test
    public void recordNullResult() {
        EvalHistory h = new EvalHistory();
        h.record("x", null);
        assertEquals(1, h.size());
        assertEquals("null", h.all().get(0).resultDisplay);
    }

    @Test
    public void recordError() {
        EvalHistory h = new EvalHistory();
        h.record("bad", new EvalEngine.Result(null, "division by zero"));
        EvalHistory.EvalRecord r = h.all().get(0);
        assertTrue(r.isError);
        assertEquals("division by zero", r.errorMessage);
    }

    @Test
    public void recordStringValue() {
        EvalHistory h = new EvalHistory();
        h.record("name", new EvalEngine.Result("alice", null));
        assertEquals("\"alice\"", h.all().get(0).resultDisplay);
    }

    @Test
    public void watchRecord() {
        EvalHistory h = new EvalHistory();
        h.recordWatch("x", "x + 1", 42L);
        h.recordWatch("x", "x + 2", 43L);
        List<EvalHistory.WatchRecord> r = h.watchHistory("x");
        assertEquals(2, r.size());
        assertEquals("43", r.get(0).value);
    }

    @Test
    public void watchRecordNullValue() {
        EvalHistory h = new EvalHistory();
        h.recordWatch("x", "x", null);
        assertEquals("null", h.watchHistory("x").get(0).value);
    }

    @Test
    public void watchHistoryEnforcesMax() {
        EvalHistory h = new EvalHistory(50, 3);
        for (int i = 0; i < 10; i++) h.recordWatch("x", "x", (long) i);
        assertEquals(3, h.watchHistory("x").size());
    }

    @Test
    public void watchHistoryNonExistent() {
        EvalHistory h = new EvalHistory();
        assertTrue(h.watchHistory("nonexistent").isEmpty());
    }

    @Test
    public void clear() {
        EvalHistory h = new EvalHistory();
        h.record("a", new EvalEngine.Result(1L, null));
        h.recordWatch("x", "x", 1L);
        h.clear();
        assertEquals(0, h.size());
        assertTrue(h.watchHistory("x").isEmpty());
    }

    @Test
    public void replayRecalculates() {
        EvalHistory h = new EvalHistory();
        h.record("2 + 3", new EvalEngine.Result(0L, null));
        List<EvalHistory.EvalRecord> replayed = h.replay(null, 10);
        assertEquals(1, replayed.size());
        assertEquals("5", replayed.get(0).resultDisplay);
    }

    @Test
    public void replayLimitedByN() {
        EvalHistory h = new EvalHistory();
        for (int i = 0; i < 5; i++) h.record("1 + 1", new EvalEngine.Result(2L, null));
        List<EvalHistory.EvalRecord> replayed = h.replay(null, 2);
        assertEquals(2, replayed.size());
    }
}
