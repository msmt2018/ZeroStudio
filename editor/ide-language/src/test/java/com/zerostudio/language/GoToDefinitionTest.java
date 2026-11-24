package com.zerostudio.language;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.GoToDefinitionService;
import com.zerostudio.language.service.LanguageService;
import com.zerostudio.language.model.*;
import org.junit.Test;
import java.util.Optional;
import static org.junit.Assert.*;

public class GoToDefinitionTest {
    @Test public void returnsEmptyForUnparsedFile() {
        LanguageService svc = new LanguageService(LanguageId.JAVA) {};
        GoToDefinitionService gtd = new GoToDefinitionService(svc);
        Optional<ResolutionResult> r = gtd.findDefinition("Foo.java", "", 0);
        assertFalse(r.isPresent());
    }
}