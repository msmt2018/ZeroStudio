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
import java.util.List;
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

      List<Tag> tags = iterateMatches(config, sourceCode, tree);
      tags.sort(Comparator
          .comparingInt((Tag t) -> t.getNameStartByte())
          .thenComparingInt(Tag::getNameEndByte));

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
    List<Tag> tagQueue = new ArrayList<>();
    Deque<LocalScope> scopeStack = new ArrayDeque<>();
    // 初始全局 scope
    scopeStack.push(new LocalScope(true, 0, source.length, new ArrayList<>()));

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
      TSNode nameNode = null;
      TSNode tagNode = null;
      TSNode ignoreNode = null;
      List<TSNode> docNodes = new ArrayList<>();
      TSNode docsAdjacentNode = null;
      TagsConfiguration.NamedCapture namedCapture = null;
      boolean isIgnored = false;

      TagsConfiguration.PatternInfo patternInfo = config.getPatternInfo(patternIndex);

      for (TSQueryCapture capture : captures) {
        int captureIndex = capture.getIndex();
        TSNode node = capture.getNode();

        if (captureIndex == nameCaptureIndex) {
          nameNode = node;
        } else if (captureIndex == ignoreCaptureIndex) {
          ignoreNode = node;
          isIgnored = true;
        } else if (captureIndex == docCaptureIndex) {
          docNodes.add(node);
        } else {
          TagsConfiguration.NamedCapture nc = config.getNamedCapture(captureIndex);
          if (nc != null) {
            namedCapture = nc;
            tagNode = node;
          }
        }

        // 检查 docs_adjacent_capture
        if (patternInfo.docsAdjacentCapture != null
            && captureIndex == patternInfo.docsAdjacentCapture) {
          docsAdjacentNode = node;
        }
      }

      if (nameNode == null) {
        continue;
      }

      int nameStart = nameNode.getStartByte();
      int nameEnd = nameNode.getEndByte();

      if (isIgnored) {
        // 构造忽略标签
        tagQueue.add(new Tag(
            nameStart, nameEnd, nameStart, nameEnd,
            nameStart, nameEnd, nameNode.getStartPoint(), nameNode.getEndPoint(),
            0, 0, null, false, -1));
        continue;
      }

      if (tagNode == null || namedCapture == null) {
        continue;
      }

      // 检查 name 节点是否有错误
      if (nameNode.hasErrors()) {
        continue;
      }

      // 检查 name_must_be_non_local
      if (patternInfo.nameMustBeNonLocal) {
        String nameText = sliceString(source, nameStart, nameEnd);
        if (isLocalVariable(scopeStack, nameText)) {
          continue;
        }
      }

      // 处理 docs
      String docs = processDocs(config, patternInfo, docNodes, docsAdjacentNode, source);

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

      tagQueue.add(new Tag(
          startByte, endByte,
          nameStart, nameEnd,
          lineStartByte, lineEndByte,
          nameNode.getStartPoint(), nameNode.getEndPoint(),
          utf16Columns[0], utf16Columns[1],
          docs, namedCapture.isDefinition, namedCapture.syntaxTypeId));
    }

    return tagQueue;
  }

  private void handleLocalsMatch(TagsConfiguration config, Deque<LocalScope> scopeStack,
      int patternIndex, TSQueryCapture[] captures, int localScopeCaptureIndex,
      int localDefinitionCaptureIndex, byte[] source) {
    TagsConfiguration.PatternInfo patternInfo = config.getPatternInfo(patternIndex);
    for (TSQueryCapture capture : captures) {
      int captureIndex = capture.getIndex();
      TSNode node = capture.getNode();

      if (captureIndex == localScopeCaptureIndex) {
        // 弹出已结束的 scope
        while (scopeStack.size() > 1 && scopeStack.peek().endByte < node.getStartByte()) {
          scopeStack.pop();
        }
        // push 新 scope，读取 local.scope-inherits 设置
        scopeStack.push(new LocalScope(
            patternInfo.localScopeInherits,
            node.getStartByte(), node.getEndByte(), new ArrayList<>()));
      } else if (captureIndex == localDefinitionCaptureIndex) {
        String name = sliceString(source, node.getStartByte(), node.getEndByte());
        if (!scopeStack.isEmpty()) {
          scopeStack.peek().localDefs.add(new LocalDef(name, node.getEndByte()));
        }
      }
    }
  }

  /** 检查 name 是否是局部变量（在 scope 栈中查找同名定义）。 */
  private boolean isLocalVariable(Deque<LocalScope> scopeStack, String name) {
    for (LocalScope scope : scopeStack) {
      for (LocalDef def : scope.localDefs) {
        if (def.name.equals(name)) {
          return true;
        }
      }
      if (!scope.inherits) {
        break;
      }
    }
    return false;
  }

  /** 处理文档注释：过滤相邻 + strip 正则。 */
  private String processDocs(TagsConfiguration config,
      TagsConfiguration.PatternInfo patternInfo,
      List<TSNode> docNodes, TSNode docsAdjacentNode, byte[] source) {
    if (docNodes.isEmpty()) {
      return null;
    }

    // 如果有 docs_adjacent_capture，只保留与该节点相邻的 doc
    List<TSNode> filteredDocs = new ArrayList<>(docNodes);
    if (docsAdjacentNode != null && filteredDocs.size() > 1) {
      int adjEnd = docsAdjacentNode.getEndByte();
      // 从末尾向前回溯，只保留与 adjacent 节点行相邻的 doc
      List<TSNode> adjacent = new ArrayList<>();
      int prevLineEnd = adjEnd;
      for (int i = filteredDocs.size() - 1; i >= 0; i--) {
        TSNode doc = filteredDocs.get(i);
        // 简化：检查 doc 是否在 adjacent 之前且行相邻
        // 完整实现需要检查行号，这里用字节范围近似
        if (doc.getEndByte() <= prevLineEnd) {
          adjacent.add(0, doc);
          prevLineEnd = doc.getStartByte();
        } else {
          break;
        }
      }
      if (!adjacent.isEmpty()) {
        filteredDocs = adjacent;
      }
    }

    // 拼接 docs 并应用 strip regex
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < filteredDocs.size(); i++) {
      if (i > 0) {
        sb.append('\n');
      }
      TSNode docNode = filteredDocs.get(i);
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
    final int valueEndByte;

    LocalDef(String name, int valueEndByte) {
      this.name = name;
      this.valueEndByte = valueEndByte;
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
