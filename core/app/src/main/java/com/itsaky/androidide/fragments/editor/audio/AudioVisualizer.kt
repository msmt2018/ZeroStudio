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

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 频谱可视化控制器: 封装 [Visualizer] (android.media.audiofx.Visualizer)。
 *
 * 通过音频会话 ID 采集波形数据, 归一化到 [0, 1] 后通过 [StateFlow] 暴露。
 * Compose Canvas 订阅 [waveform] 实时绘制柱状/波形/圆形频谱。
 *
 * @author ZeroStudio
 */
class AudioVisualizer(audioSessionId: Int) {

    companion object {
        private const val TAG = "AudioVisualizer"
        private const val BAND_COUNT = 32
    }

    private var visualizer: Visualizer? = null
    private val _waveform = MutableStateFlow(FloatArray(BAND_COUNT))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    init {
        runCatching {
            visualizer = Visualizer(audioSessionId).also { viz ->
                viz.captureSize = Visualizer.getCaptureSizeRange()[1]
                viz.setDataListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            waveform ?: return
                            // 将 8-bit PCM 波形 (0-255, 128=静音) 转换为 32 个频段的归一化幅度
                            _waveform.value = downsampleToBands(waveform)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            // 使用 FFT 数据 (频域) 做柱状频谱更直观
                            fft ?: return
                            _waveform.value = fftToBands(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true, // captureWaveform
                    true, // captureFFT
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Visualizer init failed: ${e.message}")
        }
    }

    /** 启动采集 (播放时调用)。 */
    fun start() {
        runCatching { visualizer?.enabled = true }.onFailure {
            Log.w(TAG, "Visualizer start failed: ${it.message}")
        }
    }

    /** 停止采集 (暂停/停止时调用)。 */
    fun stop() {
        runCatching { visualizer?.enabled = false }
        _waveform.value = FloatArray(BAND_COUNT)
    }

    /** 释放资源。 */
    fun release() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        }
    }

    // ── 内部: FFT → 频段 ──────────────────────────────────────

    /**
     * 将 FFT 字节数组转换为 [BAND_COUNT] 个频段的归一化幅度 (0..1)。
     *
     * FFT 数据格式: [re0, im0, re1, im1, ...], 每个 byte 是 signed 8-bit。
     * 频率分辨率 = samplingRate / 2 / (fft.length / 2)。
     */
    private fun fftToBands(fft: ByteArray): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        val n = fft.size / 2 // FFT 采样点数的一半 (实数部分)
        if (n == 0) return bands

        // 对数分频段: 低频密集, 高频稀疏 (符合人耳感知)
        val magnitudes = FloatArray(n)
        for (i in 0 until n) {
            val re = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            magnitudes[i] = kotlin.math.sqrt(re * re + im * im) / 128f
        }

        // 对数映射到 BAND_COUNT 个频段
        for (i in 0 until BAND_COUNT) {
            val startIdx = (Math.pow(n.toDouble(), i.toDouble() / BAND_COUNT)).toInt()
            val endIdx = (Math.pow(n.toDouble(), (i + 1).toDouble() / BAND_COUNT)).toInt()
                .coerceAtMost(n)
            if (startIdx >= endIdx) {
                bands[i] = magnitudes[startIdx.coerceAtMost(n - 1)]
            } else {
                // 取频段内最大值
                bands[i] = magnitudes.slice(startIdx until endIdx).maxOrNull() ?: 0f
            }
        }

        // 归一化 + 平滑
        val max = bands.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
        return bands.map { (it / max).coerceIn(0f, 1f) }.toFloatArray()
    }

    /** 降采样波形数据到 [BAND_COUNT] 个频段 (备用, FFT 不可用时使用)。 */
    private fun downsampleToBands(waveform: ByteArray): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        val step = waveform.size / BAND_COUNT
        if (step == 0) return bands
        for (i in 0 until BAND_COUNT) {
            val start = i * step
            val end = (start + step).coerceAtMost(waveform.size)
            var sum = 0f
            for (j in start until end) {
                // 8-bit unsigned PCM: 128 = 静音, 转为绝对振幅
                sum += kotlin.math.abs((waveform[j].toInt() and 0xFF) - 128) / 128f
            }
            bands[i] = (sum / (end - start)).coerceIn(0f, 1f)
        }
        return bands
    }
}
