package android.zero.studio.termux.ui.theme

import android.content.Context
import android.graphics.Color
import android.zero.studio.termux.TermuxFragment
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle

/**
 * Manages terminal color themes with persistence. Contains a massive collection of 100+
 * professional themes including Material, Monokai, Solarized, Retro, and more.
 *
 * @author android_zero
 */
class TerminalThemeManager(private val context: Context) {

  companion object {
    private const val PREFS_NAME = "terminal_theme_settings"
    private const val KEY_CURRENT_THEME = "current_theme_key"
  }

  data class Theme(
      val name: String,
      val background: Int,
      val foreground: Int,
      val cursor: Int,
      val selection: Int = Color.parseColor("#40FFFFFF"), // Default selection color
  )

  // --- Massive Theme Collection ---
  private val themes =
      listOf(
          // === Default & Classics ===
          Theme(
              "Default (Dark)",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Light",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#000000"),
              Color.parseColor("#000000"),
          ),

          // === Material Design Inspired ===
          Theme(
              "Material Dark",
              Color.parseColor("#212121"),
              Color.parseColor("#EEFFFF"),
              Color.parseColor("#80CBC4"),
          ),
          Theme(
              "Material Light",
              Color.parseColor("#FAFAFA"),
              Color.parseColor("#212121"),
              Color.parseColor("#009688"),
          ),
          Theme(
              "Material Ocean",
              Color.parseColor("#0F111A"),
              Color.parseColor("#8F93A2"),
              Color.parseColor("#FFCC00"),
          ),
          Theme(
              "Material Palenight",
              Color.parseColor("#292D3E"),
              Color.parseColor("#A6ACCD"),
              Color.parseColor("#80CBC4"),
          ),
          Theme(
              "Material Deep Ocean",
              Color.parseColor("#0F111A"),
              Color.parseColor("#8F93A2"),
              Color.parseColor("#82Aaff"),
          ),
          Theme(
              "Material Lighter",
              Color.parseColor("#FAFAFA"),
              Color.parseColor("#546E7A"),
              Color.parseColor("#80CBC4"),
          ),
          Theme(
              "Material Darker",
              Color.parseColor("#212121"),
              Color.parseColor("#EEFFFF"),
              Color.parseColor("#FFCC00"),
          ),
          Theme(
              "Material Volcano",
              Color.parseColor("#2C0A0E"),
              Color.parseColor("#FF5722"),
              Color.parseColor("#FF9800"),
          ),

          // === Material 3 Style (Dynamic-ish tones) ===
          Theme(
              "M3 Surface",
              Color.parseColor("#1C1B1F"),
              Color.parseColor("#E6E1E5"),
              Color.parseColor("#D0BCFF"),
          ),
          Theme(
              "M3 Surface Variant",
              Color.parseColor("#49454F"),
              Color.parseColor("#CAC4D0"),
              Color.parseColor("#EADDFF"),
          ),
          Theme(
              "M3 Light Primary",
              Color.parseColor("#FDFCFF"),
              Color.parseColor("#1A1C1E"),
              Color.parseColor("#0061A4"),
          ),
          Theme(
              "M3 Dark Primary",
              Color.parseColor("#1A1C1E"),
              Color.parseColor("#E2E2E6"),
              Color.parseColor("#9FCAFF"),
          ),

