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

import com.itsaky.androidide.treesitter.TSNode;
import com.itsaky.androidide.treesitter.TSParser;
import com.itsaky.androidide.treesitter.TSPoint;
import com.itsaky.androidide.treesitter.TSQueryCapture;
import com.itsaky.androidide.treesitter.TSQueryCursor;
import com.itsaky.androidide.treesitter.TSQueryMatch;
import com.itsaky.androidide.treesitter.TSTree;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * 标签提取上下文。
 *
 * <p>此类是 tree-sitter 0.27 {@code TagsContext} 的 Java 等价实现。它封装了解析和标签提取的
 * 完整流程：使用 {@link TSParser} 解析源代码，用 {@link TSQueryCursor} 遍历 query 匹配，
 * 根据 {@link TagsConfiguration} 中的 capture 分类和谓词信息，提取出 {@link Tag} 列表。
 *
 * <p><strong>非线程安全。</strong>每个线程应使用独立的 {@code TagsContext} 实例。
 *
 * <p>使用示例：
 * <pre>{@code
 * TagsConfiguration config = TagsConfiguration.create(language, tagsQuery, localsQuery);
 * try (TagsContext context = new TagsContext()) {
 *   TagsResult result = context.generateTags(config, sourceCode.getBytes(StandardCharsets.UTF_8));
 *   for (Tag tag : result.getTags()) {
 *     System.out.println(tag);
 *   }
 * }
 * }</pre>
 */
public final class TagsContext implements AutoCloseable {

  /** 行范围计算的最大长度限制（与上游 Rust 实现一致）。 */
  private static final int MAX_LINE_LEN = 180;
  /** 取消检查间隔（每处理多少个 match 检查一次取消标志）。 */
  private static final int CANCELLATION_CHECK_INTERVAL = 100;

  private final TSParser parser = TSParser.create();
  private TSQueryCursor cursor;

  public TagsContext() {
  }

  /**
   * 生成标签。
   *
   * @param config     标签配置。
   * @param sourceCode 源代码（UTF-8 字节）。
   * @return 标签结果。
   * @throws TagsConfiguration.TagsException 如果解析过程中发生错误。
   */
  public TagsResult generateTags(TagsConfiguration config, byte[] sourceCode)
      throws TagsConfiguration.TagsException {
    if (sourceCode == null) {
      throw new IllegalArgumentException("sourceCode cannot be null");
    }

    parser.setLanguage(config.getLanguage());
    parser.reset();

    TSTree tree = parser.parseBytes(sourceCode);

    if (tree == null) {
      return new TagsResult(new ArrayList<>(), true);
    }

    boolean hasError = tree.getRootNode().hasErrors();

    try {
      cursor = TSQueryCursor.create();
      cursor.exec(config.getQuery(), tree.getRootNode());

      // iterateMatches 内部已完成去重、过滤和排序
      List<Tag> tags = iterateMatches(config, sourceCode, tree);

      return new TagsResult(tags, hasError);
    } finally {
      if (cursor != null) {
        cursor.close();
        cursor = null;
      }
      tree.close();
    }
  }

