/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.audio

import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 均衡器频段信息。
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    val currentLevel: Short,
)

/**
 * 均衡器状态。
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val currentPreset: String? = null,
    val presets: List<String> = emptyList(),
)

/**
 * 均衡器控制器: 封装 [Equalizer] (android.media.audiofx.Equalizer)。
 *
 * 使用音频会话 ID 绑定到 [AudioPlaybackController] 的播放会话。
 * 支持 5 个预设 + 自定义频段调节。
 *
 * @author ZeroStudio
 */
class EqualizerController(audioSessionId: Int) {

    companion object {
        private const val TAG = "EqualizerController"
    }

    private var equalizer: Equalizer? = null
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    init {
        runCatching {
            equalizer = Equalizer(0, audioSessionId).also { eq ->
                // 收集预设
                val presetNames = mutableListOf<String>()
                for (i in 0 until eq.numberOfPresets) {
                    presetNames.add(eq.getPresetName(i.toShort()))
                }
                // 收集频段
                val bands = mutableListOf<EqualizerBand>()
                for (i in 0 until eq.numberOfBands) {
                    val band = i.toShort()
                    val centerFreq = eq.getCenterFreq(band) // milliHz
                    val minLevel = eq.bandLevelRange[0]
                    val maxLevel = eq.bandLevelRange[1]
                    val currentLevel = eq.getBandLevel(band)
                    bands.add(
                        EqualizerBand(
                            index = i,
                            centerFreqHz = centerFreq / 1000,
                            minLevel = minLevel,
                            maxLevel = maxLevel,
                            currentLevel = currentLevel,
                        )
                    )
                }
                _state.value = _state.value.copy(
                    bands = bands,
                    presets = presetNames,
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Equalizer init failed (device may not support): ${e.message}")
        }
    }

    /** 启用/禁用均衡器。 */
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _state.value = _state.value.copy(enabled = enabled)
    }

    /** 设置频段电平 (单位: mB, 毫贝)。 */
    fun setBandLevel(bandIndex: Int, level: Short) {
        val eq = equalizer ?: return
        if (bandIndex < 0 || bandIndex >= eq.numberOfBands) return
        runCatching {
            eq.setBandLevel(bandIndex.toShort(), level)
            _state.value = _state.value.copy(
                bands = _state.value.bands.mapIndexed { idx, band ->
                    if (idx == bandIndex) band.copy(currentLevel = level) else band
                },
                currentPreset = null, // 手动调节后清除预设标记
            )
        }.onFailure { e ->
            Log.w(TAG, "setBandLevel failed: ${e.message}")
        }
    }

    /** 应用预设。 */
    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        val presetIndex = _state.value.presets.indexOf(presetName)
        if (presetIndex < 0) return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            // 刷新频段电平
            val updatedBands = _state.value.bands.mapIndexed { idx, band ->
                band.copy(currentLevel = eq.getBandLevel(idx.toShort()))
            }
            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPreset = presetName,
            )
        }.onFailure { e ->
            Log.w(TAG, "applyPreset failed: ${e.message}")
        }
    }

    /** 释放资源。 */
    fun release() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
            equalizer = null
        }
    }
}
