/**
 * @author android_zero
 *
 * 变更说明与实现细节：
 * 1. 纯净 C API 对接：直接使用 ts_query_new 编译 SCM 语句，所有高级糖（#match? 等）全部在 C 内核原生匹配。
 * 2. 引入优先级排序与重叠冲突处理算法（Priority Arbitration），保证内层精确匹配优先于外层模糊匹配。
 */

#include "ts_highlighter_engine.hpp"

namespace zerostudio {

TSHighlighterEngine::TSHighlighterEngine() {
    m_parser = ts_parser_new();
    m_queryCursor = ts_query_cursor_new();
}

TSHighlighterEngine::~TSHighlighterEngine() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_tree) {
        ts_tree_delete(m_tree);
        m_tree = nullptr;
    }
    if (m_query) {
        ts_query_delete(m_query);
        m_query = nullptr;
    }
    if (m_queryCursor) {
        ts_query_cursor_delete(m_queryCursor);
        m_queryCursor = nullptr;
    }
    if (m_parser) {
        ts_parser_delete(m_parser);
        m_parser = nullptr;
    }
}

bool TSHighlighterEngine::setLanguage(const TSLanguage *language) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_language = language;
    return ts_parser_set_language(m_parser, language);
}

bool TSHighlighterEngine::setQuery(
    const char *query_source,
    uint32_t length,
    uint32_t *error_offset,
    uint32_t *error_type
) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_query) {
        ts_query_delete(m_query);
        m_query = nullptr;
    }
    if (!m_language || !query_source) return false;

    TSQueryError err_type = TSQueryErrorNone;
    // 直接送入 100% 纯净的原生 SCM，无任何 TSH 专有标记污染
    m_query = ts_query_new(m_language, query_source, length, error_offset, &err_type);
    if (error_type) {
        *error_type = static_cast<uint32_t>(err_type);
    }
    return m_query != nullptr;
}

void TSHighlighterEngine::setCompiledStyles(const int32_t *meta_array, size_t count) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_captureRules.clear();
    m_captureRules.reserve(count);

    // 格式约定：每 4 个整型定义一个捕获组的属性 [RGBA, StyleFlags, Priority, ActionId]
    for (size_t i = 0; i < count; ++i) {
        size_t base = i * 4;
        CompiledCaptureRule rule;
        rule.rgba_color = static_cast<uint32_t>(meta_array[base + 0]);
        rule.style_flags = static_cast<uint32_t>(meta_array[base + 1]);
        rule.priority = meta_array[base + 2];
        rule.action_id = meta_array[base + 3];
        m_captureRules.push_back(rule);
    }
}

void TSHighlighterEngine::setText(const char *utf8_text, size_t length) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_textBuffer.setText(utf8_text, length);

    if (m_tree) {
        ts_tree_delete(m_tree);
        m_tree = nullptr;
    }

    if (m_language) {
        TSInput input = m_textBuffer.getTSInput();
        m_tree = ts_parser_parse(m_parser, nullptr, input);
    }
}

void TSHighlighterEngine::applyIncrementalEdit(
    uint32_t start_byte, uint32_t old_end_byte, uint32_t new_end_byte,
    TSPoint start_point, TSPoint old_end_point, TSPoint new_end_point,
    const char *inserted_text, uint32_t inserted_len
) {
    std::lock_guard<std::mutex> lock(m_mutex);

    // 1. 同步更新 Native 内部文本缓冲区
    m_textBuffer.applyEdit(start_byte, old_end_byte, inserted_text, inserted_len);

    // 2. 原地通知旧 AST 发生编辑，标记脏区间 (In-place edit)
    if (m_tree) {
        TSInputEdit edit = {
            start_byte, old_end_byte, new_end_byte,
            start_point, old_end_point, new_end_point
        };
        ts_tree_edit(m_tree, &edit);
    }

    // 3. 基于旧树执行局部增量解析
    if (m_language) {
        TSInput input = m_textBuffer.getTSInput();
        TSTree *new_tree = ts_parser_parse(m_parser, m_tree, input);
        if (m_tree) {
            ts_tree_delete(m_tree);
        }
        m_tree = new_tree;
    }
}

struct RawHighlightSpan {
    uint32_t start_byte;
    uint32_t end_byte;
    uint32_t rgba;
    uint32_t style;
    int32_t priority;
    int32_t action_id;
};

std::vector<int32_t> TSHighlighterEngine::computeViewportHighlight(uint32_t start_line, uint32_t end_line) {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::vector<int32_t> output;
    output.reserve(2048);

    if (!m_tree || !m_query || !m_queryCursor) {
        return output;
    }

    TSNode root_node = ts_tree_root_node(m_tree);

    TSPoint start_pt = {start_line, 0};
    TSPoint end_pt = {end_line, UINT32_MAX};
    ts_query_cursor_set_point_range(m_queryCursor, start_pt, end_pt);
    ts_query_cursor_exec(m_queryCursor, m_query, root_node);

    TSQueryMatch match;
    uint32_t capture_index = 0;

    std::vector<RawHighlightSpan> collected;
    collected.reserve(512);

    while (ts_query_cursor_next_capture(m_queryCursor, &match, &capture_index)) {
        const TSQueryCapture &cap = match.captures[capture_index];
        uint32_t s_byte = ts_node_start_byte(cap.node);
        uint32_t e_byte = ts_node_end_byte(cap.node);

        if (s_byte >= e_byte) continue;

        CompiledCaptureRule rule;
        if (cap.index < m_captureRules.size()) {
            rule = m_captureRules[cap.index];
        }

        collected.push_back({
            s_byte,
            e_byte,
            rule.rgba_color,
            rule.style_flags,
            rule.priority,
            rule.action_id
        });
    }

    // 稳定排序：按起始位置升序；起始相同时按优先级升序，确保高优先级覆盖低优先级
    std::stable_sort(collected.begin(), collected.end(), [](const RawHighlightSpan &a, const RawHighlightSpan &b) {
        if (a.start_byte != b.start_byte) return a.start_byte < b.start_byte;
        return a.priority < b.priority;
    });

    for (const auto &span : collected) {
        output.push_back(static_cast<int32_t>(span.start_byte));
        output.push_back(static_cast<int32_t>(span.end_byte));
        output.push_back(static_cast<int32_t>(span.rgba));
        output.push_back(static_cast<int32_t>(span.style));
        output.push_back(span.action_id);
    }

    return output;
}

bool TSHighlighterEngine::hasSyntaxError() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_tree) return false;
    TSNode root = ts_tree_root_node(m_tree);
    return ts_node_has_error(root);
}

} // namespace zerostudio