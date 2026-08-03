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
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.itsaky.androidide.ui.screen.git.GitScreenCatalog
import kotlinx.coroutines.launch

/**
 * Container fragment that integrates the ten mobile-first Git Compose screen shells
 * ([GitScreenCatalog]) into the editor's sidebar.
 *
 * 渲染一个可滚动的 Tab 行 + [HorizontalPager], 用户可在 diff / branches / history /
 * changes / stash / conflict / repositories / settings / tags / interactive-rebase
 * 这十个 Git 模拟界面之间滑动切换。
 *
 * 容器强制使用深色 [darkColorScheme], 与 [com.itsaky.androidide.ui.screen.git.GitMockTheme]
 * 的深色视觉语言保持一致 (无论 app 当前是浅色还是深色主题)。
 *
 * @author android_zero
 */
class GitHostFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // 强制深色主题, 让 Tab 行与 Git 模拟界面的深色调一致。
                MaterialTheme(colorScheme = darkColorScheme()) { GitHostScreen() }
            }
        }
    }

    @Composable
    private fun GitHostScreen() {
        val screens = GitScreenCatalog.screens
        val pagerState = rememberPagerState(initialPage = 0) { screens.size }
        val scope = rememberCoroutineScope()

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 8.dp,
                ) {
                    screens.forEachIndexed { index, spec ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = spec.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    screens[page].content()
                }
            }
        }
    }
}