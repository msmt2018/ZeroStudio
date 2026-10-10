package com.androidide.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================================
// 扩展颜色规格声明（用于承载原 XML 特有的终端、涟漪、选择等非 M3 标准属性）
// ============================================================================
data class ExtendedColors(
    val ripple: Color,                  // 控件触控反馈的涟漪波纹颜色 (对应 controlHighlight)
    val selection: Color,               // 文本或代码选中的背景高亮色 (对应 textColorHighlight)
    val terminalTextColor: Color,       // 纯文本控制台/终端的默认字符输出颜色
    val terminalBackgroundColor: Color  // 纯文本控制台/终端的完整背景底色
)

// ============================================================================
// 统一主题包数据载体规格
// ============================================================================
data class AppThemeSpec(
    val id: String,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors
)

// ============================================================================
// 内置多套 XML 风格的主题包仓库
// ============================================================================
object BuiltInThemes {

    // ========================================================================
    // 主题一：森林苔藓 (Forest Moss) - 橄榄绿与大地色 自然护眼风格
    // ========================================================================
    val ForestMoss = AppThemeSpec(
        id = "forest_moss",
        name = "森林苔藓",
        
        // --- 浅色/白天模式色板配置 ---
        lightColorScheme = lightColorScheme(
            // 主导颜色：用于高频核心 UI、高亮操作按钮、激活状态控件
            primary = Color(0xFF4C662B),
            // 主导颜色容器：常用于高亮区域的轻量背景块填充（如选中的侧边栏 Tab 背景）
            primaryContainer = Color(0xFFCDEDA3),
            // 反转主导颜色：当组件临时处于反色图层（如 SurfaceInverse）之上时使用的主色
            inversePrimary = Color(0xFFB1D18A),
            // 在主导颜色之上的文本/图标颜色：确保在高饱和度 primary 背景上清晰可见（通常为纯白或极深）
            onPrimary = Color(0xFFFFFFFF),
            // 在主导颜色容器之上的文本/图标颜色：用于配合 primaryContainer 内的文本提示
            onPrimaryContainer = Color(0xFF102000),
            
            // 次要颜色：用于低频或次要的 UI 标识、复选框、小图标、不强调的操作
            secondary = Color(0xFF586249),
            // 次要颜色容器：常用于不需要过度吸睛的低优先级背景容器填充
            secondaryContainer = Color(0xFFDCE7C8),
            // 在次要颜色之上的文本/图标颜色
            onSecondary = Color(0xFFFFFFFF),
            // 在次要颜色容器之上的文本/图标颜色
            onSecondaryContainer = Color(0xFF151E0B),
            
            // 第三目标色：用于和主副色形成对比的独立语义色（如标记成功、特殊警告或特定代码符号）
            tertiary = Color(0xFF386663),
            // 第三目标色容器：用于承载 tertiary 语义的轻量级背景块
            tertiaryContainer = Color(0xFFBCECE7),
            // 在第三目标色之上的文本/图标颜色
            onTertiary = Color(0xFFFFFFFF),
            // 在第三目标色容器之上的文本/图标颜色
            onTertiaryContainer = Color(0xFF00201E),
            
            // 错误色：用于严重的系统故障、编译失败、语法报错下划线等警示场景
            error = Color(0xFFBA1A1A),
            
            // 全局大背景底色：应用脚手架最底层的画布颜色（通常最偏向白色或最偏向黑色）
            background = Color(0xFFFDFCF5),
            // 在全局大背景之上的文本/图标主色：用于常规长篇大论的代码、常规描述文本等
            onBackground = Color(0xFF1B1C18),
            
            // 表面层颜色：诸如独立卡片（Card）、对话框（Dialog）、侧边抽屉面板（Drawer）的容器背景
            surface = Color(0xFFFDFCF5),
            // 变体表面层颜色：比常规 surface 颜色略深（或略浅），常用于区分相邻的输入框或复杂容器块
            surfaceVariant = Color(0xFFE1E4D5),
            // 在表面层之上的文本/图标主色
            onSurface = Color(0xFF1B1C18),
            // 在变体表面层之上的文本/图标主色
            onSurfaceVariant = Color(0xFF44483D),
            
            // 反转表面层颜色：用于诸如临时弹出的轻量级 Toast 提示等需要与周围形成极强烈反差的容器背景
            inverseSurface = Color(0xFF30312C),
            // 在反转表面层之上的文本/图标颜色
            inverseOnSurface = Color(0xFFF2F0E9),
            
            // 边界/分割线颜色：用于输入框边框（TextField Outline）、面板间的细腻分割线或边框装饰
            outline = Color(0xFF75796C)
        ),
        
        // --- 深色/黑夜模式色板配置 ---
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFB1D18A),
            primaryContainer = Color(0xFF354E14),
            inversePrimary = Color(0xFF4C662B),
            onPrimary = Color(0xFF233600),
            onPrimaryContainer = Color(0xFFCDEDA3),
            secondary = Color(0xFFC0CBAD),
            secondaryContainer = Color(0xFF404A33),
            onSecondary = Color(0xFF2A331E),
            onSecondaryContainer = Color(0xFFDCE7C8),
            tertiary = Color(0xFFA0D0CB),
            tertiaryContainer = Color(0xFF1F4E4B),
            onTertiary = Color(0xFF003735),
            onTertiaryContainer = Color(0xFFBCECE7),
            error = Color(0xFFFFB4AB),
            background = Color(0xFF1B1C18),
            onBackground = Color(0xFFE4E2DA),
            surface = Color(0xFF1B1C18),
            surfaceVariant = Color(0xFF44483D),
            onSurface = Color(0xFFE4E2DA),
            onSurfaceVariant = Color(0xFFC5C8BA),
            inverseSurface = Color(0xFFE4E2DA),
            inverseOnSurface = Color(0xFF1B1C18),
            outline = Color(0xFF8F9285)
        ),
        
        // --- 浅色模式下的专有扩展颜色集 ---
        lightExtended = ExtendedColors(
            ripple = Color(0x334C662B),                  // 触控波纹：透明度 20% 的橄榄绿
            selection = Color(0x66CDEDA3),               // 选区高亮：透明度 40% 的浅嫩绿
            terminalTextColor = Color(0xFF1B1C18),       // 终端文本：护眼深灰黑字符
            terminalBackgroundColor = Color(0xFFFDFCF5)  // 终端背景：极柔和的仿纸质大地白
        ),
        
        // --- 深色模式下的专有扩展颜色集 ---
        darkExtended = ExtendedColors(
            ripple = Color(0x44B1D18A),                  // 触控波纹：透明度 26% 的明亮苔藓绿
            selection = Color(0x66354E14),               // 选区高亮：透明度 40% 的深沉苔藓绿
            terminalTextColor = Color(0xFFE4E2DA),       // 终端文本：高对比度米白字符
            terminalBackgroundColor = Color(0xFF1B1C18)  // 终端背景：纯暗黑护眼底色
        )
    )

    // ========================================================================
    // 主题二：深海蔚蓝 (Deep Ocean) - 演示多包并存时的色彩规格
    // ========================================================================
    val DeepOcean = AppThemeSpec(
        id = "deep_ocean",
        name = "深海蔚蓝",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF0061A4),
            primaryContainer = Color(0xFFD1E4FF),
            inversePrimary = Color(0xFF9ECAFF),
            onPrimary = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF535F70),
            secondaryContainer = Color(0xFFD7E3F7),
            onSecondary = Color(0xFFFFFFFF),
            onSecondaryContainer = Color(0xFF101C2B),
            tertiary = Color(0xFF6B5778),
            tertiaryContainer = Color(0xFFF2DAFF),
            onTertiary = Color(0xFFFFFFFF),
            onTertiaryContainer = Color(0xFF251432),
            error = Color(0xFFBA1A1A),
            background = Color(0xFFFDFCFF),
            onBackground = Color(0xFF1A1C1E),
            surface = Color(0xFFFDFCFF),
            surfaceVariant = Color(0xFFDFE2EB),
            onSurface = Color(0xFF1A1C1E),
            onSurfaceVariant = Color(0xFF43474E),
            inverseSurface = Color(0xFF2F3033),
            inverseOnSurface = Color(0xFFF1F0F4),
            outline = Color(0xFF73777F)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF9ECAFF),
            primaryContainer = Color(0xFF00487D),
            inversePrimary = Color(0xFF0061A4),
            onPrimary = Color(0xFF003258),
            onPrimaryContainer = Color(0xFFD1E4FF),
            secondary = Color(0xFFBBC7DB),
            secondaryContainer = Color(0xFF3C4758),
            onSecondary = Color(0xFF253140),
            onSecondaryContainer = Color(0xFFD7E3F7),
            tertiary = Color(0xFFD3E0F2),
            tertiaryContainer = Color(0xFF523F5F),
            onTertiary = Color(0xFF3B2948),
            onTertiaryContainer = Color(0xFFF2DAFF),
            error = Color(0xFFFFB4AB),
            background = Color(0xFF1A1C1E),
            onBackground = Color(0xFFE2E2E6),
            surface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFF43474E),
            onSurface = Color(0xFFE2E2E6),
            onSurfaceVariant = Color(0xFFC3C7D0),
            inverseSurface = Color(0xFFE2E2E6),
            inverseOnSurface = Color(0xFF1A1C1E),
            outline = Color(0xFF8D9199)
        ),
        lightExtended = ExtendedColors(
            ripple = Color(0x330061A4),
            selection = Color(0x669ECAFF),
            terminalTextColor = Color(0xFF1A1C1E),
            terminalBackgroundColor = Color(0xFFFDFCFF)
        ),
        darkExtended = ExtendedColors(
            ripple = Color(0x449ECAFF),
            selection = Color(0x6600487D),
            terminalTextColor = Color(0xFFE2E2E6),
            terminalBackgroundColor = Color(0xFF1A1C1E)
        )
    )

    // 静态注册列表：后续如果有第3、第4个从 XML 搬过来的包，直接往这里追加即可
    val allThemes = listOf(ForestMoss, DeepOcean)
}


