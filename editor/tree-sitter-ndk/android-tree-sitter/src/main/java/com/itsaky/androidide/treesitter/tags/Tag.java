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

package com.itsaky.androidide.treesitter.tags;

import com.itsaky.androidide.treesitter.TSPoint;
import java.util.Objects;

/**
 * 表示一个从源代码中提取的标签（tag）。
 *
 * <p>对应 tree-sitter 0.27 的 {@code TSTag} 结构体。一个标签描述了源代码中的一个命名实体，
 * 例如函数定义、类定义、变量引用等。
 *
 * <p>标签分为两类：
 * <ul>
 *   <li>定义标签（{@code is_definition = true}）：如 {@code definition.function}、
 *       {@code definition.class} 等 capture 匹配的节点。</li>
 *   <li>引用标签（{@code is_definition = false}）：如 {@code reference.call} 等 capture 匹配的节点。</li>
 * </ul>
 *
 * <p>{@code syntaxType} 字段标识标签的语法类型（如 "function"、"class"、"method"），
 * 其值由 query 中 capture 名称的前缀（{@code definition.} 或 {@code reference.}）之后的部分决定。
 *
 * @see TagsConfiguration#getSyntaxTypeName(int)
 */
public final class Tag {

  /** 标签整体的起始字节偏移（name 和 tag 节点中较小的起始字节）。 */
  private final int startByte;
  /** 标签整体的结束字节偏移（name 和 tag 节点中较大的结束字节）。 */
  private final int endByte;
  /** {@code @name} capture 节点的起始字节偏移。 */
  private final int nameStartByte;
  /** {@code @name} capture 节点的结束字节偏移。 */
  private final int nameEndByte;
  /** name 所在行的起始字节偏移（已 trim 空白，最长 180 字节）。 */
  private final int lineStartByte;
  /** name 所在行的结束字节偏移。 */
  private final int lineEndByte;
  /** name 节点的起始 point。 */
  private final TSPoint startPoint;
  /** name 节点的结束 point。 */
  private final TSPoint endPoint;
  /** name 在行内的 UTF-16 起始列。 */
  private final int utf16StartColumn;
  /** name 在行内的 UTF-16 结束列。 */
  private final int utf16EndColumn;
  /** 文档字符串，可能为 {@code null}。来自 {@code @doc} capture。 */
  private final String docs;
  /** 是否为定义标签。 */
  private final boolean isDefinition;
  /** 语法类型 ID，可通过 {@link TagsConfiguration#getSyntaxTypeName(int)} 查询名称。 */
  private final int syntaxTypeId;

  Tag(int startByte, int endByte, int nameStartByte, int nameEndByte,
      int lineStartByte, int lineEndByte, TSPoint startPoint, TSPoint endPoint,
      int utf16StartColumn, int utf16EndColumn, String docs,
      boolean isDefinition, int syntaxTypeId) {
    this.startByte = startByte;
    this.endByte = endByte;
    this.nameStartByte = nameStartByte;
    this.nameEndByte = nameEndByte;
    this.lineStartByte = lineStartByte;
    this.lineEndByte = lineEndByte;
    this.startPoint = startPoint;
    this.endPoint = endPoint;
    this.utf16StartColumn = utf16StartColumn;
    this.utf16EndColumn = utf16EndColumn;
    this.docs = docs;
    this.isDefinition = isDefinition;
    this.syntaxTypeId = syntaxTypeId;
  }

  /**
   * 创建一个被忽略的哨兵标签，对应上游 Rust {@code Tag::ignored}。
   *
   * <p>哨兵标签的 {@code startByte}/{@code endByte} 被设为 {@link Integer#MAX_VALUE}，
   * 其它字段为零值。通过 {@link #isIgnored()} 检测。
   *
   * @param nameStartByte name 节点的起始字节偏移。
   * @param nameEndByte   name 节点的结束字节偏移。
   */
  static Tag ignored(int nameStartByte, int nameEndByte) {
    return new Tag(
        Integer.MAX_VALUE, Integer.MAX_VALUE,
        nameStartByte, nameEndByte,
        0, 0,
        TSPoint.create(0, 0), TSPoint.create(0, 0),
        0, 0,
        null, false, 0);
  }

  /**
   * 检查此标签是否是被忽略的哨兵标签。
   *
   * <p>对应上游 Rust {@code Tag::is_ignored}。当 {@code startByte == Integer.MAX_VALUE}
   * 时返回 {@code true}。
   */
  public boolean isIgnored() {
    return startByte == Integer.MAX_VALUE;
  }

  public int getStartByte() { return startByte; }
  public int getEndByte() { return endByte; }
  public int getNameStartByte() { return nameStartByte; }
  public int getNameEndByte() { return nameEndByte; }
  public int getLineStartByte() { return lineStartByte; }
  public int getLineEndByte() { return lineEndByte; }
  public TSPoint getStartPoint() { return startPoint; }
  public TSPoint getEndPoint() { return endPoint; }
  public int getUtf16StartColumn() { return utf16StartColumn; }
  public int getUtf16EndColumn() { return utf16EndColumn; }
  /** 返回文档字符串，如果没有则返回 {@code null}。 */
  public String getDocs() { return docs; }
  public boolean isDefinition() { return isDefinition; }
  public int getSyntaxTypeId() { return syntaxTypeId; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Tag)) return false;
    Tag tag = (Tag) o;
    return startByte == tag.startByte && endByte == tag.endByte
        && nameStartByte == tag.nameStartByte && nameEndByte == tag.nameEndByte
        && isDefinition == tag.isDefinition && syntaxTypeId == tag.syntaxTypeId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(startByte, endByte, nameStartByte, nameEndByte,
        isDefinition, syntaxTypeId);
  }

  @Override
  public String toString() {
    return "Tag{name=[" + nameStartByte + "," + nameEndByte + ")"
        + ", range=[" + startByte + "," + endByte + ")"
        + ", isDefinition=" + isDefinition
        + ", syntaxTypeId=" + syntaxTypeId
        + ", docs=" + (docs != null ? "(" + docs.length() + " chars)" : "null")
        + "}";
  }
}
