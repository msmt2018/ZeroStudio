package com.zerostudio.language;

import com.zerostudio.language.index.InMemoryProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;
import com.zerostudio.language.service.BreakpointNavigation;
import com.zerostudio.language.service.CallNavigation;
import com.zerostudio.language.service.DebugHostSync;
import com.zerostudio.language.service.EditorIntegration;
import com.zerostudio.language.service.LanguageService;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test for the full breakpoint / host / editor flow.
 *
 * <p>This is the canonical demonstration of the user's primary requirement:
 * <ol>
 *   <li>The host application runs in another process; the debugger hits a
 *       breakpoint on a real line in a real source file.</li>
 *   <li>The host UI must freeze.</li>
 *   <li>The editor must open the source file at that line.</li>
 *   <li>The user must be able to click a token (e.g. {@code Toast.makeText})
 *       and have the editor jump to the declaration - even across files
 *       (Go-to-Definition).</li>
 *   <li>The user must be able to navigate to the next / previous
 *       breakpoint line in the file.</li>
 *   <li>Callers / callees of a method must be discoverable.</li>
 *   <li>Unfreezing must restore the host.</li>
 * </ol>
 */
public class DebugHostSyncTest {

    private LanguageService language;
    private FakeHost host;
    private FakeOpenHandler open;
    private DebugHostSync sync;

    @Before
    public void setUp() {
        language = new LanguageService(new InMemoryProjectIndex());
        host = new FakeHost();
        open = new FakeOpenHandler();
        sync = new DebugHostSync(language, host, open);
    }

    @Test
    public void fullFlow_breakpointHitFreezesHostAndOpensSource() throws Exception {
        // 1) Two source files - the API and the call site.
        String toastSrc =
                "package android.widget;\n" +
                "public class Toast {\n" +
                "    public static Toast makeText(Object ctx, String text, int dur) { return new Toast(); }\n" +
                "    public void show() {}\n" +
                "}\n";
        language.parseText("android/widget/Toast.java", toastSrc, LanguageId.JAVA);

        String mainSrc =
                "package com.example;\n" +
                "import android.widget.Toast;\n" +
                "public class MainActivity {\n" +
                "    void onButtonClick(Object ctx) {\n" +
                "        Toast.makeText(ctx, \"hi\", 1).show();\n" +
                "    }\n" +
                "}\n";
        ParsedFile main = language.parseText(
                "com/example/MainActivity.java", mainSrc, LanguageId.JAVA);

        // 2) Simulate the debugger hitting line 5 (0-based 4) in MainActivity.
        SourceLocation hit = sync.onBreakpointHit(main.path, 4, 9)
                .get(2, TimeUnit.SECONDS);

        // 3) Host must be frozen.
        assertTrue("host should be frozen on breakpoint hit (freezeCount="
                + host.freezeCount.get() + ")", host.frozen.get());
        assertNotNull("freeze reason should be recorded", host.reason.get());

        // 4) Editor must have opened the file at the right line.
        EditorIntegration.OpenRequest firstOpen = open.last.get();
        assertNotNull("editor should have received an open request", firstOpen);
        assertEquals(main.path, firstOpen.file);
        assertEquals(4, firstOpen.range.start.line);
    }

