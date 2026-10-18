/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. Tree-sitter 高性能增量语法高亮引擎，生命周期完全受控于 C++。
 * 2. 闭环封装 Parser、Tree、Query、QueryCursor，外部无需了解任何指针详情。
 * 3. 视口驱动计算（Viewport-Driven）：通过设置行范围限制 Query 遍历区间，
 *    仅提取当前屏幕可见行的高亮标记。
 * 4. 扁平化数据下发：直接将解析结果编码为跨步紧凑的 int32_t 连续内存块。
 *
 * 方法流程脑图：
 * [Kotlin 传入 Edit 增量]
 *            │
 *            ▼
 * TSHighlighterEngine::applyIncrementalEdit()
 * ├── NativeTextBuffer::applyEdit() ──> [更新 Native 文本]
 * ├── ts_tree_edit() ──────────────────> [标记旧 AST 节点位移]
 * └── ts_parser_parse() ───────────────> [增量重构 AST 树 (耗时 < 1ms)]
 *
 * [Kotlin 传入视口行范围]
 *            │
 *            ▼
 * TSHighlighterEngine::computeViewportHighlight()
 * ├── ts_query_cursor_set_point_range() ──> [限制光标遍历区间]
 * ├── ts_query_cursor_exec() ─────────────> [仅在视口匹配 Token]
 * └── 填充 std::vector<int32_t> ──────────> [打包 [Start, End, CaptureId, PatternIndex]]
 */

#ifndef ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP
#define ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP

#include <vector>
#include <string>
#include <mutex>
#include <cstdint>
#include "tree_sitter/api.h"
#include "native_text_buffer.hpp"

namespace zerostudio {

class TSHighlighterEngine {
public:
    TSHighlighterEngine();
    ~TSHighlighterEngine();

    // 禁用拷贝语义
    TSHighlighterEngine(const TSHighlighterEngine &) = delete;
    TSHighlighterEngine &operator=(const TSHighlighterEngine &) = delete;

    /**
     * 绑定目标编程语言解析定义。
     */
    bool setLanguage(const TSLanguage *language);

    /**
     * 编译并设定高亮 S-expression 查询表达式。
     */
    bool setHighlightQuery(const char *query_source, uint32_t length, uint32_t *error_offset, uint32_t *error_type);

    /**
     * 初始加载/全量重置文件文本。
     */
    void setText(const char *utf8_text, size_t length);

    /**
     * 极速增量重解析：同步更新 Native 文本与语法树。
     */
    void applyIncrementalEdit(
        uint32_t start_byte, uint32_t old_end_byte, uint32_t new_end_byte,
        TSPoint start_point, TSPoint old_end_point, TSPoint new_end_point,
        const char *inserted_text, uint32_t inserted_len
    );

    /**
     * 针对屏幕当前视口可见区域提取高亮标记。
     * @param start_line 视口首行（从 0 开始）
     * @param end_line   视口末行
     * @return 扁平整型数组：每 4 个 int 描述一个高亮区间 [StartByte, EndByte, CaptureId, PatternIndex]
     */
    std::vector<int32_t> computeViewportHighlight(uint32_t start_line, uint32_t end_line);

    /**
     * 根据 captureId 获取其语义角色名称（如 keyword, string, function）。
     */
    const char *getCaptureName(uint32_t capture_id, uint32_t *length) const;

    /**
     * 获取当前 AST 是否包含语法错误节点。
     */
    bool hasSyntaxError();

private:
    mutable std::mutex m_mutex;        ///< 线程安全锁：支持并发编辑与异步渲染请求
    NativeTextBuffer m_textBuffer;     ///< Native 文本存储区
    TSParser *m_parser{nullptr};       ///< Tree-sitter 主解析器
    TSTree *m_tree{nullptr};           ///< 当前 AST 语法树
    const TSLanguage *m_language{nullptr}; ///< 当前绑定的语言模型
    TSQuery *m_query{nullptr};         ///< 编译后高亮查询模型
    TSQueryCursor *m_queryCursor{nullptr}; ///< 高级查询游标
};

} // namespace zerostudio

#endif // ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP