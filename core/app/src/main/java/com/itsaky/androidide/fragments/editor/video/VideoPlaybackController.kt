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

package com.itsaky.androidide.fragments.editor.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
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
 * 字幕轨道信息。
 *
 * @param trackIndex TrackGroup 中的索引
 * @param label 显示名 (如 "English", "Chinese (Simplified)")
 * @param language ISO 639-1 语言代码 (如 "en", "zh")
 */
data class SubtitleTrack(
    val trackIndex: Int,
    val label: String,
    val language: String?,
)

/**
 * 视频播放状态。
 *
 * 包含基础播放状态 + 视频特有状态 (尺寸 / 比例 / 字幕轨道 / 控件可见性)。
 */
data class VideoPlaybackState(
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
    // ── 视频特有 ──
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoAspectRatio: Float = 0f,
    val availableSubtitles: List<SubtitleTrack> = emptyList(),
    val selectedSubtitleIndex: Int = -1, // -1 = 关闭字幕
    val controlsVisible: Boolean = true,
    val brightness: Float = -1f, // -1 = 跟随系统
    val volume: Float = 1.0f,
)

/**
 * 视频播放控制器: 封装 [ExoPlayer], 通过 [StateFlow] 暴露视频播放状态。
 *
 * 与 [com.itsaky.androidide.fragments.editor.audio.AudioPlaybackController] 类似的封装模式,
 * 但增加了视频特有功能: 字幕轨道选择 / 视频尺寸跟踪 / 亮度音量控制 / 控件可见性。
 *
 * 生命周期: Fragment `onCreateView` 创建, `onDestroyView` 调用 [release]。
 *
 * @author ZeroStudio
 */
class VideoPlaybackController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().also { p ->
            p.addListener(object : Player.Listener {
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

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val aspect = if (videoSize.height > 0) {
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                    } else 0f
                    _state.value = _state.value.copy(
                        videoWidth = videoSize.width,
                        videoHeight = videoSize.height,
                        videoAspectRatio = aspect,
                    )
                }

                override fun onTracksChanged(tracks: Tracks) {
                    refreshSubtitleTracks()
                }
            })
        }
    }

    /**
     * 设置播放列表。
     *
     * @param uris 视频文件 Uri 列表
     * @param startIndex 起始播放索引
     */
    fun setMediaItems(uris: List<Uri>, startIndex: Int = 0) {
        if (uris.isEmpty()) return
        val mediaItems = uris.map { MediaItem.fromUri(it) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        _state.value = _state.value.copy(trackCount = uris.size, currentTrackIndex = startIndex)
    }

    /** 设置单个视频文件。 */
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

    /** 设置音量 (0..1)。 */
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        player.volume = v
        _state.value = _state.value.copy(volume = v)
    }

    /** 设置亮度 (0..1, -1 表示跟随系统)。 */
    fun setBrightness(brightness: Float) {
        _state.value = _state.value.copy(brightness = brightness)
    }

    /** 切换控件可见性 (自动隐藏 / 显示)。 */
    fun toggleControls() {
        _state.value = _state.value.copy(controlsVisible = !_state.value.controlsVisible)
    }

    fun setControlsVisible(visible: Boolean) {
        _state.value = _state.value.copy(controlsVisible = visible)
    }

    /**
     * 选择字幕轨道。
     *
     * @param trackIndex [SubtitleTrack.trackIndex], -1 表示关闭字幕
     */
    fun selectSubtitle(trackIndex: Int) {
        val tracks = player.currentTracks
        // 找到文本 (字幕) track group
        var groupIdx = -1
        for (i in 0 until tracks.groups.size) {
            val group = tracks.groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                groupIdx = i
                break
            }
        }
        if (groupIdx < 0) return

        val trackGroups = player.currentTracks.groups
        val textGroup = trackGroups[groupIdx]
        if (trackIndex < 0 || trackIndex >= textGroup.length) {
            // 关闭字幕
            val params = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            player.trackSelectionParameters = params
            _state.value = _state.value.copy(selectedSubtitleIndex = -1)
        } else {
            val params = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    TrackSelectionOverride(textGroup.mediaTrackGroup, trackIndex),
                )
                .build()
            player.trackSelectionParameters = params
            _state.value = _state.value.copy(selectedSubtitleIndex = trackIndex)
        }
    }

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
            volume = player.volume,
        )
        updatePosition()
        refreshSubtitleTracks()
    }

    private fun updatePosition() {
        _state.value = _state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0L))
    }

    /**
     * 从 [Player.getCurrentTracks] 提取可用字幕轨道列表。
     */
    private fun refreshSubtitleTracks() {
        val tracks = player.currentTracks
        val subtitles = mutableListOf<SubtitleTrack>()
        for (i in 0 until tracks.groups.size) {
            val group = tracks.groups[i]
            if (group.type != androidx.media3.common.C.TRACK_TYPE_TEXT) continue
            for (trackIdx in 0 until group.length) {
                if (!group.isTrackSupported(trackIdx)) continue
                val format = group.getTrackFormat(trackIdx)
                val label = format.label ?: format.language ?: "Track ${trackIdx + 1}"
                subtitles.add(
                    SubtitleTrack(
                        trackIndex = trackIdx,
                        label = label,
                        language = format.language,
                    ),
                )
            }
        }
        // 仅在字幕列表确实变化时更新, 避免 StateFlow 误触发重组
        val current = _state.value.availableSubtitles
        if (current.size != subtitles.size ||
            current.zip(subtitles).any { it.first.label != it.second.label }
        ) {
            _state.value = _state.value.copy(availableSubtitles = subtitles)
        }
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
