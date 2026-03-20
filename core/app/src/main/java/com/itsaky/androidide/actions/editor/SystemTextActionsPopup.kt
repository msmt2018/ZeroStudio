package com.itsaky.androidide.actions.editor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 动态读取系统 `ACTION_PROCESS_TEXT` 的 Compose 悬浮窗
 * 采用 Material 3 标准，支持平滑展开与收起动画
 *
 * @author android_zero
 */
class SystemTextActionsPopup(
    private val context: Context,
    private val editor: CodeEditor,
    private val selectedText: String
) : PopupWindow(context) {

    // 存储系统动作的数据模型
    data class ProcessTextAction(
        val label: String,
        val icon: Drawable?,
        val intent: Intent
    )

    private val composeView: ComposeView
    // 控制动画状态
    private val isVisibleState = mutableStateOf(false)

    init {
        val actions = getSystemTextActions()

        composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setContent {
                MaterialTheme {
                    SystemActionsMenu(actions)
                }
            }
        }

        contentView = composeView
        width = ViewGroup.LayoutParams.WRAP_CONTENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isFocusable = true
        isOutsideTouchable = true
        elevation = 0f // 交给 Compose 内部处理阴影
        setBackgroundDrawable(null) // 透明背景，使用 Compose 的 Surface

        // 拦截原生的 dismiss，使其先执行动画再关闭
        setOnDismissListener {
            isVisibleState.value = false
        }
    }

    /**
     * 获取系统注册的所有文本处理动作 (翻译、搜索、小爱等)
     */
    private fun getSystemTextActions(): List<ProcessTextAction> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        return resolveInfos.mapNotNull { info ->
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val actionIntent = Intent().apply {
                setClassName(info.activityInfo.packageName, info.activityInfo.name)
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true) // 这里声明为只读(如翻译/搜索)
            }
            ProcessTextAction(label, icon, actionIntent)
        }.distinctBy { it.label }
    }

    @Composable
    private fun SystemActionsMenu(actions: List<ProcessTextAction>) {
        val isVisible by isVisibleState
        val coroutineScope = rememberCoroutineScope()

        // 丝滑的展开和淡入动画
        AnimatedVisibility(
            visible = isVisible,
            enter = expandVertically(
                animationSpec = tween(250),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                if (actions.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("无可用系统动作", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        items(actions) { action ->
                            ActionItemRow(action) {
                                // 点击项后执行 Intent 并关闭弹窗
                                executeAction(action.intent)
                                coroutineScope.launch {
                                    isVisibleState.value = false
                                    delay(200) // 等待动画结束
                                    super@SystemTextActionsPopup.dismiss()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionItemRow(action: ProcessTextAction, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (action.icon != null) {
                Image(
                    bitmap = action.icon.toBitmap().asImageBitmap(),
                    contentDescription = action.label,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    private fun executeAction(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 带有动画的显示方法
     */
    fun show(anchor: View, x: Int, y: Int) {
        // 先显示透明的 PopupWindow
        showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        // 触发 Compose 动画
        isVisibleState.value = true
    }
}