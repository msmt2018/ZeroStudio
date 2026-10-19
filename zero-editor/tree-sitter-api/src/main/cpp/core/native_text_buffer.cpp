/**
 * @author android_zero
 *
 * 变更说明与实现细节：
 * 1. 采用 STL 连续存储优化小规模增量插入开销。
 * 2. 行索引采用二分查找（std::upper_bound），使得根据字节换算行号的时间复杂度保持在 O(log N)。
 * 3. 严格边界安全保护，输入越界自动做饱和裁剪，杜绝越界访问崩溃。
 */

#include "native_text_buffer.hpp"

namespace zerostudio {

NativeTextBuffer::NativeTextBuffer() {
    m_buffer.reserve(8192);
    m_line_offsets.push_back(0);
}

NativeTextBuffer::~NativeTextBuffer() = default;

void NativeTextBuffer::setText(const char *utf8_text, size_t length) {
    if (!utf8_text || length == 0) {
        m_buffer.clear();
        m_line_offsets.clear();
        m_line_offsets.push_back(0);
        return;
    }
    m_buffer.assign(utf8_text, length);
    rebuildLineOffsets();
}

void NativeTextBuffer::applyEdit(
    uint32_t start_byte,
    uint32_t old_end_byte,
    const char *new_text,
    uint32_t new_text_len
) {
    size_t cur_len = m_buffer.size();
    if (start_byte > cur_len) {
        start_byte = static_cast<uint32_t>(cur_len);
    }
    if (old_end_byte > cur_len) {
        old_end_byte = static_cast<uint32_t>(cur_len);
    }
    if (start_byte > old_end_byte) {
        start_byte = old_end_byte;
    }

    size_t delete_count = old_end_byte - start_byte;
    
    if (new_text && new_text_len > 0) {
        m_buffer.replace(start_byte, delete_count, new_text, new_text_len);
    } else {
        m_buffer.erase(start_byte, delete_count);
    }

    rebuildLineOffsets();
}

void NativeTextBuffer::rebuildLineOffsets() {
    m_line_offsets.clear();
    m_line_offsets.push_back(0);

    const char *data = m_buffer.data();
    size_t size = m_buffer.size();

    for (size_t i = 0; i < size; ++i) {
        if (data[i] == '\n') {
            m_line_offsets.push_back(static_cast<uint32_t>(i + 1));
        }
    }
}

TSPoint NativeTextBuffer::pointForByte(uint32_t byte_offset) const {
    if (m_buffer.empty() || byte_offset == 0) {
        return TSPoint{0, 0};
    }
    if (byte_offset > m_buffer.size()) {
        byte_offset = static_cast<uint32_t>(m_buffer.size());
    }

    auto it = std::upper_bound(m_line_offsets.begin(), m_line_offsets.end(), byte_offset);
    uint32_t row = static_cast<uint32_t>(std::distance(m_line_offsets.begin(), it) - 1);
    uint32_t col = byte_offset - m_line_offsets[row];

    return TSPoint{row, col};
}

const char *NativeTextBuffer::tsReadCallback(
    void *payload,
    uint32_t byte_index,
    TSPoint position,
    uint32_t *bytes_read
) {
    (void)position;
    auto *self = static_cast<NativeTextBuffer *>(payload);

    if (byte_index >= self->m_buffer.size()) {
        *bytes_read = 0;
        return "";
    }

    *bytes_read = static_cast<uint32_t>(self->m_buffer.size() - byte_index);
    return self->m_buffer.data() + byte_index;
}

TSInput NativeTextBuffer::getTSInput() {
    TSInput input;
    input.payload = this;
    input.read = tsReadCallback;
    input.encoding = TSInputEncodingUTF8;
    input.decode = nullptr;
    return input;
}

} // namespace zerostudio