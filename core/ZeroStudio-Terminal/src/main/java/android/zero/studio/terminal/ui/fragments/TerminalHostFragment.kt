package android.zero.studio.termux.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.zero.studio.termux.service.SessionService
import android.zero.studio.termux.ui.activities.terminal.MainActivity
import android.zero.studio.termux.ui.navHosts.MainActivityNavHost
import android.zero.studio.termux.ui.routes.MainActivityRoutes
import android.zero.studio.termux.ui.screens.terminal.terminalView
import android.zero.studio.termux.ui.theme.TermixTheme
import android.zero.studio.termux.ui.theme.colorscheme.ColorSchemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 终端宿主 Fragment — 一比一复刻 [MainActivity] 的生命周期与 Compose 承载逻辑。
 *
 * 复刻自 [android.zero.studio.termux.ui.activities.terminal.MainActivity]，适配 Fragment 生命周期:
 *  - [onCreateView]: 构建 [ComposeView] 承载终端 Compose UI
 *  - [onStart]: 启动并绑定 [SessionService] (与 MainActivity.onStart 一致)
 *  - [onStop]: 解绑 [SessionService]
 *  - [onResume]: 注册键盘可见性监听 + 恢复 IME (与 MainActivity.onResume 一致)
 *  - [onPause]: 记录键盘状态 (与 MainActivity.onPause 一致)
 *
 * 与 MainActivity 的差异:
 *  1. 用 [ComposeView] + [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed]
 *     替代 Activity 的 setContent; Compose 内容在 ServiceConnection 回调中设置
 *     (与 MainActivity 一致: 绑定成功后才 setContent, 因为终端 UI 依赖 SessionBinder)
 *  2. Fragment 无法直接 registerForActivityResult, 在 [onCreate] 中注册
 *  3. 宿主 Activity 必须是 [MainActivity] (终端 UI 强依赖 sessionBinder)
 *
 * @author android_zero
 */
class TerminalHostFragment : Fragment() {

    /** 从宿主 [MainActivity] 获取的 SessionService 绑定器。 */
    private val sessionBinder: SessionService.SessionBinder?
        get() = (activity as? MainActivity)?.sessionBinder

    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // 让宿主 MainActivity 持有 binder, 与 MainActivity.onServiceConnected 行为一致
            val binder = service as SessionService.SessionBinder
            (activity as? MainActivity)?.let { host ->
                host.sessionBinder = binder
                host.isBound = true
            }
            isBound = true

            // 与 MainActivity 一致: 绑定成功后在主线程 setContent
            // 但 Fragment 用 ComposeView, 这里只需触发 recomposition
            lifecycleScope.launch(Dispatchers.Main) {
                composeView?.setContent {
                    // 与 MainActivity 一致: 在 Compose 树根部读取配色状态
                    val currentColorScheme by ColorSchemeManager.currentScheme

                    TermixTheme(terminalColorScheme = currentColorScheme) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()
                            val hostActivity = activity as? MainActivity
                            if (hostActivity != null) {
                                MainActivityNavHost(
                                    navController = navController,
                                    mainActivity = hostActivity,
                                )
                            } else {
                                // 宿主不是 MainActivity, 显示错误提示
                                Text(
                                    text = "TerminalHostFragment requires MainActivity as host",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            val backStackEntry by navController.currentBackStackEntryAsState()

                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(backStackEntry?.destination?.route) {
                                if (backStackEntry?.destination?.route != MainActivityRoutes.MainScreen.route) {
                                    // 与 MainActivity 一致: 离开终端页面时清理焦点 + 隐藏 IME
                                    focusManager.clearFocus(force = true)
                                    terminalView.get()?.clearFocus()
                                    keyboardController?.hide()
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            (activity as? MainActivity)?.let { host ->
                host.isBound = false
                host.sessionBinder = null
            }
        }
    }

    private var composeView: ComposeView? = null

    private var denied = 1
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted && denied <= 2) {
                denied++
                requestPermission()
            }
        }

    private var isKeyboardVisible = false
    private var wasKeyboardOpen = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is MainActivity) {
            throw IllegalStateException(
                "TerminalHostFragment must be hosted by MainActivity, but got ${context.javaClass.name}"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // 与 MainActivity.onCreate 一致: 启用 edge-to-edge
        // Fragment 没有 enableEdgeToEdge, 通过宿主 Activity 调用
        (activity as? MainActivity)?.enableEdgeToEdge()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            // 初始占位内容, 绑定成功后由 serviceConnection 替换
            setContent {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Loading terminal...")
                }
            }
            composeView = this
        }
    }

    override fun onStart() {
        super.onStart()
        // 与 MainActivity.onStart 一致: 启动并绑定 SessionService
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(Intent(ctx, SessionService::class.java))
        } else {
            ctx.startService(Intent(ctx, SessionService::class.java))
        }
        Intent(ctx, SessionService::class.java).also { intent ->
            // 注意: Fragment 用 context.bindService 而非 activity.bindService
            // BIND_AUTO_CREATE 与 MainActivity 一致
            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // 与 MainActivity.onStop 一致: 解绑 SessionService
        if (isBound) {
            context?.unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        // 与 MainActivity.onResume 一致: 注册键盘可见性监听 + 恢复 IME
        val rootView = view ?: return
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val isVisible = keypadHeight > screenHeight * 0.15

            isKeyboardVisible = isVisible
        }

        if (wasKeyboardOpen && !isKeyboardVisible) {
            terminalView.get()?.let {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 与 MainActivity.onPause 一致: 记录键盘状态
        wasKeyboardOpen = isKeyboardVisible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView = null
    }

    /** 与 MainActivity.requestPermission 一致: 请求通知权限 (Android 13+)。 */
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                  requireContext(),
                  android.Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
