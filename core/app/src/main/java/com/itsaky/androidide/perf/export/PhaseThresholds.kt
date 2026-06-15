/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.perf.export

/**
 * Phase 耗时告警阈值 (PR #6/6).
 *
 * 把 phase 耗时映射到 [Severity] 等级, UI (PR #7) 用此决定 phase 行的颜色:
 * - [Severity.OK]: 绿色 / 主题 primary — 正常
 * - [Severity.WARN]: 黄色 / tertiary — 偏慢, 关注
 * - [Severity.SLOW]: 橙色 — 明显慢
 * - [Severity.CRITICAL]: 红色 / error — 严重, 需要优化
 *
 * ## 默认阈值
 *
 * | 范围 (ms) | Severity |
 * | --- | --- |
 * | 0 — 100 | OK |
 * | 100 — 500 | WARN |
 * | 500 — 1500 | SLOW |
 * | 1500+ | CRITICAL |
 *
 * 阈值可在运行时调 [setThresholds] (PR #7 接入设置 UI).
 *
 * ## 与启动总耗时比例阈值
 *
 * 除绝对阈值外, 我们也提供 [severityByRatio], 让单 phase 占总耗时
 * 比例过大时也升级告警. 例如 init_koin 在 5s 总启动里 1s 本身不算 CRITICAL,
 * 但占 20% 算"过分".
 *
 * @author android_zero
 */
object PhaseThresholds {

  /** 告警等级. */
  enum class Severity {
    OK,
    WARN,
    SLOW,
    CRITICAL,
  }

  /**
   * 默认绝对阈值 (ms): [0, 100, 500, 1500, ∞)
   *
   * `thresholds[0]` = OK 上限 (不包含): < 100 = OK
   * `thresholds[1]` = WARN 上限: 100-499 = WARN
   * `thresholds[2]` = SLOW 上限: 500-1499 = SLOW
   * `thresholds[3]` = 任意上限: ≥ 1500 = CRITICAL
   *
   * 必须放在 [thresholds] 之前, 否则 forward reference 报
   * "Variable 'DEFAULT_THRESHOLDS' must be initialized" —
   * Kotlin object 成员按声明顺序初始化.
   */
  val DEFAULT_THRESHOLDS = longArrayOf(100L, 500L, 1500L, Long.MAX_VALUE)

  /**
   * 当前阈值 (ms). 默认可写, 业务可调.
   *
   * 数组索引对应 [Severity] 枚举 (0=OK 上限, 1=WARN 上限, 2=SLOW 上限, ≥3=CRITICAL).
   */
  @Volatile
  var thresholds: LongArray = DEFAULT_THRESHOLDS
    private set

  /** 设置自定义阈值. 长度必须 = 4 (含 0 + 3 个上限). */
  fun setThresholds(newThresholds: LongArray) {
    require(newThresholds.size == 4) { "thresholds must have 4 elements" }
    require(newThresholds[0] >= 0) { "OK 上限必须 >= 0" }
    for (i in 1 until newThresholds.size) {
      require(newThresholds[i] > newThresholds[i - 1]) { "阈值必须严格递增" }
    }
    thresholds = newThresholds.copyOf()
  }

  /** 恢复默认阈值. */
  fun resetToDefault() {
    thresholds = DEFAULT_THRESHOLDS.copyOf()
  }

  /**
   * 绝对耗时 → Severity.
   */
  fun severity(elapsedMs: Long): Severity {
    val t = thresholds
    return when {
      elapsedMs < t[0] -> Severity.OK
      elapsedMs < t[1] -> Severity.WARN
      elapsedMs < t[2] -> Severity.SLOW
      else -> Severity.CRITICAL
    }
  }

  /**
   * 比例告警: phase 耗时 / 总耗时 → Severity.
   *
   * 比例阈值 (默认 5% / 15% / 30%):
   * - < 5%: OK
   * - 5% — 15%: WARN
   * - 15% — 30%: SLOW
   * - ≥ 30%: CRITICAL
   */
  fun severityByRatio(phaseMs: Long, totalMs: Long): Severity {
    if (totalMs <= 0L) return Severity.OK
    val ratio = phaseMs.toDouble() / totalMs.toDouble()
    return when {
      ratio < RATIO_WARN -> Severity.OK
      ratio < RATIO_SLOW -> Severity.WARN
      ratio < RATIO_CRITICAL -> Severity.SLOW
      else -> Severity.CRITICAL
    }
  }

  /**
   * 综合告警: 取 [severity] 和 [severityByRatio] 的最大值.
   */
  fun maxSeverity(elapsedMs: Long, totalMs: Long): Severity {
    val byAbs = severity(elapsedMs)
    val byRatio = severityByRatio(elapsedMs, totalMs)
    return if (byAbs.ordinal >= byRatio.ordinal) byAbs else byRatio
  }

  private const val RATIO_WARN = 0.05
  private const val RATIO_SLOW = 0.15
  private const val RATIO_CRITICAL = 0.30

  // 注: DEFAULT_THRESHOLDS 必须在 thresholds 之前声明, 上面已前置
}