  private List<Tag> iterateMatches(TagsConfiguration config, byte[] source, TSTree tree)
      throws TagsConfiguration.TagsException {
    // C5 修复：收集所有 (Tag, patternIndex) 对，用于后续按 (nameEnd, nameStart) 去重
    List<Tag> tagList = new ArrayList<>();
    List<Integer> patternIndices = new ArrayList<>();
    Map<Long, Integer> dedupMap = new HashMap<>();

    Deque<LocalScope> scopeStack = new ArrayDeque<>();
    // 初始全局 scope（与上游 Rust 一致：inherits=false, range=0..source.len()）
    scopeStack.push(new LocalScope(false, 0, source.length, new ArrayList<>()));

    int tagsPatternIndex = config.getTagsPatternIndex();
    int nameCaptureIndex = config.getNameCaptureIndex();
    int docCaptureIndex = config.getDocCaptureIndex();
    int ignoreCaptureIndex = config.getIgnoreCaptureIndex();
    int localScopeCaptureIndex = config.getLocalScopeCaptureIndex();
    int localDefinitionCaptureIndex = config.getLocalDefinitionCaptureIndex();

    int iterCount = 0;

    TSQueryMatch match;
    while ((match = cursor.nextMatch()) != null) {
      iterCount++;
      if (iterCount % CANCELLATION_CHECK_INTERVAL == 0) {
        // 取消检查点（当前实现不支持迭代阶段取消，预留扩展）
      }

      int patternIndex = match.getPatternIndex();
      TSQueryCapture[] captures = match.getCaptures();
      if (captures == null) continue;

      if (patternIndex < tagsPatternIndex) {
        // locals 段：处理 local.scope 和 local.definition
        handleLocalsMatch(config, scopeStack, patternIndex, captures,
            localScopeCaptureIndex, localDefinitionCaptureIndex, source);
        continue;
      }

      // tags 段：提取 name/doc/ignore/syntax_type
      // 与上游 Rust 一致的 capture 处理结构：独立的 if 语句（非 if-else 链），
      // 允许单个 capture 同时匹配多个条件。
      TSNode nameNode = null;
      TSNode tagNode = null;
      List<TSNode> docNodes = new ArrayList<>();
      TSNode docsAdjacentNode = null;
      TagsConfiguration.NamedCapture namedCapture = null;
      boolean isIgnored = false;

      TagsConfiguration.PatternInfo patternInfo = config.getPatternInfo(patternIndex);

      for (TSQueryCapture capture : captures) {
        int captureIndex = capture.getIndex();
        TSNode node = capture.getNode();

        // C7 修复：@ignore 设置 isIgnored 并设置 nameNode（作为 fallback）
        // 与上游 Rust 一致：is_ignored = true; name_node = Some(capture.node)
        if (captureIndex == ignoreCaptureIndex) {
          isIgnored = true;
          nameNode = node;
        }

        // docs_adjacent_capture 检查（独立 if，与上游 Rust 一致）
        if (patternInfo.docsAdjacentCapture != null
            && captureIndex == patternInfo.docsAdjacentCapture) {
          docsAdjacentNode = node;
        }

        // C3 修复：@name 覆盖 nameNode（在 @ignore 之后设置，与上游 Rust 一致）
        if (captureIndex == nameCaptureIndex) {
          nameNode = node;
        } else if (captureIndex == docCaptureIndex) {
          docNodes.add(node);
        }

        // named capture (definition.*/reference.*，独立 if，与上游 Rust 一致)
        TagsConfiguration.NamedCapture nc = config.getNamedCapture(captureIndex);
        if (nc != null) {
          namedCapture = nc;
          tagNode = node;
        }
      }

      if (nameNode == null) {
        continue;
      }

      int nameStart = nameNode.getStartByte();
      int nameEnd = nameNode.getEndByte();

      Tag tag;
      // C3 修复：与上游 Rust 一致，先检查 tagNode，再检查 is_ignored
      // 上游：if let Some(tag_node) = tag_node { ... } else if is_ignored { ... } else { continue }
      if (tagNode != null && namedCapture != null) {
        // 检查 name 节点是否有错误
        if (nameNode.hasErrors()) {
          continue;
        }

        // C6 修复：检查 name_must_be_non_local，使用 scope 范围验证
        if (patternInfo.nameMustBeNonLocal) {
          String nameText = sliceString(source, nameStart, nameEnd);
          if (isLocalVariable(scopeStack, nameText, nameStart, nameEnd)) {
            continue;
          }
        }

        // 处理 docs（C10 修复：使用行号比较）
        String docs = processDocs(patternInfo, docNodes, docsAdjacentNode, source);

        // 计算标签范围
        int tagStart = tagNode.getStartByte();
        int tagEnd = tagNode.getEndByte();
        int startByte = Math.min(tagStart, nameStart);
        int endByte = Math.max(tagEnd, nameEnd);

        // 计算 line range
        int[] lineRange = computeLineRange(source, nameStart, nameEnd);
        int lineStartByte = lineRange[0];
        int lineEndByte = lineRange[1];

        // 计算 UTF-16 列
        int[] utf16Columns = computeUtf16Columns(source, nameStart, nameEnd,
            nameNode.getStartPoint());

        tag = new Tag(
            startByte, endByte,
            nameStart, nameEnd,
            lineStartByte, lineEndByte,
            nameNode.getStartPoint(), nameNode.getEndPoint(),
            utf16Columns[0], utf16Columns[1],
            docs, namedCapture.isDefinition, namedCapture.syntaxTypeId);
      } else if (isIgnored) {
        // C4 修复：使用 Tag.ignored() 哨兵标签（与上游 Rust Tag::ignored 一致）
        tag = Tag.ignored(nameStart, nameEnd);
      } else {
        continue;
      }

      // C5 修复：标签去重，按 (nameEnd, nameStart) 去重，保留 patternIndex 最小的
      // 与上游 Rust 的 binary_search_by_key + pattern_index 比较一致
      long dedupKey = ((long) nameEnd << 32) | (nameStart & 0xFFFFFFFFL);
      Integer existingIdx = dedupMap.get(dedupKey);
      if (existingIdx == null) {
        dedupMap.put(dedupKey, tagList.size());
        tagList.add(tag);
        patternIndices.add(patternIndex);
      } else if (patternIndices.get(existingIdx) > patternIndex) {
        tagList.set(existingIdx, tag);
        patternIndices.set(existingIdx, patternIndex);
      }
    }

    // C4 修复：过滤 ignored 标签（与上游 Rust 的 if tag.is_ignored() { continue; } 一致）
    List<Tag> result = new ArrayList<>();
    for (Tag t : tagList) {
      if (!t.isIgnored()) {
        result.add(t);
      }
    }
    result.sort(Comparator
        .comparingInt(Tag::getNameStartByte)
        .thenComparingInt(Tag::getNameEndByte));

    return result;
  }

