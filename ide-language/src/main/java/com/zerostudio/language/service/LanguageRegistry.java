package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import java.util.*;

public final class LanguageRegistry {
    private static final Map<String, LanguageService> SERVICES = new LinkedHashMap<>();
    public static void register(LanguageService svc) { SERVICES.put(svc.languageId().name(), svc); }
    public static LanguageService get(LanguageId id) { return SERVICES.get(id.name()); }
    public static Collection<LanguageService> all() { return Collections.unmodifiableCollection(SERVICES.values()); }
    public static void clearForTests() { SERVICES.clear(); }
}