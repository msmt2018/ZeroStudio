# ide-decompiler

ZeroStudio 的 Java/Kotlin 字节码反编译库。在断点调试时,当 IDE 需要查看
没有源码的类 (如第三方依赖、系统框架类) 的实现,或把行号映射回源码时,
通过本库把 `.class` / `.dex` 反编译为可读的 Java 源码。

## 支持的反编译器

| 反编译器 | 实现类 | 来源 |
| --- | --- | --- |
| CFR | `impl/cfr/CfrDecompiler` | `decompile/cfr-0.152.jar` (compileOnly) |
| Procyon | `impl/procyon/ProcyonDecompiler` | 内置 |

两个实现都实现了统一的 [`Decompiler`](src/main/java/com/zerostudio/decompiler/api/Decompiler.java)
接口,可通过 `DecompilerRegistry` 按名字获取。

## 目录结构

```
src/main/java/com/zerostudio/decompiler/
├── api/                    反编译器统一 API
│   ├── Decompiler.java          接口: name() / version() / decompile(request)
│   ├── DecompileRequest.java    输入 (字节码 + 选项)
│   ├── DecompileResult.java     输出 (反编译源码 + 诊断)
│   ├── DecompilerRegistry.java  注册表,按 name 查找反编译器
│   └── CfrOptionKeys.java       CFR 反编译器选项键
├── impl/                   具体反编译器实现
│   ├── cfr/CfrDecompiler.java
│   └── procyon/ProcyonDecompiler.java
└── cache/                  反编译结果缓存
    ├── CachingDecompiler.java        包装器,带缓存
    └── MethodLevelDecompiler.java    方法级增量反编译
```

## API 用法

```java
// 注册反编译器 (通常在启动时做一次)
DecompilerRegistry.register(new CfrDecompiler());
DecompilerRegistry.register(new ProcyonDecompiler());

// 选择一个反编译器
Decompiler d = DecompilerRegistry.get("cfr");

// 反编译
DecompileRequest req = new DecompileRequest(classBytes, options);
DecompileResult result = d.decompile(req);
System.out.println(result.getSource());
```

## 在调试器架构中的位置

断点命中后,如果命中的类没有对应源码文件,IDE 会:

1. 通过 JDWP 拿到类的字节码
2. 调用本库反编译为 Java 源码
3. 在编辑器中展示反编译结果并标注当前行

## 依赖

- `decompile/cfr-0.152.jar` — CFR 反编译器 (compileOnly,运行时由宿主提供)
- 不依赖 Android SDK,纯 Java 库

## 相关模块

- [`ide-debugger`](../ide-debugger/README.md) — 调试器引擎,反编译的调用方
- [`ide-language`](../../ide-language/README.md) — 语言分析,可对反编译结果做符号解析

## License

GPL-3.0-or-later (same as AndroidIDE)
