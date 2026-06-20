/*
 *  This file is part of ZeroStudio.
 *
 *  ZeroStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ZeroStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ZeroStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.onboarding.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 基于 Android SharedPreferences 的 [OnboardingPreferences] 实现 (生产环境).
 *
 * - 文件名: `zero_onboarding_prefs`
 * - 模式: 私有 (MODE_PRIVATE)
 * - 内存缓存 + 响应式 Flow
 *
 * 用法:
 * ```
 * val prefs = SharedPreferencesOnboardingPreferences(context)
 * OnboardingPreferences.setDefault(prefs)
 * ```
 */
class SharedPreferencesOnboardingPreferences(
  context: Context,
  fileName: String = DEFAULT_FILE_NAME,
) : OnboardingPreferences {

  private val sp: SharedPreferences =
    context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

  // 内存缓存: 避免每次都从 SP 读取 (SP 是异步刷盘的)
  private val cache: MutableStateFlow<Set<String>> = MutableStateFlow(loadAllFromDisk())

  private fun loadAllFromDisk(): Set<String> {
    return sp.all.keys
      .filter { it.startsWith(KEY_PREFIX) }
      .map { it.removePrefix(KEY_PREFIX) }
      .toSet()
  }

  override fun isCompleted(guideId: String): Boolean {
    return cache.value.contains(guideId) || sp.getBoolean(key(guideId), false)
  }

  override fun markCompleted(guideId: String) {
    sp.edit().putBoolean(key(guideId), true).apply()
    cache.value = cache.value + guideId
  }

  override fun reset(guideId: String) {
    sp.edit().remove(key(guideId)).apply()
    cache.value = cache.value - guideId
  }

  override fun resetAll() {
    val editor = sp.edit()
    cache.value.forEach { editor.remove(key(it)) }
    editor.apply()
    cache.value = emptySet()
  }

  override fun observeCompleted(guideId: String): Flow<Boolean> =
    cache.asStateFlow()
      .map { it.contains(guideId) }
      .distinctUntilChanged()

  override fun getAllCompleted(): Set<String> = cache.value.toSet()

  private fun key(guideId: String): String = "$KEY_PREFIX$guideId"

  companion object {
    const val DEFAULT_FILE_NAME = "zero_onboarding_prefs"
    private const val KEY_PREFIX = "onboarding_completed_"
  }
}

/**
 * 内存版 [OnboardingPreferences] (测试 / 预览用).
 *
 * 特点:
 *  - 进程内存储, 不写磁盘
 *  - 每次创建都是新的空状态
 *  - 提供响应式 Flow 监听
 */
class MemoryOnboardingPreferences : OnboardingPreferences {

  private val state: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

  override fun isCompleted(guideId: String): Boolean = state.value.contains(guideId)

  override fun markCompleted(guideId: String) {
    state.value = state.value + guideId
  }

  override fun reset(guideId: String) {
    state.value = state.value - guideId
  }

  override fun resetAll() {
    state.value = emptySet()
  }

  override fun observeCompleted(guideId: String): Flow<Boolean> =
    state.asStateFlow()
      .map { it.contains(guideId) }
      .distinctUntilChanged()

  override fun getAllCompleted(): Set<String> = state.value.toSet()
}
