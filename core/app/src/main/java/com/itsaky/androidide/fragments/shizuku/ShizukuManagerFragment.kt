/*
 *  ZeroStudio IDE - Shizuku 管理器
 *
 *  在 IDE 内提供一个轻量的 Shizuku 管理界面:
 *    1. 显示 Shizuku 运行 / 授权 / 版本状态 (复用 DefaultShizukuProbe)
 *    2. 一键跳转 Shizuku Manager 启动 server
 *    3. 一键发起授权请求 (Shizuku.requestPermission)
 *    4. 一键跳转 Shizuku Manager 的无线 ADB 配对教程
 *
 *  core/app 只依赖 projects.modules.shizuku.api (客户端 SDK),
 *  manager APK 是独立进程, 通过 Intent 显式 component 跳转,
 *  未安装时给出明确提示。
 */

package com.itsaky.androidide.fragments.shizuku

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.itsaky.androidide.debugger.connection.shizuku.DefaultShizukuProbe
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuStatus
import com.itsaky.androidide.onboarding.effects.frostedGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku 管理器 Fragment —— 在 IDE 偏好设置里点击「打开 Shizuku 管理器」打开。
 *
 * UI 全 Compose, 风格与 [com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment]
 * 一致, 浮动卡片用 [frostedGlass] 磨砂玻璃效果。
 */
class ShizukuManagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        setContent {
            MaterialTheme {
                ShizukuManagerScreen()
            }
        }
    }
}

// region 常量

private const val SHIZUKU_MANAGER_PKG = "moe.shizuku.manager"
private const val SHIZUKU_HOME_ACTIVITY = "moe.shizuku.manager.home.HomeActivity"
private const val SHIZUKU_PAIR_ACTIVITY = "moe.shizuku.manager.adb.AdbPairingTutorialActivity"

/**
 * Shizuku.requestPermission 的 requestCode, 与
 * [com.itsaky.androidide.debugger.connection.shizuku.ShizukuProbe.REQUEST_PERMISSION]
 * 同值 (0x5B1A), 但本 Fragment 不直接 import 接口常量, 避免耦合。
 */
private const val SHIZUKU_REQUEST_CODE = 0x5B1A

// endregion

// region 顶层 Composable

@Composable
private fun ShizukuManagerScreen() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<ShizukuStatus?>(null) }
    // tick 改变会触发重新 probe (首次进入 / 手动刷新 / binder 状态变化 / 授权回调)
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        status = withContext(Dispatchers.IO) { DefaultShizukuProbe().probe() }
    }

    // 监听 Shizuku binder 上线 / 下线, 自动刷新状态
    DisposableEffect(Unit) {
        val onReceived = Shizuku.OnBinderReceivedListener { tick++ }
        val onDead = Shizuku.OnBinderDeadListener { tick++ }
        runCatching {
            Shizuku.addBinderReceivedListener(onReceived)
            Shizuku.addBinderDeadListener(onDead)
        }
        onDispose {
            runCatching { Shizuku.removeBinderReceivedListener(onReceived) }
            runCatching { Shizuku.removeBinderDeadListener(onDead) }
        }
    }

    // 监听授权结果, 自动刷新状态
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
    ) {
        Text(
            text = "Shizuku 管理器",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "通过 Shizuku 启动系统 WIFI 无线调试, 免 USB 即可执行 ADB 等价操作。",
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(16.dp))
        StatusCard(status = status)

        Spacer(Modifier.height(16.dp))
        ActionButtons(
            status = status,
            onLaunchManager = { launchShizukuManager(ctx) },
            onAuthorize = {
                runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
                    .onFailure { toast(ctx, "授权请求失败: ${it.message}") }
            },
            onPairWirelessAdb = { launchWirelessAdbPairing(ctx) },
            onRefresh = { tick++ },
        )

        Spacer(Modifier.height(16.dp))
        HelpFooter()
    }
}

// endregion

// region 状态卡片

/**
 * 状态卡片: 显示 Shizuku 运行 / 授权 / 版本信息。
 * 加载中 / 已就绪 / 已运行未授权 / 未运行 四种状态, 用 FrostedGlass 风格。
 */
