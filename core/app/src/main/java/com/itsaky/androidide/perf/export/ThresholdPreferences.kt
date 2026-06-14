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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * 自定义 phase 告警阈值的持久化 (Advanced / Commit 4).
 *
 * 用 [DataStore]<[Preferences]> 把 [PhaseThresholds] 阈值 (3 个非零 ms)
 * 跨重启保存. 不存 "0" (OK 上限 = WARN 上限 - 默认差) — 我们存**完整的
 * 3 个边界**: WARN/SLOW/CRITICAL 的 ms 上限. OK 上限由 0 (含) 到
 * WARN (不含) 自动定义, 不需要持久化.
 *
 * ## 存储 key
 *
 * - `pref_warn_ms` — < 此值为 OK, ≥ 此值为 WARN
 * - `pref_slow_ms` — ≥ 此值为 SLOW
 * - `pref_critical_ms` — ≥ 此值为 CRITICAL
 *
 * ## 加载 / 保存
 *
 * - [loadIntoThresholds]: 启动时 (IDEApplication.onCreate) 调, 同步阻塞
 *   读第一次 cache, 之后异步 observe 跟新. 也可在 [save] 后手动调一次
 *   让 PhaseThresholds 立即生效.
 * - [save]: 用户在 UI 调 Slider 改完后调, 异步写 DataStore.
 *
 * @author android_zero
 */
object ThresholdPreferences {

  private val log = LoggerFactory.getLogger(ThresholdPreferences::class.java)

  private val Context.dataStore: DataStore<Preferences> by
      preferencesDataStore(name = "perf_thresholds")

  private val KEY_WARN = longPreferencesKey("pref_warn_ms")
  private val KEY_SLOW = longPreferencesKey("pref_slow_ms")
  private val KEY_CRITICAL = longPreferencesKey("pref_critical_ms")

  /**
   * 从 DataStore 加载一次, 应用到 [PhaseThresholds].
   *
   * 用 [runBlocking] 同步读, 因为 [PhaseThresholds] 必须在 IDEApplication
   * .onCreate 末尾前就准备好 (BootTimelineCard 第一帧就要按阈值染色).
   * DataStore 的 .first() 读 cache 是 fast path, 不会真正阻塞 IO.
   *
   * @return 加载到的阈值 (默认 = [PhaseThresholds.DEFAULT_THRESHOLDS])
   */
  @JvmStatic
  fun loadIntoThresholds(context: Context): LongArray {
    return try {
      val prefs = runBlocking { context.dataStore.data.first() }
      val warn = prefs[KEY_WARN] ?: PhaseThresholds.DEFAULT_THRESHOLDS[0]
      val slow = prefs[KEY_SLOW] ?: PhaseThresholds.DEFAULT_THRESHOLDS[1]
      val crit = prefs[KEY_CRITICAL] ?: PhaseThresholds.DEFAULT_THRESHOLDS[2]
      val arr = longArrayOf(warn, slow, crit, Long.MAX_VALUE)
      // 校验: 必须严格递增
      if (warn >= slow || slow >= crit) {
        log.warn(
            "ThresholdPreferences: loaded invalid thresholds ({}), use defaults",
            arr.toList(),
        )
        PhaseThresholds.DEFAULT_THRESHOLDS
      } else {
        arr
      }
    } catch (e: Throwable) {
      log.warn("ThresholdPreferences: load failed: {}", e.message)
      PhaseThresholds.DEFAULT_THRESHOLDS
    }
  }

  /**
   * 异步保存到 DataStore, 并立即把新阈值应用到 [PhaseThresholds].
   *
   * 入参必须严格递增. 入参是 4 元素数组 (最后一位是 CRITICAL 的 Long.MAX_VALUE).
   * 我们只持久化前 3 个.
   */
  @JvmStatic
  fun save(context: Context, thresholds: LongArray) {
    require(thresholds.size == 4) { "thresholds must have 4 elements" }
    val warn = thresholds[0]
    val slow = thresholds[1]
    val crit = thresholds[2]
    require(warn in 1..(slow - 1)) { "warn must be in [1, slow)" }
    require(slow in (warn + 1)..(crit - 1)) { "slow must be in (warn, crit)" }
    require(crit in (slow + 1)..(Long.MAX_VALUE / 2)) { "crit must be > slow" }
    // 立即生效 (UI 下次 collectAsState 就能拿到新色)
    PhaseThresholds.setThresholds(thresholds)
    // 异步持久化
    CoroutineScope(Dispatchers.IO).launch {
      try {
        context.dataStore.edit { prefs ->
          prefs[KEY_WARN] = warn
          prefs[KEY_SLOW] = slow
          prefs[KEY_CRITICAL] = crit
        }
        log.info("ThresholdPreferences: saved warn={} slow={} crit={}", warn, slow, crit)
      } catch (e: Throwable) {
        log.warn("ThresholdPreferences: save failed: {}", e.message)
      }
    }
  }

  /** 恢复默认阈值 + 持久化. */
  @JvmStatic
  fun resetToDefault(context: Context) {
    PhaseThresholds.resetToDefault()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        context.dataStore.edit { prefs ->
          prefs.remove(KEY_WARN)
          prefs.remove(KEY_SLOW)
          prefs.remove(KEY_CRITICAL)
        }
        log.info("ThresholdPreferences: reset to default")
      } catch (e: Throwable) {
        log.warn("ThresholdPreferences: reset failed: {}", e.message)
      }
    }
  }
}
