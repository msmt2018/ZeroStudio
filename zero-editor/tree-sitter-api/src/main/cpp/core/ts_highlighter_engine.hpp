/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 扩充视口计算引擎：无缝支持由 TSH 编译出的原生 SCM 查询与多 Capture 样式/优先级/动作表。
 * 2. 导出格式规范（步长 5 个整型）：[StartByte, EndByte, PackedStyle, Priority, ActionId]。
 * 3. 支持原生 Tree-sitter 谓词（#match?, #eq?, #any-of?, #not-match?）。
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

struct CaptureStyleMeta {
    int32_t packed_style{0};
    int32_t priority{100};
    int32_t action_id{0};
};

class TSHighlighterEngine {
public:
    TSHighlighterEngine();
    ~TSHighlighterEngine();

    TSHighlighterEngine(const TSHighlighterEngine &) = delete;
    TSHighlighterEngine &operator=(const TSHighlighterEngine &) = delete;

    bool setLanguage(const TSLanguage *language);
    bool setQuery(const char *query_source, uint32_t length, uint32_t *error_offset, uint32_t *error_type);
    void setCaptureStyleTable(const int32_t *meta_table, size_t entry_count);
    void setText(const char *utf8_text, size_t length);

    void applyIncrementalEdit(
        uint32_t start_byte, uint32_t old_end_byte, uint32_t new_end_byte,
        TSPoint start_point, TSPoint old_end_point, TSPoint new_end_point,
        const char *inserted_text, uint32_t inserted_len
    );

    std::vector<int32_t> computeViewportHighlight(uint32_t start_line, uint32_t end_line);
    const char *getCaptureName(uint32_t capture_id, uint32_t *length) const;
    bool hasSyntaxError();

private:
    mutable std::mutex m_mutex;
    NativeTextBuffer m_textBuffer;
    TSParser *m_parser{nullptr};
    TSTree *m_tree{nullptr};
    const TSLanguage *m_language{nullptr};
    TSQuery *m_query{nullptr};
    TSQueryCursor *m_queryCursor{nullptr};
    std::vector<CaptureStyleMeta> m_styleMetas;
};

} // namespace zerostudio

#endif // ZERO_STUDIO_TS_HIGHLIGHTER_ENGINE_HPP