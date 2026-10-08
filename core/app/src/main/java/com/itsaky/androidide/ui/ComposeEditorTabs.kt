package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.itsaky.androidide.R

/** A content-sized, horizontally scrolling tab strip shared by editor hosts. */
@Composable
fun <T> EditorTabStrip(
    tabs: List<T>,
    selectedTab: T?,
    title: (T) -> String,
    onSelect: (T) -> Unit,
    onClose: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (T) -> Unit = {},
) {
    if (tabs.isEmpty()) return
    // Recreate TabRow's measured positions when tabs are removed/reordered. Selection is
    // derived from identity, never from a remembered index into an older list.
    key(tabs) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
            modifier = modifier.fillMaxWidth(),
            edgePadding = 0.dp,
        ) {
            tabs.forEach { tab ->
                key(tab) {
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onSelect(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                icon(tab)
                                Text(title(tab), maxLines = 1)
                                IconButton(onClick = { onClose(tab) }, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        painterResource(R.drawable.ic_close),
                                        contentDescription = "${stringResource(R.string.btn_close)} ${title(tab)}",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Compose tab strip for the View-based editor host. No Material View TabLayout is used.
 * The small imperative facade keeps existing file-save and Fragment lifecycle controllers
 * responsible for accepting close requests; the close button never discards a file itself.
 */
class ComposeEditorTabs @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    private var tabs by mutableStateOf<List<Tab>>(emptyList())
    private var selectedTab by mutableStateOf<Tab?>(null)
    private val listeners = mutableListOf<OnTabSelectedListener>()
    var onCloseTab: (Tab) -> Unit = {}
    val tabCount get() = tabs.size
    val selectedTabPosition get() = tabs.indexOf(selectedTab)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    inner class Tab internal constructor() {
        var tag: Any? = null
        var text: CharSequence? by mutableStateOf(null)
        var icon: Drawable? by mutableStateOf(null)
        val position get() = tabs.indexOf(this)
        val view get() = this@ComposeEditorTabs
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
        visibility = VISIBLE
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
    override fun Content() {
        MaterialTheme {
            EditorTabStrip(
                tabs = tabs,
                selectedTab = selectedTab,
                title = { it.text?.toString().orEmpty() },
                onSelect = ::selectTab,
                onClose = { onCloseTab(it) },
                icon = { tab ->
                    tab.icon?.let { drawable ->
                        val bitmap = androidx.compose.runtime.remember(drawable) {
                            drawable.toBitmap(24, 24).asImageBitmap()
                        }
                        Icon(bitmap, null, modifier = Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                    }
                },
            )
        }
    }
}
