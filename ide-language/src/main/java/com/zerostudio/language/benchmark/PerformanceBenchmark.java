package com.zerostudio.language.benchmark;

import com.zerostudio.language.index.DefaultProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.python.PythonSymbolExtractor;
import com.zerostudio.language.javascript.JsSymbolExtractor;
import com.zerostudio.language.goext.GoSymbolExtractor;
import com.zerostudio.language.rust.RustSymbolExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 性能基准：测量关键操作的吞吐量。
 *
 * 不依赖 JUnit（通过 main() 直接运行），避免被测试套件错误判定。
 *
 * 报告：
 *  - 解析 1000 个不同文件所需时间
 *  - 索引 10000 个符号所需时间
 *  - 引用查找 1000 次所需时间
 *  - 跨语言搜索 1000 次所需时间
 */
public final class PerformanceBenchmark {

    public static final class Result {
        public final String name;
        public final long elapsedMs;
        public final long opsPerSecond;
        public final int ops;

        public Result(String name, long elapsedMs, int ops) {
            this.name = name;
            this.elapsedMs = elapsedMs;
            this.ops = ops;
            this.opsPerSecond = elapsedMs > 0 ? (ops * 1000L / elapsedMs) : 0;
        }

        @Override
        public String toString() {
            return String.format("%-40s  %d ops in %d ms = %,d ops/sec",
                    name, ops, elapsedMs, opsPerSecond);
        }
    }

    public static List<Result> runAll() {
        List<Result> out = new ArrayList<>();
        out.add(benchJavaParser());
        out.add(benchPythonParser());
        out.add(benchJsParser());
        out.add(benchGoParser());
        out.add(benchRustParser());
        out.add(benchProjectIndex());
        out.add(benchReferenceSearch());
        out.add(benchCrossLangSearch());
        out.add(benchLargeFile());
        return out;
    }

    private static long t0() { return System.nanoTime(); }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static Result benchJavaParser() {
        int n = 1000;
        JavaParserFacade ext = new JavaParserFacade();
        long start = t0();
        for (int i = 0; i < n; i++) {
            String src = "package com.bench;\n" +
                    "import java.util.List;\n" +
                    "public class C" + i + " {\n" +
                    "    public void m" + i + "(int x) { }\n" +
                    "    public int get(int idx) { return idx; }\n" +
                    "}\n";
            ext.parse("C" + i + ".java", src);
        }
        return new Result("java-parse-1000-files", elapsedMs(start), n);
    }

    private static Result benchPythonParser() {
        int n = 1000;
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        long start = t0();
        for (int i = 0; i < n; i++) {
            String src = "import os\n" +
                    "from typing import List\n" +
                    "class C" + i + ":\n" +
                    "    def m" + i + "(self, x):\n" +
                    "        return x\n";
            ext.extract("C" + i + ".py", src);
        }
        return new Result("python-parse-1000-files", elapsedMs(start), n);
    }

    private static Result benchJsParser() {
        int n = 1000;
        JsSymbolExtractor ext = new JsSymbolExtractor();
        long start = t0();
        for (int i = 0; i < n; i++) {
            String src = "import { Component } from 'react';\n" +
                    "export class C" + i + " extends Component {\n" +
                    "    render() { return null; }\n" +
                    "}\n";
            ext.extract("C" + i + ".tsx", src);
        }
        return new Result("js-parse-1000-files", elapsedMs(start), n);
    }

    private static Result benchGoParser() {
        int n = 1000;
        GoSymbolExtractor ext = new GoSymbolExtractor();
        long start = t0();
        for (int i = 0; i < n; i++) {
            String src = "package x\n" +
                    "import \"fmt\"\n" +
                    "type C" + i + " struct { Name string }\n" +
                    "func (c *C" + i + ") Get() string { return c.Name }\n";
            ext.extract("C" + i + ".go", src);
        }
        return new Result("go-parse-1000-files", elapsedMs(start), n);
    }