  private void handleLocalsMatch(TagsConfiguration config, Deque<LocalScope> scopeStack,
      int patternIndex, TSQueryCapture[] captures, int localScopeCaptureIndex,
      int localDefinitionCaptureIndex, byte[] source) {
    TagsConfiguration.PatternInfo patternInfo = config.getPatternInfo(patternIndex);
    for (TSQueryCapture capture : captures) {
      int captureIndex = capture.getIndex();
      TSNode node = capture.getNode();

      if (captureIndex == localScopeCaptureIndex) {
        // 与上游 Rust 一致：只 push，不 pop。scope 的 range 在 isLocalVariable
        // 中通过范围包含检查来正确处理，无需手动弹出已结束的 scope。
        scopeStack.push(new LocalScope(
            patternInfo.localScopeInherits,
            node.getStartByte(), node.getEndByte(), new ArrayList<>()));
      } else if (captureIndex == localDefinitionCaptureIndex) {
        // 与上游 Rust 一致：找到包含此定义范围的最内层 scope（iter().rev().find）
        // Deque 迭代从 head（innermost）到 tail（outermost），与 Rust iter().rev() 一致。
        int defStart = node.getStartByte();
        int defEnd = node.getEndByte();
        for (LocalScope scope : scopeStack) {
          if (scope.startByte <= defStart && scope.endByte >= defEnd) {
            String name = sliceString(source, defStart, defEnd);
            scope.localDefs.add(new LocalDef(name));
            break;
          }
        }
      }
    }
  }

  /**
   * 检查 name 是否是局部变量（在 scope 栈中查找同名定义）。
   *
   * <p>C6 修复：与上游 Rust 一致，只检查范围包含 name 的 scope，
   * 且非继承的 scope 会中断向父 scope 的查找。
   * Deque 迭代从 head（innermost）到 tail（outermost），与 Rust iter().rev() 一致。
   */
  private boolean isLocalVariable(Deque<LocalScope> scopeStack, String name,
      int nameStart, int nameEnd) {
    for (LocalScope scope : scopeStack) {
      if (scope.startByte <= nameStart && scope.endByte >= nameEnd) {
        for (LocalDef def : scope.localDefs) {
          if (def.name.equals(name)) {
            return true;
          }
        }
        if (!scope.inherits) {
          break;
        }
      }
    }
    return false;
  }

