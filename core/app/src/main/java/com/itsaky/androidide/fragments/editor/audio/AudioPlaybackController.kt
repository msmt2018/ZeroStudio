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

import android.content.Context
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音频播放状态。
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleMode: Boolean = false,
    val currentTrackIndex: Int = 0,
    val trackCount: Int = 0,
    val errorMessage: String? = null,
)

/**
 * 音频播放控制器: 封装 [ExoPlayer], 通过 [StateFlow] 暴露播放状态。
 *
 * 生命周期: Fragment `onCreateView` 创建, `onDestroyView` 调用 [release]。
 * 内部用 [ExoPlayer] 实现播放/暂停/seek/列表切换/倍速/循环/随机。
 *
 * @author ZeroStudio
 */
class AudioPlaybackController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.value = _state.value.copy(isBuffering = true)
                        Player.STATE_READY -> _state.value = _state.value.copy(isBuffering = false, errorMessage = null)
                        Player.STATE_ENDED -> stopPositionUpdates()
                        Player.STATE_IDLE -> stopPositionUpdates()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(
                        errorMessage = error.localizedMessage ?: "Playback error",
                        isPlaying = false,
                        isBuffering = false,
                    )
                }
            })
        }
    }

    /** 当前播放的 audioSessionId, 供 [EqualizerController] / [AudioVisualizer] 使用。 */
    val audioSessionId: Int
        get() = player.audioSessionId

    /**
     * 设置播放列表 (同目录下的音频文件)。
     *
     * @param uris 音频文件 Uri 列表
     * @param startIndex 起始播放索引
     */
    fun setMediaItems(uris: List<Uri>, startIndex: Int = 0) {
        if (uris.isEmpty()) return
        val mediaItems = uris.map { MediaItem.fromUri(it) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        _state.value = _state.value.copy(trackCount = uris.size, currentTrackIndex = startIndex)
    }

    /** 设置单个音频文件。 */
    fun setMediaItem(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        _state.value = _state.value.copy(trackCount = 1, currentTrackIndex = 0)
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun stop() = player.stop()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updatePosition()
    }

    fun seekToNext() = player.seekToNextMediaItem()
    fun seekToPrevious() = player.seekToPreviousMediaItem()

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
        _state.value = _state.value.copy(repeatMode = mode)
    }

    fun toggleRepeatMode() {
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeatMode(next)
    }

    fun setShuffleMode(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        _state.value = _state.value.copy(shuffleMode = enabled)
    }

    fun toggleShuffle() = setShuffleMode(!_state.value.shuffleMode)

    /** 释放资源, 必须在 Fragment onDestroyView 调用。 */
    fun release() {
        stopPositionUpdates()
        player.release()
        scope.cancel()
    }

    // ── 内部 ──────────────────────────────────────────────────

    private fun updateState() {
        _state.value = _state.value.copy(
            durationMs = if (player.duration > 0) player.duration else 0L,
            currentTrackIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            playbackSpeed = player.playbackParameters.speed,
            repeatMode = player.repeatMode,
            shuffleMode = player.shuffleModeEnabled,
        )
        updatePosition()
    }

    private fun updatePosition() {
        _state.value = _state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0L))
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = scope.launch {
            while (true) {
                updatePosition()
                delay(200)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun CoroutineScope.cancel() {
        this.coroutineContext[Job]?.cancel()
    }
}
