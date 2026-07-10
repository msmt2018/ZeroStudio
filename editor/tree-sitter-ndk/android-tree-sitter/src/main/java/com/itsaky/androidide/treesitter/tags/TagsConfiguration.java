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

import com.itsaky.androidide.treesitter.TSLanguage;
import com.itsaky.androidide.treesitter.TSQuery;
import com.itsaky.androidide.treesitter.TSQueryPredicateStep;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 标签提取配置。
 *
 * <p>此类是 tree-sitter 0.27 {@code TagsConfiguration} 的 Java 等价实现。它封装了
 * 用于标签提取的 {@link TSQuery}（由 {@code tags_query} 和 {@code locals_query} 拼接而成），
 * 并解析了每个 pattern 的谓词信息。
 *
 * <p>配置构造后是**不可变的**，可以安全地在多个线程间共享。但底层的 {@link TSQuery}
 * 是 native 对象，使用完毕后必须调用 {@link #close()} 释放。
 *
 * <p><strong>capture 名称约定：</strong>
 * <ul>
 *   <li>{@code @name} — 标签的名称节点（必需）。</li>
 *   <li>{@code @doc} — 标签的文档注释节点。</li>
 *   <li>{@code @ignore} — 标记应忽略的标签。</li>
 *   <li>{@code @local.scope} — 局部作用域。</li>
 *   <li>{@code @local.definition} — 局部变量定义。</li>
 *   <li>{@code @definition.<kind>} — 定义标签，kind 为语法类型（如 function、class）。</li>
 *   <li>{@code @reference.<kind>} — 引用标签。</li>
 * </ul>
 *
 * <p><strong>谓词约定：</strong>
 * <ul>
 *   <li>{@code (#is-not? local)} — 属性谓词，标记 pattern 的 name 必须是非局部变量（跳过局部变量定义）。</li>
 *   <li>{@code #set! local.scope-inherits "false"} — 属性设置，局部作用域不继承父作用域。</li>
 *   <li>{@code (select-adjacent! @doc @name)} — 通用谓词，只保留与指定 capture 行相邻的文档注释。第一个参数必须是 {@code @doc}。</li>
 *   <li>{@code (strip! @doc "regex")} — 通用谓词，从文档注释中去除匹配正则的内容。第一个参数必须是 {@code @doc}。</li>
 * </ul>
 */
public final class TagsConfiguration implements AutoCloseable {

  private static final int NO_CAPTURE = -1;

  private final TSLanguage language;
  private final TSQuery query;
  private final int tagsPatternIndex;
  private final int nameCaptureIndex;
  private final int docCaptureIndex;
  private final int ignoreCaptureIndex;
  private final int localScopeCaptureIndex;
  private final int localDefinitionCaptureIndex;
  private final List<String> syntaxTypeNames;
  private final Map<Integer, NamedCapture> captureMap;
  private final PatternInfo[] patternInfos;

  private TagsConfiguration(TSLanguage language, TSQuery query, int tagsPatternIndex,
      int nameCaptureIndex, int docCaptureIndex, int ignoreCaptureIndex,
      int localScopeCaptureIndex, int localDefinitionCaptureIndex,
      List<String> syntaxTypeNames, Map<Integer, NamedCapture> captureMap,
      PatternInfo[] patternInfos) {
    this.language = language;
    this.query = query;
    this.tagsPatternIndex = tagsPatternIndex;
    this.nameCaptureIndex = nameCaptureIndex;
    this.docCaptureIndex = docCaptureIndex;
    this.ignoreCaptureIndex = ignoreCaptureIndex;
    this.localScopeCaptureIndex = localScopeCaptureIndex;
    this.localDefinitionCaptureIndex = localDefinitionCaptureIndex;
    this.syntaxTypeNames = syntaxTypeNames;
    this.captureMap = captureMap;
    this.patternInfos = patternInfos;
  }

  /**
   * 创建标签提取配置。
   *
   * @param language    目标语言。
   * @param tagsQuery   tags query 源码（UTF-8）。
   * @param localsQuery locals query 源码（UTF-8），可为空字符串。
   * @return 配置实例。
   * @throws TagsException 如果 query 或正则表达式无效。
   */
  public static TagsConfiguration create(TSLanguage language, String tagsQuery,
      String localsQuery) throws TagsException {
    Objects.requireNonNull(language, "language cannot be null");
    Objects.requireNonNull(tagsQuery, "tagsQuery cannot be null");
    if (localsQuery == null) {
      localsQuery = "";
    }

    // 拼接 locals_query + tags_query
    String querySource = localsQuery + tagsQuery;
    // C2 修复：使用 UTF-8 字节长度（与上游 Rust locals_query.len() 一致），
    // 而非 Java String.length() 返回的 UTF-16 char 数。
    int tagsQueryOffset = localsQuery.getBytes(StandardCharsets.UTF_8).length;

    TSQuery query = TSQuery.create(language, querySource);
    if (query == null || !query.canAccess()) {
      throw new TagsException(TagsError.INVALID_QUERY,
          "Failed to create query");
    }

    // 划分 pattern 归属：pattern_index < tagsPatternIndex → locals 段
    int patternCount = query.getPatternCount();
    int tagsPatternIndex = 0;
    for (int i = 0; i < patternCount; i++) {
      int startByte = query.getStartByteForPattern(i);
      if (startByte < tagsQueryOffset) {
        tagsPatternIndex = i + 1;
      }
    }

    // 分类 capture
    int nameCaptureIndex = NO_CAPTURE;
    int docCaptureIndex = NO_CAPTURE;
    int ignoreCaptureIndex = NO_CAPTURE;
    int localScopeCaptureIndex = NO_CAPTURE;
    int localDefinitionCaptureIndex = NO_CAPTURE;
    List<String> syntaxTypeNames = new ArrayList<>();
    Map<String, Integer> syntaxTypeMap = new HashMap<>();
    Map<Integer, NamedCapture> captureMap = new HashMap<>();

    int captureCount = query.getCaptureCount();
    for (int i = 0; i < captureCount; i++) {
      String name = query.getCaptureNameForId(i);
      if (name == null) continue;
      switch (name) {
        case "name":
          nameCaptureIndex = i;
          break;
        case "ignore":
          ignoreCaptureIndex = i;
          break;
        case "doc":
          docCaptureIndex = i;
          break;
        case "local.scope":
          localScopeCaptureIndex = i;
          break;
        case "local.definition":
          localDefinitionCaptureIndex = i;
          break;
        default:
          if (name.startsWith("definition.")) {
            String kind = name.substring("definition.".length());
            Integer id = syntaxTypeMap.get(kind);
            if (id == null) {
              id = syntaxTypeNames.size();
              syntaxTypeNames.add(kind);
              syntaxTypeMap.put(kind, id);
            }
            captureMap.put(i, new NamedCapture(id, true));
          } else if (name.startsWith("reference.")) {
            String kind = name.substring("reference.".length());
            Integer id = syntaxTypeMap.get(kind);
            if (id == null) {
              id = syntaxTypeNames.size();
              syntaxTypeNames.add(kind);
              syntaxTypeMap.put(kind, id);
            }
            captureMap.put(i, new NamedCapture(id, false));
          } else if (name.isEmpty() || name.equals("local.reference")) {
            // 忽略
          } else {
            query.close();
            throw new TagsException(TagsError.INVALID_CAPTURE,
                "Invalid capture name: " + name);
          }
          break;
      }
    }

    // 解析每个 pattern 的谓词信息
    PatternInfo[] patternInfos = new PatternInfo[patternCount];
    for (int i = 0; i < patternCount; i++) {
      patternInfos[i] = parsePatternInfo(query, i, docCaptureIndex);
    }

    return new TagsConfiguration(language, query, tagsPatternIndex,
        nameCaptureIndex, docCaptureIndex, ignoreCaptureIndex,
        localScopeCaptureIndex, localDefinitionCaptureIndex,
        syntaxTypeNames, captureMap, patternInfos);
  }

  /**
   * 解析单个 pattern 的谓词信息。
   *
   * <p>tree-sitter 的 C API 通过 {@code ts_query_predicates_for_pattern} 返回所有谓词的
   * 原始步骤（TSQueryPredicateStep 数组），每个谓词以 Done 步骤结尾。第一个 String 步骤
   * 是操作符名称（如 {@code "is-not?"}、{@code "set!"}、{@code "select-adjacent!"}、
   * {@code "strip!"}）。
   *
   * <p>与上游 Rust {@code tags.rs} 的 {@code TagsConfiguration::new} 一致：
   * <ul>
   *   <li>{@code (#is-not? local)} — 属性谓词，设置 {@code nameMustBeNonLocal}。</li>
   *   <li>{@code #set! local.scope-inherits "false"} — 属性设置，设置 {@code localScopeInherits}。</li>
   *   <li>{@code (select-adjacent! @doc @X)} — 通用谓词，第一个参数必须是 {@code @doc} capture，
   *       第二个参数（capture）设为 {@code docsAdjacentCapture}。</li>
   *   <li>{@code (strip! @doc "regex")} — 通用谓词，第一个参数必须是 {@code @doc} capture，
   *       第二个参数（string）编译为正则设为 {@code docStripRegex}。</li>
   * </ul>
   */
  private static PatternInfo parsePatternInfo(TSQuery query, int patternIndex,
      int docCaptureIndex) throws TagsException {
    PatternInfo info = new PatternInfo();
    TSQueryPredicateStep[] steps = query.getPredicatesForPattern(patternIndex);

    int i = 0;
    while (i < steps.length) {
      // 收集一个完整谓词的步骤（直到 Done）
      List<TSQueryPredicateStep> predicateSteps = new ArrayList<>();
      while (i < steps.length && steps[i].getType() != TSQueryPredicateStep.Type.Done) {
        predicateSteps.add(steps[i]);
        i++;
      }
      i++; // 跳过 Done

      if (predicateSteps.isEmpty()) continue;

      // 第一个步骤必须是 String（操作符名称）
      TSQueryPredicateStep opStep = predicateSteps.get(0);
      if (opStep.getType() != TSQueryPredicateStep.Type.String) continue;
      String operator = query.getStringValueForId(opStep.getValueId());
      if (operator == null) continue;

      // C8 修复：检查第一个参数是否是 @doc capture（与上游 Rust 的
      // predicate.args.first() == Some(&QueryPredicateArg::Capture(doc_capture_index)) 一致）。
      // select-adjacent! 和 strip! 要求第一个参数是 @doc。
      boolean firstArgIsDoc = false;
      if (predicateSteps.size() >= 2 && docCaptureIndex != NO_CAPTURE) {
        TSQueryPredicateStep firstArg = predicateSteps.get(1);
        if (firstArg.getType() == TSQueryPredicateStep.Type.Capture
            && firstArg.getValueId() == docCaptureIndex) {
          firstArgIsDoc = true;
        }
      }

      // 收集 string/capture 参数（用于 is-not? 和 set!）
      List<String> stringArgs = new ArrayList<>();
      List<Integer> captureArgs = new ArrayList<>();
      for (int j = 1; j < predicateSteps.size(); j++) {
        TSQueryPredicateStep step = predicateSteps.get(j);
        if (step.getType() == TSQueryPredicateStep.Type.String) {
          String val = query.getStringValueForId(step.getValueId());
          if (val != null) stringArgs.add(val);
        } else if (step.getType() == TSQueryPredicateStep.Type.Capture) {
          captureArgs.add(step.getValueId());
        }
      }

      switch (operator) {
        case "is-not?":
          // C1 修复：与上游 Rust 一致，使用属性谓词 (#is-not? local)，
          // 而非之前错误的通用谓词 (#not-local?)。
          // 第一个 string 参数是属性 key，检查是否为 "local"。
          if (!stringArgs.isEmpty() && "local".equals(stringArgs.get(0))) {
            info.nameMustBeNonLocal = true;
          }
          break;
        case "set!":
          // #set! key value 或 #set! key
          if (!stringArgs.isEmpty()) {
            String key = stringArgs.get(0);
            if ("local.scope-inherits".equals(key)) {
              if (stringArgs.size() >= 2) {
                info.localScopeInherits = !"false".equals(stringArgs.get(1));
              }
            }
          }
          break;
        case "select-adjacent!":
          // C8+C9 修复：与上游 Rust 一致，要求第一个参数是 @doc capture，
          // 使用第二个参数（predicateSteps.get(2)，即 args.get(1)）作为 docsAdjacentCapture。
          if (firstArgIsDoc && predicateSteps.size() >= 3) {
            TSQueryPredicateStep secondArg = predicateSteps.get(2);
            if (secondArg.getType() == TSQueryPredicateStep.Type.Capture) {
              info.docsAdjacentCapture = secondArg.getValueId();
            }
          }
          break;
        case "strip!":
          // C8 修复：与上游 Rust 一致，要求第一个参数是 @doc capture，
          // 使用第二个参数（predicateSteps.get(2)，即 args.get(1)）作为正则字符串。
          if (firstArgIsDoc && predicateSteps.size() >= 3) {
            TSQueryPredicateStep secondArg = predicateSteps.get(2);
            if (secondArg.getType() == TSQueryPredicateStep.Type.String) {
              String pattern = query.getStringValueForId(secondArg.getValueId());
              if (pattern != null) {
                try {
                  info.docStripRegex = Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                  throw new TagsException(TagsError.INVALID_REGEX,
                      "Invalid strip regex: " + pattern, e);
                }
              }
            }
          }
          break;
        default:
          // 其它谓词（如 #eq?、#match?）由 tree-sitter 内部处理，这里不解析
          break;
      }
    }

    return info;
  }

  public TSLanguage getLanguage() { return language; }
  public TSQuery getQuery() { return query; }
  public int getTagsPatternIndex() { return tagsPatternIndex; }
  public int getNameCaptureIndex() { return nameCaptureIndex; }
  public int getDocCaptureIndex() { return docCaptureIndex; }
  public int getIgnoreCaptureIndex() { return ignoreCaptureIndex; }
  public int getLocalScopeCaptureIndex() { return localScopeCaptureIndex; }
  public int getLocalDefinitionCaptureIndex() { return localDefinitionCaptureIndex; }

  /** 获取语法类型的名称（如 "function"、"class"）。 */
  public String getSyntaxTypeName(int id) {
    if (id < 0 || id >= syntaxTypeNames.size()) return null;
    return syntaxTypeNames.get(id);
  }

  /** 获取语法类型总数。 */
  public int getSyntaxTypeCount() { return syntaxTypeNames.size(); }

  /** 查询指定 capture 索引的命名信息（syntaxTypeId + isDefinition），可能返回 {@code null}。 */
  NamedCapture getNamedCapture(int captureIndex) {
    return captureMap.get(captureIndex);
  }

  /** 获取指定 pattern 的信息。 */
  PatternInfo getPatternInfo(int patternIndex) {
    return patternInfos[patternIndex];
  }

  boolean hasDocCapture() { return docCaptureIndex != NO_CAPTURE; }

  @Override
  public void close() {
    if (query != null) {
      query.close();
    }
  }

  // ---- 内部数据类 ----

  /** capture 的命名信息。 */
  static final class NamedCapture {
    final int syntaxTypeId;
    final boolean isDefinition;

    NamedCapture(int syntaxTypeId, boolean isDefinition) {
      this.syntaxTypeId = syntaxTypeId;
      this.isDefinition = isDefinition;
    }
  }

  /** pattern 的谓词信息。 */
  static final class PatternInfo {
    /** docs 相邻 capture 的索引，{@code null} 表示无。 */
    Integer docsAdjacentCapture = null;
    /** 局部作用域是否继承父作用域，默认 true。 */
    boolean localScopeInherits = true;
    /** name 是否必须是非局部变量。 */
    boolean nameMustBeNonLocal = false;
    /** 文档注释的 strip 正则，{@code null} 表示无。 */
    Pattern docStripRegex = null;
  }

  /** 标签配置异常。 */
  public static class TagsException extends Exception {
    private final TagsError error;

    public TagsException(TagsError error, String message) {
      super(message);
      this.error = error;
    }

    public TagsException(TagsError error, String message, Throwable cause) {
      super(message, cause);
      this.error = error;
    }

    public TagsError getError() { return error; }
  }
}
