package com.termix.ui.theme.colorscheme

import java.util.Properties

/**
 * Represents a complete terminal color scheme with ANSI 16 colors and special colors.
 * 
 * This data class holds all the colors needed for both terminal rendering and app UI theming.
 * Color values are stored as ARGB integers (0xAARRGGBB format).
 */
data class TerminalColorScheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    
    // ANSI 16 colors (indices 0-15)
    val black: Int,        // 0
    val red: Int,          // 1
    val green: Int,        // 2
    val yellow: Int,       // 3
    val blue: Int,         // 4
    val magenta: Int,      // 5
    val cyan: Int,         // 6
    val white: Int,        // 7
    val brightBlack: Int,  // 8
    val brightRed: Int,    // 9
    val brightGreen: Int,  // 10
    val brightYellow: Int, // 11
    val brightBlue: Int,   // 12
    val brightMagenta: Int,// 13
    val brightCyan: Int,   // 14
    val brightWhite: Int,  // 15
    
    // Special colors
    val foreground: Int,   // Default text color (index 256)
    val background: Int,   // Default background color (index 257)
    val cursor: Int,       // Cursor color (index 258)
) {
    
    /**
     * Converts this scheme to a Properties object compatible with Termux's
     * TerminalColorScheme.updateWith(Properties) method.
     */
    fun toProperties(): Properties = Properties().apply {
        // ANSI 16 colors
        setProperty("color0", toHexColor(black))
        setProperty("color1", toHexColor(red))
        setProperty("color2", toHexColor(green))
        setProperty("color3", toHexColor(yellow))
        setProperty("color4", toHexColor(blue))
        setProperty("color5", toHexColor(magenta))
        setProperty("color6", toHexColor(cyan))
        setProperty("color7", toHexColor(white))
        setProperty("color8", toHexColor(brightBlack))
        setProperty("color9", toHexColor(brightRed))
        setProperty("color10", toHexColor(brightGreen))
        setProperty("color11", toHexColor(brightYellow))
        setProperty("color12", toHexColor(brightBlue))
        setProperty("color13", toHexColor(brightMagenta))
        setProperty("color14", toHexColor(brightCyan))
        setProperty("color15", toHexColor(brightWhite))
        
        // Special colors
        setProperty("foreground", toHexColor(foreground))
        setProperty("background", toHexColor(background))
        setProperty("cursor", toHexColor(cursor))
    }
    
    companion object {
        /**
         * Converts an ARGB integer to a hex color string (#RRGGBB format).
         */
        private fun toHexColor(argb: Int): String {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return String.format("#%02x%02x%02x", r, g, b)
        }
        
        /**
         * Parses a hex color string (#RRGGBB or #RGB format) to an ARGB integer.
         */
        fun parseHexColor(hex: String): Int {
            val cleanHex = hex.removePrefix("#")
            return when (cleanHex.length) {
                3 -> {
                    val r = cleanHex[0].toString().repeat(2).toInt(16)
                    val g = cleanHex[1].toString().repeat(2).toInt(16)
                    val b = cleanHex[2].toString().repeat(2).toInt(16)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> {
                    val rgb = cleanHex.toLong(16).toInt()
                    (0xFF shl 24) or rgb
                }
                else -> throw IllegalArgumentException("Invalid hex color: $hex")
            }
        }
        
        /**
         * Helper function to create a color from hex string.
         */
        fun hex(hex: String): Int = parseHexColor(hex)
    }
}
