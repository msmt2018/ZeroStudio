# ide-language

ZeroStudio 的语言分析核心库。取代了被废弃的 `language-lexer/*` 子模块,
提供一个统一的、编译器级的代码分析入口。

## 覆盖的语言

| 语言 | 词法 / 语法 | 符号 | Go-to-Definition |
| --- | --- | --- | --- |
| Java | JavaParser 3.27 | JavaSymbolSolver | 支持 |
| Kotlin | org.jetbrains.kotlin:kotlin-compiler (PSI) | 编译器内建 | 支持 |
| C | Tree-Sitter (`tree-sitter-c`) | C 符号表 + 索引 | 支持 |
| C++ | Tree-Sitter (`tree-sitter-cpp`) | C++ 符号表 + 索引 | 支持 |

## 目录结构

```
src/main/java/com/zerostudio/language/
├── model/        通用数据模型 (LanguageId, SourceRange, Reference, Symbol, ...)
├── lexer/        词法分析
│   ├── Lexer.java        抽象接口
│   ├── Token.java
│   ├── JavaLexer.java    (用 JavaParser / 手写 fallback)
│   ├── KotlinLexer.java  (用 Kotlin compiler PSI)
│   ├── CLexer.java       (用 Tree-Sitter)
│   └── CppLexer.java     (用 Tree-Sitter)
├── parser/       语法分析 -> AST
│   ├── AstNode.java      抽象节点
│   ├── JavaAst.java      包装 JavaParser CompilationUnit
│   ├── KotlinAst.java
│   ├── CAst.java
│   └── CppAst.java
├── symbols/      符号解析 + 类型推断
│   ├── Symbol.java
│   ├── SymbolKind.java   (CLASS / METHOD / FIELD / VARIABLE / PARAMETER)
│   ├── Scope.java
│   ├── ResolutionResult.java
│   └── JavaSymbolResolver.java / KotlinSymbolResolver.java / C/CppSymbolResolver.java
├── index/        跨文件索引
│   ├── ProjectIndex.java
│   ├── FileRevision.java
│   ├── InMemoryProjectIndex.java
│   └── DeltaIndex.java
└── service/      对外统一 API
    ├── LanguageService.java        (单文件解析入口)
    ├── GoToDefinitionService.java  (Ctrl+点击 + 长按菜单的来源)
    ├── LanguageRegistry.java       (LanguageId -> 实现)
    └── EditorIntegration.kt        (与代码编辑器交互的适配层)
```

## 入口

```java
LanguageService svc = LanguageService.getInstance();

ParsedFile parsed = svc.parse(LanguageId.JAVA, file);
Reference ref     = svc.findReferenceAt(parsed, new SourcePosition(line, column));
ResolutionResult res = svc.resolve(parsed, ref);
if (res != null) {
    openInEditor(res.targetFile, res.targetRange);
}
```

## 与调试器的协作

`ide-debugger` 命中一个断点时,通过本库:

1. 在 host 端拿到 `class + lineNumber`
2. 用 `LanguageService.guessSourceFile(class)` 找回对应的源码文件
3. 用 `GoToDefinitionService` 把行号映射到 AST 中的 `Statement` / `Expression`
4. 编辑器跳转到该位置并高亮

详细规范见 [docs/DEBUGGER_FULL_AUDIT.md](../docs/DEBUGGER_FULL_AUDIT.md)。

## 单元测试

```
./gradlew :ide-language:test
```
