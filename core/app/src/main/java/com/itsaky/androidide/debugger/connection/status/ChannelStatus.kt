package com.itsaky.androidide.debugger.connection.status

import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel

/**
 * 单个连接通道的归一化状态。
 *
 * @param channel 通道标识（同时携带卡片色条颜色）
 * @param level 红黄绿层级
 * @param label 简短文字，如「已连接」「连接中」「未授权」
 * @param deviceName 已连接时附带设备名，用于在卡片 / 总览条展示
 */
data class ChannelStatus(
    val channel: DcChannel,
    val level: DcStatusLevel,
    val label: String,
    val deviceName: String? = null,
)