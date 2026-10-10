package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.itsaky.androidide.R

/**
 * 紧凑型 Tab 条：支持双轴紧凑高度与吸附式关闭/多选下拉菜单 (DropdownMenu)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> EditorTabStrip(
    tabs: List<T>,
    selectedTab: T?,
    title: (T) -> String,
    onSelect: (T) -> Unit,
    onClose: (T) -> Unit,
    onCloseOthers: (T) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (T) -> Unit = {},
) {
    if (tabs.isEmpty()) return
    val tabHeight = 28.dp // 规范化：高度减少 40%

    key(tabs) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
            modifier = modifier.fillMaxWidth().height(tabHeight),
            edgePadding = 4.dp,
        ) {
            tabs.forEach { tab ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onSelect(tab) },
                        modifier = Modifier
                            .height(tabHeight)
                            .combinedClickable(
                                onClick = { onSelect(tab) },
                                onLongClick = { menuExpanded = true },
                            ),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                icon(tab)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = title(tab),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onClose(tab) },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = "Close",
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        },
                    )

                    // 核心：长按或重选 Tab 时，弹出吸附在当前 Tab 下方的纯 Compose DropdownMenu
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        offset = DpOffset(0.dp, 2.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("关闭当前标签", fontSize = 12.sp) },
                            onClick = {
                                menuExpanded = false
                                onClose(tab)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("关闭其他标签", fontSize = 12.sp) },
                            onClick = {
                                menuExpanded = false
                                onCloseOthers(tab)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("关闭全部标签", fontSize = 12.sp) },
                            onClick = {
                                menuExpanded = false
                                onCloseAll()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 控制器适配层
 */
class ComposeEditorTabs(private val context: Context) {
    var visible by mutableStateOf(true)
    private var tabs by mutableStateOf<List<Tab>>(emptyList())
    private var selectedTab by mutableStateOf<Tab?>(null)
    private val listeners = mutableListOf<OnTabSelectedListener>()
    var onCloseTab: (Tab) -> Unit = {}
    var onCloseOtherTabs: (Tab) -> Unit = {}
    var onCloseAllTabs: () -> Unit = {}
    val tabCount get() = tabs.size
    val selectedTabPosition get() = tabs.indexOf(selectedTab)

    inner class Tab internal constructor() {
        var tag: Any? = null
        var text: CharSequence? by mutableStateOf(null)
        var icon: Drawable? by mutableStateOf(null)
        val position get() = tabs.indexOf(this)
        val isSelected get() = selectedTab === this
        fun select() = selectTab(this)
        fun setIcon(resource: Int) { icon = AppCompatResources.getDrawable(context, resource) }
    }

    interface OnTabSelectedListener {
        fun onTabSelected(tab: Tab)
        fun onTabUnselected(tab: Tab)
        fun onTabReselected(tab: Tab)
    }

    fun newTab() = Tab()
    fun getTabAt(index: Int): Tab? = tabs.getOrNull(index)
    fun addOnTabSelectedListener(listener: OnTabSelectedListener) { listeners.add(listener) }

    fun addTab(tab: Tab) {
        if (tab in tabs) return
        tabs = tabs + tab
        visible = true
        if (selectedTab == null) selectTab(tab)
    }

    fun selectTab(tab: Tab?) {
        if (tab != null && tab !in tabs) return
        val previous = selectedTab
        if (previous === tab) {
            tab?.let { current -> listeners.toList().forEach { it.onTabReselected(current) } }
            return
        }
        selectedTab = tab
        previous?.let { old -> listeners.toList().forEach { it.onTabUnselected(old) } }
        tab?.let { current -> listeners.toList().forEach { it.onTabSelected(current) } }
    }

    fun removeTabAt(index: Int) { getTabAt(index)?.let(::removeTab) }

    fun removeTab(tab: Tab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs = tabs - tab
        if (selectedTab === tab) selectTab(tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)))
    }

    fun removeAllTabs() {
        tabs = emptyList()
        selectTab(null)
    }

    @Composable
    fun Content() {
        MaterialTheme {
            EditorTabStrip(
                tabs = tabs,
                selectedTab = selectedTab,
                title = { it.text?.toString().orEmpty() },
                onSelect = ::selectTab,
                onClose = { onCloseTab(it) },
                onCloseOthers = { onCloseOtherTabs(it) },
                onCloseAll = { onCloseAllTabs() },
                icon = { tab ->
                    tab.icon?.let { drawable ->
                        val bitmap = remember(drawable) {
                            drawable.toBitmap(20, 20).asImageBitmap()
                        }
                        Icon(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Unspecified,
                        )
                    }
                },
            )
        }
    }
}