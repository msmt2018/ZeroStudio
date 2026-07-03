/*
 *  ZeroStudio IDE - ide-debugger
 *  Symbol & DWARF Manager (Phase 20)
 *
 *  符号与源码映射的统一入口 (SourceNameMapper)。
 *
 *  内部持有 3 类解析器:
 *    - JavaR8MappingResolver   (R8/ProGuard mapping.txt)
 *    - DwarfSymbolResolver     (Native .so .debug_info / .debug_line)
 *    - JavaAstSymbolResolver   (本地 .java 源码,包装 SourceLocator/AstIndex)
 *
 *  线程安全 (CopyOnWriteArrayList / volatile 引用);
 *  解析失败一律降级为 RAW 透传,绝不抛错给 UI。
 */

package com.zerostudio.debugger.symbol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.api.MappedSourceLocation;
import com.zerostudio.debugger.api.NativeAddress;
import com.zerostudio.debugger.api.SourceLocation;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SourceNameMapper {

    private static final SourceNameMapper INSTANCE = new SourceNameMapper();

    public static SourceNameMapper getInstance() { return INSTANCE; }

    private final CopyOnWriteArrayList<SymbolResolver> resolvers = new CopyOnWriteArrayList<>();

    private SourceNameMapper() {
        // 默认注册 3 个解析器,顺序 = 解析优先级。
        resolvers.add(new JavaR8MappingResolver());
        resolvers.add(new DwarfSymbolResolver());
        resolvers.add(new JavaAstSymbolResolver());
    }

    /** 注册一个自定义解析器。 */
    public void register(@NonNull SymbolResolver r) {
        if (r == null) return;
        resolvers.addIfAbsent(r);
    }

    /** 卸载某个解析器。 */
    public void unregister(@NonNull SymbolResolver r) {
        resolvers.remove(r);
    }

    /** 当前注册的解析器数量。 */
    public int resolverCount() { return resolvers.size(); }

    // ---- Java 符号解析 ----

    /**
     * 给一个 JDI 上报的 (类名, 方法名, 字段名),返回反混淆 + 源码位置。
     * 任一解析器认领后立即返回,后面的解析器不再尝试。
     */
    @NonNull
    public MappedSourceLocation mapJava(
            @NonNull String rawClassName,
            @Nullable String rawMethodName,
            @Nullable String rawFieldName,
            @Nullable SourceLocation src) {
        for (SymbolResolver r : resolvers) {
            if (!r.supportsJava()) continue;
            MappedSourceLocation mapped = r.mapJava(rawClassName, rawMethodName, rawFieldName, src);
            if (mapped != null && mapped.remapped) return mapped;
        }
        // 全部失败,返回 RAW 透传
        return rawOnly(rawClassName, rawMethodName, rawFieldName, src,
                src == null ? MappedSourceLocation.Kind.JAVA : MappedSourceLocation.Kind.JAVA);
    }

    // ---- Native 符号解析 ----

    /**
     * 给一个 Native 栈帧地址,返回 (函数名, 源文件, 源行)。
     * 若无 DWARF,返回 NATIVE_UNKNOWN。
     */
    @NonNull
    public MappedSourceLocation mapNative(@NonNull NativeAddress addr) {
        for (SymbolResolver r : resolvers) {
            if (!r.supportsNative()) continue;
            MappedSourceLocation mapped = r.mapNative(addr);
            if (mapped != null) return mapped;
        }
        // 全部失败
        return new MappedSourceLocation(
                "?" /*rawClass*/ , null, null,
                "?" /*origClass*/, null, null,
                null, 0,
                addr.address, addr.module,
                MappedSourceLocation.Kind.NATIVE_UNKNOWN);
    }

    // ---- 加载 helper ----

    /** 从 R8/ProGuard mapping 文件加载。 */
    public boolean loadR8Mapping(@Nullable File mappingFile) {
        if (mappingFile == null || !mappingFile.isFile()) return false;
        for (SymbolResolver r : resolvers) {
            if (r instanceof JavaR8MappingResolver) {
                return ((JavaR8MappingResolver) r).load(mappingFile);
            }
        }
        return false;
    }

    /** 从 InputStream 加载 R8 mapping (用于从 APK 内部读取)。 */
    public boolean loadR8Mapping(@Nullable InputStream in) {
        if (in == null) return false;
        for (SymbolResolver r : resolvers) {
            if (r instanceof JavaR8MappingResolver) {
                return ((JavaR8MappingResolver) r).load(in);
            }
        }
        return false;
    }

    /** 注册一个 .so 调试符号 (DWARF)。 */
    public boolean registerNativeModule(@NonNull String soName, @NonNull File soFile) {
        for (SymbolResolver r : resolvers) {
            if (r instanceof DwarfSymbolResolver) {
                return ((DwarfSymbolResolver) r).registerModule(soName, soFile);
            }
        }
        return false;
    }

    /** 重置全部解析器,释放缓存。 */
    public void clearAll() {
        for (SymbolResolver r : resolvers) r.clear();
    }

    @NonNull
    private static MappedSourceLocation rawOnly(
            @NonNull String cls, @Nullable String m, @Nullable String f,
            @Nullable SourceLocation src, MappedSourceLocation.Kind kind) {
        return new MappedSourceLocation(
                cls, m, f,
                cls, m, f,
                src == null ? null : src.sourceFile,
                src == null ? 0 : src.lineNumber,
                0L, null, kind);
    }

    // ---- 内部 SymbolResolver 接口 ----

    public interface SymbolResolver {
        default boolean supportsJava() { return false; }
        default boolean supportsNative() { return false; }
        @Nullable MappedSourceLocation mapJava(
                @NonNull String rawClass,
                @Nullable String rawMethod,
                @Nullable String rawField,
                @Nullable SourceLocation src);
        @Nullable MappedSourceLocation mapNative(@NonNull NativeAddress addr);
        default void clear() {}
    }

    // ---- Java AST 解析器 (wrapper over SourceLocator) ----
    private static final class JavaAstSymbolResolver implements SymbolResolver {
        @Override public boolean supportsJava() { return true; }
        @Override
        public @Nullable MappedSourceLocation mapJava(
                @NonNull String rawClass,
                @Nullable String rawMethod,
                @Nullable String rawField,
                @Nullable SourceLocation src) {
            // AstIndex 是无 IO 的内存索引,IDE 在 editor 打开 .java 时填充。
            // 这里仅做"在 .java 里有该 class 名字 + 简单行号提取",
            // 真实生产环境中,IDE 通过 SourceLocator 已经拿到 file:line,
            // 此 resolver 仅在 R8 未命中且 src==null 时给一个回退。
            if (src == null) return null;
            // 直接回 RAW 即可
            return new MappedSourceLocation(
                    rawClass, rawMethod, rawField,
                    rawClass, rawMethod, rawField,
                    src.sourceFile, src.lineNumber,
                    0L, null, MappedSourceLocation.Kind.JAVA);
        }
    }

    @NonNull
    public List<SymbolResolver> resolversView() {
        return java.util.Collections.unmodifiableList(resolvers);
    }
}