// ============================================================================
// 4. 全局动态控制器 (运行时修改状态直接触发全屏刷新)
// ============================================================================
enum class NightMode { 
    FOLLOW_SYSTEM, // 跟随系统黑白模式
    FORCE_LIGHT,   // 强制白天亮色
    FORCE_DARK     // 强制黑夜暗色
}

object DynamicThemeManager {
    // 当前启用的内置主题风格包 (可随时赋值切换)
    var currentTheme by mutableStateOf(BuiltInThemes.ForestMoss)
    
    // 当前的昼夜模式控制状态
    var nightMode by mutableStateOf(NightMode.FOLLOW_SYSTEM)
}

// 响应式全局主题组件入口
@Composable
fun AndroidIDETheme(content: @Composable () -> Unit) {
    // 判断最终渲染为白天还是黑夜
    val isDark = when (DynamicThemeManager.nightMode) {
        NightMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        NightMode.FORCE_LIGHT -> false
        NightMode.FORCE_DARK -> true
    }

    // 动态读取当前选中的主题包数据
    val themeSpec = DynamicThemeManager.currentTheme
    val colorScheme = if (isDark) themeSpec.darkColorScheme else themeSpec.lightColorScheme
    val extendedColors = if (isDark) themeSpec.darkExtended else themeSpec.lightExtended

    // 混合分发 Material 3 原生属性与本地自定义扩展扩展属性
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// 统一的属性调用单例 (供应用内任意组件读取)
object IDETheme {
    val colors: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val extended: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
