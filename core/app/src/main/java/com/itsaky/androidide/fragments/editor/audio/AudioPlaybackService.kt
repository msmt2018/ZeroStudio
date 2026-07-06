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
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import org.slf4j.LoggerFactory

/**
 * 音频后台播放服务 —— 基于 Media3 [MediaSessionService]。
 *
 * ## 职责
 * - 在后台持续播放音频 (Fragment 关闭 / 应用切后台时仍可播放)
 * - 通过 [MediaSession] 暴露给系统媒体控制 (通知栏 / 锁屏 / 蓝牙耳机按键 / Android Auto)
 * - 自动管理前台通知 (由 Media3 [DefaultMediaNotificationProvider] 提供)
 * - 处理媒体按键 (play / pause / next / previous)
 *
 * ## 启动方式
 * ```
 * AudioPlaybackService.startPlayback(
 *     context = ctx,
 *     uri = uri,
 *     positionMs = 0L,
 * )
 * ```
 *
 * ## 生命周期
 * - 首次启动 → `onCreate()` 创建 [ExoPlayer] + [MediaSession]
 * - 调用 `startPlayback()` → 启动服务 + 设置播放列表 + 自动 play
 * - 播放中 → 前台通知持续显示 (Media3 自动), 服务保持 alive
 * - 播放结束 (STATE_ENDED 且无循环) → 自动 stopSelf
 * - 用户从通知栏 dismiss → stopSelf
 *
 * ## 与 Fragment 的关系
 * [AudioPreviewFragment] 默认使用 [AudioPlaybackController] 做前台播放 (fragment 范围内)。
 * 当用户切到后台 / 关闭 fragment 但希望继续播放时, 调用 [startPlayback] 把播放交给本服务。
 * 后续可通过 [MediaController] (Media3) 连接到本服务的 [MediaSession] 实现远程控制。
 *
 * @author ZeroStudio
 */
class AudioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val LOG = LoggerFactory.getLogger(TAG)

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // 播放结束且不循环 → 停止服务
                        if (playbackState == Player.STATE_ENDED) {
                            LOG.info("Playback ended, stopping service")
                            stopSelf()
                        }
                    }
                })
            }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        LOG.info("AudioPlaybackService created")
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // MediaSessionService 会自动管理前台通知 (DefaultMediaNotificationProvider)
        // 我们只需处理自定义 action
        if (intent?.action == ACTION_START_PLAYBACK) {
            handleStartPlaybackIntent(intent)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从 recent tasks 滑掉应用: 如果没在播放就停止服务
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        LOG.info("AudioPlaybackService destroyed")
        super.onDestroy()
    }

    // ── 内部: 处理 startPlayback Intent ───────────────────────

    private fun handleStartPlaybackIntent(intent: Intent) {
        val p = player ?: return
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        val uri = Uri.parse(uriString)

        // 设置单个媒体项 + 从指定位置开始
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        if (positionMs > 0) p.seekTo(positionMs)
        p.playWhenReady = true
        LOG.info("Started playback: uri={}, positionMs={}", uriString, positionMs)
    }

    companion object {
        const val TAG = "AudioPlaybackService"

        const val ACTION_START_PLAYBACK = "com.itsaky.androidide.audio.START_PLAYBACK"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_POSITION_MS = "extra_position_ms"

        /**
         * 启动后台播放服务并开始播放指定音频文件。
         *
         * @param context 任意 context
         * @param uri 音频文件 Uri
         * @param positionMs 起始位置 (毫秒), 0 表示从头开始
         */
        fun startPlayback(context: Context, uri: Uri, positionMs: Long = 0L) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_POSITION_MS, positionMs)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止后台播放服务。
         */
        fun stopPlayback(context: Context) {
            context.stopService(Intent(context, AudioPlaybackService::class.java))
        }
    }
}
