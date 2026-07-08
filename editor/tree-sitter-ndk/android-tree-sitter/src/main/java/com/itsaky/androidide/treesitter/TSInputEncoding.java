/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 源代码输入编码类型。
 *
 * <p>与 tree-sitter 0.27 的 {@code TSInputEncoding} 枚举一致。0.27 将原
 * {@code TSInputEncodingUTF16} 拆分为 {@code TSInputEncodingUTF16LE} 和
 * {@code TSInputEncodingUTF16BE}，并新增 {@code TSInputEncodingCustom}（自定义 decode 函数）。
 *
 * <p>对于 {@link #TSInputEncodingCustom}，{@link #getCharset()} 返回 {@code null}，
 * 因为自定义编码没有对应的 Java {@link Charset}。使用 Custom 编码需要通过 C 层设置
 * {@code TSInput.decode} 函数指针，目前 Java 层不直接支持。
 */
public enum TSInputEncoding {
    /** UTF-8 编码（tree-sitter flag=0）。 */
    TSInputEncodingUTF8(0, StandardCharsets.UTF_8),
    /** UTF-16 小端序（tree-sitter flag=1）。0.27 中原 TSInputEncodingUTF16 被拆分为 LE/BE。 */
    TSInputEncodingUTF16LE(1, StandardCharsets.UTF_16LE),
    /** UTF-16 大端序（tree-sitter flag=2）。0.27 新增。 */
    TSInputEncodingUTF16BE(2, StandardCharsets.UTF_16BE),
    /** 自定义编码（tree-sitter flag=3）。0.27 新增，需要 C 层提供 decode 函数。 */
    TSInputEncodingCustom(3, null);

    private final int flag;
    private final Charset charset;

    TSInputEncoding(int flag, Charset charset) {
        this.flag = flag;
        this.charset = charset;
    }

    public int getFlag() {
        return flag;
    }

    /**
     * 获取此编码对应的 Java {@link Charset}。
     *
     * @return 对应的 Charset，{@link #TSInputEncodingCustom} 返回 {@code null}。
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 根据 tree-sitter flag 值查找对应的编码。
     *
     * @param flag tree-sitter C API 中的编码 flag 值（0-3）。
     * @return 对应的 {@link TSInputEncoding}，无效 flag 返回 {@link #TSInputEncodingUTF8}。
     */
    public static TSInputEncoding fromFlag(int flag) {
        for (TSInputEncoding encoding : values()) {
            if (encoding.flag == flag) {
                return encoding;
            }
        }
        return TSInputEncodingUTF8;
    }
}
