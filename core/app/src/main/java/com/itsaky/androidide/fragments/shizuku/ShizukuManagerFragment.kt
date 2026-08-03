/*
 *  ZeroStudio IDE - Shizuku 管理器
 *
 *  在 IDE 内提供一个轻量的 Shizuku 管理界面:
 *    1. 显示 Shizuku 运行 / 授权 / 版本状态 (复用 DefaultShizukuProbe)
 *    2. 一键发起无线配对 (内置 ShizukuPairingService, Android 11+)
 *    3. 一键启动 Shizuku Server (内置 ShizukuPairingService.startServer)
 *    4. 一键跳转 Shizuku Manager 启动 server / 管理
 *    5. 一键发起授权请求 (Shizuku.requestPermission)
 *
 *  core/app 只依赖 projects.modules.shizuku.api (客户端 SDK),
 *  manager APK 是独立进程, 通过 Intent 显式 component 跳转,
 *  未安装时给出明确提示。
 */

package com.itsaky.androidide.fragments.shizuku

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.itsaky.androidide.debugger.connection.shizuku.DefaultShizukuProbe
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuStatus
import com.itsaky.androidide.debugger.shizuku.ShizukuPairingService
import com.itsaky.androidide.debugger.shizuku.ShizukuPairingService.Companion.ACTION_PAIRING_STATE
import com.itsaky.androidide.debugger.shizuku.ShizukuPairingService.Companion.EXTRA_MESSAGE
import com.itsaky.androidide.debugger.shizuku.ShizukuPairingService.Companion.EXTRA_STATE
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
    // tick 改变会触发重新 probe (首次进入 / 手动刷新 / binder 状态变化 / 授权回调 / 配对成功)
    var tick by remember { mutableIntStateOf(0) }

    // 配对流程状态 (由 ShizukuPairingService 通过 LocalBroadcast 回传)
    var pairingState by remember { mutableStateOf<String?>(null) }
    var pairingMessage by remember { mutableStateOf<String?>(null) }

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

    // 监听 ShizukuPairingService 配对状态广播 (SEARCHING / FOUND / PAIRING /
    // CONNECTING / STARTING / SUCCESS / FAILED), SUCCESS 时触发状态刷新
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_PAIRING_STATE) {
                    pairingState = intent.getStringExtra(EXTRA_STATE)
                    pairingMessage = intent.getStringExtra(EXTRA_MESSAGE)
                    if (pairingState == "SUCCESS") tick++  // 触发状态刷新
                }
            }
        }
        val filter = IntentFilter(ACTION_PAIRING_STATE)
        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
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

        if (pairingState != null) {
            Spacer(Modifier.height(12.dp))
            PairingStateCard(
                state = pairingState,
                message = pairingMessage,
            )
        }

        Spacer(Modifier.height(16.dp))
        ActionButtons(
            status = status,
            onStartPairing = {
                runCatching { ShizukuPairingService.start(ctx) }
                    .onFailure { toast(ctx, "启动配对失败: ${it.message}") }
            },
            onStartServer = {
                runCatching { ShizukuPairingService.startServer(ctx) }
                    .onFailure { toast(ctx, "启动 Server 失败: ${it.message}") }
            },
            onLaunchManager = { launchShizukuManager(ctx) },
            onAuthorize = {
                runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
                    .onFailure { toast(ctx, "授权请求失败: ${it.message}") }
            },
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

// region 配对状态卡片

/**
 * 配对状态卡片: 显示 [ShizukuPairingService] 配对流程的实时状态。
 *
 * 状态来自 service 通过 [ACTION_PAIRING_STATE] 广播的 [EXTRA_STATE]:
 * SEARCHING / FOUND / PAIRING / CONNECTING / STARTING / SUCCESS / FAILED。
 */
@Composable
private fun PairingStateCard(state: String?, message: String?) {
    if (state == null) return

    val icon: ImageVector
    val tint: Color
    val text: String
    val showProgress: Boolean
    when (state) {
        "SEARCHING" -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFF64B5F6)
            text = "正在搜索配对服务..."
            showProgress = true
        }
        "FOUND" -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFFFFB74D)
            text = "已发现配对服务,请在通知栏输入配对码"
            showProgress = false
        }
        "PAIRING" -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFF64B5F6)
            text = "正在配对..."
            showProgress = true
        }
        "CONNECTING" -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFF64B5F6)
            text = "正在连接 ADB..."
            showProgress = true
        }
        "STARTING" -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFF64B5F6)
            text = "正在启动 Shizuku Server..."
            showProgress = true
        }
        "SUCCESS" -> {
            icon = Icons.Outlined.CheckCircle
            tint = Color(0xFF66BB6A)
            text = "Shizuku 配对并启动成功!"
            showProgress = false
        }
        "FAILED" -> {
            icon = Icons.Outlined.Error
            tint = Color(0xFFEF5350)
            text = if (message.isNullOrEmpty()) "配对失败" else "配对失败: $message"
            showProgress = false
        }
        else -> {
            icon = Icons.Outlined.Info
            tint = Color(0xFFCCCCCC)
            text = message ?: state
            showProgress = false
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
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (showProgress) {
                CircularProgressIndicator(
                    color = tint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// endregion

// region 操作按钮

@Composable
private fun ActionButtons(
    status: ShizukuStatus?,
    onStartPairing: () -> Unit,
    onStartServer: () -> Unit,
    onLaunchManager: () -> Unit,
    onAuthorize: () -> Unit,
    onRefresh: () -> Unit,
) {
    val canAuthorize = status?.isRunning == true && !status.isGranted
    // 仅当 Shizuku 未运行时允许尝试启动 (此时 ADB 可能已连接)
    val canStartServer = status != null && !status.isRunning
    val canWirelessPair = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. 开始无线配对 (内置 ShizukuPairingService, Android 11+)
        Button(
            onClick = onStartPairing,
            enabled = canWirelessPair,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (canWirelessPair) "开始无线配对 (推荐)" else "需要 Android 11+")
        }
        Text(
            text = "请先在系统设置 > 开发者选项 > 无线调试 中打开无线调试,然后点击「使用配对码配对设备」",
            color = Color(0xFF999999),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 2. 启动 Shizuku Server (内置 startServer, 走 ADB start 脚本)
        OutlinedButton(
            onClick = onStartServer,
            enabled = canStartServer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("启动 Shizuku Server")
        }
        Text(
            text = "需要已安装 Shizuku Manager APK 以提供 start 脚本, 且设备已通过 USB / 无线 ADB 连接",
            color = Color(0xFF999999),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 3. 打开 Shizuku Manager (手动启动 / 管理)
        OutlinedButton(
            onClick = onLaunchManager,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("打开 Shizuku Manager")
        }

        // 4. 授权 Shizuku 访问
        OutlinedButton(
            onClick = onAuthorize,
            enabled = canAuthorize,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("授权 Shizuku 访问")
        }

        // 5. 刷新状态
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
            "1. 在系统设置 > 开发者选项 中开启「无线调试」(Android 11+)",
            "2. 在无线调试页面点击「使用配对码配对设备」",
            "3. 点击上方「开始无线配对」,在通知栏输入 6 位配对码",
            "4. 配对成功后 Shizuku Server 将自动启动",
            "5. 回到本页点击「授权」给 IDE 访问权限",
            "6. 在 Debugger 设置中选择 Shizuku 连接方式",
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
