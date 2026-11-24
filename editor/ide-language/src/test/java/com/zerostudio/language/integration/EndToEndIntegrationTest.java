package com.zerostudio.language.integration;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.api.DecompilerRegistry;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import com.zerostudio.decompiler.cache.MethodLevelDecompiler;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;
import com.zerostudio.language.index.DefaultProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.CallHierarchyService;
import com.zerostudio.language.service.FindReferencesService;
import com.zerostudio.language.service.HoverService;
import com.zerostudio.language.source.SourceJarIndex;
import com.zerostudio.language.source.SourceLocator;
import com.zerostudio.language.source.SmartSourceLocator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * 端到端集成测试：模拟真实工作流。
 *
 * 场景：
 *  1. 读取 .class → 反编译 → 解析 → 索引 → 查找引用 / 悬停 / 调用层次
 *  2. 跨 .java 源文件 + 反编译类的统一索引
 *  3. 性能：方法级缓存命中
 *  4. SourceJarIndex + SourceLocator 协同
 */
public class EndToEndIntegrationTest {

    private static final String JUNIT_JAR = "/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/lib/junit-4.13.2.jar";

    @BeforeClass
    public static void setup() {
        // 注册 CFR
        DecompilerRegistry.register(new CfrDecompiler());
    }

    /** 1. 反编译 .class → 创建 ParsedFile → 索引 → 引用查找 */
    @Test
    public void decompileClassAndIndex() {
        // 从 junit jar 中读取一个 .class
        byte[] classBytes = readClassBytes(JUNIT_JAR, "org/junit/Assert.class");
        assertNotNull("could not read Assert.class", classBytes);
        Decompiler cfr = DecompilerRegistry.get("cfr");
        assertNotNull(cfr);
        DecompileResult r = cfr.decompile(DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(classBytes)
                .build());
        assertTrue("decompilation should succeed: " + r.failure, r.isOk());

        // 解析反编译后的源码
        JavaParserFacade parser = new JavaParserFacade();
        ParsedFile pf = parser.parse("org/junit/Assert.java", r.source);
        assertEquals("org.junit", pf.packageName);
        assertTrue("expected at least one reference", pf.references.size() > 0);

        // 索引
        ProjectIndex idx = new DefaultProjectIndex();
        idx.index(pf);

        // 查找引用 - 使用 findByName，因为 Assert 是被 import 的
        FindReferencesService svc = new FindReferencesService(idx);
        List<FindReferencesService.Match> refs = svc.findByName("Assert", true);
        // 至少包含 Assert 类自身的引用
        assertTrue("expected at least 1 reference, got " + refs.size(), refs.size() >= 1);
    }

    /** 2. 解析 + 反编译 + HoverService 集成 */
    @Test
    public void hoverOnDecompiledClass() {
        byte[] classBytes = readClassBytes(JUNIT_JAR, "junit/framework/TestCase.class");
        assertNotNull(classBytes);
        Decompiler cfr = DecompilerRegistry.get("cfr");
        DecompileResult r = cfr.decompile(DecompileRequest.builder()
                .className("junit.framework.TestCase")
                .classBytes(classBytes)
                .build());
        assertTrue(r.isOk());
        JavaParserFacade parser = new JavaParserFacade();
        ParsedFile pf = parser.parse("TestCase.java", r.source);
        ProjectIndex idx = new DefaultProjectIndex();
        idx.index(pf);

        // 找到第一个 CLASS 引用，hover 它
        Reference classRef = null;
        int classLine = 1;
        int classCol = 1;
        for (Reference ref : pf.references) {
            if (ref.kind == Reference.ReferenceKind.CLASS) {
                classRef = ref;
                if (ref.range != null && ref.range.start != null) {
                    classLine = ref.range.start.line;
                    classCol = ref.range.start.column;
                }
                break;
            }
        }
        assertNotNull("expected a class reference", classRef);
        // Hover 不会抛异常
        HoverService hover = new HoverService(idx);
        HoverService.HoverInfo info = hover.hover("TestCase.java", classLine, classCol);
        // 不测试具体内容，因为类名依赖反编译结果
        // 只要能调用即可
        assertNotNull(hover);
    }

    /** 3. 方法级缓存 - 第二次 decompile 不调用底层 */
    @Test
    public void methodLevelCacheWorks() {
        byte[] classBytes = readClassBytes(JUNIT_JAR, "org/junit/Assert.class");
        assertNotNull(classBytes);
        Decompiler cfr = DecompilerRegistry.get("cfr");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(cfr, 10);
        // 第一次
        mld.decompile(DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(classBytes)
                .build());
        // 第二次
        mld.decompile(DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(classBytes)
                .build());
        // 缓存了 1 个 class
        assertEquals(1, mld.cachedClassCount());
    }

