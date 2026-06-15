/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.onboarding

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.appintro.SlidePolicy
import com.google.gson.Gson
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.OnboardingActivity
import com.itsaky.androidide.app.configuration.IDEBuildConfigProvider
import com.itsaky.androidide.repository.sdkmanager.models.SdkManifest
import com.itsaky.androidide.repository.sdkmanager.models.SdkTreeNode
import com.itsaky.androidide.repository.sdkmanager.services.SdkInstallerManager
import com.itsaky.androidide.utils.ConnectionInfo
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.getConnectionInfo
import com.termux.app.TermuxInstaller
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全新精简版 SDK 与环境安装 Fragment。 支持在线下载解压配置，以及本地离线包 (sdkresources.tar.gz) 自动布署。
 *
 * @author android_zero
 */
class OdSdkToolInstallFragment : Fragment(), SlidePolicy {

  private var backgroundDataRestrictionReceiver: BroadcastReceiver? = null
  private var networkStateChangeCallback: ConnectivityManager.NetworkCallback? = null
  private val netStateFlow = MutableStateFlow(ConnectionInfo.UNKNOWN)

  private val setupViewModel: OdSdkSetupViewModel by viewModels()

  companion object {
    @JvmStatic
    fun newInstance(context: Context): OdSdkToolInstallFragment {
      return OdSdkToolInstallFragment()
    }
  }

  fun isAutoInstall(): Boolean = false

