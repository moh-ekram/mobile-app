package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val border: Color,
    val cardBorder: Color
)

val LocalAppPalette = staticCompositionLocalOf {
    AppPalette(
        isDark = false,
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        cardBackground = Color.White,
        textPrimary = Color(0xFF0F172A),
        textMuted = Color(0xFF64748B),
        border = Color(0xFFE2E8F0),
        cardBorder = Color(0xFFF1F5F9)
    )
}

private val DarkAppPalette = AppPalette(
    isDark = true,
    background = Color(0xFF0B0F19),
    surface = Color(0xFF151C2C),
    cardBackground = Color(0xFF1E293B),
    textPrimary = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
    border = Color(0xFF334155),
    cardBorder = Color(0xFF334155)
)

private val LightAppPalette = AppPalette(
    isDark = false,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    cardBackground = Color.White,
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF64748B),
    border = Color(0xFFE2E8F0),
    cardBorder = Color(0xFFF1F5F9)
)

private val DarkColorScheme =
  darkColorScheme(
    primary = IndigoSecondary,
    secondary = IndigoPrimary,
    tertiary = EmeraldSuccess,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = IndigoPrimary,
    secondary = IndigoSecondary,
    tertiary = EmeraldSuccess,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val palette = if (darkTheme) DarkAppPalette else LightAppPalette

  CompositionLocalProvider(LocalAppPalette provides palette) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
