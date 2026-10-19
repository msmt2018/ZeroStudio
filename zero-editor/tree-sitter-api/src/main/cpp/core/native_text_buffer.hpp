/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 原生内存文本缓冲区（NativeTextBuffer），专为树解析与编辑器交互设计。
 * 2. 避免大文本在 JNI 与 JVM 堆之间高频序列化与拷贝，全量文本驻留 Native 堆。
 * 3. 对接 Tree-sitter 核心 TSInput 规范，提供零拷贝分块只读（Zero-Copy Chunk Reading）回调函数。
 * 4. 维护行偏移索引表（Line Offsets Cache），支持极速字节偏移与 (Row, Column) 坐标互查。
 *
 * 工作流程线路图：
 * [用户键入/删除]
 *       │
 *       ▼
 * NativeTextBuffer::applyEdit() ──> [调整底层 std::string 字节内存]
 *       │
 *       ▼
 * NativeTextBuffer::updateLineOffsets() ──> [增量/全量刷新行首字节索引]
 *       │
 *       ▼
 * Tree-sitter 解析调用 tsReadCallback() ──> [返回当前分块只读裸指针，无任何中间内存分配]
 */

#ifndef ZERO_STUDIO_NATIVE_TEXT_BUFFER_HPP
#define ZERO_STUDIO_NATIVE_TEXT_BUFFER_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include "tree_sitter/api.h"

namespace zerostudio {

class NativeTextBuffer {
public:
    NativeTextBuffer();
    ~NativeTextBuffer();

    NativeTextBuffer(const NativeTextBuffer &) = delete;
    NativeTextBuffer &operator=(const NativeTextBuffer &) = delete;

    void setText(const char *utf8_text, size_t length);
    void applyEdit(uint32_t start_byte, uint32_t old_end_byte, const char *new_text, uint32_t new_text_len);

    const char *getText() const { return m_buffer.data(); }
    size_t getLength() const { return m_buffer.size(); }

    TSPoint pointForByte(uint32_t byte_offset) const;
    TSInput getTSInput();

private:
    std::string m_buffer;
    std::vector<uint32_t> m_line_offsets;

    void rebuildLineOffsets();

    static const char *tsReadCallback(
        void *payload,
        uint32_t byte_index,
        TSPoint position,
        uint32_t *bytes_read
    );
};

} // namespace zerostudio

#endif // ZERO_STUDIO_NATIVE_TEXT_BUFFER_HPP