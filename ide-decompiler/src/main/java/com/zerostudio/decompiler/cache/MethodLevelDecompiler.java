package com.zerostudio.decompiler.cache;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方法级反编译缓存：把整个类反编译的结果按方法拆开，单独缓存。
 *
 * 优势：
 *  - 调试器打开某个 .class 时，IDE 经常只关心一两个方法。
 *  - 用 getMethod(className, methodName) 即可拿到该方法的源码片段。
 *  - 整类缓存命中时，不再调用底层 Decompiler。
 *
 * 拆分算法（粗略）：
 *  - 用正则找到所有 `methodName(...)` 形的方法签名行
 *  - 从签名开始，到下一个方法签名（或类结束）前为方法体
 *  - 忽略花括号配对失败的截断片段
 */
public final class MethodLevelDecompiler implements Decompiler {

    private static final Pattern METHOD_HEADER = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|abstract|final|synchronized|native|default)(?:\\s+(?:public|private|protected|static|abstract|final|synchronized|native|default))*\\s+)?"
                    + "(?:[A-Za-z_][\\w$.]*(?:<[^>]*>)?|void)\\s+"
                    + "([A-Za-z_]\\w*)\\s*\\(");

    private final Decompiler inner;
    private final int maxCachedClasses;
    private final Map<String, Map<String, MethodEntry>> cache;

    public static final class MethodEntry {
        public final String signature;
        public final String body;
        public final int startLine;
        public final int endLine;
        public final long timestamp;

        public MethodEntry(String signature, String body, int startLine, int endLine) {
            this.signature = signature;
            this.body = body;
            this.startLine = startLine;
            this.endLine = endLine;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public MethodLevelDecompiler(Decompiler inner) {
        this(inner, 64);
    }

    public MethodLevelDecompiler(Decompiler inner, int maxCachedClasses) {
        this.inner = inner;
        this.maxCachedClasses = maxCachedClasses;
        // 用同步的 LinkedHashMap 实现 LRU
        this.cache = Collections.synchronizedMap(new java.util.LinkedHashMap<String, Map<String, MethodEntry>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Map<String, MethodEntry>> eldest) {
                return size() > maxCachedClasses;
            }
        });
    }

    @Override
    public String name() { return inner.name() + "+methodCache"; }

    @Override
    public String version() { return inner.version(); }

    @Override
    public DecompileResult decompile(DecompileRequest request) {
        // 委托给底层，必要时再读 cache
        if (cache.containsKey(request.className)) {
            // 已有 className 的拆分，直接返回完整结果
            StringBuilder sb = new StringBuilder();
            for (MethodEntry me : cache.get(request.className).values()) {
                sb.append(me.signature).append('\n');
                sb.append(me.body).append('\n');
            }
            return DecompileResult.ok(request.className, sb.toString(), Collections.emptyMap());
        }
        DecompileResult result = inner.decompile(request);
        if (result.isOk()) {
            Map<String, MethodEntry> methods = splitMethods(result.source);
            cache.put(request.className, methods);
        }
        return result;
    }

    /** 仅获取某个方法的源码（不触发完整反编译） */
    public String getMethod(String className, String methodName) {
        Map<String, MethodEntry> methods = cache.get(className);
        if (methods == null) return null;
        MethodEntry me = methods.get(methodName);
        return me == null ? null : (me.signature + "\n" + me.body);
    }

    /** 列出该类已缓存的方法名 */
    public List<String> listMethods(String className) {
        Map<String, MethodEntry> methods = cache.get(className);
        if (methods == null) return Collections.emptyList();
        return new ArrayList<>(methods.keySet());
    }

    /** 把反编译后的源码按方法拆分 */
    private Map<String, MethodEntry> splitMethods(String source) {
        Map<String, MethodEntry> result = new HashMap<>();
        if (source == null || source.isEmpty()) return result;
        String[] lines = source.split("\n");
        int currentMethodStart = -1;
        String currentMethodName = null;
        StringBuilder currentBody = new StringBuilder();
        String currentSig = null;
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (currentMethodName == null) {
                Matcher m = METHOD_HEADER.matcher(line);
                if (m.find()) {
                    currentMethodName = m.group(1);
                    currentMethodStart = i;
                    currentSig = line;
                    currentBody.setLength(0);
                    currentBody.append(line).append('\n');
                    braceDepth = countChar(line, '{') - countChar(line, '}');
                }
            } else {
                currentBody.append(line).append('\n');
                braceDepth += countChar(line, '{') - countChar(line, '}');
                if (braceDepth <= 0) {
                    result.put(currentMethodName, new MethodEntry(currentSig, currentBody.toString().trim(), currentMethodStart, i));
                    currentMethodName = null;
                    currentSig = null;
                    braceDepth = 0;
                }
            }
        }
        // 处理未闭合的方法（保留）
        if (currentMethodName != null && currentBody.length() > 0) {
            result.put(currentMethodName, new MethodEntry(currentSig, currentBody.toString().trim(), currentMethodStart, lines.length - 1));
        }
        return result;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    public void clear() {
        cache.clear();
    }

    public int cachedClassCount() {
        return cache.size();
    }
}
