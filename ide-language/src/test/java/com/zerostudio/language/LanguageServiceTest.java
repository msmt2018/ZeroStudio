package com.zerostudio.language;
import com.zerostudio.language.service.LanguageRegistry;
import com.zerostudio.language.model.LanguageId;
import org.junit.Test;
import static org.junit.Assert.*;
public class LanguageServiceTest {
    @Test public void registrySupportsJava() {
        // verify registry can hold services
        assertNotNull(LanguageRegistry.all());
    }
}