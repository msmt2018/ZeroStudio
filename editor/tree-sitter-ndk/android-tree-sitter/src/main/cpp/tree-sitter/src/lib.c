// tree-sitter 0.27.0 amalgamation 入口 (android-tree-sitter 内嵌版)
// 【修改】移除 wasm_store.c (wasm 支持不做, 原文件未复制进本目录)
#define _POSIX_C_SOURCE 200112L

#include "./alloc.c"
#include "./get_changed_ranges.c"
#include "./language.c"
#include "./lexer.c"
#include "./node.c"
#include "./parser.c"
#include "./point.c"
#include "./query.c"
#include "./stack.c"
#include "./subtree.c"
#include "./tree_cursor.c"
#include "./tree.c"
