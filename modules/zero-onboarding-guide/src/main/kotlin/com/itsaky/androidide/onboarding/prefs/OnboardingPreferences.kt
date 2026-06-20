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

import kotlinx.coroutines.flow.Flow

/**
 * 引导持久化偏好设置 (Onboarding persistent preferences).
 *
 * 用于记录"某个引导 (用 [guideId] 标识) 是否已经完成",
 * 实现 **一次性引导**: 已完成的引导以后永久不再提示.
 *
 * ## 用法
 * ```
 * // 初始化一次
 * val prefs: OnboardingPreferences = SharedPreferencesOnboardingPreferences(context)
 * OnboardingPreferences.setDefault(prefs)
 *
 * // 创建带持久化的引导
 * LaunchOnboarding(
 *   steps = steps,
 *   config = OnboardingConfig(guideId = "first_time_user", skipIfCompleted = true),
 * )
 * ```
 *
 * 设计目标:
 *  - **接口与实现分离**: 易于测试 (MemoryOnboardingPreferences) 和生产 (SharedPreferences)
 *  - **响应式**: 提供 [observeCompleted] Flow, 引导状态可被 UI 响应式订阅
 *  - **全局可访问**: 通过 [OnboardingPreferences.setDefault] 设置一个默认实例, 方便快速接入
 */
interface OnboardingPreferences {

  /**
   * 引导是否已完成.
   *
   * @param guideId 引导唯一标识 (例如 "first_time_user", "feature_editor_quickstart")
   * @return true = 已完成, false = 未完成
   */
  fun isCompleted(guideId: String): Boolean

  /**
   * 标记引导为已完成.
   *
   * 标记后, [isCompleted] 将返回 true, 除非调用 [reset] 清除.
   */
  fun markCompleted(guideId: String)

  /**
   * 重置指定引导的完成状态.
   */
  fun reset(guideId: String)

  /**
   * 重置所有引导的完成状态.
   */
  fun resetAll()

  /**
   * 观察引导的完成状态变化.
   *
   * 当 [markCompleted] / [reset] / [resetAll] 被调用时, Flow 会发出新值.
   */
  fun observeCompleted(guideId: String): Flow<Boolean>

  /**
   * 获取所有已完成的引导 ID 列表.
   */
  fun getAllCompleted(): Set<String>

  companion object {
    /** 默认全局实例 (可用于快速接入, 无需显式注入). */
    @Volatile
    private var defaultInstance: OnboardingPreferences? = null

    fun setDefault(prefs: OnboardingPreferences) {
      defaultInstance = prefs
    }

    fun getDefault(): OnboardingPreferences? = defaultInstance

    fun getDefaultOrNull(): OnboardingPreferences? = defaultInstance
  }
}