  fun buildIdeSetupArguments(): Array<String> = emptyArray()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) { SetupConfigurationScreen() }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    updateConnectionStatus()
    monitorNetworkState()
  }

  override fun onStop() {
    super.onStop()
    removeNetworkMonitors()
  }

  private fun updateConnectionStatus(networkCapabilities: NetworkCapabilities? = null) {
    netStateFlow.value = getConnectionInfo(requireContext(), networkCapabilities)
  }

  private fun monitorNetworkState() {
    val connectivityManager = requireContext().getSystemService<ConnectivityManager>() ?: return
    networkStateChangeCallback?.also { connectivityManager.registerDefaultNetworkCallback(it) }

    networkStateChangeCallback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              networkCapabilities: NetworkCapabilities,
          ) {
            updateConnectionStatus(networkCapabilities)
          }

          override fun onLost(network: Network) {
            netStateFlow.value = ConnectionInfo.UNKNOWN
          }
        }

    val networkRequest =
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
    connectivityManager.registerNetworkCallback(networkRequest, networkStateChangeCallback!!)

    backgroundDataRestrictionReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context?, intent: Intent?) {
            updateConnectionStatus()
          }
        }

    requireContext()
        .registerReceiver(
            backgroundDataRestrictionReceiver!!,
            IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
        )
  }

  private fun removeNetworkMonitors() {
    networkStateChangeCallback?.also {
      requireContext().getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(it)
      networkStateChangeCallback = null
    }
    backgroundDataRestrictionReceiver?.also {
      requireContext().unregisterReceiver(it)
      backgroundDataRestrictionReceiver = null
    }
  }

  override val isPolicyRespected: Boolean
    get() = getConnectionInfo(requireContext()).isConnected

  override fun onUserIllegallyRequestedNextPage() {
    requireActivity().flashError(R.string.msg_no_internet)
  }

  @Composable
  private fun SetupConfigurationScreen() {
    val netState by netStateFlow.collectAsState()
    val isLoading by setupViewModel.isLoading.collectAsState()
    val treeNodes by setupViewModel.treeNodes.collectAsState()
    val hasPendingChanges by setupViewModel.hasPendingChanges.collectAsState()

    var installGit by remember { mutableStateOf(true) }
    var installSsh by remember { mutableStateOf(true) }
    var applyNdkFix by remember { mutableStateOf(true) }
    var applyCmakePatch by remember { mutableStateOf(true) }
    var installOffline by remember { mutableStateOf(false) }

    var useGithubMirror by remember { mutableStateOf(false) }
    var githubMirrorUrl by remember { mutableStateOf("https://gh.llkk.cc/") }

    var showActionDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var selectedJdk by remember { mutableStateOf("17") }
    var jdkExpanded by remember { mutableStateOf(false) }

    val currentAbi = IDEBuildConfigProvider.getInstance().cpuAbiName
    val context = LocalContext.current

    fun getValidMirror(): String {
      if (!useGithubMirror) return ""
      val t = githubMirrorUrl.trim()
      if (t.isBlank()) return ""
      if (!t.startsWith("http://") && !t.startsWith("https://")) return ""
      if (!t.endsWith("/")) return ""
      return t
    }

    // 主题色板: 暗/亮模式 + 自定义品牌色与渐变
    val isDark = isSystemInDarkTheme()
    val brand = remember(isDark) { OdSdkSetupColors.of(isDark) }

    CompositionLocalProvider(LocalOdSdkColors provides brand) {
      Column(
          modifier = Modifier.fillMaxSize().background(brand.canvas)
      ) {
        // 1. 紧凑头部: 标题 + 副标题 + 网络状态 + ABI 徽章
        OdSdkSetupHeader(
            abiName = currentAbi,
            netState = netState,
            onOpenNetworkSettings = {
              context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            },
        )

        // 2. 主区域 - SDK 树 (无外壳卡, 直接融入背景, 顶部加 3D 渐变描边)
        Box(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
        ) {
          OdSdkTreeSection(
              isLoading = isLoading,
              treeNodes = treeNodes,
              onNodeCheckChange = { node, newState ->
                // 强制安装的项 (android-sdk, cmdline-tools) 不可切换
                if (node.componentType == "android-sdk" ||
                    node.componentType == "cmdline-tools") {
                  return@OdSdkTreeSection
                }
                val parent = node.parent
                // 单选组: build-tools / platform-tools (同组内只允许一个被勾选)
                val isSingleSelectGroup =
                    parent != null &&
                        parent.children
                            .firstOrNull()
                            ?.componentType in
                            setOf("build-tools", "platform-tools")
                if (isSingleSelectGroup && newState == ToggleableState.On && parent != null) {
                  parent.children.forEach { sibling ->
                    if (sibling !== node) sibling.checkedState = ToggleableState.Off
                  }
                }
                node.checkedState = newState
                node.updateParentState()
                setupViewModel.triggerPendingChangesCheck()
              },
              onGroupToggle = { group -> group.isExpanded = !group.isExpanded },
          )
        }

        // 3. 附加配置 (紧凑横向布局, 高度受控)
        OdAdditionalConfigsBar(
            selectedJdk = selectedJdk,
            onJdkChange = { selectedJdk = it },
            jdkExpanded = jdkExpanded,
            onJdkExpandedChange = { jdkExpanded = it },
            installGit = installGit,
            onInstallGitChange = { installGit = it },
            installSsh = installSsh,
            onInstallSshChange = { installSsh = it },
            applyNdkFix = applyNdkFix,
            onApplyNdkFixChange = { applyNdkFix = it },
            applyCmakePatch = applyCmakePatch,
            onApplyCmakePatchChange = { applyCmakePatch = it },
            useGithubMirror = useGithubMirror,
            onUseGithubMirrorChange = { useGithubMirror = it },
            installOffline = installOffline,
            onInstallOfflineChange = { installOffline = it },
            githubMirrorUrl = githubMirrorUrl,
            onGithubMirrorUrlChange = { githubMirrorUrl = it },
            onReload = { setupViewModel.loadData(getValidMirror()) },
        )

        // 4. 底部 CTA
        OdStickyActionBar(
            enabled = installOffline || hasPendingChanges || installGit || installSsh,
            installOffline = installOffline,
            onClick = {
              val activity = requireActivity()
              TermuxInstaller.setupBootstrapIfNeeded(activity) {
                activity.runOnUiThread {
                  if (installOffline) showOfflineDialog = true else showActionDialog = true
                }
              }
            },
        )
      }
    }

    if (showActionDialog) {
      val toInstall = setupViewModel.getInstallTasks()
      ActionConfirmAndRunDialog(
          toInstall = toInstall,
          installGit = installGit,
          installSsh = installSsh,
          applyNdkFix = applyNdkFix,
          applyCmakePatch = applyCmakePatch,
          jdkVersion = selectedJdk,
          githubMirror = getValidMirror(),
          onDismiss = {
            showActionDialog = false
            setupViewModel.loadData(getValidMirror())
          },
          onSuccess = { (requireActivity() as? OnboardingActivity)?.onSetupCompleted() },
      )
    }

    if (showOfflineDialog) {
      OfflineConfirmAndRunDialog(
          onDismiss = { showOfflineDialog = false },
          onSuccess = { (requireActivity() as? OnboardingActivity)?.onSetupCompleted() },
      )
    }
  }

  // ---------------- 主题色板 ----------------

  /**
   * 引导页 SDK 配置专属调色板。 在 Material 3 基础色之上, 提供树形容器 / CTA / 网络状态等场景
   * 使用的 3D 渐变与高对比辅助色, 确保暗/亮模式下均有正确的视觉冲击。
   */
  private data class OdSdkSetupColors(
      val isDark: Boolean,
      val canvas: Color,
      val surface: Color,
      val surfaceContainer: Color,
      val surfaceContainerHigh: Color,
      val surfaceContainerHighest: Color,
      val onSurface: Color,
      val onSurfaceVariant: Color,
      val onSurfaceMuted: Color,
      val outline: Color,
      val outlineVariant: Color,
      val primary: Color,
      val onPrimary: Color,
      val primaryContainer: Color,
      val onPrimaryContainer: Color,
      val secondary: Color,
      val tertiary: Color,
      val error: Color,
      val warning: Color,
      val success: Color,
      val treeContainerGradient: Brush,
      val treeAccentStart: Color,
      val treeAccentEnd: Color,
      val ctaGradient: Brush,
      val networkOkBg: Color,
      val networkOkFg: Color,
      val networkWarnBg: Color,
      val networkWarnFg: Color,
      val radioDot: Color,
      val forcedBadgeBg: Color,
      val forcedBadgeFg: Color,
  ) {
    companion object {
      fun of(isDark: Boolean): OdSdkSetupColors = if (isDark) dark() else light()

      private fun light() =
          OdSdkSetupColors(
              isDark = false,
              canvas = Color(0xFFFAF8FF),
              surface = Color(0xFFFFFFFF),
              surfaceContainer = Color(0xFFF3EEFF),
              surfaceContainerHigh = Color(0xFFECE6FF),
              surfaceContainerHighest = Color(0xFFE4DCFF),
              onSurface = Color(0xFF1C1B1F),
              onSurfaceVariant = Color(0xFF49454F),
              onSurfaceMuted = Color(0xFF7A7785),
              outline = Color(0xFFCAC4D0),
              outlineVariant = Color(0xFFE7E0EC),
              primary = Color(0xFF6750A4),
              onPrimary = Color(0xFFFFFFFF),
              primaryContainer = Color(0xFFEADDFF),
              onPrimaryContainer = Color(0xFF21005D),
              secondary = Color(0xFF625B71),
              tertiary = Color(0xFF7D5260),
              error = Color(0xFFB3261E),
              warning = Color(0xFFE65100),
              success = Color(0xFF1B5E20),
              treeContainerGradient =
                  Brush.verticalGradient(
                      0.00f to Color(0xFFF7F2FF),
                      0.50f to Color(0xFFEFE8FF),
                      1.00f to Color(0xFFE4D9FF),
                  ),
              treeAccentStart = Color(0xFFB388FF),
              treeAccentEnd = Color(0xFF7C4DFF),
              ctaGradient =
                  Brush.horizontalGradient(
                      listOf(Color(0xFF7C4DFF), Color(0xFF536DFE))
                  ),
              networkOkBg = Color(0xFFE8F5E9),
              networkOkFg = Color(0xFF2E7D32),
              networkWarnBg = Color(0xFFFFF3E0),
              networkWarnFg = Color(0xFFE65100),
              radioDot = Color(0xFF7C4DFF),
              forcedBadgeBg = Color(0xFFE0E0E0),
              forcedBadgeFg = Color(0xFF424242),
          )

      private fun dark() =
          OdSdkSetupColors(
              isDark = true,
              canvas = Color(0xFF0F0A1E),
              surface = Color(0xFF1A1230),
              surfaceContainer = Color(0xFF1F1640),
              surfaceContainerHigh = Color(0xFF261C4A),
              surfaceContainerHighest = Color(0xFF2D2155),
              onSurface = Color(0xFFF5F0FF),
              onSurfaceVariant = Color(0xFFCDC2E0),
              onSurfaceMuted = Color(0xFF8E84A8),
              outline = Color(0xFF49454F),
              outlineVariant = Color(0xFF2D2640),
              primary = Color(0xFFD0BCFF),
              onPrimary = Color(0xFF381E72),
              primaryContainer = Color(0xFF4F378B),
              onPrimaryContainer = Color(0xFFEADDFF),
              secondary = Color(0xFFCCC2DC),
              tertiary = Color(0xFFEFB8C8),
              error = Color(0xFFF2B8B5),
              warning = Color(0xFFFFB74D),
              success = Color(0xFF81C784),
              treeContainerGradient =
                  Brush.verticalGradient(
                      0.00f to Color(0xFF1A1240),
                      0.50f to Color(0xFF1F1648),
                      1.00f to Color(0xFF12092E),
                  ),
              treeAccentStart = Color(0xFFB388FF),
              treeAccentEnd = Color(0xFF82B1FF),
              ctaGradient =
                  Brush.horizontalGradient(
                      listOf(Color(0xFF7C4DFF), Color(0xFF448AFF))
                  ),
              networkOkBg = Color(0xFF1B3A1F),
              networkOkFg = Color(0xFF81C784),
              networkWarnBg = Color(0xFF3A2A0E),
              networkWarnFg = Color(0xFFFFB74D),
              radioDot = Color(0xFFB388FF),
              forcedBadgeBg = Color(0xFF3A3050),
              forcedBadgeFg = Color(0xFFCDC2E0),
          )
    }
  }

  private val LocalOdSdkColors =
      staticCompositionLocalOf<OdSdkSetupColors> {
        error("OdSdkSetupColors not provided")
      }

  // ---------------- 头部 ----------------

  @Composable
  private fun OdSdkSetupHeader(
      abiName: String,
      netState: ConnectionInfo,
      onOpenNetworkSettings: () -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "header-alpha")
    val offsetY by animateFloatAsState(if (visible) 0f else -10f, tween(500), label = "header-y")

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 4.dp)
                .graphicsLayer {
                  this.alpha = alpha
                  translationY = offsetY
                }
    ) {
      // 顶部小徽标 + 章节名
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(26.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
          Icon(
              Icons.Filled.Download,
              contentDescription = null,
              tint = colors.onPrimaryContainer,
              modifier = Modifier.size(14.dp),
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "STEP  •  ENVIRONMENT",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceMuted,
            fontWeight = FontWeight.Medium,
        )
      }

      Spacer(Modifier.height(8.dp))

      // 主标题
      Text(
          text = "SDK 安装与配置",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = colors.onSurface,
      )

      Spacer(Modifier.height(2.dp))

      // 副标题 + ABI 徽章 (单行, 比例合理)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "为 IDE 安装开发工具以保证其正常运行",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        OdAbiBadge(abiName = abiName)
      }

      Spacer(Modifier.height(8.dp))

      // 网络状态 - 紧凑胶囊
      OdNetworkStatusPill(netState = netState, onClick = onOpenNetworkSettings)
    }
  }

  @Composable
  private fun OdAbiBadge(abiName: String) {
    val colors = LocalOdSdkColors.current
    Surface(
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
      Text(
          text = "ABI  $abiName",
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      )
    }
  }

  private enum class OdNetSeverity { OK, WARN, ERROR }

  @Composable
  private fun OdNetworkStatusPill(netState: ConnectionInfo, onClick: () -> Unit) {
    val colors = LocalOdSdkColors.current
    val (label, severity) =
        when {
          !netState.isConnected || netState === ConnectionInfo.UNKNOWN ->
              stringResource(R.string.msg_no_internet) to OdNetSeverity.ERROR
          netState.isCellularTransport ->
              stringResource(R.string.msg_connected_to_cellular) to OdNetSeverity.WARN
          netState.isMeteredConnection ->
              stringResource(R.string.msg_connected_to_metered_connection) to
                  OdNetSeverity.WARN
          else -> "网络已连接" to OdNetSeverity.OK
        }
    val bg: Color
    val fg: Color
    when (severity) {
      OdNetSeverity.OK -> {
        bg = colors.networkOkBg
        fg = colors.networkOkFg
      }
      OdNetSeverity.WARN -> {
        bg = colors.networkWarnBg
        fg = colors.networkWarnFg
      }
      OdNetSeverity.ERROR -> {
        bg = colors.error.copy(alpha = 0.12f)
        fg = colors.error
      }
    }
    val clickable = severity != OdNetSeverity.OK

    // 状态点呼吸动画
    val infinite = rememberInfiniteTransition(label = "net-pulse")
    val dotAlpha by
        infinite.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "net-dot-alpha",
        )

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50),
        modifier =
            Modifier.clickable(enabled = clickable) { onClick() }
                .graphicsLayer { shadowElevation = if (severity == OdNetSeverity.ERROR) 6f else 0f },
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Box(
            modifier =
                Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector =
                if (severity == OdNetSeverity.OK) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
      }
    }
  }

  // ---------------- SDK 树 (主区域) ----------------

  @Composable
  private fun OdSdkTreeSection(
      isLoading: Boolean,
      treeNodes: List<SdkTreeNode>,
      onNodeCheckChange: (SdkTreeNode, ToggleableState) -> Unit,
      onGroupToggle: (SdkTreeNode) -> Unit,
  ) {
    val colors = LocalOdSdkColors.current

    // 3D 渐变外壳 + 顶部高光描边
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.treeContainerGradient)
                .border(
                    width = 1.dp,
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                colors.treeAccentStart.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        ),
                    shape = RoundedCornerShape(20.dp),
                )
    ) {
      if (isLoading) {
        OdSdkTreeLoading()
      } else if (treeNodes.isEmpty()) {
        OdSdkTreeEmpty()
      } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          itemsIndexed(treeNodes, key = { _, it -> it.id }) { index, node ->
            OdSdkTopLevelEntry(
                node = node,
                index = index,
                onNodeCheckChange = onNodeCheckChange,
                onGroupToggle = onGroupToggle,
            )
          }
        }
      }
    }
  }

  @Composable
  private fun OdSdkTreeLoading() {
    val colors = LocalOdSdkColors.current
    val infinite = rememberInfiniteTransition(label = "loading")
    val angle by
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
            label = "loading-angle",
        )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.size(46.dp)
                    .graphicsLayer { rotationZ = angle }
                    .border(
                        width = 3.dp,
                        color = colors.primary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
          Box(
              modifier =
                  Modifier.size(20.dp)
                      .clip(CircleShape)
                      .background(colors.primary.copy(alpha = 0.85f))
          )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "正在获取 SDK 列表...",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }

  @Composable
  private fun OdSdkTreeEmpty() {
    val colors = LocalOdSdkColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Build,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "暂无可用组件",
            color = colors.onSurfaceMuted,
            style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }

  @Composable
  private fun OdSdkTopLevelEntry(
      node: SdkTreeNode,
      index: Int,
      onNodeCheckChange: (SdkTreeNode, ToggleableState) -> Unit,
      onGroupToggle: (SdkTreeNode) -> Unit,
  ) {
    // 错落入场动画
    var visible by remember(node.id) { mutableStateOf(false) }
    LaunchedEffect(node.id) {
      kotlinx.coroutines.delay((40L * index).coerceAtMost(360L))
      visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + expandVertically(tween(320)),
    ) {
      if (node.isGroup) {
        OdSdkGroupCard(
            group = node,
            onGroupToggle = onGroupToggle,
            onNodeCheckChange = onNodeCheckChange,
        )
      } else {
        OdSdkStandaloneRow(node = node, onNodeCheckChange = onNodeCheckChange)
      }
    }
  }

  @Composable
  private fun OdSdkGroupCard(
      group: SdkTreeNode,
      onGroupToggle: (SdkTreeNode) -> Unit,
      onNodeCheckChange: (SdkTreeNode, ToggleableState) -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    val children = group.children
    val isExpanded = group.isExpanded
    val selectedCount = children.count { it.checkedState == ToggleableState.On }
    val totalCount = children.size
    val isSingleSelect = children.firstOrNull()?.componentType in
        setOf("build-tools", "platform-tools")
    val groupIcon = odSdkGroupIcon(group)
    val groupTint = odSdkGroupTint(group, colors)

    // 展开 / 折叠 - 箭头旋转
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (isExpanded) 90f else 0f,
            animationSpec = tween(220),
            label = "chevron",
        )

    // 整个组卡片
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = if (colors.isDark) 0.5f else 0.65f))
    ) {
      // 父节点头部
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onGroupToggle(group) }
                  .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier =
                Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            groupIcon,
            contentDescription = null,
            tint = groupTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            group.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        OdSdkGroupStateBadge(group = group, selectedCount = selectedCount, tint = groupTint)
      }

      // 子节点 - 向右缩进, 形成视觉层次
      AnimatedVisibility(
          visible = isExpanded && children.isNotEmpty(),
          enter = expandVertically(tween(220)) + fadeIn(tween(180)),
          exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
      ) {
        Column(
            modifier =
                Modifier.padding(start = 26.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        colors.surfaceContainer.copy(alpha = if (colors.isDark) 0.7f else 0.5f)
                    )
                    .border(
                        width = 1.dp,
                        color = colors.outlineVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(vertical = 2.dp),
        ) {
          children.forEachIndexed { i, child ->
            OdSdkChildRow(
                child = child,
                isSingleSelect = isSingleSelect,
                onNodeCheckChange = onNodeCheckChange,
            )
            if (i < children.lastIndex) {
              Box(
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(1.dp)
                          .background(colors.outlineVariant.copy(alpha = 0.18f))
                          .padding(start = 12.dp)
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun OdSdkGroupStateBadge(
      group: SdkTreeNode,
      selectedCount: Int,
      tint: Color,
  ) {
    val colors = LocalOdSdkColors.current
    val total = group.children.size
    val (text, show) =
        when (group.checkedState) {
          ToggleableState.On -> "已选 $selectedCount" to true
          ToggleableState.Indeterminate -> "$selectedCount / $total" to true
          ToggleableState.Off -> "" to false
        }
    if (!show) return
    Surface(
        color = tint.copy(alpha = 0.18f),
        contentColor = tint,
        shape = RoundedCornerShape(50),
    ) {
      Text(
          text,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
  }

  @Composable
  private fun OdSdkChildRow(
      child: SdkTreeNode,
      isSingleSelect: Boolean,
      onNodeCheckChange: (SdkTreeNode, ToggleableState) -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    val isChecked = child.checkedState == ToggleableState.On
    val isForced =
        child.componentType == "android-sdk" || child.componentType == "cmdline-tools"

    val bg by
        animateColorAsState(
            targetValue =
                if (isChecked) colors.primary.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                else Color.Transparent,
            animationSpec = tween(180),
            label = "child-bg",
        )

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(bg)
                .clickable(enabled = !isForced) {
                  val newState = if (isChecked) ToggleableState.Off else ToggleableState.On
                  onNodeCheckChange(child, newState)
                }
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // 左侧 3D 凸起引导线
      Box(
          modifier =
              Modifier.width(3.dp)
                  .height(22.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(
                      if (isChecked) colors.primary
                      else colors.outlineVariant.copy(alpha = 0.6f)
                  )
      )
      Spacer(Modifier.width(8.dp))

      if (isSingleSelect) {
        // 单选: radio 圆点
        OdRadioDot(
            selected = isChecked,
            enabled = !isForced,
            onClick = { onNodeCheckChange(child, ToggleableState.On) },
        )
      } else {
        // 多选: checkbox
        OdCheckBox(
            checked = isChecked,
            enabled = !isForced,
            onCheckedChange = { newChecked ->
              onNodeCheckChange(child, if (newChecked) ToggleableState.On else ToggleableState.Off)
            },
        )
      }
      Spacer(Modifier.width(8.dp))

      Text(
          child.name,
          fontSize = 13.sp,
          fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
          color = if (isForced) colors.onSurfaceVariant else colors.onSurface,
          modifier = Modifier.weight(1f),
      )

      if (isForced) {
        OdForcedBadge()
      } else if (isChecked) {
        OdSelectedDot()
      }
    }
  }

  @Composable
  private fun OdSdkStandaloneRow(
      node: SdkTreeNode,
      onNodeCheckChange: (SdkTreeNode, ToggleableState) -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    val isChecked = node.checkedState == ToggleableState.On
    val bg by
        animateColorAsState(
            targetValue =
                if (isChecked) colors.primary.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                else Color.Transparent,
            animationSpec = tween(180),
            label = "standalone-bg",
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    colors.surface.copy(alpha = if (colors.isDark) 0.5f else 0.65f)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Filled.Build,
          contentDescription = null,
          tint = colors.primary,
          modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(
          node.name,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = colors.onSurface,
          modifier = Modifier.weight(1f),
      )
      OdForcedBadge()
    }
  }

  @Composable
  private fun OdRadioDot(selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOdSdkColors.current
    val ringColor by
        animateColorAsState(
            targetValue = if (selected) colors.radioDot else colors.onSurfaceMuted,
            animationSpec = tween(180),
            label = "radio-ring",
        )
    val scale by
        animateFloatAsState(if (selected) 1f else 0.85f, tween(180), label = "radio-scale")
    Box(
        modifier =
            Modifier.size(20.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(width = 2.dp, color = ringColor, shape = CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
      if (selected) {
        Box(
            modifier =
                Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(colors.radioDot)
                    .graphicsLayer {
                      shadowElevation = 4f
                      shape = CircleShape
                      clip = false
                    }
        )
      }
    }
  }

  @Composable
  private fun OdCheckBox(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalOdSdkColors.current
    val bg by
        animateColorAsState(
            targetValue = if (checked) colors.primary else Color.Transparent,
            animationSpec = tween(180),
            label = "cb-bg",
        )
    val border by
        animateColorAsState(
            targetValue = if (checked) colors.primary else colors.onSurfaceMuted,
            animationSpec = tween(180),
            label = "cb-border",
        )
    val scale by animateFloatAsState(if (checked) 1.05f else 1f, tween(180), label = "cb-scale")
    Box(
        modifier =
            Modifier.size(20.dp)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(width = 2.dp, color = border, shape = RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.Center,
    ) {
      if (checked) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier =
                Modifier.size(14.dp)
                    .graphicsLayer { shadowElevation = 2f; clip = false },
        )
      }
    }
  }

  @Composable
  private fun OdForcedBadge() {
    val colors = LocalOdSdkColors.current
    Surface(
        color = colors.forcedBadgeBg,
        contentColor = colors.forcedBadgeFg,
        shape = RoundedCornerShape(50),
    ) {
      Text(
          "REQUIRED",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
      )
    }
  }

  @Composable
  private fun OdSelectedDot() {
    val colors = LocalOdSdkColors.current
    val pulse by
        rememberInfiniteTransition(label = "sel")
            .animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "sel-pulse",
            )
    Box(
        modifier =
            Modifier.size(10.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = pulse))
                .graphicsLayer {
                  shadowElevation = 4f
                  shape = CircleShape
                  clip = false
                }
    )
  }

  private fun odSdkGroupIcon(node: SdkTreeNode) =
      when (node.componentType) {
        "platform-tools" -> Icons.Filled.Speed
        "build-tools" -> Icons.Filled.Build
        "ndk" -> Icons.Filled.Memory
        "cmake" -> Icons.Filled.Code
        else ->
            when {
              node.name.contains("Platform", ignoreCase = true) -> Icons.Filled.Storage
              else -> Icons.Filled.Build
            }
      }

  private fun odSdkGroupTint(node: SdkTreeNode, colors: OdSdkSetupColors): Color =
      when (node.componentType) {
        "platform-tools" -> colors.tertiary
        "build-tools" -> colors.primary
        "ndk" -> colors.secondary
        "cmake" -> colors.treeAccentStart
        else -> colors.primary
      }

  // ---------------- 附加配置 ----------------

  @Composable
  private fun OdAdditionalConfigsBar(
      selectedJdk: String,
      onJdkChange: (String) -> Unit,
      jdkExpanded: Boolean,
      onJdkExpandedChange: (Boolean) -> Unit,
      installGit: Boolean,
      onInstallGitChange: (Boolean) -> Unit,
      installSsh: Boolean,
      onInstallSshChange: (Boolean) -> Unit,
      applyNdkFix: Boolean,
      onApplyNdkFixChange: (Boolean) -> Unit,
      applyCmakePatch: Boolean,
      onApplyCmakePatchChange: (Boolean) -> Unit,
      useGithubMirror: Boolean,
      onUseGithubMirrorChange: (Boolean) -> Unit,
      installOffline: Boolean,
      onInstallOfflineChange: (Boolean) -> Unit,
      githubMirrorUrl: String,
      onGithubMirrorUrlChange: (String) -> Unit,
      onReload: () -> Unit,
  ) {
    val colors = LocalOdSdkColors.current

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
      // 分区标题 + JDK 切换 (右对齐, 紧凑)
      Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            "附加配置",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            letterSpacing = 0.3.sp,
        )
        Spacer(Modifier.weight(1f))
        JdkSwitcher(
            selectedJdk = selectedJdk,
            onJdkChange = onJdkChange,
            expanded = jdkExpanded,
            onExpandedChange = onJdkExpandedChange,
        )
      }

      // 第一行: Git / SSH / NDK Fix / CMake Patch / 镜像 / 离线
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OdConfigChip(
            label = "Git",
            icon = Icons.Filled.Code,
            checked = installGit,
            onCheckedChange = onInstallGitChange,
            modifier = Modifier.weight(1f),
        )
        OdConfigChip(
            label = "SSH",
            icon = Icons.Filled.Key,
            checked = installSsh,
            onCheckedChange = onInstallSshChange,
            modifier = Modifier.weight(1f),
        )
        OdConfigChip(
            label = "NDK Fix",
            icon = Icons.Filled.Build,
            checked = applyNdkFix,
            onCheckedChange = onApplyNdkFixChange,
            modifier = Modifier.weight(1f),
        )
        OdConfigChip(
            label = "CMake",
            icon = Icons.Filled.Code,
            checked = applyCmakePatch,
            onCheckedChange = onApplyCmakePatchChange,
            modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(6.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OdConfigChip(
            label = "GitHub 镜像",
            icon = Icons.Outlined.Wifi,
            checked = useGithubMirror,
            onCheckedChange = onUseGithubMirrorChange,
            modifier = Modifier.weight(1.4f),
        )
        OdConfigChip(
            label = "离线安装",
            icon = Icons.Filled.Lock,
            checked = installOffline,
            onCheckedChange = onInstallOfflineChange,
            highlight = true,
            modifier = Modifier.weight(1f),
        )
      }

      // 镜像 URL 行 (仅启用时显示)
      AnimatedVisibility(visible = useGithubMirror && !installOffline) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
              value = githubMirrorUrl,
              onValueChange = onGithubMirrorUrlChange,
              modifier = Modifier.weight(1f),
              textStyle =
                  LocalTextStyle.current.copy(fontSize = 12.sp, color = colors.onSurface),
              singleLine = true,
              placeholder = {
                Text("https://gh.llkk.cc/", fontSize = 12.sp, color = colors.onSurfaceMuted)
              },
              shape = RoundedCornerShape(10.dp),
          )
          Spacer(Modifier.width(8.dp))
          // 重载按钮 - 3D 圆形
          Box(
              modifier =
                  Modifier.size(40.dp)
                      .clip(CircleShape)
                      .background(colors.primary)
                      .clickable { onReload() }
                      .graphicsLayer { shadowElevation = 4f; shape = CircleShape; clip = false },
              contentAlignment = Alignment.Center,
          ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Reload",
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun JdkSwitcher(
      selectedJdk: String,
      onJdkChange: (String) -> Unit,
      expanded: Boolean,
      onExpandedChange: (Boolean) -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "jdk-arr")
    Box {
      Surface(
          color = colors.surfaceContainerHigh,
          contentColor = colors.onSurface,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.clickable { onExpandedChange(true) },
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        ) {
          Icon(
              Icons.Filled.Memory,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(13.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("JDK $selectedJdk", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.width(2.dp))
          Icon(
              Icons.Filled.ChevronRight,
              contentDescription = null,
              tint = colors.onSurfaceVariant,
              modifier = Modifier.size(14.dp).rotate(rotation),
          )
        }
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        DropdownMenuItem(
            text = { Text("OpenJDK 17 (Recommended)", fontSize = 13.sp) },
            onClick = {
              onJdkChange("17")
              onExpandedChange(false)
            },
        )
        DropdownMenuItem(
            text = { Text("OpenJDK 21 (Experimental)", fontSize = 13.sp) },
            onClick = {
              onJdkChange("21")
              onExpandedChange(false)
            },
        )
      }
    }
  }

  @Composable
  private fun OdConfigChip(
      label: String,
      icon: androidx.compose.ui.graphics.vector.ImageVector,
      checked: Boolean,
      onCheckedChange: (Boolean) -> Unit,
      highlight: Boolean = false,
      modifier: Modifier = Modifier,
  ) {
    val colors = LocalOdSdkColors.current
    val bg by
        animateColorAsState(
            targetValue =
                if (checked) {
                  if (highlight) colors.tertiary.copy(alpha = 0.18f)
                  else colors.primary.copy(alpha = if (colors.isDark) 0.20f else 0.14f)
                } else colors.surfaceContainer,
            animationSpec = tween(180),
            label = "chip-bg",
        )
    val fg by
        animateColorAsState(
            targetValue = if (checked) colors.primary else colors.onSurfaceVariant,
            animationSpec = tween(180),
            label = "chip-fg",
        )
    val border by
        animateColorAsState(
            targetValue =
                if (checked) {
                  if (highlight) colors.tertiary else colors.primary
                } else colors.outlineVariant,
            animationSpec = tween(180),
            label = "chip-border",
        )
    val scale by
        animateFloatAsState(if (checked) 1.02f else 1f, tween(160), label = "chip-scale")
    Row(
        modifier =
            modifier
                .height(38.dp)
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(width = 1.dp, color = border, shape = RoundedCornerShape(10.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // 左侧 3D 勾选指示器
      Box(
          modifier =
              Modifier.size(16.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (checked) colors.primary else Color.Transparent)
                  .border(
                      width = 1.5.dp,
                      color = if (checked) colors.primary else colors.onSurfaceMuted,
                      shape = RoundedCornerShape(4.dp),
                  ),
          contentAlignment = Alignment.Center,
      ) {
        if (checked) {
          Icon(
              Icons.Filled.Check,
              contentDescription = null,
              tint = colors.onPrimary,
              modifier = Modifier.size(11.dp),
          )
        }
      }
      Spacer(Modifier.width(6.dp))
      Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
      Spacer(Modifier.width(4.dp))
      Text(
          label,
          fontSize = 11.sp,
          fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
          color = if (checked) colors.onSurface else colors.onSurfaceVariant,
          maxLines = 1,
      )
    }
  }

  // ---------------- 底部 CTA ----------------

  @Composable
  private fun OdStickyActionBar(
      enabled: Boolean,
      installOffline: Boolean,
      onClick: () -> Unit,
  ) {
    val colors = LocalOdSdkColors.current
    val infinite = rememberInfiniteTransition(label = "cta-pulse")
    val pulse by
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = if (enabled) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
            label = "cta-pulse-alpha",
        )
    val label =
        if (installOffline) "Start Offline Installation" else "Start Environment Setup"
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                  shadowElevation = if (enabled) 12f else 0f
                  shape = RoundedCornerShape(14.dp)
                  clip = false
                }
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (enabled) colors.ctaGradient
                    else
                        Brush.horizontalGradient(
                            listOf(
                                colors.surfaceContainer,
                                colors.surfaceContainer
                            )
                        )
                )
                .clickable(enabled = enabled, onClick = onClick)
    ) {
      // 顶部高光 - 3D 感
      Box(
          modifier =
              Modifier.matchParentSize()
                  .background(
                      Brush.verticalGradient(
                          0.0f to Color.White.copy(alpha = if (enabled) 0.20f else 0f),
                          0.4f to Color.White.copy(alpha = if (enabled) 0.06f else 0f),
                          1.0f to Color.Transparent,
                      )
                  )
      )
      // 启用时呼吸外圈
      if (enabled) {
        Box(
            modifier =
                Modifier.matchParentSize()
                    .border(
                        width = 1.5.dp,
                        color = colors.primary.copy(alpha = 0.35f * (1f - pulse)),
                        shape = RoundedCornerShape(14.dp),
                    )
        )
      }
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).padding(horizontal = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = null,
            tint = if (enabled) colors.onPrimary else colors.onSurfaceMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) colors.onPrimary else colors.onSurfaceMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.4.sp,
        )
      }
    }
  }

  @Composable
  private fun ActionConfirmAndRunDialog(
      toInstall: List<SdkTreeNode>,
      installGit: Boolean,
      installSsh: Boolean,
      applyNdkFix: Boolean,
      applyCmakePatch: Boolean,
      jdkVersion: String,
      githubMirror: String,
      onDismiss: () -> Unit,
      onSuccess: () -> Unit,
  ) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentTaskName by remember { mutableStateOf("") }
    val consoleLogs = remember { mutableStateListOf<String>() }

    fun addLog(msg: String) {
      consoleLogs.add(msg)
    }

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties =
            DialogProperties(dismissOnBackPress = !isRunning, dismissOnClickOutside = !isRunning),
        title = {
          Text(if (isFinished) "Setup Completed" else "Confirm Installation", fontSize = 16.sp)
        },
        text = {
          Column(modifier = Modifier.fillMaxWidth()) {
            if (!isRunning && !isFinished) {
              Text("Components to install/update:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              toInstall.forEach { Text("- ${it.name}", fontSize = 12.sp) }
              Text("- OpenJDK $jdkVersion", fontSize = 12.sp)
              if (installGit) Text("- Git Version Control", fontSize = 12.sp)
              if (installSsh) Text("- OpenSSH Remote Auth", fontSize = 12.sp)

              val installingNdk = toInstall.any { it.componentType == "ndk" }
              val installingCmake = toInstall.any { it.componentType == "cmake" }

              if (installingNdk || installingCmake) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(6.dp))
                Text("Additional Configurations:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (installingNdk) {
                  Text(
                      "• Apply NDK Fixes (symlinks & patches)",
                      fontSize = 11.sp,
                      color = if (applyNdkFix) MaterialTheme.colorScheme.onSurface else Color.Gray,
                  )
                }
                if (installingCmake) {
                  Text(
                      "• Apply CMake Patches",
                      fontSize = 11.sp,
                      color =
                          if (applyCmakePatch) MaterialTheme.colorScheme.onSurface else Color.Gray,
                  )
                }
              }
              if (githubMirror.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "• Active Github Mirror: $githubMirror",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            if (isRunning || isFinished) {
              Text(text = "Current: $currentTaskName", style = MaterialTheme.typography.labelSmall)
              LinearProgressIndicator(
                  progress = { currentProgress },
                  modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              )

              val listState = rememberLazyListState()
              LaunchedEffect(consoleLogs.size) {
                if (consoleLogs.isNotEmpty()) listState.animateScrollToItem(consoleLogs.lastIndex)
              }
              Box(
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(180.dp)
                          .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
                          .padding(6.dp)
              ) {
                LazyColumn(state = listState) {
                  items(consoleLogs) { msg ->
                    val textColor =
                        when {
                          msg.startsWith("ERR") || msg.startsWith("WARN") -> Color(0xFFFF5252)
                          msg.startsWith(">>") -> Color(0xFF64B5F6)
                          else -> Color(0xFFA5D6A7)
                        }
                    Text(
                        text = msg,
                        color = textColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp,
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          if (!isFinished) {
            Button(
                onClick = {
                  isRunning = true
                  coroutineScope.launch(Dispatchers.IO) {

                    // 系统依赖与包管理器更新
                    currentTaskName = "Configuring package environment..."
                    currentProgress = -1f
                    addLog(">> Updating pkg repositories...")
                    TermuxCommand.run(context) {
                          executable("sh")
                          args("-c", "pkg update -y && pkg upgrade -y")
                        }
                        .also { if (it.stdout.isNotBlank()) addLog(it.stdout) }

                    // 安装基础包和解压工具
                    addLog(">> Installing required base packages...")
                    TermuxCommand.run(context) {
                          executable("sh")
                          args(
                              "-c",
                              "pkg install -y bash curl wget jq tar unzip p7zip xz-utils patch sed grep coreutils findutils diffutils",
                          )
                        }
                        .also { if (it.stdout.isNotBlank()) addLog(it.stdout) }

                    currentTaskName = "Checking extraction tools..."
                    addLog(">> Verifying unzip/7z/tar availability...")
                    TermuxCommand.run(context) {
                          executable("sh")
                          args(
                              "-c",
                              "command -v unzip && command -v 7z && command -v tar && command -v xz",
                          )
                        }
                        .also {
                          if (it.stdout.isNotBlank()) addLog(it.stdout)
                          if (!it.isSuccess && it.stderr.isNotBlank())
                              addLog("WARN/ERR tools check: ${it.stderr}")
                        }

                    // Git 和 OpenSSH
                    if (installGit) {
                      currentTaskName = "Installing Git..."
                      addLog(">> Installing Git...")
                      TermuxCommand.run(context) {
                        executable("sh")
                        args("-c", "pkg install -y git")
                      }
                    }
                    if (installSsh) {
                      currentTaskName = "Installing OpenSSH..."
                      addLog(">> Installing OpenSSH...")
                      TermuxCommand.run(context) {
                        executable("sh")
                        args("-c", "pkg install -y openssh")
                      }
                    }

                    // 安装 JDK
                    currentTaskName = "Installing OpenJDK $jdkVersion..."
                    addLog(">> Installing package: 'openjdk-$jdkVersion'")
                    TermuxCommand.run(context) {
                          executable("sh")
                          args("-c", "pkg install -y openjdk-$jdkVersion")
                        }
                        .also { addLog(">> JDK $jdkVersion has been installed.") }

                    addLog(">> Updating ide-environment.properties...")
                    val jdkDir = "${Environment.PREFIX.absolutePath}/opt/openjdk"
                    val propsDir = File(Environment.PREFIX, "etc")
                    if (!propsDir.exists()) propsDir.mkdirs()
                    val propsFile = File(propsDir, "ide-environment.properties")
                    try {
                      propsFile.writeText("JAVA_HOME=$jdkDir\n")
                      addLog(">> JAVA_HOME=$jdkDir")
                      addLog(">> Properties file updated successfully!")
                    } catch (e: Exception) {
                      addLog("WARN: Failed to write ide-environment.properties: ${e.message}")
                    }

                    // 执行 SDK/NDK/CMake 安装
                    for (node in toInstall) {
                      currentTaskName = "Installing ${node.name}"
                      currentProgress = 0f
                      val success =
                          SdkInstallerManager.downloadAndInstall(
                              context = context,
                              node = node,
                              applyNdkFix = applyNdkFix,
                              applyCmakePatch = applyCmakePatch,
                              onProgress = { currentProgress = it },
                              onLog = ::addLog,
                          )
                      if (!success) {
                        addLog("ERROR: Failed to install ${node.name}. Continuing next task.")
                      }
                    }

                    isFinished = true
                    isRunning = false
                    currentTaskName = "All tasks completed. Environment is ready!"
                    currentProgress = 1f
                  }
                },
                enabled = !isRunning,
            ) {
              Text("Execute", fontSize = 13.sp)
            }
          } else {
            Button(onClick = onSuccess) { Text("Finish & Launch", fontSize = 13.sp) }
          }
        },
        dismissButton = {
          if (!isRunning && !isFinished) {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 13.sp) }
          }
        },
    )
  }

  @Composable
  fun OfflineConfirmAndRunDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentTaskName by remember { mutableStateOf("") }

    val consoleLogs = remember { mutableStateListOf<String>() }
    fun addLog(msg: String) {
      consoleLogs.add(msg)
    }

    var offlineUriStr by remember { mutableStateOf("") }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
          if (uri != null) {
            offlineUriStr = uri.toString()
          }
        }

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties =
            DialogProperties(dismissOnBackPress = !isRunning, dismissOnClickOutside = !isRunning),
        title = {
          Text(
              if (isFinished) "Offline Setup Completed" else "Offline Installation",
              fontSize = 16.sp,
          )
        },
        text = {
          Column(modifier = Modifier.fillMaxWidth()) {
            if (!isRunning && !isFinished) {
              Text(
                  "Please select the offline resources package (sdkresources.tar.gz):",
                  fontSize = 13.sp,
              )
              Spacer(modifier = Modifier.height(8.dp))

              Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = offlineUriStr,
                    onValueChange = { offlineUriStr = it },
                    modifier = Modifier.weight(1f).height(50.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                    singleLine = true,
                    placeholder = { Text("content://...", fontSize = 11.sp) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { launcher.launch("application/*") }) { Text("Select") }
              }
            }

            if (isRunning || isFinished) {
              Text(text = "Current: $currentTaskName", style = MaterialTheme.typography.labelSmall)
              LinearProgressIndicator(
                  progress = { currentProgress },
                  modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              )

              val listState = rememberLazyListState()
              LaunchedEffect(consoleLogs.size) {
                if (consoleLogs.isNotEmpty()) listState.animateScrollToItem(consoleLogs.lastIndex)
              }
              Box(
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(180.dp)
                          .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
                          .padding(6.dp)
              ) {
                LazyColumn(state = listState) {
                  items(consoleLogs) { msg ->
                    val textColor =
                        when {
                          msg.startsWith("ERR") || msg.startsWith("WARN") -> Color(0xFFFF5252)
                          msg.startsWith(">>") -> Color(0xFF64B5F6)
                          else -> Color(0xFFA5D6A7)
                        }
                    Text(
                        text = msg,
                        color = textColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp,
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          if (!isFinished) {
            Button(
                onClick = {
                  if (offlineUriStr.isBlank()) {
                    addLog("ERR: No file selected!")
                    return@Button
                  }
                  isRunning = true
                  coroutineScope.launch(Dispatchers.IO) {
                    currentTaskName = "Preparing offline package..."
                    currentProgress = -1f
                    try {
                      val homeDir = Environment.HOME
                      val targetArchive = File(homeDir, "sdkresources.tar.gz")

                      addLog(">> Copying selected file to HOME...")
                      val uri = Uri.parse(offlineUriStr)
                      context.contentResolver.openInputStream(uri)?.use { input ->
                        targetArchive.outputStream().use { output -> input.copyTo(output) }
                      }

                      addLog(">> File copied. Installing tar & dpkg...")
                      TermuxCommand.run(context) {
                        executable("sh")
                        args("-c", "pkg install tar dpkg -y")
                      }

                      val scriptFile = File(Environment.TMP_DIR, "offline_install.sh")
                      val script =
                          """
                                        #!/system/bin/sh
                                        set -e
                                        HOME_DIR="${homeDir.absolutePath}"
                                        CACHE_DIR="${'$'}HOME_DIR/Installcache"
                                        PKG_DIR="${'$'}CACHE_DIR/packages"

                                        cd "${'$'}HOME_DIR"
                                        echo ">> Extracting sdkresources.tar.gz to Installcache..."
                                        mkdir -p "${'$'}CACHE_DIR"
                                        tar -xzf sdkresources.tar.gz -C "${'$'}CACHE_DIR"

                                        echo ">> Extracting android-sdk.tar.gz to HOME..."
                                        cd "${'$'}CACHE_DIR"
                                        if [ -f "android-sdk.tar.gz" ]; then
                                            tar -xzf android-sdk.tar.gz -C "${'$'}HOME_DIR"
                                        else
                                            echo "WARN: android-sdk.tar.gz not found!"
                                        fi

                                        echo ">> Extracting packages.tar.gz..."
                                        mkdir -p "${'$'}PKG_DIR"
                                        if [ -f "packages.tar.gz" ]; then
                                            tar -xzf packages.tar.gz -C "${'$'}PKG_DIR"
                                            echo ">> Installing deb packages..."
                                            cd "${'$'}PKG_DIR"
                                            for deb in *.deb; do
                                                if [ -f "${'$'}deb" ]; then
                                                    dpkg -i "${'$'}deb" || apt install -y "${'$'}deb" || true
                                                fi
                                            done
                                        else
                                            echo "WARN: packages.tar.gz not found!"
                                        fi

                                        echo ">> Updating ide-environment.properties..."
                                        PROPS_DIR="${Environment.PREFIX.absolutePath}/etc"
                                        mkdir -p "${'$'}PROPS_DIR"
                                        PROPS_FILE="${'$'}PROPS_DIR/ide-environment.properties"

                                        JDK_DIR=""
                                        if [ -d "${Environment.PREFIX.absolutePath}/lib/jvm" ]; then
                                            JDK_DIR=${'$'}(find "${Environment.PREFIX.absolutePath}/lib/jvm" -mindepth 1 -maxdepth 1 -type d | head -n 1)
                                        fi
                                        if [ -z "${'$'}JDK_DIR" ] && [ -d "${Environment.PREFIX.absolutePath}/opt" ]; then
                                            JDK_DIR=${'$'}(find "${Environment.PREFIX.absolutePath}/opt" -mindepth 1 -maxdepth 1 -type d -name "openjdk*" | head -n 1)
                                        fi

                                        if [ -n "${'$'}JDK_DIR" ]; then
                                            echo "JAVA_HOME=${'$'}JDK_DIR" > "${'$'}PROPS_FILE"
                                            echo ">> JAVA_HOME=${'$'}JDK_DIR"
                                        else
                                            echo "WARN: Could not find JDK installation directory!"
                                        fi

                                        echo ">> Cleaning up temporary files..."
                                        cd "${'$'}HOME_DIR"
                                        rm -rf "${'$'}CACHE_DIR"
                                        rm -f sdkresources.tar.gz

                                        echo ">> Offline installation completed."
                                    """
                              .trimIndent()

                      scriptFile.writeText(script)
                      scriptFile.setExecutable(true)

                      currentTaskName = "Executing offline installation..."
                      val cmdResult =
                          TermuxCommand.run(context) {
                            label("Offline_Installer")
                            executable("sh")
                            args(scriptFile.absolutePath)
                          }

                      if (cmdResult.stdout.isNotBlank()) addLog(cmdResult.stdout)
                      if (cmdResult.stderr.isNotBlank()) addLog("ERR: ${cmdResult.stderr}")

                      scriptFile.delete()
                    } catch (e: Exception) {
                      addLog("ERR: ${e.message}")
                    }

                    isFinished = true
                    isRunning = false
                    currentTaskName = "All tasks completed. Environment is ready!"
                    currentProgress = 1f
                  }
                },
                enabled = !isRunning,
            ) {
              Text("Execute", fontSize = 13.sp)
            }
          } else {
            Button(onClick = onSuccess) { Text("Finish & Launch", fontSize = 13.sp) }
          }
        },
        dismissButton = {
          if (!isRunning && !isFinished) {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 13.sp) }
          }
        },
    )
  }
}

/** 专门针对引导页的精简版 ViewModel (支持 GitHub 镜像参数) */
class OdSdkSetupViewModel(application: Application) : AndroidViewModel(application) {

  private val _treeNodes = MutableStateFlow<List<SdkTreeNode>>(emptyList())
  val treeNodes: StateFlow<List<SdkTreeNode>> = _treeNodes.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _hasPendingChanges = MutableStateFlow(false)
  val hasPendingChanges: StateFlow<Boolean> = _hasPendingChanges.asStateFlow()

  private var currentMirror: String = ""

  private fun normalizeVersion(rawVersion: String): String {
    val noPrefix = rawVersion.trimStart('_', '-')
    return noPrefix.replace("_", ".").trimStart('.')
  }

  private fun compareVersionDesc(a: String, b: String): Int {
    val ap = a.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
    val bp = b.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
    val max = maxOf(ap.size, bp.size)
    for (i in 0 until max) {
      val av = ap.getOrElse(i) { 0 }
      val bv = bp.getOrElse(i) { 0 }
      if (av != bv) return bv.compareTo(av)
    }
    return b.compareTo(a)
  }

  init {
    loadData()
  }

  fun loadData(mirrorUrl: String = currentMirror) {
    currentMirror = mirrorUrl
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val rootNodes = mutableListOf<SdkTreeNode>()
        val manifest = fetchManifest(mirrorUrl)

        if (manifest != null) {
          val arch = getArch()
          val queryArch = if (arch == "armv7l" || arch == "armv8l") "arm" else arch

          fun applyMirror(url: String): String {
            return if (mirrorUrl.isNotEmpty() && url.startsWith("https://github.com"))
                mirrorUrl + url
            else url
          }

          // Android SDK (强制)
          val sdkUrl = manifest.androidSdk
          if (!sdkUrl.isNullOrBlank() && sdkUrl.lowercase() != "x") {
            rootNodes.add(
                SdkTreeNode(
                    name = "Android SDK Platform",
                    revision = "Latest",
                    downloadUrl = applyMirror(sdkUrl),
                    componentType = "android-sdk",
                    checkedState = ToggleableState.On,
                )
            )
          }

          // Cmdline Tools (强制)
          val cmdUrl = manifest.cmdlineTools
          if (!cmdUrl.isNullOrBlank() && cmdUrl.lowercase() != "x") {
            rootNodes.add(
                SdkTreeNode(
                    name = "Command-line Tools",
                    revision = "Latest",
                    downloadUrl = applyMirror(cmdUrl),
                    componentType = "cmdline-tools",
                    checkedState = ToggleableState.On,
                )
            )
          }

          // Build Tools
          manifest.buildTools?.get(queryArch)?.let { map ->
            val group = SdkTreeNode(name = "Build-Tools", isGroup = true, isExpanded = false)
            map.forEach { (k, url) ->
              if (url.isNotBlank() && url.lowercase() != "x") {
                val ver = normalizeVersion(k)
                group.children.add(
                    SdkTreeNode(
                        name = "Build-Tools $ver",
                        revision = ver,
                        downloadUrl = applyMirror(url),
                        componentType = "build-tools",
                        parent = group,
                    )
                )
              }
            }
            group.children.sortWith { a, b -> compareVersionDesc(a.revision, b.revision) }
            // 默认勾选最新
            group.children.firstOrNull()?.let { it.checkedState = ToggleableState.On }
            group.updateParentState()
            if (group.children.isNotEmpty()) rootNodes.add(group)
          }

          // Platform Tools (特定推荐 35.0.2)
          manifest.platformTools?.get(queryArch)?.let { map ->
            val group = SdkTreeNode(name = "Platform-Tools", isGroup = true, isExpanded = false)
            map.forEach { (k, url) ->
              if (url.isNotBlank() && url.lowercase() != "x") {
                val ver = normalizeVersion(k)
                group.children.add(
                    SdkTreeNode(
                        name = "Platform-Tools $ver",
                        revision = ver,
                        downloadUrl = applyMirror(url),
                        componentType = "platform-tools",
                        parent = group,
                    )
                )
              }
            }
            group.children.sortWith { a, b -> compareVersionDesc(a.revision, b.revision) }
            val targetNode =
                group.children.find { it.revision == "35.0.2" } ?: group.children.firstOrNull()
            targetNode?.let { it.checkedState = ToggleableState.On }
            group.updateParentState()
            if (group.children.isNotEmpty()) rootNodes.add(group)
          }

          // NDK
          manifest.androidNdk?.get(queryArch)?.let { map ->
            val group = SdkTreeNode(name = "NDK (Side by side)", isGroup = true, isExpanded = false)
            map.forEach { (k, url) ->
              if (url.isNotBlank() && url.lowercase() != "x") {
                val ver = normalizeVersion(k)
                group.children.add(
                    SdkTreeNode(
                        name = "NDK $ver",
                        revision = ver,
                        downloadUrl = applyMirror(url),
                        componentType = "ndk",
                        parent = group,
                    )
                )
              }
            }
            group.children.sortWith { a, b -> compareVersionDesc(a.revision, b.revision) }
            group.updateParentState()
            if (group.children.isNotEmpty()) rootNodes.add(group)
          }

          // CMake
          manifest.androidCmake?.get(queryArch)?.let { map ->
            val group = SdkTreeNode(name = "CMake", isGroup = true, isExpanded = false)
            map.forEach { (k, url) ->
              if (url.isNotBlank() && url.lowercase() != "x") {
                val ver = normalizeVersion(k)
                group.children.add(
                    SdkTreeNode(
                        name = "CMake $ver",
                        revision = ver,
                        downloadUrl = applyMirror(url),
                        componentType = "cmake",
                        parent = group,
                    )
                )
              }
            }
            group.children.sortWith { a, b -> compareVersionDesc(a.revision, b.revision) }
            group.updateParentState()
            if (group.children.isNotEmpty()) rootNodes.add(group)
          }
        }

        _treeNodes.value = rootNodes
        checkPendingChanges()
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        _isLoading.value = false
      }
    }
  }

  private suspend fun fetchManifest(mirrorUrl: String): SdkManifest? =
      withContext(Dispatchers.IO) {
        try {
          val baseUrl =
              "https://github.com/msmt2018/SDK-tool-for-Android-platform/releases/download/IDESdkDownJson2.3/manifest.json"
          val targetUrl =
              if (mirrorUrl.isNotEmpty() && baseUrl.startsWith("https://github.com"))
                  mirrorUrl + baseUrl
              else baseUrl

          val url = URL(targetUrl)
          val connection = url.openConnection() as HttpURLConnection
          connection.connectTimeout = 10000
          connection.readTimeout = 10000
          if (connection.responseCode == 200) {
            val json = connection.inputStream.bufferedReader().readText()
            Gson().fromJson(json, SdkManifest::class.java)
          } else null
        } catch (e: Exception) {
          null
        }
      }

  private fun getArch(): String = IDEBuildConfigProvider.getInstance().cpuArch.name.lowercase()

  fun triggerPendingChangesCheck() {
    var hasChanges = false
    fun checkNode(node: SdkTreeNode) {
      if (!node.isGroup) {
        if (node.checkedState == ToggleableState.On) {
          hasChanges = true
        }
      }
      node.children.forEach { checkNode(it) }
    }
    _treeNodes.value.forEach { checkNode(it) }
    _hasPendingChanges.value = hasChanges
  }

  private fun checkPendingChanges() {
    triggerPendingChangesCheck()
  }

  fun getInstallTasks(): List<SdkTreeNode> {
    val toInstall = mutableListOf<SdkTreeNode>()
    fun collect(node: SdkTreeNode) {
      if (!node.isGroup && node.checkedState == ToggleableState.On) {
        toInstall.add(node)
      }
      node.children.forEach { collect(it) }
    }
    _treeNodes.value.forEach { collect(it) }
    return toInstall
  }
}
