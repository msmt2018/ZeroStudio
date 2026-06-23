package com.zerostudio.language;

import com.zerostudio.language.index.InMemoryProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.GoToDefinitionService;
import com.zerostudio.language.service.LanguageService;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GoToDefinitionTest {

    /**
     * The example from the design discussion:
     *
     * <pre>
     * val context = LocalContext.current
     * Button(onClick = { val text = "Hello toast!"
     *     Toast.makeText(context, text, duration).show() })
     * </pre>
     *
     * Setting a breakpoint on {@code Toast.makeText} should jump the editor
     * to the call site {@code Toast.makeText(context, text, duration)}.
     */
    @Test
    public void resolvesCallAcrossFiles() {
        LanguageService svc = new LanguageService(new InMemoryProjectIndex());

        // 1) A simple Toast.java defining the API
        String toastSrc =
                "package android.widget;\n" +
                "public class Toast {\n" +
                "    public static Toast makeText(Object ctx, String text, int dur) { return new Toast(); }\n" +
                "    public void show() {}\n" +
                "}\n";
        ParsedFile toast = svc.parseText("android/widget/Toast.java", toastSrc,
                com.zerostudio.language.model.LanguageId.JAVA);
        assertNotNull(toast);
        assertTrue(toast.parseError == null);

        // 2) A MainActivity.java that calls Toast.makeText
        String mainSrc =
                "package com.example;\n" +
                "import android.widget.Toast;\n" +
                "public class MainActivity {\n" +
                "    void onButtonClick(Object ctx) {\n" +
                "        String text = \"hi\";\n" +
                "        Toast.makeText(ctx, text, 1).show();\n" +
                "    }\n" +
                "}\n";
        ParsedFile main = svc.parseText("com/example/MainActivity.java", mainSrc,
                com.zerostudio.language.model.LanguageId.JAVA);

        // 3) Find a position inside the call to Toast.makeText
        //    line 5 (0-based: 4) at the start of "Toast".
        // Locate the actual position from the parsed file to be robust.
        com.zerostudio.language.model.Reference callRef = main.references.stream()
                .filter(r -> r.name.equals("makeText"))
                .findFirst()
                .orElse(null);
        assertNotNull("expected a 'makeText' reference in MainActivity", callRef);

        // 4) Resolve
        GoToDefinitionService g = svc.goToDefinition();
        ResolutionResult result = g.resolve(
                new SourceLocation(main.path,
                        new SourcePosition(callRef.range.start.line,
                                callRef.range.start.column + 1)));
        assertNotNull(result);
        assertTrue("expected resolution to Toast.makeText, got: " + result,
                result.isResolved());
        assertTrue(result.targetSymbol.name.equals("makeText"));
    }
}