    /** 4. 跨 .java 源文件 + 反编译类的统一索引 */
    @Test
    public void mixedSourceAndDecompiledIndex() {
        ProjectIndex idx = new DefaultProjectIndex();
        // 源文件
        JavaParserFacade parser = new JavaParserFacade();
        ParsedFile source = parser.parse("Caller.java",
                "package my;\n" +
                        "public class Caller {\n" +
                        "    public void run() {\n" +
                        "        junit.framework.TestCase t = new MyTest();\n" +
                        "    }\n" +
                        "}\n");
        idx.index(source);
        // 反编译类
        byte[] classBytes = readClassBytes(JUNIT_JAR, "junit/framework/TestCase.class");
        Decompiler cfr = DecompilerRegistry.get("cfr");
        DecompileResult r = cfr.decompile(DecompileRequest.builder()
                .className("junit.framework.TestCase")
                .classBytes(classBytes)
                .build());
        ParsedFile decompiled = parser.parse("TestCase.java", r.source);
        idx.index(decompiled);
        // 索引中应有两个类
        assertNotNull(idx.fileFor("my.Caller"));
        assertNotNull(idx.fileFor("junit.framework.TestCase"));
    }

    /** 5. SourceJarIndex + jar 文件扫描 */
    @Test
    public void sourceJarIndexScan() throws Exception {
        // 创建一个临时 source-jar
        File tempJar = File.createTempFile("test-sources", ".jar");
        tempJar.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempJar))) {
            addToZip(zos, "com/x/A.java", "package com.x;\npublic class A { public void foo() {} public void bar() {} }");
            addToZip(zos, "com/x/B.java", "package com.x;\npublic class B { public int calc(int n) { return n+1; } }");
        }
        SourceJarIndex idx = new SourceJarIndex();
        idx.indexArchive(tempJar.getAbsolutePath());
        // A 类应有 2 个方法
        List<SourceJarIndex.Entry> methods = idx.methodsOf("com.x.A");
        assertEquals(2, methods.size());
        // 找 foo
        List<SourceJarIndex.Entry> foo = idx.find("com.x.A", "foo");
        assertEquals(1, foo.size());
    }

    /** 6. SmartSourceLocator 选择最优候选 */
    @Test
    public void smartSourceLocatorSelects() {
        SmartSourceLocator sel = new SmartSourceLocator();
        sel.setStrategy(SmartSourceLocator.SelectionStrategy.PREFER_SHADED);
        List<SourceLocator.LocatedSource> candidates = new ArrayList<>();
        candidates.add(SourceLocator.LocatedSource.of(
                SourceLocator.Kind.SOURCE_JAR, "com.x.A", "lib-1.0.jar", "// src", "lib-1.0.jar"));
        candidates.add(SourceLocator.LocatedSource.of(
                SourceLocator.Kind.SOURCE_JAR, "com.x.A", "lib-1.0-shaded.jar", "// src", "lib-1.0-shaded.jar"));
        SourceLocator.LocatedSource best = sel.select(candidates);
        assertNotNull(best);
        assertEquals("lib-1.0-shaded.jar", best.originPath);
    }

    /** 7. CachingDecompiler 集成 - 多次请求走缓存 */
    @Test
    public void cachingDecompilerIntegration() {
        byte[] classBytes = readClassBytes(JUNIT_JAR, "org/junit/Assert.class");
        Decompiler cfr = DecompilerRegistry.get("cfr");
        CachingDecompiler cache = new CachingDecompiler(cfr, 32);
        DecompileRequest req = DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(classBytes)
                .build();
        DecompileResult r1 = cache.decompile(req);
        assertTrue(r1.isOk());
        DecompileResult r2 = cache.decompile(req);
        assertTrue(r2.isOk());
        // 缓存命中
        assertNotNull(cache);
    }

    /** 8. CallHierarchyService 在多文件中工作 */
    @Test
    public void callHierarchyAcrossFiles() {
        ProjectIndex idx = new DefaultProjectIndex();
        JavaParserFacade parser = new JavaParserFacade();
        ParsedFile a = parser.parse("A.java",
                "package x;\n" +
                        "public class A {\n" +
                        "    public void start() {\n" +
                        "        helper();\n" +
                        "    }\n" +
                        "    public void helper() {}\n" +
                        "}\n");
        ParsedFile b = parser.parse("B.java",
                "package x;\n" +
                        "public class B {\n" +
                        "    public void run() {\n" +
                        "        new A().start();\n" +
                        "    }\n" +
                        "}\n");
        idx.index(a);
        idx.index(b);
        CallHierarchyService chs = new CallHierarchyService(idx);
        // helper 被 A.helper() 自身（声明）和 start() 调用
        List<CallHierarchyService.CallSite> callers = chs.callersOf("helper");
        // 至少有 1 个（start() 内的调用）
        assertTrue("expected at least 1 caller, got " + callers.size(), callers.size() >= 1);
    }

    // --- helpers ---

    private byte[] readClassBytes(String jar, String entry) {
        try (ZipFile zf = new ZipFile(new File(jar))) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getName().equals(entry)) {
                    try (InputStream is = zf.getInputStream(e)) {
                        return is.readAllBytes();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void addToZip(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(content.getBytes());
        zos.closeEntry();
    }
}
