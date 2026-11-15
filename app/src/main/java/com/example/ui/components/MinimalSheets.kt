package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * Unified minimal ModalBottomSheet shell used across Book Library & Hybrid Reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    containerColor: Color = LocalAppPalette.current.surface,
    titleColor: Color = LocalAppPalette.current.textPrimary,
    mutedColor: Color = LocalAppPalette.current.textMuted,
    borderColor: Color = LocalAppPalette.current.border,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = AppShapes.SheetTop,
        tonalElevation = AppElevation.None,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = AppSpacing.Md, bottom = AppSpacing.Xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(AppShapes.Pill)
                    .background(borderColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.Xl, end = AppSpacing.Sm, top = AppSpacing.Xs, bottom = AppSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = PoppinsFontFamily,
                        fontSize = AppTypeSize.SheetTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontFamily = PoppinsFontFamily,
                            fontSize = AppTypeSize.Caption,
                            color = mutedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    headerActions()
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppSpacing.TouchMin)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = mutedColor,
                            modifier = Modifier.size(AppIconSize.Toolbar)
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = AppBorder.Hairline,
                color = borderColor.copy(alpha = 0.7f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Xl, vertical = AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
                content = content
            )
        }
    }
}

@Composable
fun MinimalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = IndigoPrimary,
    surfaceColor: Color = LocalAppPalette.current.background,
    textColor: Color = LocalAppPalette.current.textPrimary,
    borderColor: Color = LocalAppPalette.current.border,
    leadingDotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Small,
        color = if (selected) accentColor.copy(alpha = 0.12f) else surfaceColor,
        border = BorderStroke(
            width = if (selected) AppBorder.Active else AppBorder.Hairline,
            color = if (selected) accentColor else borderColor
        ),
        shadowElevation = AppElevation.None,
        tonalElevation = AppElevation.None,
        modifier = modifier.defaultMinSize(minHeight = 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(AppShapes.Pill)
                        .background(leadingDotColor)
                        .border(AppBorder.Hairline, borderColor, AppShapes.Pill)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontSize = AppTypeSize.Caption,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) accentColor else textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MinimalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accentColor: Color = IndigoPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = contentColor,
            disabledContainerColor = accentColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.8f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = AppElevation.None,
            pressedElevation = AppElevation.None
        ),
        modifier = modifier.heightIn(min = AppSpacing.TouchMin)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(AppSpacing.Sm))
        }
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontSize = AppTypeSize.Body,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MinimalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textColor: Color = LocalAppPalette.current.textMuted,
    borderColor: Color = LocalAppPalette.current.border,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.Medium,
        border = BorderStroke(AppBorder.Hairline, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
        modifier = modifier.heightIn(min = AppSpacing.TouchMin)
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontSize = AppTypeSize.Body,
            fontWeight = FontWeight.Medium
        )
    }
}
