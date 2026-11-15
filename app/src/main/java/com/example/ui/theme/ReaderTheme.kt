package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed color palettes for the 4 built-in Ebook Reader themes,
 * kept isolated from general screen components.
 */
data class ReaderThemePalette(
    val key: String,
    val label: String,
    val background: Color,
    val ink: Color,
    val subtleSurface: Color,
    val accent: Color,
    val isDark: Boolean,
    val chromeBg: Color,
    val chromeText: Color,
    val chromeMuted: Color,
    val chromeBorder: Color
)

data class HighlightSwatch(
    val hex: String,
    val color: Color
)

object ReaderThemes {
    val Paper = ReaderThemePalette(
        key = "paper",
        label = "Paper",
        background = Color.White,
        ink = SlateText,
        subtleSurface = Color(0xFFF8FAFC),
        accent = IndigoPrimary,
        isDark = false,
        chromeBg = Color.White,
        chromeText = SlateText,
        chromeMuted = SlateMuted,
        chromeBorder = SlateBorder
    )

    val Sepia = ReaderThemePalette(
        key = "sepia",
        label = "Sepia",
        background = Color(0xFFFBF4E4),
        ink = Color(0xFF433422),
        subtleSurface = Color(0xFFF3E7CF),
        accent = Color(0xFF9A5B13),
        isDark = false,
        chromeBg = Color(0xFFFBF4E4),
        chromeText = Color(0xFF433422),
        chromeMuted = Color(0xFF7A6248),
        chromeBorder = Color(0xFFE5D4B3)
    )

    val Mist = ReaderThemePalette(
        key = "mist",
        label = "Mist",
        background = Color(0xFFF1F5F9),
        ink = Color(0xFF0F172A),
        subtleSurface = Color(0xFFE2E8F0),
        accent = IndigoPrimary,
        isDark = false,
        chromeBg = Color(0xFFF1F5F9),
        chromeText = Color(0xFF0F172A),
        chromeMuted = SlateMuted,
        chromeBorder = Color(0xFFCBD5E1)
    )

    val Dark = ReaderThemePalette(
        key = "dark",
        label = "Night",
        background = Color(0xFF0F172A),
        ink = Color(0xFFE2E8F0),
        subtleSurface = Color(0xFF1E293B),
        accent = Color(0xFFA5B4FC),
        isDark = true,
        chromeBg = Color(0xFF151C2C),
        chromeText = Color(0xFFF1F5F9),
        chromeMuted = Color(0xFF94A3B8),
        chromeBorder = Color(0xFF334155)
    )

    val All = listOf(Paper, Sepia, Mist, Dark)

    fun resolve(key: String): ReaderThemePalette = when (key) {
        "sepia" -> Sepia
        "mist" -> Mist
        "dark" -> Dark
        else -> Paper
    }

    val HighlightSwatches = listOf(
        HighlightSwatch("#FDE047", Color(0xFFFDE047)),
        HighlightSwatch("#86EFAC", Color(0xFF86EFAC)),
        HighlightSwatch("#7DD3FC", Color(0xFF7DD3FC)),
        HighlightSwatch("#FDA4AF", Color(0xFFFDA4AF))
    )

    fun parseHighlightColor(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            HighlightSwatches.first().color
        }
    }
}
