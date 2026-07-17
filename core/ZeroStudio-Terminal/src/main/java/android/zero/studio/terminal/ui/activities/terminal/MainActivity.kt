package android.zero.studio.termux.ui.activities.terminal

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import android.zero.studio.termux.ui.fragments.TerminalHost
import android.zero.studio.termux.ui.fragments.TerminalHostFragment

/**
 * 终端空壳 Activity — 仅作为 [TerminalHostFragment] 的容器。
 *
 * 所有终端逻辑 (SessionService 绑定、Compose UI、键盘监听、权限请求等) 已迁移至
 * [TerminalHostFragment], 此 Activity 仅负责:
 *  1. [enableEdgeToEdge] 启用边到边显示
 *  2. 通过 FragmentManager 加载 [TerminalHostFragment]
 *  3. 处理 awake_intent (从通知拉起时退至后台)
 *
 * 实现 [TerminalHost] 仅用于兼容 (sessionBinder 委托至 TerminalSessionHolder)。
 *
 * @author android_zero
 */
class MainActivity : FragmentActivity(), TerminalHost {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 提供 Fragment 容器
        setContentView(FrameLayout(this).apply { id = android.R.id.content })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, TerminalHostFragment())
                .commitAllowingStateLoss()
        }

        if (intent.hasExtra("awake_intent")) {
            moveTaskToBack(true)
        }
    }
}
