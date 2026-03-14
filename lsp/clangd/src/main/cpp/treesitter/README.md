# Tree-sitter Integration

本目录包含 tree-sitter 语言解析器和 JNI 绑定，用于语法高亮和代码分析。

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                         sora-editor                                  │
│  (TsLanguageSpec, TsAnalyzeManager, TsBracketPairs, 语法高亮等)       │
│                              │                                       │
│                              ▼                                       │
├─────────────────────────────────────────────────────────────────────┤
│              Kotlin 绑定层 (JNI 包装)                                 │
│  TSParser.kt ──► nativeCreate(), nativeParseString()                │
│  TSTree.kt   ──► nativeRootNode(), nativeEdit()                     │
│  TSNode.kt   ──► nativeType(), nativeStartByte(), nativeChild()     │
│  TSQuery.kt  ──► nativeCreate(), nativePatternCount()               │
│                              │                                       │
│                              ▼ JNI 调用                              │
├─────────────────────────────────────────────────────────────────────┤
│              C++ JNI 绑定 (native_compiler.so)                       │
│  ts_jni.cpp       ──► Java_com_wuxianggujun_..._TSParser_*          │
│  ts_node_jni.cpp  ──► Java_com_wuxianggujun_..._TSNode_*            │
│  ts_query_jni.cpp ──► Java_com_wuxianggujun_..._TSQuery_*           │
│                              │                                       │
│                              ▼ 调用 tree-sitter C API                │
├─────────────────────────────────────────────────────────────────────┤
│              tree-sitter 核心库 (lib/src/)                           │
│  ts_parser_new(), ts_parser_parse_string()                          │
│  ts_tree_root_node(), ts_node_type(), ts_node_child()               │
│  ts_query_new(), ts_query_cursor_exec()                             │
│                              │                                       │
│                              ▼ 使用语言解析器                         │
├─────────────────────────────────────────────────────────────────────┤
│              语言解析器 (parser.c)                                    │
│  tree_sitter_cpp()   ──► C++ 语法解析                                │
│  tree_sitter_cmake() ──► CMake 语法解析                              │
└─────────────────────────────────────────────────────────────────────┘
```

## 目录结构

```
treesitter/
├── include/
│   └── tree_sitter/
│       └── api.h              # Tree-sitter 公共 API (v0.24+)
├── lib/
│   └── src/                   # Tree-sitter 核心库源码
│       ├── lib.c              # 主入口文件
│       ├── parser.c           # 解析器实现
│       ├── query.c            # 查询实现
│       ├── tree.c             # 语法树实现
│       ├── node.c             # 节点实现
│       ├── lexer.c            # 词法分析器
│       ├── stack.c            # 解析栈
│       ├── subtree.c          # 子树管理
│       ├── language.c         # 语言支持
│       ├── unicode/           # Unicode 支持
│       └── ...
├── core/                      # JNI 绑定 (核心 API)
│   ├── ts_jni.cpp             # TSLanguage, TSParser, TSTree
│   ├── ts_node_jni.cpp        # TSNode
│   └── ts_query_jni.cpp       # TSQuery, TSQueryCursor
├── cmake/                     # CMake 语言解析器
│   ├── parser.c               # tree-sitter-cmake 生成
│   ├── scanner.c              # 外部扫描器
│   └── cmake_jni.cpp          # JNI: tree_sitter_cmake()
└── cpp/                       # C++ 语言解析器
    ├── parser.c               # tree-sitter-cpp 生成
    ├── scanner.c              # 外部扫描器
    └── cpp_jni.cpp            # JNI: tree_sitter_cpp()
```

## Kotlin 绑定

Kotlin 绑定位于 `external/sora-editor/language-treesitter/src/main/java/`:

### 核心类 (`com.wuxianggujun.tinaide.treesitter`)

| 类 | 说明 |
|---|---|
| `TSLanguage` | 语言定义，包装 `const TSLanguage*` |
| `TSParser` | 解析器，用于解析源代码 |
| `TSTree` | 语法树，解析结果 |
| `TSNode` | 语法树节点 |
| `TSQuery` | 查询对象，用于模式匹配 |
| `TSQueryCursor` | 查询游标，遍历匹配结果 |
| `TSQueryMatch` | 查询匹配结果 |
| `TSQueryCapture` | 捕获的节点 |
| `TSPoint` | 位置 (行, 列) |
| `TSInputEdit` | 增量编辑信息 |
| `TSNativeObject` | Native 对象基类 |

### 语言绑定 (`com.wuxianggujun.tinaide.treesitter.languages`)

| 类 | 说明 |
|---|---|
| `TSLanguageCpp` | C++ 语言 |
| `TSLanguageCMake` | CMake 语言 |

## 使用示例

```kotlin
// 1. 获取语言实例
val language = TSLanguageCpp.getInstance()

// 2. 创建解析器并设置语言
val parser = TSParser.create()
parser.setLanguage(language)

// 3. 解析代码
val code = """
    int main() {
        return 0;
    }
""".trimIndent()
val tree = parser.parseString(code)

// 4. 遍历语法树
val rootNode = tree.rootNode
println("Root type: ${rootNode.type}")  // "translation_unit"
println("Child count: ${rootNode.childCount}")

// 5. 使用查询进行模式匹配
val query = TSQuery.create(language, "(function_definition) @func")
val cursor = TSQueryCursor.create()
cursor.exec(query, rootNode)

var match = cursor.nextMatch()
while (match != null) {
    for (capture in match.captures) {
        println("Found function at ${capture.node.startPoint}")
    }
    match = cursor.nextMatch()
}

// 6. 清理资源
cursor.close()
query.close()
tree.close()
parser.close()
```

## 与 sora-editor 集成

sora-editor 的 `language-treesitter` 模块已修改为使用这些绑定：

```kotlin
// 创建语言规范
val languageSpec = TsLanguageSpec(
    language = TSLanguageCpp.getInstance(),
    highlightScmSource = cppHighlightsScm,
    codeBlocksScmSource = cppBlocksScm,
    bracketsScmSource = cppBracketsScm
)

// 创建语言支持
val language = TsLanguage(languageSpec, /* ... */)
editor.setEditorLanguage(language)
```

## 版本信息

- Tree-sitter 核心库: v0.24+ (API version 15)
- tree-sitter-cpp: v0.25.9
- tree-sitter-cmake: v0.25.10

## 注意事项

1. **内存管理**: 所有 `TSNativeObject` 子类都实现了 `AutoCloseable`，使用完毕后需要调用 `close()` 或使用 `use {}` 块
2. **线程安全**: `TSParser` 不是线程安全的，每个线程应使用独立的解析器实例
3. **增量解析**: 支持通过 `TSTree.edit()` 和 `TSParser.parseString(code, oldTree)` 进行增量解析

## 参考链接

- [Tree-sitter 官方文档](https://tree-sitter.github.io/tree-sitter/)
- [Tree-sitter GitHub](https://github.com/tree-sitter/tree-sitter)
- [tree-sitter-cpp](https://github.com/tree-sitter/tree-sitter-cpp)
- [tree-sitter-cmake](https://github.com/uyha/tree-sitter-cmake)
