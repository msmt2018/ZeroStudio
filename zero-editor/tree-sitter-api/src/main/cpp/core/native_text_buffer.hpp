/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

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