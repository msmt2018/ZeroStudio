package com.termix.ui.theme.colorscheme

import com.termix.ui.theme.colorscheme.TerminalColorScheme.Companion.hex

/**
 * Collection of bundled terminal color schemes.
 * 
 * All color schemes are based on popular terminal themes with accurate color values.
 * Each scheme includes both ANSI 16 colors and special colors (foreground, background, cursor).
 */
object ColorSchemes {

    private const val DEFAULT_SCHEME_ID = "default"
    
    private val DefaultDarkTemplate = TerminalColorScheme(
        id = "default_dark",
        name = "Default",
        isDark = true,
        black = hex("#000000"),
        red = hex("#cd0000"),
        green = hex("#00cd00"),
        yellow = hex("#cdcd00"),
        blue = hex("#6495ed"),
        magenta = hex("#cd00cd"),
        cyan = hex("#00cdcd"),
        white = hex("#e5e5e5"),
        brightBlack = hex("#7f7f7f"),
        brightRed = hex("#ff0000"),
        brightGreen = hex("#00ff00"),
        brightYellow = hex("#ffff00"),
        brightBlue = hex("#5c5cff"),
        brightMagenta = hex("#ff00ff"),
        brightCyan = hex("#00ffff"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#ffffff"),
        background = hex("#000000"),
        cursor = hex("#ffffff")
    )

    private val DefaultLightTemplate = TerminalColorScheme(
        id = "default_light",
        name = "Default",
        isDark = false,
        black = hex("#000000"),
        red = hex("#cd0000"),
        green = hex("#00cd00"),
        yellow = hex("#cdcd00"),
        blue = hex("#005faf"),
        magenta = hex("#cd00cd"),
        cyan = hex("#008787"),
        white = hex("#666666"),
        brightBlack = hex("#7f7f7f"),
        brightRed = hex("#ff0000"),
        brightGreen = hex("#00af00"),
        brightYellow = hex("#b57614"),
        brightBlue = hex("#005faf"),
        brightMagenta = hex("#ff00ff"),
        brightCyan = hex("#00afaf"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#1f1f1f"),
        background = hex("#ffffff"),
        cursor = hex("#1f1f1f")
    )

    val Default = resolveDefaultForAppTheme(isDark = true)

    fun resolveDefaultForAppTheme(isDark: Boolean): TerminalColorScheme {
        val template = if (isDark) DefaultDarkTemplate else DefaultLightTemplate
        return template.copy(id = DEFAULT_SCHEME_ID, name = "Default")
    }
    
    /**
     * Dracula - A dark theme with vibrant colors.
     * https://draculatheme.com/
     */
    val Dracula = TerminalColorScheme(
        id = "dracula",
        name = "Dracula",
        isDark = true,
        black = hex("#21222c"),
        red = hex("#ff5555"),
        green = hex("#50fa7b"),
        yellow = hex("#f1fa8c"),
        blue = hex("#bd93f9"),
        magenta = hex("#ff79c6"),
        cyan = hex("#8be9fd"),
        white = hex("#f8f8f2"),
        brightBlack = hex("#6272a4"),
        brightRed = hex("#ff6e6e"),
        brightGreen = hex("#69ff94"),
        brightYellow = hex("#ffffa5"),
        brightBlue = hex("#d6acff"),
        brightMagenta = hex("#ff92df"),
        brightCyan = hex("#a4ffff"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#f8f8f2"),
        background = hex("#282a36"),
        cursor = hex("#f8f8f2")
    )
    
    /**
     * Nord - An arctic, north-bluish color palette.
     * https://www.nordtheme.com/
     */
    val Nord = TerminalColorScheme(
        id = "nord",
        name = "Nord",
        isDark = true,
        black = hex("#3b4252"),
        red = hex("#bf616a"),
        green = hex("#a3be8c"),
        yellow = hex("#ebcb8b"),
        blue = hex("#81a1c1"),
        magenta = hex("#b48ead"),
        cyan = hex("#88c0d0"),
        white = hex("#e5e9f0"),
        brightBlack = hex("#4c566a"),
        brightRed = hex("#bf616a"),
        brightGreen = hex("#a3be8c"),
        brightYellow = hex("#ebcb8b"),
        brightBlue = hex("#81a1c1"),
        brightMagenta = hex("#b48ead"),
        brightCyan = hex("#8fbcbb"),
        brightWhite = hex("#eceff4"),
        foreground = hex("#d8dee9"),
        background = hex("#2e3440"),
        cursor = hex("#d8dee9")
    )
    
    /**
     * Solarized Dark - Precision colors for machines and people.
     * https://ethanschoonover.com/solarized/
     */
    val SolarizedDark = TerminalColorScheme(
        id = "solarized_dark",
        name = "Solarized Dark",
        isDark = true,
        black = hex("#073642"),
        red = hex("#dc322f"),
        green = hex("#859900"),
        yellow = hex("#b58900"),
        blue = hex("#268bd2"),
        magenta = hex("#d33682"),
        cyan = hex("#2aa198"),
        white = hex("#eee8d5"),
        brightBlack = hex("#002b36"),
        brightRed = hex("#cb4b16"),
        brightGreen = hex("#586e75"),
        brightYellow = hex("#657b83"),
        brightBlue = hex("#839496"),
        brightMagenta = hex("#6c71c4"),
        brightCyan = hex("#93a1a1"),
        brightWhite = hex("#fdf6e3"),
        foreground = hex("#839496"),
        background = hex("#002b36"),
        cursor = hex("#839496")
    )
    
    /**
     * Solarized Light - Light variant of Solarized.
     * https://ethanschoonover.com/solarized/
     */
    val SolarizedLight = TerminalColorScheme(
        id = "solarized_light",
        name = "Solarized Light",
        isDark = false,
        black = hex("#073642"),
        red = hex("#dc322f"),
        green = hex("#859900"),
        yellow = hex("#b58900"),
        blue = hex("#268bd2"),
        magenta = hex("#d33682"),
        cyan = hex("#2aa198"),
        white = hex("#eee8d5"),
        brightBlack = hex("#002b36"),
        brightRed = hex("#cb4b16"),
        brightGreen = hex("#586e75"),
        brightYellow = hex("#657b83"),
        brightBlue = hex("#839496"),
        brightMagenta = hex("#6c71c4"),
        brightCyan = hex("#93a1a1"),
        brightWhite = hex("#fdf6e3"),
        foreground = hex("#657b83"),
        background = hex("#fdf6e3"),
        cursor = hex("#657b83")
    )
    
    /**
     * Gruvbox Dark - Retro groove color scheme.
     * https://github.com/morhetz/gruvbox
     */
    val GruvboxDark = TerminalColorScheme(
        id = "gruvbox_dark",
        name = "Gruvbox Dark",
        isDark = true,
        black = hex("#282828"),
        red = hex("#cc241d"),
        green = hex("#98971a"),
        yellow = hex("#d79921"),
        blue = hex("#458588"),
        magenta = hex("#b16286"),
        cyan = hex("#689d6a"),
        white = hex("#a89984"),
        brightBlack = hex("#928374"),
        brightRed = hex("#fb4934"),
        brightGreen = hex("#b8bb26"),
        brightYellow = hex("#fabd2f"),
        brightBlue = hex("#83a598"),
        brightMagenta = hex("#d3869b"),
        brightCyan = hex("#8ec07c"),
        brightWhite = hex("#ebdbb2"),
        foreground = hex("#ebdbb2"),
        background = hex("#282828"),
        cursor = hex("#ebdbb2")
    )
    
    /**
     * Gruvbox Light - Light variant of Gruvbox.
     * https://github.com/morhetz/gruvbox
     */
    val GruvboxLight = TerminalColorScheme(
        id = "gruvbox_light",
        name = "Gruvbox Light",
        isDark = false,
        black = hex("#fbf1c7"),
        red = hex("#cc241d"),
        green = hex("#98971a"),
        yellow = hex("#d79921"),
        blue = hex("#458588"),
        magenta = hex("#b16286"),
        cyan = hex("#689d6a"),
        white = hex("#7c6f64"),
        brightBlack = hex("#928374"),
        brightRed = hex("#9d0006"),
        brightGreen = hex("#79740e"),
        brightYellow = hex("#b57614"),
        brightBlue = hex("#076678"),
        brightMagenta = hex("#8f3f71"),
        brightCyan = hex("#427b58"),
        brightWhite = hex("#3c3836"),
        foreground = hex("#3c3836"),
        background = hex("#fbf1c7"),
        cursor = hex("#3c3836")
    )
    
    /**
     * One Dark - Atom's iconic One Dark theme.
     * https://github.com/atom/atom/tree/master/packages/one-dark-syntax
     */
    val OneDark = TerminalColorScheme(
        id = "one_dark",
        name = "One Dark",
        isDark = true,
        black = hex("#282c34"),
        red = hex("#e06c75"),
        green = hex("#98c379"),
        yellow = hex("#e5c07b"),
        blue = hex("#61afef"),
        magenta = hex("#c678dd"),
        cyan = hex("#56b6c2"),
        white = hex("#abb2bf"),
        brightBlack = hex("#5c6370"),
        brightRed = hex("#e06c75"),
        brightGreen = hex("#98c379"),
        brightYellow = hex("#e5c07b"),
        brightBlue = hex("#61afef"),
        brightMagenta = hex("#c678dd"),
        brightCyan = hex("#56b6c2"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#abb2bf"),
        background = hex("#282c34"),
        cursor = hex("#528bff")
    )
    
    /**
     * Tokyo Night - A clean, dark theme inspired by Tokyo city lights.
     * https://github.com/enkia/tokyo-night-vscode-theme
     */
    val TokyoNight = TerminalColorScheme(
        id = "tokyo_night",
        name = "Tokyo Night",
        isDark = true,
        black = hex("#15161e"),
        red = hex("#f7768e"),
        green = hex("#9ece6a"),
        yellow = hex("#e0af68"),
        blue = hex("#7aa2f7"),
        magenta = hex("#bb9af7"),
        cyan = hex("#7dcfff"),
        white = hex("#a9b1d6"),
        brightBlack = hex("#414868"),
        brightRed = hex("#f7768e"),
        brightGreen = hex("#9ece6a"),
        brightYellow = hex("#e0af68"),
        brightBlue = hex("#7aa2f7"),
        brightMagenta = hex("#bb9af7"),
        brightCyan = hex("#7dcfff"),
        brightWhite = hex("#c0caf5"),
        foreground = hex("#a9b1d6"),
        background = hex("#1a1b26"),
        cursor = hex("#c0caf5")
    )
    
    /**
     * Tokyo Night Light - Light variant of Tokyo Night.
     */
    val TokyoNightLight = TerminalColorScheme(
        id = "tokyo_night_light",
        name = "Tokyo Night Light",
        isDark = false,
        black = hex("#0f0f14"),
        red = hex("#8c4351"),
        green = hex("#485e30"),
        yellow = hex("#8f5e15"),
        blue = hex("#34548a"),
        magenta = hex("#5a4a78"),
        cyan = hex("#0f4b6e"),
        white = hex("#343b58"),
        brightBlack = hex("#9699a3"),
        brightRed = hex("#8c4351"),
        brightGreen = hex("#485e30"),
        brightYellow = hex("#8f5e15"),
        brightBlue = hex("#34548a"),
        brightMagenta = hex("#5a4a78"),
        brightCyan = hex("#0f4b6e"),
        brightWhite = hex("#343b58"),
        foreground = hex("#343b58"),
        background = hex("#d5d6db"),
        cursor = hex("#343b58")
    )
    
    /**
     * Catppuccin Mocha - Soothing pastel theme (darkest variant).
     * https://github.com/catppuccin/catppuccin
     */
    val CatppuccinMocha = TerminalColorScheme(
        id = "catppuccin_mocha",
        name = "Catppuccin Mocha",
        isDark = true,
        black = hex("#45475a"),
        red = hex("#f38ba8"),
        green = hex("#a6e3a1"),
        yellow = hex("#f9e2af"),
        blue = hex("#89b4fa"),
        magenta = hex("#f5c2e7"),
        cyan = hex("#94e2d5"),
        white = hex("#bac2de"),
        brightBlack = hex("#585b70"),
        brightRed = hex("#f38ba8"),
        brightGreen = hex("#a6e3a1"),
        brightYellow = hex("#f9e2af"),
        brightBlue = hex("#89b4fa"),
        brightMagenta = hex("#f5c2e7"),
        brightCyan = hex("#94e2d5"),
        brightWhite = hex("#a6adc8"),
        foreground = hex("#cdd6f4"),
        background = hex("#1e1e2e"),
        cursor = hex("#f5e0dc")
    )
    
    /**
     * Catppuccin Latte - Soothing pastel theme (light variant).
     * https://github.com/catppuccin/catppuccin
     */
    val CatppuccinLatte = TerminalColorScheme(
        id = "catppuccin_latte",
        name = "Catppuccin Latte",
        isDark = false,
        black = hex("#5c5f77"),
        red = hex("#d20f39"),
        green = hex("#40a02b"),
        yellow = hex("#df8e1d"),
        blue = hex("#1e66f5"),
        magenta = hex("#ea76cb"),
        cyan = hex("#179299"),
        white = hex("#acb0be"),
        brightBlack = hex("#6c6f85"),
        brightRed = hex("#d20f39"),
        brightGreen = hex("#40a02b"),
        brightYellow = hex("#df8e1d"),
        brightBlue = hex("#1e66f5"),
        brightMagenta = hex("#ea76cb"),
        brightCyan = hex("#179299"),
        brightWhite = hex("#bcc0cc"),
        foreground = hex("#4c4f69"),
        background = hex("#eff1f5"),
        cursor = hex("#dc8a78")
    )
    
    /**
     * Monokai - Classic Monokai color scheme.
     */
    val Monokai = TerminalColorScheme(
        id = "monokai",
        name = "Monokai",
        isDark = true,
        black = hex("#272822"),
        red = hex("#f92672"),
        green = hex("#a6e22e"),
        yellow = hex("#f4bf75"),
        blue = hex("#66d9ef"),
        magenta = hex("#ae81ff"),
        cyan = hex("#a1efe4"),
        white = hex("#f8f8f2"),
        brightBlack = hex("#75715e"),
        brightRed = hex("#f92672"),
        brightGreen = hex("#a6e22e"),
        brightYellow = hex("#f4bf75"),
        brightBlue = hex("#66d9ef"),
        brightMagenta = hex("#ae81ff"),
        brightCyan = hex("#a1efe4"),
        brightWhite = hex("#f9f8f5"),
        foreground = hex("#f8f8f2"),
        background = hex("#272822"),
        cursor = hex("#f8f8f2")
    )
    
    /**
     * Material Dark - Material Design inspired dark theme.
     */
    val MaterialDark = TerminalColorScheme(
        id = "material_dark",
        name = "Material Dark",
        isDark = true,
        black = hex("#212121"),
        red = hex("#f07178"),
        green = hex("#c3e88d"),
        yellow = hex("#ffcb6b"),
        blue = hex("#82aaff"),
        magenta = hex("#c792ea"),
        cyan = hex("#89ddff"),
        white = hex("#eeffff"),
        brightBlack = hex("#545454"),
        brightRed = hex("#f07178"),
        brightGreen = hex("#c3e88d"),
        brightYellow = hex("#ffcb6b"),
        brightBlue = hex("#82aaff"),
        brightMagenta = hex("#c792ea"),
        brightCyan = hex("#89ddff"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#eeffff"),
        background = hex("#212121"),
        cursor = hex("#ffcc00")
    )
    
    /**
     * Ayu Dark - Ayu theme dark variant.
     */
    val AyuDark = TerminalColorScheme(
        id = "ayu_dark",
        name = "Ayu Dark",
        isDark = true,
        black = hex("#0a0e14"),
        red = hex("#ff3333"),
        green = hex("#b8cc52"),
        yellow = hex("#e7c547"),
        blue = hex("#36a3d9"),
        magenta = hex("#f07178"),
        cyan = hex("#95e6cb"),
        white = hex("#b3b1ad"),
        brightBlack = hex("#626a73"),
        brightRed = hex("#ff6565"),
        brightGreen = hex("#eafe84"),
        brightYellow = hex("#fff779"),
        brightBlue = hex("#68d5ff"),
        brightMagenta = hex("#ffa3aa"),
        brightCyan = hex("#c7fffd"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#b3b1ad"),
        background = hex("#0a0e14"),
        cursor = hex("#e6b450")
    )
    
    /**
     * Ayu Light - Ayu theme light variant.
     */
    val AyuLight = TerminalColorScheme(
        id = "ayu_light",
        name = "Ayu Light",
        isDark = false,
        black = hex("#000000"),
        red = hex("#ff3333"),
        green = hex("#86b300"),
        yellow = hex("#f29718"),
        blue = hex("#41a6d9"),
        magenta = hex("#f07178"),
        cyan = hex("#4dbf99"),
        white = hex("#ffffff"),
        brightBlack = hex("#323232"),
        brightRed = hex("#ff6565"),
        brightGreen = hex("#b8e532"),
        brightYellow = hex("#ffc94a"),
        brightBlue = hex("#73d8ff"),
        brightMagenta = hex("#ffa3aa"),
        brightCyan = hex("#7ff1cb"),
        brightWhite = hex("#ffffff"),
        foreground = hex("#5c6166"),
        background = hex("#fafafa"),
        cursor = hex("#ff6a00")
    )
    
    /**
     * All available color schemes.
     */
    val all: List<TerminalColorScheme> = listOf(
        Default,
        Dracula,
        Nord,
        OneDark,
        TokyoNight,
        TokyoNightLight,
        CatppuccinMocha,
        CatppuccinLatte,
        SolarizedDark,
        SolarizedLight,
        GruvboxDark,
        GruvboxLight,
        Monokai,
        MaterialDark,
        AyuDark,
        AyuLight
    )
    
    /**
     * Dark color schemes only.
     */
    val darkSchemes: List<TerminalColorScheme> = all.filter { it.isDark }
    
    /**
     * Light color schemes only.
     */
    val lightSchemes: List<TerminalColorScheme> = all.filter { !it.isDark }
    
    /**
     * Get a color scheme by its ID.
     */
    fun getById(id: String): TerminalColorScheme? = all.find { it.id == id }
    
    /**
     * Get a color scheme by its ID, or return the default scheme.
     */
    fun getByIdOrDefault(id: String): TerminalColorScheme = getById(id) ?: Default
}
