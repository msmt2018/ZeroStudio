/**
 * @author android_zero
 *
 * 架构更新：
 * 1. 彻底支持 100% 纯净 SCM 文本与 TSH 提取的样式表解耦。
 * 2. 支持 RGBA 全色域直接输出，不再使用粗糙的 16 位颜色。
 * 3. 增强游标匹配器：支持优先级（Priority）覆盖逻辑，重叠范围中高优先级 Token 自动覆盖低优先级 Token。
 * 4. JNI 返回扁平数组采用工业级布局：[StartByte, EndByte, RgbaColor, StyleFlags, ActionId]。
 *
 * 方法流程图：
 * compileQuery(scm) ──> JNI::setQueryAndStyles() ──> ts_query_new()
 *                                                          │
 * onEdit / onScroll ──> computeViewportHighlight() ────────┴──> Flat IntArray [S, E, RGBA, Flags, Action]
 */

#ifndef ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP
#define ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP

#include <vector>
#include <string>
#include <mutex>
#include <cstdint>
#include <unordered_map>
#include <algorithm>
#include "tree_sitter/api.h"
#include "native_text_buffer.hpp"

namespace zerostudio {

// 紧凑捕获组样式结构
struct CompiledCaptureRule {
    uint32_t rgba_color{0xFFFFFFFF}; // RGBA 32-bit 精确颜色
    uint32_t style_flags{0};         // 样式标志位 (Bit 0: Bold, Bit 1: Italic, Bit 2: Underline, Bit 3: Strikethrough)
    int32_t  priority{100};          // 覆盖优先级 (默认 100)
    int32_t  action_id{0};           // 编辑器交互动作 ID
};

class TSHighlighterEngine {
public:
    TSHighlighterEngine();
    ~TSHighlighterEngine();

    TSHighlighterEngine(const TSHighlighterEngine &) = delete;
    TSHighlighterEngine &operator=(const TSHighlighterEngine &) = delete;

    bool setLanguage(const TSLanguage *language);
    
    // 设置 100% 纯净的 Tree-sitter 原生 SCM 查询文本
    bool setQuery(const char *query_source, uint32_t length, uint32_t *error_offset, uint32_t *error_type);

    // 绑定与查询捕获组完全对应的扁平元数据表
    void setCompiledStyles(const int32_t *meta_array, size_t count);

    void setText(const char *utf8_text, size_t length);

    void applyIncrementalEdit(
        uint32_t start_byte, uint32_t old_end_byte, uint32_t new_end_byte,
        TSPoint start_point, TSPoint old_end_point, TSPoint new_end_point,
        const char *inserted_text, uint32_t inserted_len
    );

    /**
     * 视口计算高亮：内部基于优先级（Priority）做区间覆盖处理，消除重叠高亮闪烁
     * 输出扁平数组，每 5 个整型为一个 Span 单元：
     * [0: StartByte, 1: EndByte, 2: RgbaColor, 3: StyleFlags, 4: ActionId]
     */
    std::vector<int32_t> computeViewportHighlight(uint32_t start_line, uint32_t end_line);

    bool hasSyntaxError();

private:
    mutable std::mutex m_mutex;
    NativeTextBuffer m_textBuffer;
    TSParser *m_parser{nullptr};
    TSTree *m_tree{nullptr};
    const TSLanguage *m_language{nullptr};
    TSQuery *m_query{nullptr};
    TSQueryCursor *m_queryCursor{nullptr};
    
    std::vector<CompiledCaptureRule> m_captureRules;
};

} // namespace zerostudio

#endif // ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP