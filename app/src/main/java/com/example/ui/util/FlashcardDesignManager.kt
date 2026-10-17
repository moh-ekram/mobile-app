package com.example.ui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Configuration data model for user-customized flashcard appearance and animations.
 * All default values represent the original pristine styling of the app.
 */
data class FlashcardDesignConfig(
    val cardStyle: String = "default", // "default", "minimal", "elevated", "neumorphic", "gradient"
    val backgroundColorHex: String = "default", // "default" or hex string (e.g. "#FFFDF5")
    val place1MaxFontSize: Int = 30, // 18..42 sp
    val place2MaxFontSize: Int = 20, // 14..32 sp
    val place3MaxFontSize: Int = 15, // 12..24 sp
    val swipeAnimationStyle: String = "slide", // "slide", "stack_rotate", "zoom_fade", "flip"
    val flipAnimationStyle: String = "flip_3d_h", // "flip_3d_h", "flip_3d_v", "fade", "zoom"
    val cornerRadiusDp: Int = 28, // 8..36 dp
    val cardElevationDp: Int = 2, // 0..12 dp
    val borderWidthDp: Float = 1.0f, // 0f..3f dp
    val textAlignment: String = "center", // "center", "start"
    val fontFamilyType: String = "default" // "default", "serif", "monospace", "sans"
) {
    /**
     * Resolves the actual background color, respecting light/dark theme fallback if set to default.
     */
    fun resolveCardBackgroundColor(defaultSurfaceColor: Color): Color {
        return if (backgroundColorHex.equals("default", ignoreCase = true) || backgroundColorHex.isBlank()) {
            defaultSurfaceColor
        } else {
            try {
                val hex = if (!backgroundColorHex.startsWith("#")) "#$backgroundColorHex" else backgroundColorHex
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                defaultSurfaceColor
            }
        }
    }
}

/**
 * Preset background color swatches for quick, aesthetic customization.
 */
data class ColorSwatch(
    val id: String,
    val name: String,
    val hex: String,
    val isDark: Boolean = false
)

object FlashcardDesignManager {
    private const val PREFS_NAME = "flashcard_custom_design_prefs"
    private const val KEY_CONFIG_JSON = "custom_design_config_json"

    val COLOR_PRESETS = listOf(
        ColorSwatch("default", "Default", "default"),
        ColorSwatch("white", "Pure White", "#FFFFFF"),
        ColorSwatch("cream", "Warm Cream", "#FFFDF5"),
        ColorSwatch("mint", "Soft Mint", "#F0FDF4"),
        ColorSwatch("lavender", "Lavender", "#F5F3FF"),
        ColorSwatch("sky", "Cool Sky", "#F0F9FF"),
        ColorSwatch("rose", "Rose Blush", "#FFF1F2"),
        ColorSwatch("slate_dark", "Slate Dark", "#1E293B", isDark = true),
        ColorSwatch("midnight", "Midnight", "#0F172A", isDark = true)
    )

    val SWIPE_ANIMATIONS = listOf(
        Pair("slide", "Smooth Slide"),
        Pair("stack_rotate", "Card Stack / Deck"),
        Pair("zoom_fade", "Zoom & Fade"),
        Pair("flip", "Flip Transition")
    )

    val FLIP_ANIMATIONS = listOf(
        Pair("flip_3d_h", "3D Horizontal Flip"),
        Pair("flip_3d_v", "3D Vertical Flip"),
        Pair("fade", "Fade Transition"),
        Pair("zoom", "Zoom Flip")
    )

    val CARD_STYLES = listOf(
        Pair("default", "Default Modern"),
        Pair("minimal", "Flat Minimal"),
        Pair("elevated", "Deep Elevated"),
        Pair("neumorphic", "Soft Neumorphic"),
        Pair("gradient", "Gradient Accent")
    )

    fun loadConfig(context: Context): FlashcardDesignConfig {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CONFIG_JSON, null) ?: return FlashcardDesignConfig()
            val obj = JSONObject(jsonStr)
            FlashcardDesignConfig(
                cardStyle = obj.optString("cardStyle", "default"),
                backgroundColorHex = obj.optString("backgroundColorHex", "default"),
                place1MaxFontSize = obj.optInt("place1MaxFontSize", 30),
                place2MaxFontSize = obj.optInt("place2MaxFontSize", 20),
                place3MaxFontSize = obj.optInt("place3MaxFontSize", 15),
                swipeAnimationStyle = obj.optString("swipeAnimationStyle", "slide"),
                flipAnimationStyle = obj.optString("flipAnimationStyle", "flip_3d_h"),
                cornerRadiusDp = obj.optInt("cornerRadiusDp", 28),
                cardElevationDp = obj.optInt("cardElevationDp", 2),
                borderWidthDp = obj.optDouble("borderWidthDp", 1.0).toFloat(),
                textAlignment = obj.optString("textAlignment", "center"),
                fontFamilyType = obj.optString("fontFamilyType", "default")
            )
        } catch (_: Exception) {
            FlashcardDesignConfig()
        }
    }

    fun saveConfig(context: Context, config: FlashcardDesignConfig) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val obj = JSONObject().apply {
                put("cardStyle", config.cardStyle)
                put("backgroundColorHex", config.backgroundColorHex)
                put("place1MaxFontSize", config.place1MaxFontSize)
                put("place2MaxFontSize", config.place2MaxFontSize)
                put("place3MaxFontSize", config.place3MaxFontSize)
                put("swipeAnimationStyle", config.swipeAnimationStyle)
                put("flipAnimationStyle", config.flipAnimationStyle)
                put("cornerRadiusDp", config.cornerRadiusDp)
                put("cardElevationDp", config.cardElevationDp)
                put("borderWidthDp", config.borderWidthDp.toDouble())
                put("textAlignment", config.textAlignment)
                put("fontFamilyType", config.fontFamilyType)
            }
            prefs.edit().putString(KEY_CONFIG_JSON, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefault(context: Context): FlashcardDesignConfig {
        val defaultConfig = FlashcardDesignConfig()
        saveConfig(context, defaultConfig)
        return defaultConfig
    }
}
