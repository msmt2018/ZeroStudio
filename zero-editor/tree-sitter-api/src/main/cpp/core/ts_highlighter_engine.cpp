/**
 * @author android_zero
 *
 * 变更说明与实现细节：
 * 1. 采用线程互斥锁保证后台多线程解析与主线程读取视口高亮互不干扰。
 * 2. 增量重解析严格保证：ts_tree_edit 优先标记旧 AST 节点失效并计算位移，
 *    紧接着把旧树传递给 ts_parser_parse，由官方核心实现复用（通常耗时 < 1ms）。
 * 3. 视口裁剪：使用 ts_query_cursor_set_point_range 彻底过滤屏幕外无关 AST。
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

bool TSHighlighterEngine::setHighlightQuery(
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
    if (!m_language) return false;

    TSQueryError err_type = TSQueryErrorNone;
    m_query = ts_query_new(m_language, query_source, length, error_offset, &err_type);
    if (error_type) {
        *error_type = static_cast<uint32_t>(err_type);
    }
    return m_query != nullptr;
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

    // 1. 同步更新 Native 内部文本缓冲区（零拷贝驻留）
    m_textBuffer.applyEdit(start_byte, old_end_byte, inserted_text, inserted_len);

    // 2. 原地通知旧 AST 发生编辑，标记脏区间 (In-place edit)
    if (m_tree) {
        TSInputEdit edit = {
            start_byte,
            old_end_byte,
            new_end_byte,
            start_point,
            old_end_point,
            new_end_point
        };
        ts_tree_edit(m_tree, &edit);
    }

    // 3. 基于旧树执行局部增量解析 (耗时通常 < 1ms，0 次大文本 JNI 传输)
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
    result.reserve(1024);

    if (!m_tree || !m_query || !m_queryCursor) {
        return result;
    }

    TSNode root_node = ts_tree_root_node(m_tree);

    // 视口范围过滤：仅在视口范围内执行匹配，大幅度削减无用节点遍历
    TSPoint start_pt = {start_line, 0};
    TSPoint end_pt = {end_line, UINT32_MAX};
    ts_query_cursor_set_point_range(m_queryCursor, start_pt, end_pt);

    ts_query_cursor_exec(m_queryCursor, m_query, root_node);

    TSQueryMatch match;
    uint32_t capture_index = 0;

    // 遍历匹配项，以扁平紧凑格式写入 vector [Start, End, CaptureId, PatternIndex]
    while (ts_query_cursor_next_capture(m_queryCursor, &match, &capture_index)) {
        const TSQueryCapture &cap = match.captures[capture_index];
        uint32_t s_byte = ts_node_start_byte(cap.node);
        uint32_t e_byte = ts_node_end_byte(cap.node);

        result.push_back(static_cast<int32_t>(s_byte));
        result.push_back(static_cast<int32_t>(e_byte));
        result.push_back(static_cast<int32_t>(cap.index));
        result.push_back(static_cast<int32_t>(match.pattern_index));
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