          // === Popular IDE & Editor Themes ===
          Theme(
              "Dracula",
              Color.parseColor("#282A36"),
              Color.parseColor("#F8F8F2"),
              Color.parseColor("#FF79C6"),
          ),
          Theme(
              "Monokai",
              Color.parseColor("#272822"),
              Color.parseColor("#F8F8F2"),
              Color.parseColor("#F92672"),
          ),
          Theme(
              "Monokai Pro",
              Color.parseColor("#2D2A2E"),
              Color.parseColor("#FCFCFA"),
              Color.parseColor("#FFD866"),
          ),
          Theme(
              "Monokai Soda",
              Color.parseColor("#222222"),
              Color.parseColor("#F6F6F6"),
              Color.parseColor("#F92672"),
          ),
          Theme(
              "Solarized Dark",
              Color.parseColor("#002B36"),
              Color.parseColor("#839496"),
              Color.parseColor("#93A1A1"),
          ),
          Theme(
              "Solarized Light",
              Color.parseColor("#FDF6E3"),
              Color.parseColor("#657B83"),
              Color.parseColor("#586E75"),
          ),
          Theme(
              "Nord",
              Color.parseColor("#2E3440"),
              Color.parseColor("#D8DEE9"),
              Color.parseColor("#88C0D0"),
          ),
          Theme(
              "One Dark",
              Color.parseColor("#282C34"),
              Color.parseColor("#ABB2BF"),
              Color.parseColor("#528BFF"),
          ),
          Theme(
              "One Light",
              Color.parseColor("#FAFAFA"),
              Color.parseColor("#383A42"),
              Color.parseColor("#528BFF"),
          ),
          Theme(
              "Gruvbox Dark",
              Color.parseColor("#282828"),
              Color.parseColor("#EBDBB2"),
              Color.parseColor("#FE8019"),
          ),
          Theme(
              "Gruvbox Light",
              Color.parseColor("#FBF1C7"),
              Color.parseColor("#3C3836"),
              Color.parseColor("#928374"),
          ),
          Theme(
              "GitHub Dark",
              Color.parseColor("#24292E"),
              Color.parseColor("#D1D5DA"),
              Color.parseColor("#C8E1FF"),
          ),
          Theme(
              "GitHub Light",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#24292E"),
              Color.parseColor("#0366D6"),
          ),
          Theme(
              "Atom",
              Color.parseColor("#161719"),
              Color.parseColor("#C5C8C6"),
              Color.parseColor("#FD5FF1"),
          ),
          Theme(
              "Sublime Snazzy",
              Color.parseColor("#282A36"),
              Color.parseColor("#EFF0EB"),
              Color.parseColor("#FF5C57"),
          ),

