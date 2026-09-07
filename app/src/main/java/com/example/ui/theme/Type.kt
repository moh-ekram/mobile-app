package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val KalpurushFont = FontFamily(
    Font(R.font.kalpurush, FontWeight.Normal)
)

fun isBengaliText(text: String?): Boolean {
    if (text == null) return false
    return text.any { it in '\u0980'..'\u09FF' }
}

fun selectFontForText(text: String?): FontFamily {
    return if (isBengaliText(text)) KalpurushFont else FontFamily.SansSerif
}

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
  )
