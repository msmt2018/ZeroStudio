package com.termix.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.termix.libcommons.application
import com.termix.libcommons.isDarkMode
import com.termix.settings.Settings
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

/**
 * Manages terminal color schemes and their application to both the terminal emulator
 * and the app's Material 3 theme.
 * 
 * This singleton handles:
 * - Loading and persisting the selected color scheme
 * - Applying color schemes to the Termux terminal emulator
 * - Generating Material 3 ColorScheme from terminal colors for app UI consistency
 */
object ColorSchemeManager {

    private const val DEFAULT_SCHEME_ID = "default"

    private var appDarkTheme = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> application?.let { isDarkMode(it) } ?: true
    }
    
    /**
     * Observable state for the current color scheme.
     * UI components can observe this to react to scheme changes.
     */
    val currentScheme = mutableStateOf(loadSavedScheme())
    
    /**
     * Weak reference to the terminal view for applying color updates.
     */
    private var terminalViewRef: WeakReference<TerminalView>? = null
    
    /**
     * Loads the saved color scheme from preferences.
     */
    private fun loadSavedScheme(): TerminalColorScheme {
        return resolveSchemeById(Settings.terminal_color_scheme)
    }

    private fun resolveSchemeById(schemeId: String): TerminalColorScheme {
        return if (schemeId == DEFAULT_SCHEME_ID) {
            ColorSchemes.resolveDefaultForAppTheme(appDarkTheme)
        } else {
            ColorSchemes.getById(schemeId) ?: ColorSchemes.resolveDefaultForAppTheme(appDarkTheme)
        }
    }

    fun resolveSchemeForAppTheme(scheme: TerminalColorScheme, isDarkTheme: Boolean): TerminalColorScheme {
        return if (scheme.id == DEFAULT_SCHEME_ID) {
            ColorSchemes.resolveDefaultForAppTheme(isDarkTheme)
        } else {
            scheme
        }
    }

    fun syncDefaultSchemeWithAppTheme(isDarkTheme: Boolean) {
        if (appDarkTheme == isDarkTheme && Settings.terminal_color_scheme != DEFAULT_SCHEME_ID) {
            return
        }
        appDarkTheme = isDarkTheme

        if (Settings.terminal_color_scheme == DEFAULT_SCHEME_ID) {
            val resolved = resolveSchemeById(DEFAULT_SCHEME_ID)
            if (!isSameVisualScheme(currentScheme.value, resolved)) {
                currentScheme.value = resolved
                applyToTerminal(resolved)
            }
        }
    }
    
    /**
     * Sets the terminal view reference for color updates.
     */
    fun setTerminalView(terminalView: TerminalView?) {
        terminalViewRef = terminalView?.let { WeakReference(it) }
    }
    
    /**
     * Gets the current color scheme.
     */
    fun getCurrentScheme(): TerminalColorScheme = currentScheme.value

    fun shouldUseDarkUiText(scheme: TerminalColorScheme = currentScheme.value): Boolean {
        return isArgbColorLight(scheme.background)
    }

    fun isSchemeDark(scheme: TerminalColorScheme = currentScheme.value): Boolean {
        return !shouldUseDarkUiText(scheme)
    }

    fun hasCustomSchemeSelection(): Boolean {
        val selectedSchemeId = Settings.terminal_color_scheme
        return selectedSchemeId != DEFAULT_SCHEME_ID && ColorSchemes.getById(selectedSchemeId) != null
    }
    
    /**
     * Sets and applies a new color scheme.
     * 
     * @param scheme The color scheme to apply
     * @param persist Whether to save the selection to preferences (default: true)
     */
    fun setColorScheme(scheme: TerminalColorScheme, persist: Boolean = true) {
        val resolvedScheme = resolveSchemeForAppTheme(scheme, appDarkTheme)
        currentScheme.value = resolvedScheme

        if (persist) {
            Settings.terminal_color_scheme = scheme.id
        }

        // Apply to terminal
        applyToTerminal(resolvedScheme)
    }
    
    
    /**
     * Applies the current color scheme to the terminal.
     * Call this when the terminal view is created or recreated.
     */
    fun applyCurrentSchemeToTerminal() {
        val resolved = resolveSchemeById(Settings.terminal_color_scheme)
        currentScheme.value = resolved
        applyToTerminal(resolved)
    }

    private fun isSameVisualScheme(a: TerminalColorScheme, b: TerminalColorScheme): Boolean {
        return a.background == b.background &&
            a.foreground == b.foreground &&
            a.cursor == b.cursor &&
            a.isDark == b.isDark
    }

    private fun isArgbColorLight(argb: Int): Boolean {
        return isColorLight(Color(argb))
    }
    
    /**
     * Applies a color scheme to the Termux terminal emulator.
     */
    private fun applyToTerminal(scheme: TerminalColorScheme) {
        // Update the global color scheme
        val props = scheme.toProperties()
        TerminalColors.COLOR_SCHEME.updateWith(props)
        
        // Update the terminal view if available
        terminalViewRef?.get()?.let { terminalView ->
            // Reset colors to the new scheme
            terminalView.mEmulator?.mColors?.reset()
            
            // NOTE: Don't set background color here - let the AndroidView update
            // block handle it, since it knows whether a background image is set
            // (if image is set, terminal view should be transparent)
            
            // Force a redraw
            terminalView.postInvalidate()
            terminalView.onScreenUpdated()
        }
    }
    
    
    /**
     * Generates a Material 3 ColorScheme based on the terminal color scheme.
     * This ensures visual consistency between the terminal and app UI.
     */
    fun generateMaterial3ColorScheme(scheme: TerminalColorScheme = currentScheme.value): ColorScheme {
        val background = Color(scheme.background)
        val foreground = Color(scheme.foreground)
        val primary = Color(scheme.blue)
        val secondary = Color(scheme.cyan)
        val tertiary = Color(scheme.magenta)
        val error = Color(scheme.red)
        
        return if (scheme.isDark) {
            generateDarkColorScheme(
                background = background,
                foreground = foreground,
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                error = error,
                scheme = scheme
            )
        } else {
            generateLightColorScheme(
                background = background,
                foreground = foreground,
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                error = error,
                scheme = scheme
            )
        }
    }
    
    /**
     * Generates a dark Material 3 ColorScheme from terminal colors.
     */
    private fun generateDarkColorScheme(
        background: Color,
        foreground: Color,
        primary: Color,
        secondary: Color,
        tertiary: Color,
        error: Color,
        scheme: TerminalColorScheme
    ): ColorScheme {
        // Derive surface colors from background
        val surface = background
        val surfaceVariant = blendColors(background, foreground, 0.08f)
        val surfaceContainer = blendColors(background, foreground, 0.05f)
        val surfaceContainerHigh = blendColors(background, foreground, 0.10f)
        val surfaceContainerLow = blendColors(background, foreground, 0.03f)
        
        // Derive on-colors (text colors)
        val onBackground = foreground
        val onSurface = foreground
        val onSurfaceVariant = blendColors(foreground, background, 0.2f)
        
        // Derive container colors
        val primaryContainer = blendColors(primary, background, 0.7f)
        val secondaryContainer = blendColors(secondary, background, 0.7f)
        val tertiaryContainer = blendColors(tertiary, background, 0.7f)
        val errorContainer = blendColors(error, background, 0.7f)
        
        // Derive on-container colors
        val onPrimary = if (isColorLight(primary)) Color.Black else Color.White
        val onSecondary = if (isColorLight(secondary)) Color.Black else Color.White
        val onTertiary = if (isColorLight(tertiary)) Color.Black else Color.White
        val onError = if (isColorLight(error)) Color.Black else Color.White
        
        val onPrimaryContainer = blendColors(primary, Color.White, 0.3f)
        val onSecondaryContainer = blendColors(secondary, Color.White, 0.3f)
        val onTertiaryContainer = blendColors(tertiary, Color.White, 0.3f)
        val onErrorContainer = blendColors(error, Color.White, 0.3f)
        
        // Outline colors
        val outline = blendColors(foreground, background, 0.5f)
        val outlineVariant = blendColors(foreground, background, 0.7f)
        
        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = foreground,
            inverseOnSurface = background,
            inversePrimary = blendColors(primary, Color.Black, 0.3f),
            surfaceTint = primary,
            scrim = Color.Black
        ).copy(
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = blendColors(background, foreground, 0.14f),
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = blendColors(background, foreground, 0.01f),
            surfaceBright = blendColors(background, foreground, 0.12f),
            surfaceDim = blendColors(background, Color.Black, 0.1f)
        )
    }
    
    /**
     * Generates a light Material 3 ColorScheme from terminal colors.
     */
    private fun generateLightColorScheme(
        background: Color,
        foreground: Color,
        primary: Color,
        secondary: Color,
        tertiary: Color,
        error: Color,
        scheme: TerminalColorScheme
    ): ColorScheme {
        // Derive surface colors from background
        val surface = background
        val surfaceVariant = blendColors(background, foreground, 0.05f)
        val surfaceContainer = blendColors(background, foreground, 0.03f)
        val surfaceContainerHigh = blendColors(background, foreground, 0.08f)
        val surfaceContainerLow = blendColors(background, foreground, 0.02f)
        
        // Derive on-colors (text colors)
        val onBackground = foreground
        val onSurface = foreground
        val onSurfaceVariant = blendColors(foreground, background, 0.3f)
        
        // For light themes, darken the primary colors slightly for better contrast
        val adjustedPrimary = darkenColor(primary, 0.1f)
        val adjustedSecondary = darkenColor(secondary, 0.1f)
        val adjustedTertiary = darkenColor(tertiary, 0.1f)
        val adjustedError = darkenColor(error, 0.1f)
        
        // Derive container colors (lighter versions)
        val primaryContainer = blendColors(adjustedPrimary, background, 0.85f)
        val secondaryContainer = blendColors(adjustedSecondary, background, 0.85f)
        val tertiaryContainer = blendColors(adjustedTertiary, background, 0.85f)
        val errorContainer = blendColors(adjustedError, background, 0.85f)
        
        // Derive on-container colors
        val onPrimary = Color.White
        val onSecondary = Color.White
        val onTertiary = Color.White
        val onError = Color.White
        
        val onPrimaryContainer = darkenColor(adjustedPrimary, 0.3f)
        val onSecondaryContainer = darkenColor(adjustedSecondary, 0.3f)
        val onTertiaryContainer = darkenColor(adjustedTertiary, 0.3f)
        val onErrorContainer = darkenColor(adjustedError, 0.3f)
        
        // Outline colors
        val outline = blendColors(foreground, background, 0.5f)
        val outlineVariant = blendColors(foreground, background, 0.3f)
        
        return lightColorScheme(
            primary = adjustedPrimary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = adjustedSecondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = adjustedTertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = adjustedError,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = foreground,
            inverseOnSurface = background,
            inversePrimary = blendColors(adjustedPrimary, Color.White, 0.3f),
            surfaceTint = adjustedPrimary,
            scrim = Color.Black
        ).copy(
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = blendColors(background, foreground, 0.10f),
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = blendColors(background, foreground, 0.01f),
            surfaceBright = blendColors(background, Color.White, 0.05f),
            surfaceDim = blendColors(background, foreground, 0.06f)
        )
    }
    
    /**
     * Blends two colors together.
     * @param color1 The first color
     * @param color2 The second color
     * @param ratio The blend ratio (0.0 = all color1, 1.0 = all color2)
     */
    private fun blendColors(color1: Color, color2: Color, ratio: Float): Color {
        val inverseRatio = 1f - ratio
        return Color(
            red = color1.red * inverseRatio + color2.red * ratio,
            green = color1.green * inverseRatio + color2.green * ratio,
            blue = color1.blue * inverseRatio + color2.blue * ratio,
            alpha = 1f
        )
    }
    
    /**
     * Darkens a color by a given factor.
     */
    private fun darkenColor(color: Color, factor: Float): Color {
        return Color(
            red = color.red * (1f - factor),
            green = color.green * (1f - factor),
            blue = color.blue * (1f - factor),
            alpha = color.alpha
        )
    }
    
    /**
     * Determines if a color is light (high luminance).
     */
    private fun isColorLight(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance > 0.5f
    }
    
    }