@Composable
private fun StatusCard(status: ShizukuStatus?) {
    val icon: ImageVector
    val tint: Color
    val headline: String
    val lines: List<String>
    when {
        status == null -> {
            icon = Icons.Outlined.Refresh
            tint = Color(0xFFCCCCCC)
            headline = "检测中…"
            lines = listOf("正在查询 Shizuku 服务状态")
        }
        status.isReady -> {
            icon = Icons.Outlined.CheckCircle
            tint = Color(0xFF66BB6A)
            headline = "Shizuku 就绪"
            lines = listOf(
                "运行状态: 已运行",
                "授权状态: 已授权",
                "Server UID: ${status.serverUid}",
                "API 版本: ${status.serverApiVersion}",
            )
        }
        status.isRunning && !status.isGranted -> {
            icon = Icons.Outlined.Security
            tint = Color(0xFFFFB74D)
            headline = "Shizuku 已运行, 等待授权"
            lines = listOf(
                "运行状态: 已运行",
                "授权状态: 未授权",
                "Server UID: ${status.serverUid}",
                "API 版本: ${status.serverApiVersion}",
                "请点击下方「授权」按钮",
            )
        }
        else -> {
            icon = Icons.Outlined.Error
            tint = Color(0xFFEF5350)
            headline = "Shizuku 未运行"
            lines = listOf(
                status.notRunningReason ?: "请先安装并启动 Shizuku Manager",
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .frostedGlass(
                shape = RoundedCornerShape(20.dp),
                tint = Color(0xFF2D2D30),
                alpha = 0.85f,
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = headline,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (status == null) {
                CircularProgressIndicator(
                    color = Color(0xFF9CDCFE),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        lines.forEach { line ->
            Text(
                text = line,
                color = Color(0xFFEEEEEE),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

// endregion

// region 操作按钮

@Composable
private fun ActionButtons(
    status: ShizukuStatus?,
    onLaunchManager: () -> Unit,
    onAuthorize: () -> Unit,
    onPairWirelessAdb: () -> Unit,
    onRefresh: () -> Unit,
) {
    val canAuthorize = status?.isRunning == true && !status.isGranted
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onLaunchManager,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("启动 Shizuku / 打开 Manager")
        }

        OutlinedButton(
            onClick = onAuthorize,
            enabled = canAuthorize,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("授权 Shizuku 访问")
        }

        OutlinedButton(
            onClick = onPairWirelessAdb,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("无线 ADB 配对 (WIFI 调试)")
        }

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("刷新状态")
        }
    }
}

// endregion

// region 帮助说明

@Composable
private fun HelpFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF252526))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF9CDCFE),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "使用说明",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        val steps = listOf(
            "1. 在系统设置开启「无线调试」(Android 11+)",
            "2. 安装 Shizuku Manager APK (从 shizuku.com 下载)",
            "3. 在 Shizuku Manager 内通过无线调试启动 Server",
            "4. 回到本页, 点击「授权」给 IDE 访问权限",
            "5. 在 Debugger 设置中选择 Shizuku 连接方式",
        )
        steps.forEach { step ->
            Text(
                text = step,
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

// endregion

// region 启动 helper

/** 跳转 Shizuku Manager 主页 (启动 Server)。未安装时提示用户。 */
private fun launchShizukuManager(ctx: Context) {
    val intent = Intent().apply {
        component = ComponentName(SHIZUKU_MANAGER_PKG, SHIZUKU_HOME_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!tryStartActivity(ctx, intent)) {
        toast(ctx, "Shizuku Manager 未安装, 请先从 shizuku.com 下载安装")
    }
}

/** 跳转 Shizuku Manager 无线 ADB 配对教程页。未安装时提示。 */
private fun launchWirelessAdbPairing(ctx: Context) {
    val intent = Intent().apply {
        component = ComponentName(SHIZUKU_MANAGER_PKG, SHIZUKU_PAIR_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!tryStartActivity(ctx, intent)) {
        toast(ctx, "Shizuku Manager 未安装, 无法打开无线 ADB 配对")
    }
}

/**
 * 启动 Activity 前先用 packageManager 检查 manager APK 是否安装,
 * 避免 ActivityNotFoundException 直接抛出。
 * 返回 true 表示成功启动。
 */
private fun tryStartActivity(ctx: Context, intent: Intent): Boolean {
    return try {
        ctx.packageManager.getPackageInfo(SHIZUKU_MANAGER_PKG, 0)
        ctx.startActivity(intent)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

// endregion