          // === Retro & Hacker Styles ===
          Theme(
              "Retro Hacker Green",
              Color.parseColor("#0D1117"),
              Color.parseColor("#00FF00"),
              Color.parseColor("#00FF00"),
          ),
          Theme(
              "Retro Amber",
              Color.parseColor("#1B1B1B"),
              Color.parseColor("#FFB000"),
              Color.parseColor("#FFB000"),
          ),
          Theme(
              "Retro CRT",
              Color.parseColor("#111111"),
              Color.parseColor("#33FF00"),
              Color.parseColor("#33FF00"),
          ),
          Theme(
              "Phosphor",
              Color.parseColor("#212121"),
              Color.parseColor("#21F121"),
              Color.parseColor("#21F121"),
          ),
          Theme(
              "Cyberpunk",
              Color.parseColor("#000B1E"),
              Color.parseColor("#00FF9C"),
              Color.parseColor("#FF0055"),
          ),
          Theme(
              "Matrix",
              Color.parseColor("#000000"),
              Color.parseColor("#00FF00"),
              Color.parseColor("#003300"),
          ),
          Theme(
              "Blueprint",
              Color.parseColor("#1F2947"),
              Color.parseColor("#82AAFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "IBM 3270",
              Color.parseColor("#000000"),
              Color.parseColor("#20C20E"),
              Color.parseColor("#20C20E"),
          ),

          // === Nature & Earth Tones ===
          Theme(
              "Forest",
              Color.parseColor("#1B2B34"),
              Color.parseColor("#ADD8E6"),
              Color.parseColor("#99C794"),
          ),
          Theme(
              "Jungle",
              Color.parseColor("#1F2823"),
              Color.parseColor("#99B898"),
              Color.parseColor("#5C8C6E"),
          ),
          Theme(
              "Earth",
              Color.parseColor("#262220"),
              Color.parseColor("#DDC7A1"),
              Color.parseColor("#A89984"),
          ),
          Theme(
              "Autumn",
              Color.parseColor("#292220"),
              Color.parseColor("#D19A66"),
              Color.parseColor("#E06C75"),
          ),
          Theme(
              "Oceanic Next",
              Color.parseColor("#1B2B34"),
              Color.parseColor("#D8DEE9"),
              Color.parseColor("#6699CC"),
          ),
          Theme(
              "Seafoam",
              Color.parseColor("#14181D"),
              Color.parseColor("#D3E0DC"),
              Color.parseColor("#8BD8BD"),
          ),
          Theme(
              "Deep Sea",
              Color.parseColor("#09101D"),
              Color.parseColor("#566E88"),
              Color.parseColor("#00A8E8"),
          ),
          Theme(
              "Sandstorm",
              Color.parseColor("#FDF6E3"),
              Color.parseColor("#5F5A4F"),
              Color.parseColor("#A3685A"),
          ),

          // === Vibrant & Colorful ===
          Theme(
              "Dracula Pro",
              Color.parseColor("#22212C"),
              Color.parseColor("#F8F8F2"),
              Color.parseColor("#9580FF"),
          ),
          Theme(
              "Purple Rain",
              Color.parseColor("#200933"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FF00FF"),
          ),
          Theme(
              "Neon Night",
              Color.parseColor("#0D0029"),
              Color.parseColor("#00FFFF"),
              Color.parseColor("#FF00AA"),
          ),
          Theme(
              "Synthwave",
              Color.parseColor("#2B213A"),
              Color.parseColor("#FF7EDB"),
              Color.parseColor("#36F9F6"),
          ),
          Theme(
              "Outrun",
              Color.parseColor("#0D0221"),
              Color.parseColor("#A6FCDB"),
              Color.parseColor("#FF003C"),
          ),
          Theme(
              "Laser",
              Color.parseColor("#230633"),
              Color.parseColor("#D60270"),
              Color.parseColor("#00FFFF"),
          ),
          Theme(
              "Miami",
              Color.parseColor("#101E29"),
              Color.parseColor("#00FF9C"),
              Color.parseColor("#E06C75"),
          ),
          Theme(
              "Hot Dog Stand",
              Color.parseColor("#FF0000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFF00"),
          ), // High contrast/Joke theme

          // === Smooth & Pastel ===
          Theme(
              "Pastel",
              Color.parseColor("#282C34"),
              Color.parseColor("#D4BFFF"),
              Color.parseColor("#FFBFA9"),
          ),
          Theme(
              "Lavender",
              Color.parseColor("#292D3E"),
              Color.parseColor("#B4B9FF"),
              Color.parseColor("#FF80BF"),
          ),
          Theme(
              "Fairy Floss",
              Color.parseColor("#5A5475"),
              Color.parseColor("#F8F8F0"),
              Color.parseColor("#FF857F"),
          ),
          Theme(
              "Cotton Candy",
              Color.parseColor("#EAF5FF"),
              Color.parseColor("#5C6166"),
              Color.parseColor("#FF80BF"),
          ),

          // === High Contrast / Accessibility ===
          Theme(
              "High Contrast Black",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFF00"),
          ),
          Theme(
              "High Contrast White",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#000000"),
              Color.parseColor("#FF0000"),
          ),
          Theme(
              "Blue Matrix",
              Color.parseColor("#001122"),
              Color.parseColor("#0088FF"),
              Color.parseColor("#FFFFFF"),
          ),

          // === Minimalist ===
          Theme(
              "Zenburn",
              Color.parseColor("#3F3F3F"),
              Color.parseColor("#DCDCCC"),
              Color.parseColor("#DCDCCC"),
          ),
          Theme(
              "Minimal",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#333333"),
              Color.parseColor("#999999"),
          ),
          Theme(
              "Ghost",
              Color.parseColor("#1A1A1A"),
              Color.parseColor("#AAAAAA"),
              Color.parseColor("#555555"),
          ),
          Theme(
              "Snow",
              Color.parseColor("#FBFBFB"),
              Color.parseColor("#4D4D4D"),
              Color.parseColor("#4D4D4D"),
          ),

          // === Space Themes ===
          Theme(
              "SpaceGray",
              Color.parseColor("#343D46"),
              Color.parseColor("#C0C5CE"),
              Color.parseColor("#A7ADBA"),
          ),
          Theme(
              "Andromeda",
              Color.parseColor("#23262E"),
              Color.parseColor("#D5CED9"),
              Color.parseColor("#00E8C6"),
          ),
          Theme(
              "Deep Space",
              Color.parseColor("#1C1F26"),
              Color.parseColor("#9196A1"),
              Color.parseColor("#5A6172"),
          ),
          Theme(
              "Cosmic",
              Color.parseColor("#0D0D18"),
              Color.parseColor("#E0E0E0"),
              Color.parseColor("#4D4DFF"),
          ),
          Theme(
              "Galaxy",
              Color.parseColor("#110524"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFE900"),
          ),

          // === Brand / OS Specific ===
          Theme(
              "Ubuntu",
              Color.parseColor("#300A24"),
              Color.parseColor("#EEEEEC"),
              Color.parseColor("#E95420"),
          ),
          Theme(
              "Red Hat",
              Color.parseColor("#000000"),
              Color.parseColor("#CC0000"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Manjaro",
              Color.parseColor("#263238"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#2ECC71"),
          ),
          Theme(
              "Arch",
              Color.parseColor("#0F0F0F"),
              Color.parseColor("#1793D1"),
              Color.parseColor("#EEEEEE"),
          ),
          Theme(
              "Windows 10",
              Color.parseColor("#0C0C0C"),
              Color.parseColor("#CCCCCC"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "PowerShell",
              Color.parseColor("#012456"),
              Color.parseColor("#CCCCCC"),
              Color.parseColor("#CCCCCC"),
          ),
          Theme(
              "MacOS Terminal",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#000000"),
              Color.parseColor("#7F7F7F"),
          ),

          // === More Variations ===
          Theme(
              "Argonaut",
              Color.parseColor("#0E1019"),
              Color.parseColor("#FFFFAF"),
              Color.parseColor("#FF0000"),
          ),
          Theme(
              "BirdsOfParadise",
              Color.parseColor("#372725"),
              Color.parseColor("#E6E1C4"),
              Color.parseColor("#5B4A47"),
          ),
          Theme(
              "Cobalt2",
              Color.parseColor("#193549"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFC600"),
          ),
          Theme(
              "Espresso",
              Color.parseColor("#2D2D2D"),
              Color.parseColor("#CCCCCC"),
              Color.parseColor("#999999"),
          ),
          Theme(
              "Fishtank",
              Color.parseColor("#232537"),
              Color.parseColor("#ECF0C1"),
              Color.parseColor("#F6F6F6"),
          ),
          Theme(
              "Glacier",
              Color.parseColor("#0C1115"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFE792"),
          ),
          Theme(
              "Homebrew",
              Color.parseColor("#000000"),
              Color.parseColor("#28FE14"),
              Color.parseColor("#00FF00"),
          ),
          Theme(
              "Hurtado",
              Color.parseColor("#1B1B1B"),
              Color.parseColor("#F7F7F7"),
              Color.parseColor("#F7F7F7"),
          ),
          Theme(
              "Icicle",
              Color.parseColor("#22262D"),
              Color.parseColor("#CAE3E6"),
              Color.parseColor("#00A9C6"),
          ),
          Theme(
              "IdleToes",
              Color.parseColor("#323232"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#D6D6D6"),
          ),
          Theme(
              "Igloo",
              Color.parseColor("#181C24"),
              Color.parseColor("#D6DDE5"),
              Color.parseColor("#6D798B"),
          ),
          Theme(
              "Liquid Carbon",
              Color.parseColor("#303030"),
              Color.parseColor("#AFAFAF"),
              Color.parseColor("#AFAFAF"),
          ),
          Theme(
              "Misterioso",
              Color.parseColor("#2D3743"),
              Color.parseColor("#E1E1E0"),
              Color.parseColor("#00E5EE"),
          ),
          Theme(
              "Nightlion V1",
              Color.parseColor("#000000"),
              Color.parseColor("#BBBBBB"),
              Color.parseColor("#BBBBBB"),
          ),
          Theme(
              "Nightlion V2",
              Color.parseColor("#171717"),
              Color.parseColor("#E3E3E3"),
              Color.parseColor("#E3E3E3"),
          ),
          Theme(
              "Obsidian",
              Color.parseColor("#283033"),
              Color.parseColor("#E0E2E4"),
              Color.parseColor("#A082BD"),
          ),
          Theme(
              "Panda",
              Color.parseColor("#292A2B"),
              Color.parseColor("#E6E6E6"),
              Color.parseColor("#19F9D8"),
          ),
          Theme(
              "Peppermint",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#BBBBBB"),
          ),
          Theme(
              "Red Sands",
              Color.parseColor("#7A251E"),
              Color.parseColor("#D7C9A7"),
              Color.parseColor("#D7C9A7"),
          ),
          Theme(
              "SeaShells",
              Color.parseColor("#09141B"),
              Color.parseColor("#DEB88D"),
              Color.parseColor("#DEB88D"),
          ),
          Theme(
              "SoftServer",
              Color.parseColor("#242626"),
              Color.parseColor("#99A3A2"),
              Color.parseColor("#666C6C"),
          ),
          Theme(
              "Solarized Darcula",
              Color.parseColor("#3D3F41"),
              Color.parseColor("#D4D4D4"),
              Color.parseColor("#D4D4D4"),
          ),
          Theme(
              "Spacedust",
              Color.parseColor("#0A1E24"),
              Color.parseColor("#ECF0C1"),
              Color.parseColor("#00A395"),
          ),
          Theme(
              "Space",
              Color.parseColor("#121212"),
              Color.parseColor("#8D96A0"),
              Color.parseColor("#5A6172"),
          ),
          Theme(
              "Summer Fruit",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#000000"),
              Color.parseColor("#FCF6E5"),
          ),
          Theme(
              "Teal",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#000000"),
              Color.parseColor("#008080"),
          ),
          Theme(
              "Tomorrow",
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#4D4D4C"),
              Color.parseColor("#8E908C"),
          ),
          Theme(
              "Tomorrow Night",
              Color.parseColor("#1D1F21"),
              Color.parseColor("#C5C8C6"),
              Color.parseColor("#969896"),
          ),
          Theme(
              "Tomorrow Night Blue",
              Color.parseColor("#002451"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Tomorrow Night Bright",
              Color.parseColor("#000000"),
              Color.parseColor("#EAEAEA"),
              Color.parseColor("#EAEAEA"),
          ),
          Theme(
              "Tomorrow Night Eighties",
              Color.parseColor("#2D2D2D"),
              Color.parseColor("#CCCCCC"),
              Color.parseColor("#999999"),
          ),
          Theme(
              "ToyChest",
              Color.parseColor("#24364B"),
              Color.parseColor("#31D07B"),
              Color.parseColor("#8A5EDC"),
          ),
          Theme(
              "Treehouse",
              Color.parseColor("#191919"),
              Color.parseColor("#AA9138"),
              Color.parseColor("#F2E5BC"),
          ),
          Theme(
              "Twilight",
              Color.parseColor("#141414"),
              Color.parseColor("#F7F7F7"),
              Color.parseColor("#F7F7F7"),
          ),
          Theme(
              "Vibrant Ink",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Wombat",
              Color.parseColor("#171717"),
              Color.parseColor("#DEDACF"),
              Color.parseColor("#DEDACF"),
          ),

          // === High Tech ===
          Theme(
              "Alien Blood",
              Color.parseColor("#0F1610"),
              Color.parseColor("#637D58"),
              Color.parseColor("#73FA79"),
          ),
          Theme(
              "Belafonte Day",
              Color.parseColor("#D5CCBA"),
              Color.parseColor("#45373C"),
              Color.parseColor("#45373C"),
          ),
          Theme(
              "Belafonte Night",
              Color.parseColor("#20111B"),
              Color.parseColor("#968C83"),
              Color.parseColor("#968C83"),
          ),
          Theme(
              "Chalk",
              Color.parseColor("#151515"),
              Color.parseColor("#D0D0D0"),
              Color.parseColor("#D0D0D0"),
          ),
          Theme(
              "Chalkboard",
              Color.parseColor("#29262F"),
              Color.parseColor("#D9E6F2"),
              Color.parseColor("#D9E6F2"),
          ),
          Theme(
              "Ciapre",
              Color.parseColor("#191C27"),
              Color.parseColor("#AEA47F"),
              Color.parseColor("#AEA47F"),
          ),
          Theme(
              "Dark Pastel",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Desert",
              Color.parseColor("#333333"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Dimmed Monokai",
              Color.parseColor("#1F1F1F"),
              Color.parseColor("#B9BCB6"),
              Color.parseColor("#B9BCB6"),
          ),
          Theme(
              "DotGov",
              Color.parseColor("#252B35"),
              Color.parseColor("#EAE2E0"),
              Color.parseColor("#EAE2E0"),
          ),
          Theme(
              "Elementary",
              Color.parseColor("#101010"),
              Color.parseColor("#F2F2F2"),
              Color.parseColor("#F2F2F2"),
          ),
          Theme(
              "Flat",
              Color.parseColor("#2C3E50"),
              Color.parseColor("#E0E0E0"),
              Color.parseColor("#1ABC9C"),
          ),
          Theme(
              "Flora",
              Color.parseColor("#241F24"),
              Color.parseColor("#F0F1E2"),
              Color.parseColor("#F0F1E2"),
          ),
          Theme(
              "Frenzy",
              Color.parseColor("#000000"),
              Color.parseColor("#D0D0D0"),
              Color.parseColor("#D0D0D0"),
          ),
          Theme(
              "FrontEndDelight",
              Color.parseColor("#1B1C1D"),
              Color.parseColor("#ADADAD"),
              Color.parseColor("#ADADAD"),
          ),
          Theme(
              "FunForrest",
              Color.parseColor("#251200"),
              Color.parseColor("#DEC165"),
              Color.parseColor("#DEC165"),
          ),
          Theme(
              "Gogh",
              Color.parseColor("#000000"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Grape",
              Color.parseColor("#171423"),
              Color.parseColor("#9F9FA1"),
              Color.parseColor("#9F9FA1"),
          ),
          Theme(
              "Grass",
              Color.parseColor("#13773D"),
              Color.parseColor("#FFF0A5"),
              Color.parseColor("#FFF0A5"),
          ),
          Theme(
              "Hacktober",
              Color.parseColor("#141414"),
              Color.parseColor("#C9C9C9"),
              Color.parseColor("#C9C9C9"),
          ),
          Theme(
              "Hardcore",
              Color.parseColor("#212121"),
              Color.parseColor("#A0A0A0"),
              Color.parseColor("#A0A0A0"),
          ),
          Theme(
              "Harper",
              Color.parseColor("#010101"),
              Color.parseColor("#A8A49D"),
              Color.parseColor("#A8A49D"),
          ),
          Theme(
              "Highway",
              Color.parseColor("#222225"),
              Color.parseColor("#EDEDED"),
              Color.parseColor("#EDEDED"),
          ),
          Theme(
              "Hipster Green",
              Color.parseColor("#100B05"),
              Color.parseColor("#84C9BC"),
              Color.parseColor("#84C9BC"),
          ),
          Theme(
              "Homebrew Ocean",
              Color.parseColor("#2B303B"),
              Color.parseColor("#EFF1F5"),
              Color.parseColor("#EFF1F5"),
          ),
          Theme(
              "IC_Green_PPL",
              Color.parseColor("#3A3D3F"),
              Color.parseColor("#D9EFD3"),
              Color.parseColor("#D9EFD3"),
          ),
          Theme(
              "IC_Orange_PPL",
              Color.parseColor("#262626"),
              Color.parseColor("#FFCB83"),
              Color.parseColor("#FFCB83"),
          ),
          Theme(
              "Jackie Brown",
              Color.parseColor("#2C1D16"),
              Color.parseColor("#EFB44C"),
              Color.parseColor("#EFB44C"),
          ),
          Theme(
              "Japanesque",
              Color.parseColor("#1E1E1E"),
              Color.parseColor("#F7F6EC"),
              Color.parseColor("#F7F6EC"),
          ),
          Theme(
              "Jellybeans",
              Color.parseColor("#151515"),
              Color.parseColor("#888888"),
              Color.parseColor("#888888"),
          ),
          Theme(
              "JetBrains Darcula",
              Color.parseColor("#282828"),
              Color.parseColor("#A9B7C6"),
              Color.parseColor("#BBBBBB"),
          ),
          Theme(
              "Kibble",
              Color.parseColor("#0E100A"),
              Color.parseColor("#F7F7F7"),
              Color.parseColor("#F7F7F7"),
          ),
          Theme(
              "Kolor",
              Color.parseColor("#2F2F2F"),
              Color.parseColor("#E2E2E2"),
              Color.parseColor("#E2E2E2"),
          ),
          Theme(
              "Lab Fox",
              Color.parseColor("#2E2E2E"),
              Color.parseColor("#FFFFFF"),
              Color.parseColor("#FFFFFF"),
          ),
          Theme(
              "Later This Evening",
              Color.parseColor("#222222"),
              Color.parseColor("#959595"),
              Color.parseColor("#959595"),
          ),
          Theme(
              "Lavandula",
              Color.parseColor("#050014"),
              Color.parseColor("#736E7D"),
              Color.parseColor("#736E7D"),
          ),
          Theme(
              "Man Page",
              Color.parseColor("#FEF49C"),
              Color.parseColor("#000000"),
              Color.parseColor("#000000"),
          ),
          Theme(
              "Material",
              Color.parseColor("#263238"),
              Color.parseColor("#ECEFF1"),
              Color.parseColor("#ECEFF1"),
          ),
          Theme(
              "Mathias",
              Color.parseColor("#000000"),
              Color.parseColor("#BBBBBB"),
              Color.parseColor("#BBBBBB"),
          ),
          Theme(
              "Medallion",
              Color.parseColor("#1D1908"),
              Color.parseColor("#CAC296"),
              Color.parseColor("#CAC296"),
          ),
          // === Monet-inspired creative palettes ===
          Theme(
              "Monet Water Garden Dawn",
              Color.parseColor("#1E2E3F"),
              Color.parseColor("#CFE8F6"),
              Color.parseColor("#F6D7A7"),
          ),
          Theme(
              "Monet Lily Mist",
              Color.parseColor("#2C3C46"),
              Color.parseColor("#DCEEE7"),
              Color.parseColor("#A8D8C8"),
          ),
          Theme(
              "Monet Sunset Haystack",
              Color.parseColor("#3A2A2C"),
              Color.parseColor("#F7D9BF"),
              Color.parseColor("#F2A97F"),
          ),
          Theme(
              "Monet Foggy Seine",
              Color.parseColor("#2F3A48"),
              Color.parseColor("#D7DEE8"),
              Color.parseColor("#A9B8CF"),
          ),
          Theme(
              "Monet Rose Morning",
              Color.parseColor("#3D2F3A"),
              Color.parseColor("#F3DFE8"),
              Color.parseColor("#E8B7CC"),
          ),
          Theme(
              "Monet Iris Twilight",
              Color.parseColor("#2A2F45"),
              Color.parseColor("#DADCF5"),
              Color.parseColor("#A5A7E6"),
          ),
          Theme(
              "Monet Poppy Field",
              Color.parseColor("#3B2F27"),
              Color.parseColor("#F6E2CF"),
              Color.parseColor("#E57D64"),
          ),
          Theme(
              "Monet Wheat Light",
              Color.parseColor("#3A3528"),
              Color.parseColor("#EFE6CC"),
              Color.parseColor("#D4BC7D"),
          ),
          Theme(
              "Monet Coastal Haze",
              Color.parseColor("#243744"),
              Color.parseColor("#D5EAF2"),
              Color.parseColor("#8EC5D6"),
          ),
          Theme(
              "Monet Violet Reflection",
              Color.parseColor("#2D2940"),
              Color.parseColor("#E4DFF2"),
              Color.parseColor("#B9A4D8"),
          ),
      )

  fun getAvailableThemes(): List<Theme> = themes

  /**
   * Fix for "找不到符号 方法 getThemeNames()" Returns an array of theme names for UI display (e.g. in
   * AlertDialog)
   */
  fun getThemeNames(): Array<String> {
    return themes.map { it.name }.toTypedArray()
  }

  private fun saveTheme(themeName: String) {
    prefs.edit().putString(KEY_CURRENT_THEME, themeName).commit()
  }

  fun getSavedTheme(): Theme {
    val savedName = prefs.getString(KEY_CURRENT_THEME, themes[0].name)
    return themes.find { it.name == savedName } ?: themes[0]
  }

  /** Apply theme to a specific session (used when creating new session) */
  fun applyCurrentThemeToSession(session: TerminalSession?) {
    if (session == null) return
    val theme = getSavedTheme()
    val emulator = session.emulator ?: return

    // Update the color palette indices
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
  }

  /** Apply theme to the entire UI and save selection */
  fun applyTheme(fragment: TermuxFragment, theme: Theme) {
    saveTheme(theme.name)

    // Apply to current view immediately
    fragment.terminalView?.setBackgroundColor(theme.background)

    // Apply to all active sessions
    val service = fragment.termuxService
    if (service != null) {
      for (i in 0 until service.termuxSessionsSize) {
        service.getTermuxSession(i)?.terminalSession?.let { session ->
          val emulator = session.emulator
          if (emulator != null) {
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
          }
        }
      }
    }

    // Force redraw
    fragment.terminalView?.onScreenUpdated()
  }

  /** Call this in Fragment.onCreateView/onStart to restore theme */
  fun restoreTheme(fragment: TermuxFragment) {
    val theme = getSavedTheme()
    fragment.terminalView?.setBackgroundColor(theme.background)

    val service = fragment.termuxService
    if (service != null) {
      for (i in 0 until service.termuxSessionsSize) {
        service.getTermuxSession(i)?.terminalSession?.let { session ->
          val emulator = session.emulator ?: return@let
          emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
          emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
          emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
        }
      }
    }
    fragment.terminalView?.onScreenUpdated()
  }

  private val prefs by lazy {
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }
}