    @Test
    public void goToDefinitionWorksFromCallSite() throws Exception {
        // Same two files as above, plus an extra one to ensure multi-file
        // resolution works.
        language.parseText("android/widget/Toast.java",
                "package android.widget;\n" +
                "public class Toast {\n" +
                "    public static Toast makeText(Object ctx, String text, int dur) { return new Toast(); }\n" +
                "    public void show() {}\n" +
                "}\n",
                LanguageId.JAVA);

        ParsedFile main = language.parseText(
                "com/example/MainActivity.java",
                "package com.example;\n" +
                "import android.widget.Toast;\n" +
                "public class MainActivity {\n" +
                "    void onButtonClick(Object ctx) {\n" +
                "        Toast.makeText(ctx, \"hi\", 1).show();\n" +
                "    }\n" +
                "}\n",
                LanguageId.JAVA);

        // The user is paused at the call site and Ctrl+clicks on
        // "makeText" - the editor must jump to the Toast.java declaration.
        SourceLocation where = main.references.stream()
                .filter(r -> r.name.equals("makeText"))
                .findFirst()
                .map(r -> new SourceLocation(main.path, r.range.start))
                .orElseThrow(() -> new AssertionError("makeText ref not found"));

        // Place the cursor at the breakpoint line first.
        sync.onBreakpointHit(main.path, 4, 9).get(2, TimeUnit.SECONDS);
        // Then the user invokes Go-to-Definition.
        ResolutionResult result = sync.goToDefinition(where)
                .get(2, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue("expected to resolve to Toast.makeText, got: " + result,
                result.isResolved());
        assertEquals("makeText", result.targetSymbol.name);
        assertEquals("android/widget/Toast.java", result.targetFile);
    }

    @Test
    public void unfreezeRestoresHost() throws Exception {
        language.parseText("a/A.java",
                "class A { void m() {} }\n", LanguageId.JAVA);
        sync.onBreakpointHit("a/A.java", 0, 0).get(2, TimeUnit.SECONDS);
        assertTrue(sync.isFrozen());
        sync.unfreeze();
        assertFalse(sync.isFrozen());
    }

    @Test
    public void nextAndPreviousBreakpointNavigation() {
        language.parseText("a/A.java",
                "class A {\n" +
                "    void m1() {}\n" +    // line 1
                "    void m2() {}\n" +    // line 2
                "    void m3() {}\n" +    // line 3
                "}\n",
                LanguageId.JAVA);
        ParsedFile a = language.index().lookup().file("a/A.java");
        // Cursor at line 0; next breakpoint is the next symbol line > 0.
        int next = BreakpointNavigation.nextBreakpointLine(
                a, new SourcePosition(0, 0));
        assertTrue("expected a next breakpoint line, got -1", next > 0);
        // From the last line, prev should jump back.
        int prev = BreakpointNavigation.previousBreakpointLine(
                a, new SourcePosition(a.symbols.get(a.symbols.size() - 1).range.start.line,
                        0));
        assertTrue("expected a prev breakpoint line, got -1", prev >= 0);
    }

    @Test
    public void callersAndCalleesAreDiscoverable() {
        String a =
                "class A {\n" +
                "    void target() {}\n" +
                "    void caller() { target(); }\n" +
                "    void caller2() { target(); }\n" +
                "}\n";
        String b =
                "class B {\n" +
                "    void externalCaller() { new A().target(); }\n" +
                "}\n";
        language.parseText("a/A.java", a, LanguageId.JAVA);
        language.parseText("b/B.java", b, LanguageId.JAVA);

        // Find the target method.
        Symbol target = language.index().lookup().byName("target").stream()
                .filter(s -> s.kind == SymbolKind.METHOD)
                .findFirst().orElseThrow();

        CallNavigation nav = new CallNavigation(language.index());
        List<CallNavigation.CallSite> callers = nav.callersOf(target);
        // We expect: caller(), caller2(), externalCaller()
        assertEquals("expected 3 call sites, got " + callers, 3, callers.size());
        assertTrue(callers.stream().anyMatch(c -> c.file.endsWith("A.java")));
        assertTrue(callers.stream().anyMatch(c -> c.file.endsWith("B.java")));

        // Callees of caller() - should be just "target".
        Symbol caller = language.index().lookup().byName("caller").stream()
                .filter(s -> s.kind == SymbolKind.METHOD)
                .findFirst().orElseThrow();
        System.err.println("DEBUG: caller.fqn=" + caller.fqn);
        ParsedFile aFile = language.index().lookup().file("a/A.java");
        for (com.zerostudio.language.model.Reference r : aFile.references) {
            if (r.kind == Reference.ReferenceKind.CALL) {
                System.err.println("DEBUG: ref name=" + r.name
                        + " containerFqn=" + r.containerFqn);
            }
        }
        List<CallNavigation.CallSite> callees = nav.calleesOf(caller);
        assertEquals(1, callees.size());
        assertEquals("target", callees.get(0).reference.name);
    }

    @Test
    public void breakpointNavigationAdvancesCursor() throws Exception {
        language.parseText("a/A.java",
                "class A {\n" +
                "    void m1() {}\n" +
                "    void m2() {}\n" +
                "    void m3() {}\n" +
                "}\n",
                LanguageId.JAVA);

        // Open the file at the top first.
        sync.onBreakpointHit("a/A.java", 0, 0).get(2, TimeUnit.SECONDS);

        SourceLocation next = sync.jumpToNextBreakpoint().get(2, TimeUnit.SECONDS);
        assertNotNull(next);
        assertEquals("a/A.java", next.file);
        assertTrue(next.position.line > 0);

        // The editor must have been told to open at that line.
        EditorIntegration.OpenRequest req = open.last.get();
        assertNotNull(req);
        assertEquals(next.position.line, req.range.start.line);
    }

    // -------- test doubles --------

    static final class FakeHost implements DebugHostSync.HostControl {
        final AtomicBoolean frozen = new AtomicBoolean();
        final AtomicReference<String> reason = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicInteger freezeCount =
                new java.util.concurrent.atomic.AtomicInteger();
        @Override public void freeze(String r) {
            frozen.set(true);
            reason.set(r);
            freezeCount.incrementAndGet();
        }
        @Override public void thaw() { frozen.set(false); reason.set(null); }
        @Override public boolean isFrozen() { return frozen.get(); }
    }

    static final class FakeOpenHandler implements EditorIntegration.OpenHandler {
        final AtomicReference<EditorIntegration.OpenRequest> last =
                new AtomicReference<>();
        final List<EditorIntegration.OpenRequest> all = new ArrayList<>();
        @Override public void open(EditorIntegration.OpenRequest req) {
            last.set(req);
            synchronized (all) { all.add(req); }
        }
    }
}
