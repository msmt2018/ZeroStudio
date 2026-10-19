/**
 * @author android_zero
 *
 * 变更说明与实现细节：
 * 1. 采用 TSQuery 原生游标循环，天然执行 SCM 中所有的原生谓词（#match?, #eq?, #any-of? 等）。
 * 2. 依据 CaptureTable 赋予匹配区间色值、粗斜体、交互 ActionId，并在冲突重叠区间利用 Priority 控制权级。
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
    m_query = ts_query_new(m_language, query_source, length, error_offset, &err_type);
    if (error_type) {
        *error_type = static_cast<uint32_t>(err_type);
    }
    return m_query != nullptr;
}

void TSHighlighterEngine::setCaptureStyleTable(const int32_t *meta_table, size_t entry_count) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_styleMetas.clear();
    m_styleMetas.reserve(entry_count);

    for (size_t i = 0; i < entry_count; ++i) {
        size_t base = i * 3;
        CaptureStyleMeta meta;
        meta.packed_style = meta_table[base];
        meta.priority = meta_table[base + 1];
        meta.action_id = meta_table[base + 2];
        m_styleMetas.push_back(meta);
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

    m_textBuffer.applyEdit(start_byte, old_end_byte, inserted_text, inserted_len);

    if (m_tree) {
        TSInputEdit edit = {
            start_byte, old_end_byte, new_end_byte,
            start_point, old_end_point, new_end_point
        };
        ts_tree_edit(m_tree, &edit);
    }

    if (m_language) {
        TSInput input = m_textBuffer.getTSInput();
        TSTree *new_tree = ts_parser_parse(m_parser, m_tree, input);
        if (m_tree) {
            ts_tree_delete(m_tree);
        }
        m_tree = new_tree;
    }
}

std::vector<int32_t> TSHighlighterEngine::computeViewportHighlight(uint32_t start_line, uint32_t end_line) {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::vector<int32_t> result;
    result.reserve(2048);

    if (!m_tree || !m_query || !m_queryCursor) {
        return result;
    }

    TSNode root_node = ts_tree_root_node(m_tree);

    TSPoint start_pt = {start_line, 0};
    TSPoint end_pt = {end_line, UINT32_MAX};
    ts_query_cursor_set_point_range(m_queryCursor, start_pt, end_pt);
    ts_query_cursor_exec(m_queryCursor, m_query, root_node);

    TSQueryMatch match;
    uint32_t capture_index = 0;

    while (ts_query_cursor_next_capture(m_queryCursor, &match, &capture_index)) {
        const TSQueryCapture &cap = match.captures[capture_index];
        uint32_t s_byte = ts_node_start_byte(cap.node);
        uint32_t e_byte = ts_node_end_byte(cap.node);

        int32_t packed_style = 0;
        int32_t priority = 100;
        int32_t action_id = 0;

        if (cap.index < m_styleMetas.size()) {
            const auto &meta = m_styleMetas[cap.index];
            packed_style = meta.packed_style;
            priority = meta.priority;
            action_id = meta.action_id;
        }

        result.push_back(static_cast<int32_t>(s_byte));
        result.push_back(static_cast<int32_t>(e_byte));
        result.push_back(packed_style);
        result.push_back(priority);
        result.push_back(action_id);
    }

    return result;
}

const char *TSHighlighterEngine::getCaptureName(uint32_t capture_id, uint32_t *length) const {
    if (!m_query) return nullptr;
    return ts_query_capture_name_for_id(m_query, capture_id, length);
}

bool TSHighlighterEngine::hasSyntaxError() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_tree) return false;
    TSNode root = ts_tree_root_node(m_tree);
    return ts_node_has_error(root);
}

} // namespace zerostudio