    private static Result benchRustParser() {
        int n = 1000;
        RustSymbolExtractor ext = new RustSymbolExtractor();
        long start = t0();
        for (int i = 0; i < n; i++) {
            String src = "pub struct C" + i + " { name: String }\n" +
                    "impl C" + i + " {\n" +
                    "    pub fn new() -> Self { C" + i + " { name: String::new() } }\n" +
                    "}\n";
            ext.extract("C" + i + ".rs", src);
        }
        return new Result("rust-parse-1000-files", elapsedMs(start), n);
    }

    private static Result benchProjectIndex() {
        int n = 1000;
        ProjectIndex idx = new DefaultProjectIndex();
        long start = t0();
        Random rng = new Random(42);
        for (int i = 0; i < n; i++) {
            String cls = "com.bench.C" + i;
            ParsedFile pf = new ParsedFile("C" + i + ".java", LanguageId.JAVA, "com.bench",
                    List.of(new Reference(cls, null,
                            Reference.ReferenceKind.CLASS, "com.bench", "C" + i + ".java", LanguageId.JAVA)),
                    "");
            idx.index(pf);
            // 模拟查找
            idx.fileFor(cls);
        }
        return new Result("index-1000-lookup-1000", elapsedMs(start), n * 2);
    }

    private static Result benchReferenceSearch() {
        int files = 100;
        int refsPerFile = 100;
        ProjectIndex idx = new DefaultProjectIndex();
        // 索引 100 个文件，每个 100 个 class 引用
        for (int f = 0; f < files; f++) {
            List<Reference> refs = new ArrayList<>();
            for (int r = 0; r < refsPerFile; r++) {
                refs.add(new Reference("C" + r, null,
                        Reference.ReferenceKind.CLASS, "com.bench", "F" + f + ".java", LanguageId.JAVA));
            }
            ParsedFile pf = new ParsedFile("F" + f + ".java", LanguageId.JAVA, "com.bench", refs, "");
            idx.index(pf);
        }
        int n = 1000;
        long start = t0();
        int hits = 0;
        for (int i = 0; i < n; i++) {
            String target = "C" + (i % refsPerFile);
            if (idx.fileFor("com.bench." + target) != null) hits++;
        }
        long elapsed = elapsedMs(start);
        return new Result("reference-lookup-1000", elapsed, n);
    }

    private static Result benchCrossLangSearch() {
        int files = 50;
        ProjectIndex idx = new DefaultProjectIndex();
        for (int f = 0; f < files; f++) {
            for (int l = 0; l < 4; l++) {
                LanguageId lang = LanguageId.values()[l % LanguageId.values().length];
                ParsedFile pf = new ParsedFile("f" + f + "." + lang.name().toLowerCase(), lang, "x",
                        List.of(new Reference("Foo", null,
                                Reference.ReferenceKind.CLASS, "x", "f" + f + "." + lang, lang)),
                        "");
                idx.index(pf);
            }
        }
        com.zerostudio.language.service.CrossLanguageResolver r =
                new com.zerostudio.language.service.CrossLanguageResolver(idx);
        int n = 1000;
        long start = t0();
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += r.findClasses("Foo").size();
        }
        return new Result("cross-lang-search-1000", elapsedMs(start), n);
    }

    private static Result benchLargeFile() {
        // 构造一个 5000 行的 Java 文件
        StringBuilder sb = new StringBuilder("package big;\n");
        for (int i = 0; i < 5000; i++) {
            sb.append("public class C").append(i).append(" { void m() {} }\n");
        }
        JavaParserFacade ext = new JavaParserFacade();
        long start = t0();
        ParsedFile pf = ext.parse("Big.java", sb.toString());
        long elapsed = elapsedMs(start);
        return new Result("large-file-5000-classes", elapsed, 1);
    }

    public static void main(String[] args) {
        System.out.println("==> Performance Benchmark");
        List<Result> results = runAll();
        for (Result r : results) {
            System.out.println(r);
        }
    }
}
