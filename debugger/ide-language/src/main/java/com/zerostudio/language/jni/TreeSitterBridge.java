package com.zerostudio.language.jni;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.source.SourceLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TreeSitter JNI 桥接：抽象对原生 tree-sitter 库的调用。
 *  - 真实环境：通过 System.loadLibrary("ts_<lang>") 加载原生 .so
 *  - 测试 / 无原生环境：使用纯 Java 实现的回退（见 {@link TreeSitterFallback}）
 *  - 加载失败时自动降级为回退实现，确保 IDE 在缺失 native lib 时仍能工作
 *
 * 支持的 grammar：C, C++, Java, Kotlin（未来扩展 Go / Rust / Python）。
 */
public final class TreeSitterBridge {

    public enum State { UNKNOWN, NATIVE_LOADED, NATIVE_FAILED, FALLBACK }

    public static final class ParseResult {
        public final String rootNodeType;
        public final List<Reference> references;
        public final State state;
        public ParseResult(String rootNodeType, List<Reference> references, State state) {
            this.rootNodeType = rootNodeType;
            this.references = references;
            this.state = state;
        }
    }

    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<LanguageId, State> STATE = new ConcurrentHashMap<>();
    private static String lastError = "";

    /** 加载/尝试加载所有原生 grammar；幂等。 */
    public static void ensureLoaded() {
        if (!ATTEMPTED.compareAndSet(false, true)) return;
        for (LanguageId id : new LanguageId[]{LanguageId.CPP, LanguageId.JAVA, LanguageId.KOTLIN}) {
            try {
                System.loadLibrary(libraryName(id));
                STATE.put(id, State.NATIVE_LOADED);
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                STATE.put(id, State.NATIVE_FAILED);
            }
        }
    }

    public static State state(LanguageId id) {
        ensureLoaded();
        State s = STATE.get(id);
        return s == null ? State.UNKNOWN : s;
    }

    public static String lastError() { return lastError; }

    public static ParseResult parse(LanguageId id, String path, String text) {
        ensureLoaded();
        State s = state(id);
        if (s == State.NATIVE_LOADED) {
            try {
                return parseNative(id, path, text);
            } catch (Throwable t) {
                lastError = t.getMessage();
                STATE.put(id, State.NATIVE_FAILED);
            }
        }
        return parseFallback(id, path, text);
    }

    private static ParseResult parseNative(LanguageId id, String path, String text) {
        // 真实实现：调用原生 tree-sitter API 获取语法树，转换为 Reference 列表
        // 这里用回退实现作为占位（native 路径需要在 Android 设备上用 NDK 编译后才能走通）
        return parseFallback(id, path, text);
    }

    private static ParseResult parseFallback(LanguageId id, String path, String text) {
        switch (id) {
            case CPP: {
                ParsedFile pf = new com.zerostudio.language.cpp.CppSymbolExtractor().extract(path, text);
                return new ParseResult("translation_unit", pf.references, State.FALLBACK);
            }
            case KOTLIN: {
                ParsedFile pf = new com.zerostudio.language.kotlin.KotlinSymbolExtractor().extract(path, text);
                return new ParseResult("kotlin_file", pf.references, State.FALLBACK);
            }
            case JAVA: {
                ParsedFile pf = new com.zerostudio.language.parser.JavaParserFacade().parse(path, text);
                return new ParseResult("program", pf.references, State.FALLBACK);
            }
            default: {
                List<Reference> refs = new ArrayList<>();
                return new ParseResult("unknown", refs, State.FALLBACK);
            }
        }
    }

    private static String libraryName(LanguageId id) {
        switch (id) {
            case CPP:    return "ts_cpp";
            case JAVA:   return "ts_java";
            case KOTLIN: return "ts_kotlin";
            default:     return "ts_unknown";
        }
    }

    public static Optional<com.zerostudio.language.model.ResolutionResult> resolve(
            LanguageId id, ParsedFile pf, Reference ref, SourceLocator locator) {
        if (locator == null) return Optional.empty();
        SourceLocator.LocatedSource src = locator.locate(pf.packageName + "." + ref.name);
        if (src.isResolved()) {
            return Optional.of(com.zerostudio.language.model.ResolutionResult.resolved(
                    src.displayPath, null,
                    new com.zerostudio.language.model.Symbol(ref.name,
                            pf.packageName + "." + ref.name,
                            com.zerostudio.language.model.SymbolKind.CLASS,
                            pf.packageName, src.displayPath)));
        }
        return Optional.empty();
    }
}
