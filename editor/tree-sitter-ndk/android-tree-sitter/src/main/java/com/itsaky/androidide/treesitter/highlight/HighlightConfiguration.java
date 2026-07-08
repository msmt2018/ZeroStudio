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

package com.itsaky.androidide.treesitter.highlight;

import com.itsaky.androidide.treesitter.TSLanguage;
import com.itsaky.androidide.treesitter.TSQuery;
import com.itsaky.androidide.treesitter.TSQueryPredicateStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 语法高亮配置。
 *
 * <p>此类是 tree-sitter 0.27 {@code HighlightConfiguration} 的 Java 等价实现。它封装了
 * 用于高亮的 {@link TSQuery}（由 {@code highlights_query}、{@code injection_query} 和
 * {@code locals_query} 拼接而成），并解析了每个 pattern 的谓词信息和 capture 索引。
 *
 * <p>配置构造后是**不可变的**，可以安全地在多个线程间共享。但底层的 {@link TSQuery}
 * 是 native 对象，使用完毕后必须调用 {@link #close()} 释放。
 *
 * <p><strong>capture 名称约定：</strong>
 * <ul>
 *   <li>{@code @injection.content} — 嵌入语言的内容节点。</li>
 *   <li>{@code @injection.language} — 嵌入语言的名称节点。</li>
 *   <li>{@code @local.scope} — 局部作用域。</li>
 *   <li>{@code @local.definition} — 局部变量定义。</li>
 *   <li>{@code @local.definition-value} — 局部变量的值。</li>
 *   <li>{@code @local.reference} — 局部变量引用。</li>
 *   <li>其它 capture 名（如 {@code function.method}、{@code keyword}）— 高亮 capture。</li>
 * </ul>
 *
 * <p><strong>谓词约定：</strong>
 * <ul>
 *   <li>{@code (#is-not? local)} — 标记 pattern 对局部变量禁用（属性谓词，与上游 Rust 一致）。</li>
 *   <li>{@code #set! injection.language "javascript"} — 指定嵌入语言名称。</li>
 *   <li>{@code #set! injection.combined} — 合并多个不连续的嵌入内容。</li>
 *   <li>{@code #set! injection.self} — 嵌入语言为自身。</li>
 *   <li>{@code #set! injection.parent} — 嵌入语言为父语言。</li>
 *   <li>{@code #set! injection.include-children} — 包含子节点。</li>
 *   <li>{@code #set! local.scope-inherits "false"} — 局部作用域不继承。</li>
 * </ul>
 */
public final class HighlightConfiguration implements AutoCloseable {

  private static final int NO_CAPTURE = -1;

  private final TSLanguage language;
  private final String languageName;
  private final TSQuery query;
  private final int highlightsPatternIndex;
  private final int localsPatternIndex;
  private final int[] highlightIndices;  // captureIndex -> highlight index 或 -1
  private final boolean[] nonLocalVariablePatterns;
  private final int injectionContentCaptureIndex;
  private final int injectionLanguageCaptureIndex;
  private final int localScopeCaptureIndex;
  private final int localDefCaptureIndex;
  private final int localDefValueCaptureIndex;
  private final int localRefCaptureIndex;
  private final boolean[] scopeInherits;
  private String[] configuredNames;

  private HighlightConfiguration(TSLanguage language, String languageName, TSQuery query,
      int highlightsPatternIndex, int localsPatternIndex, int[] highlightIndices,
      boolean[] nonLocalVariablePatterns, int injectionContentCaptureIndex,
      int injectionLanguageCaptureIndex, int localScopeCaptureIndex,
      int localDefCaptureIndex, int localDefValueCaptureIndex, int localRefCaptureIndex,
      boolean[] scopeInherits) {
    this.language = language;
    this.languageName = languageName;
    this.query = query;
    this.highlightsPatternIndex = highlightsPatternIndex;
    this.localsPatternIndex = localsPatternIndex;
    this.highlightIndices = highlightIndices;
    this.nonLocalVariablePatterns = nonLocalVariablePatterns;
    this.injectionContentCaptureIndex = injectionContentCaptureIndex;
    this.injectionLanguageCaptureIndex = injectionLanguageCaptureIndex;
    this.localScopeCaptureIndex = localScopeCaptureIndex;
    this.localDefCaptureIndex = localDefCaptureIndex;
    this.localDefValueCaptureIndex = localDefValueCaptureIndex;
    this.localRefCaptureIndex = localRefCaptureIndex;
    this.scopeInherits = scopeInherits;
  }

  /**
   * 创建高亮配置。
   *
   * @param language        目标语言。
   * @param languageName    语言名称。
   * @param highlightsQuery highlights query 源码。
   * @param injectionQuery  injection query 源码，可为空字符串。
   * @param localsQuery     locals query 源码，可为空字符串。
   * @return 配置实例。
   * @throws HighlightException 如果 query 无效。
   */
  public static HighlightConfiguration create(TSLanguage language, String languageName,
      String highlightsQuery, String injectionQuery, String localsQuery)
      throws HighlightException {
    Objects.requireNonNull(language, "language cannot be null");
    Objects.requireNonNull(languageName, "languageName cannot be null");
    Objects.requireNonNull(highlightsQuery, "highlightsQuery cannot be null");
    if (injectionQuery == null) injectionQuery = "";
    if (localsQuery == null) localsQuery = "";

    // 拼接 injection_query + locals_query + highlights_query
    String querySource = injectionQuery + localsQuery + highlightsQuery;
    int localsQueryOffset = injectionQuery.length();
    int highlightsQueryOffset = injectionQuery.length() + localsQuery.length();

    TSQuery query = TSQuery.create(language, querySource);
    if (query == null || !query.canAccess()) {
      throw new HighlightException("Failed to create query");
    }

    // 划分 pattern 归属
    int patternCount = query.getPatternCount();
    int injectionPatternCount = 0;
    int localsPatternCount = 0;
    for (int i = 0; i < patternCount; i++) {
      int startByte = query.getStartByteForPattern(i);
      if (startByte < localsQueryOffset) {
        injectionPatternCount = i + 1;
      } else if (startByte < highlightsQueryOffset) {
        localsPatternCount = i + 1 - injectionPatternCount;
      }
    }
    int localsPatternIndex = injectionPatternCount;
    int highlightsPatternIndex = injectionPatternCount + localsPatternCount;

    // 解析 capture 索引
    int injectionContentCaptureIndex = NO_CAPTURE;
    int injectionLanguageCaptureIndex = NO_CAPTURE;
    int localScopeCaptureIndex = NO_CAPTURE;
    int localDefCaptureIndex = NO_CAPTURE;
    int localDefValueCaptureIndex = NO_CAPTURE;
    int localRefCaptureIndex = NO_CAPTURE;

    int captureCount = query.getCaptureCount();
    for (int i = 0; i < captureCount; i++) {
      String name = query.getCaptureNameForId(i);
      if (name == null) continue;
      switch (name) {
        case "injection.content":
          injectionContentCaptureIndex = i;
          break;
        case "injection.language":
          injectionLanguageCaptureIndex = i;
          break;
        case "local.scope":
          localScopeCaptureIndex = i;
          break;
        case "local.definition":
          localDefCaptureIndex = i;
          break;
        case "local.definition-value":
          localDefValueCaptureIndex = i;
          break;
        case "local.reference":
          localRefCaptureIndex = i;
          break;
        default:
          break;
      }
    }

    // 解析 non_local_variable_patterns: 检查 #is-not? local 属性谓词
    // 与上游 Rust highlight.rs 一致，使用 property predicate (#is-not? local)
    // 而非通用谓词 (#not-local?)
    boolean[] nonLocalVariablePatterns = new boolean[patternCount];
    boolean[] scopeInheritsArr = new boolean[patternCount];
    for (int i = 0; i < patternCount; i++) {
      nonLocalVariablePatterns[i] = hasIsNotLocalPredicate(query, i);
      scopeInheritsArr[i] = getScopeInherits(query, i);
    }

    // highlightIndices 初始为 -1，需要调用 configure() 后才填充
    int[] highlightIndices = new int[captureCount];
    for (int i = 0; i < captureCount; i++) {
      highlightIndices[i] = -1;
    }

    return new HighlightConfiguration(language, languageName, query,
        highlightsPatternIndex, localsPatternIndex, highlightIndices,
        nonLocalVariablePatterns, injectionContentCaptureIndex,
        injectionLanguageCaptureIndex, localScopeCaptureIndex,
        localDefCaptureIndex, localDefValueCaptureIndex, localRefCaptureIndex,
        scopeInheritsArr);
  }

  /**
   * 检查 pattern 是否有 {@code (#is-not? local)} 属性谓词。
   *
   * <p>这与上游 Rust {@code highlight.rs} 的实现一致，使用 {@code property_predicates}
   * 检测 key 为 {@code "local"} 的否定属性谓词。由于 C API 的
   * {@code ts_query_predicates_for_pattern} 包含所有谓词（含 {@code #is?}/{@code #is-not?}），
   * 我们通过解析谓词步骤来检测。
   */
  private static boolean hasIsNotLocalPredicate(TSQuery query, int patternIndex) {
    TSQueryPredicateStep[] steps = query.getPredicatesForPattern(patternIndex);
    int i = 0;
    while (i < steps.length) {
      // 收集一个完整谓词
      List<TSQueryPredicateStep> predicateSteps = new ArrayList<>();
      while (i < steps.length && steps[i].getType() != TSQueryPredicateStep.Type.Done) {
        predicateSteps.add(steps[i]);
        i++;
      }
      i++; // 跳过 Done

      if (predicateSteps.isEmpty()) continue;
      TSQueryPredicateStep opStep = predicateSteps.get(0);
      if (opStep.getType() != TSQueryPredicateStep.Type.String) continue;
      String operator = query.getStringValueForId(opStep.getValueId());
      if ("is-not?".equals(operator) && predicateSteps.size() >= 2) {
        TSQueryPredicateStep argStep = predicateSteps.get(1);
        if (argStep.getType() == TSQueryPredicateStep.Type.String) {
          String argValue = query.getStringValueForId(argStep.getValueId());
          if ("local".equals(argValue)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * 解析 pattern 的 {@code (#set! local.scope-inherits "false")} 属性设置。
   *
   * <p>默认返回 {@code true}（继承）。如果设置了 {@code "false"} 则返回 {@code false}。
   * 与上游 Rust {@code highlight.rs} 的 {@code property_settings} 检测一致。
   */
  private static boolean getScopeInherits(TSQuery query, int patternIndex) {
    TSQueryPredicateStep[] steps = query.getPredicatesForPattern(patternIndex);
    int i = 0;
    while (i < steps.length) {
      List<TSQueryPredicateStep> predicateSteps = new ArrayList<>();
      while (i < steps.length && steps[i].getType() != TSQueryPredicateStep.Type.Done) {
        predicateSteps.add(steps[i]);
        i++;
      }
      i++; // 跳过 Done

      if (predicateSteps.isEmpty()) continue;
      TSQueryPredicateStep opStep = predicateSteps.get(0);
      if (opStep.getType() != TSQueryPredicateStep.Type.String) continue;
      String operator = query.getStringValueForId(opStep.getValueId());
      if ("set!".equals(operator) && predicateSteps.size() >= 2) {
        TSQueryPredicateStep keyStep = predicateSteps.get(1);
        if (keyStep.getType() == TSQueryPredicateStep.Type.String) {
          String keyValue = query.getStringValueForId(keyStep.getValueId());
          if ("local.scope-inherits".equals(keyValue)) {
            // Rust: scope.inherits = prop.value.is_none_or(|r| r == "true")
            if (predicateSteps.size() >= 3) {
              TSQueryPredicateStep valStep = predicateSteps.get(2);
              if (valStep.getType() == TSQueryPredicateStep.Type.String) {
                return "true".equals(query.getStringValueForId(valStep.getValueId()));
              }
            }
            return true; // 无值 = true
          }
        }
      }
    }
    return true; // 默认: 继承
  }

  /**
   * 配置高亮名称。
   *
   * <p>对于 query 中的每个 capture 名（如 {@code function.method.builtin}），按点分拆分后
   * 与 {@code recognizedNames} 列表做<strong>子集匹配</strong>（与上游 Rust 一致）：
   * recognized name 的每个点分段都必须出现在 capture 名的点分段集合中（顺序无关），
   * 选择匹配段数最多（最具体）的 recognized name 的索引作为该 capture 的 highlight 值。
   *
   * <p>例如，如果 recognized names 为 {@code ["function", "function.method"]}，
   * capture 名 {@code function.method.builtin} 会匹配 {@code function.method}（2 段），
   * 而不是 {@code function}（1 段）。capture 名 {@code function.builtin} 也会匹配
   * {@code function}（1 段），因为 "function" 是其分段之一。
   *
   * @param recognizedNames 已识别的高亮名称列表（按优先级从低到高）。
   */
  public void configure(String[] recognizedNames) {
    Objects.requireNonNull(recognizedNames, "recognizedNames cannot be null");
    this.configuredNames = recognizedNames.clone();

    int captureCount = query.getCaptureCount();
    for (int i = 0; i < captureCount; i++) {
      String name = query.getCaptureNameForId(i);
      if (name == null) {
        highlightIndices[i] = -1;
        continue;
      }

      // 按点分拆分 capture 名，转为 List 用于 contains 检查
      String[] capturePartsArr = name.split("\\.");
      List<String> captureParts = Arrays.asList(capturePartsArr);
      int bestIndex = -1;
      int bestMatchLen = 0;

      for (int j = 0; j < recognizedNames.length; j++) {
        String[] recognizedParts = recognizedNames[j].split("\\.");
        // 子集匹配：recognizedParts 的每一段都必须出现在 captureParts 中（顺序无关）
        boolean match = true;
        for (String part : recognizedParts) {
          if (!captureParts.contains(part)) {
            match = false;
            break;
          }
        }
        if (match && recognizedParts.length > bestMatchLen) {
          bestMatchLen = recognizedParts.length;
          bestIndex = j;
        }
      }

      highlightIndices[i] = bestIndex;
    }
  }

  public TSLanguage getLanguage() { return language; }
  public String getLanguageName() { return languageName; }
  public TSQuery getQuery() { return query; }
  public int getHighlightsPatternIndex() { return highlightsPatternIndex; }
  public int getLocalsPatternIndex() { return localsPatternIndex; }
  public int getInjectionContentCaptureIndex() { return injectionContentCaptureIndex; }
  public int getInjectionLanguageCaptureIndex() { return injectionLanguageCaptureIndex; }
  public int getLocalScopeCaptureIndex() { return localScopeCaptureIndex; }
  public int getLocalDefCaptureIndex() { return localDefCaptureIndex; }
  public int getLocalDefValueCaptureIndex() { return localDefValueCaptureIndex; }
  public int getLocalRefCaptureIndex() { return localRefCaptureIndex; }

  /** 获取指定 capture 索引对应的高亮索引，-1 表示无高亮。 */
  public int getHighlightIndex(int captureIndex) {
    if (captureIndex < 0 || captureIndex >= highlightIndices.length) return -1;
    return highlightIndices[captureIndex];
  }

  /** 检查指定 pattern 是否对局部变量禁用（即有 {@code #is-not? local} 属性谓词）。 */
  public boolean isNonLocalVariablePattern(int patternIndex) {
    if (patternIndex < 0 || patternIndex >= nonLocalVariablePatterns.length) return false;
    return nonLocalVariablePatterns[patternIndex];
  }

  /**
   * 检查指定 pattern 的 scope 是否继承父 scope。
   *
   * <p>对应 {@code #set! local.scope-inherits "false"} 属性设置。默认为 {@code true}。
   */
  public boolean doesScopeInherit(int patternIndex) {
    if (patternIndex < 0 || patternIndex >= scopeInherits.length) return true;
    return scopeInherits[patternIndex];
  }

  public String[] getConfiguredNames() {
    return configuredNames != null ? configuredNames.clone() : new String[0];
  }

  @Override
  public void close() {
    if (query != null) {
      query.close();
    }
  }

  /** 高亮配置异常。 */
  public static class HighlightException extends Exception {
    public HighlightException(String message) {
      super(message);
    }
  }
}