  /**
   * 处理文档注释：过滤相邻 + strip 正则。
   *
   * <p>C10 修复：与上游 Rust 一致，使用行号（row）比较来判断文档注释的相邻性，
   * 而非字节偏移比较。上游算法：从 doc_nodes 末尾向前回溯，如果 doc 的 end_row + 1 >= start_row
   * （即 doc 与相邻节点在同一行或紧邻的上一行），则包含该 doc 并继续回溯。
   */
  private String processDocs(TagsConfiguration.PatternInfo patternInfo,
      List<TSNode> docNodes, TSNode docsAdjacentNode, byte[] source) {
    if (docNodes.isEmpty()) {
      return null;
    }

    // C10 修复：与上游 Rust 一致，使用行号比较过滤相邻文档注释
    int docsStartIndex = 0;
    if (docsAdjacentNode != null) {
      docsStartIndex = docNodes.size();
      int startRow = docsAdjacentNode.getStartPoint().getRow();
      while (docsStartIndex > 0) {
        TSNode docNode = docNodes.get(docsStartIndex - 1);
        int prevDocEndRow = docNode.getEndPoint().getRow();
        if (prevDocEndRow + 1 >= startRow) {
          docsStartIndex--;
          startRow = docNode.getStartPoint().getRow();
        } else {
          break;
        }
      }
    }

    // 拼接 docs[docsStartIndex..] 并应用 strip regex
    StringBuilder sb = new StringBuilder();
    for (int i = docsStartIndex; i < docNodes.size(); i++) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      TSNode docNode = docNodes.get(i);
      String docText = sliceString(source, docNode.getStartByte(), docNode.getEndByte());
      if (patternInfo.docStripRegex != null) {
        Matcher m = patternInfo.docStripRegex.matcher(docText);
        docText = m.replaceAll("");
      }
      sb.append(docText);
    }

    String result = sb.toString();
    return result.isEmpty() ? null : result;
  }

  /** 计算行范围（trim 首尾空白，限制 MAX_LINE_LEN）。 */
  private int[] computeLineRange(byte[] source, int nameStart, int nameEnd) {
    // 找到 name 所在行的起始和结束
    int lineStart = nameStart;
    while (lineStart > 0 && source[lineStart - 1] != '\n') {
      lineStart--;
    }
    int lineEnd = nameEnd;
    while (lineEnd < source.length && source[lineEnd] != '\n') {
      lineEnd++;
    }

    // trim 首尾空白
    while (lineStart < lineEnd && isWhitespace(source[lineStart])) {
      lineStart++;
    }
    while (lineEnd > lineStart && isWhitespace(source[lineEnd - 1])) {
      lineEnd--;
    }

    // 限制最大长度
    if (lineEnd - lineStart > MAX_LINE_LEN) {
      lineEnd = lineStart + MAX_LINE_LEN;
    }

    return new int[]{lineStart, lineEnd};
  }

  /** 计算 UTF-16 列范围。 */
  private int[] computeUtf16Columns(byte[] source, int nameStart, int nameEnd, TSPoint startPoint) {
    // 找到行起始字节
    int lineStart = nameStart;
    while (lineStart > 0 && source[lineStart - 1] != '\n') {
      lineStart--;
    }

    // 将行首到 name 的字节转为 String，取 UTF-16 长度
    String prefix = new String(source, lineStart, nameStart - lineStart, StandardCharsets.UTF_8);
    int startCol = prefix.length();

    String name = new String(source, nameStart, nameEnd - nameStart, StandardCharsets.UTF_8);
    int endCol = startCol + name.length();

    return new int[]{startCol, endCol};
  }

  private static boolean isWhitespace(byte b) {
    return b == ' ' || b == '\t' || b == '\r' || b == '\n';
  }

  /** 从 source 字节中切片 UTF-8 字符串。 */
  private static String sliceString(byte[] source, int start, int end) {
    if (start < 0 || end > source.length || start > end) {
      return "";
    }
    return new String(source, start, end - start, StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    if (cursor != null) {
      cursor.close();
      cursor = null;
    }
    parser.close();
  }

  // ---- 内部数据类 ----

  private static final class LocalScope {
    final boolean inherits;
    final int startByte;
    final int endByte;
    final List<LocalDef> localDefs;

    LocalScope(boolean inherits, int startByte, int endByte, List<LocalDef> localDefs) {
      this.inherits = inherits;
      this.startByte = startByte;
      this.endByte = endByte;
      this.localDefs = localDefs;
    }
  }

  private static final class LocalDef {
    final String name;

    LocalDef(String name) {
      this.name = name;
    }
  }

  /** 标签提取结果。 */
  public static final class TagsResult {
    private final List<Tag> tags;
    private final boolean hasParseError;

    TagsResult(List<Tag> tags, boolean hasParseError) {
      this.tags = tags;
      this.hasParseError = hasParseError;
    }

    public List<Tag> getTags() { return tags; }
    public boolean hasParseError() { return hasParseError; }
  }
}
