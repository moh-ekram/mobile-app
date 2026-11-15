package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared minimal design tokens:
 * - Three corner radii only (8.dp, 12.dp, 20.dp) + CircleShape
 * - Zero shadows, hairline dividers
 * - Minimum 11.sp typography
 * - Uniform outlined icon sizing
 */
object AppShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(20.dp)
    val SheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = CircleShape
}

object AppElevation {
    val None = 0.dp
}

object AppBorder {
    val Hairline = 0.75.dp
    val Active = 1.25.dp
}

object AppSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val MaxReaderWidth = 680.dp
    val TouchMin = 48.dp
}

object AppIconSize {
    val Small = 16.dp
    val Inline = 18.dp
    val Toolbar = 20.dp
    val Hero = 24.dp
}

object AppTypeSize {
    val Micro = 11.sp
    val Caption = 12.sp
    val Body = 13.5.sp
    val Title = 15.sp
    val SheetTitle = 16.5.sp
    val Headline = 19.sp
}

fun appAccentColor(isDark: Boolean) = if (isDark) IndigoSecondary else IndigoPrimary
