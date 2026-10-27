package com.zerostudio.idea.editor.core.config

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 换行符标准支持矩阵
 */
enum class LineSeparatorMode(val content: String, val label: String) {
    UNIX_LF("\n", "Unix-like (LF)"),
    MAC_CR("\r", "Classic Mac (CR)"),
    WINDOWS_CRLF("\r\n", "Windows (CRLF)")
}

/**
 * 文本全维统计数据模型
 */
data class TextAnalyticsResult(
    val byteCount: Long,
    val charCount: Long,
    val wordCount: Long,
    val cjkCharCount: Long, // 中文/日韩字符统计
    val totalLines: Long
)

/**
 * 响应式编辑器偏好设置状态源 (线程安全与局部重组锁)
 */
@Stable
class EditorPreferencesState {

    // 突破极限的文本虚拟分页与性能
    var autoPaginationThresholdChars by mutableStateOf(10_000_000L) // 10万 - 10亿，默认千万
    var autoFluencyModeThresholdChars by mutableStateOf(5_000_000L)  // 自动开启流畅模式阈值
    var forceFluencyMode by mutableStateOf(false)                  // 强制流畅模式

    // 外部文件变更监听策略
    var notifyFileModifiedExternal by mutableStateOf(true)
    var notifyFileDeletedExternal by mutableStateOf(true)

    // 手势与操作交互
    var pinchToZoomEnabled by mutableStateOf(true)
    var magnifierEnabled by mutableStateOf(true)
    var currentZoomMultiplier by mutableFloatStateOf(1.0f)

    // 字体与排版
    var customFontPath by mutableStateOf<String?>(null)
    var baseFontSizeSp by mutableFloatStateOf(14.0f)
    var lineSpacingMultiplier by mutableFloatStateOf(1.2f) // 对应自定义行距
    var wordWrap by mutableStateOf(true)
    var antiWordBreaking by mutableStateOf(true)           // 自动换行保持单词完整

    // 辅助视觉显示开关
    var showLineNumbers by mutableStateOf(true)
    var pinLineNumbers by mutableStateOf(false)            // 固定行号在左边
    var showSoftWrapArrows by mutableStateOf(true)         // 显示自动换行箭头
    var showWhitespaceCharacters by mutableStateOf(false)  // 空白符（点和虚线）
    var hideIsolatedSingleSpaces by mutableStateOf(false)  // 隐藏独立单个空格
    var renderAsciiControlCharacters by mutableStateOf(true)// 显示 NUL, ESC 等
    var renderSpecialUnicodeSymbols by mutableStateOf(true) // 显示 ZWSP, NBSP, LRM

    // 缩进管理
    var autoIndentOnEnter by mutableStateOf(true)
    var useTabForIndent by mutableStateOf(false)           // false 为使用空格
    var tabStopSize by mutableStateOf(4)                   // 范围 1-12
    var showIndentGuides by mutableStateOf(true)           // 缩进参考线

    // 只读与自动保存
    var readOnlyMode by mutableStateOf(false)
    var autoSaveEnabled by mutableStateOf(false)
    var autoSaveIntervalMs by mutableStateOf(5000L)
    var lineSeparator by mutableStateOf(LineSeparatorMode.UNIX_LF)

    val scaledFontSizeSp: Float
        get() = baseFontSizeSp * currentZoomMultiplier

    fun updateZoomFactor(factor: Float) {
        currentZoomMultiplier = (currentZoomMultiplier * factor).coerceIn(0.6f, 3.0f)
    }

    /**
     * 判断当前文件尺寸是否要求进入【流畅模式】
     * 处于流畅模式下将强行精简代码高亮着色、关闭 Inlay Hints 与软换行预测，确保极低内存占用与 120 帧恒定响应
     */
    fun isFluencyModeActive(fileCharLength: Long): Boolean {
        if (forceFluencyMode) return true
        return fileCharLength >= autoFluencyModeThresholdChars
    }
}

/**
 * 现代化 Kotlin Flow 全局异步总线，取代旧式回调
 */
sealed interface EditorEngineEvent {
    data class FileExternallyModified(val fileAbsolutePath: String) : EditorEngineEvent
    data class FileExternallyDeleted(val fileAbsolutePath: String) : EditorEngineEvent
    data class AutoSaved(val timestamp: Long) : EditorEngineEvent
    data class PageSwapped(val newPageIndex: Int, val totalPages: Int) : EditorEngineEvent
}

object EditorEventBus {
    private val _events = MutableSharedFlow<EditorEngineEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: EditorEngineEvent) {
        _events.tryEmit(event)
    }
}