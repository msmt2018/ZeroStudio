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
    /**
     * 构造函数：初始化 Native 文本缓冲区并预分配基础内存块。
     */
    NativeTextBuffer();

    /**
     * 析构函数：释放底层字符缓冲和索引结构。
     */
    ~NativeTextBuffer();

    // 禁止值拷贝，防止大文本内存重复分配
    NativeTextBuffer(const NativeTextBuffer &) = delete;
    NativeTextBuffer &operator=(const NativeTextBuffer &) = delete;

    /**
     * 重置并设置全量文本。
     * @param utf8_text 外部传入的 UTF-8 文本指针
     * @param length    文本字节长度
     */
    void setText(const char *utf8_text, size_t length);

    /**
     * 原地应用编辑修改（增量替换）。
     * @param start_byte   被替换区间的起始字节偏移
     * @param old_end_byte 被替换区间的结束字节偏移
     * @param new_text     插入的新文本字符指针
     * @param new_text_len 插入的新文本字节长度
     */
    void applyEdit(uint32_t start_byte, uint32_t old_end_byte, const char *new_text, uint32_t new_text_len);

    /**
     * 获取只读数据裸指针。
     */
    const char *getText() const { return m_buffer.data(); }

    /**
     * 获取当前缓冲区的有效字节大小。
     */
    size_t getLength() const { return m_buffer.size(); }

    /**
     * 将字节偏移转换为 TSPoint (Row, Column) 行列坐标。
     * @param byte_offset 字节偏移位置
     * @return 对应的行号与列号坐标结构
     */
    TSPoint pointForByte(uint32_t byte_offset) const;

    /**
     * 构建对接 Tree-sitter 解析内核的 TSInput 驱动结构体。
     * @return 符合 TSInput 标准的按需读取配置
     */
    TSInput getTSInput();

private:
    std::string m_buffer;                      ///< 文本存储核心连续存储器
    std::vector<uint32_t> m_line_offsets;      ///< 换行符索引缓存：存储各行行首字节偏移量

    /**
     * 重新构建换行符索引，加速后续基于点位置（Row, Column）的双向换算。
     */
    void rebuildLineOffsets();

    /**
     * Tree-sitter 回调静态函数：由内核按需索取代码块。
     */
    static const char *tsReadCallback(
        void *payload,
        uint32_t byte_index,
        TSPoint position,
        uint32_t *bytes_read
    );
};

} // namespace zerostudio

#endif // ZERO_STUDIO_NATIVE_TEXT_BUFFER_HPP