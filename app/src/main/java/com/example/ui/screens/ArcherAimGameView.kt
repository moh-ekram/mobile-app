package com.example.ui.screens

import android.content.Context
import kotlinx.coroutines.isActive
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.util.HapticHelper
import com.example.ui.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

private data class LetterTargetBox(
    val text: String,
    val index: Int,
    val bounds: Rect,
    val isTarget: Boolean
)

private data class MeaningTargetBox(
    val optionIndex: Int,
    val text: String,
    val label: String,
    val center: Offset,
    val radius: Float,
    val bounds: Rect,
    val isCorrect: Boolean
)

private data class GameParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val alpha: Float,
    val rotation: Float
)

private data class EmbeddedArrow(
    val impactPoint: Offset,
    val angleRad: Float,
    val scale: Float,
    val isBullseye: Boolean,
    val hitText: String?,
    val wobble: Float
)

private fun findTargetPoint(
    touchOffset: Offset,
    boxes: List<LetterTargetBox>,
    meaningBoxes: List<MeaningTargetBox>,
    isMeaningMatch: Boolean,
    defaultPoint: Offset
): Offset {
    if (isMeaningMatch) {
        if (meaningBoxes.isEmpty()) return touchOffset
        val directOption = meaningBoxes.find { it.bounds.contains(touchOffset) }
        if (directOption != null) return directOption.center
        val closestOption = meaningBoxes.minByOrNull { (it.center - touchOffset).getDistance() }
        if (closestOption != null && (closestOption.center - touchOffset).getDistance() < closestOption.radius * 2.2f) {
            return closestOption.center
        }
        return touchOffset
    }

    if (boxes.isEmpty()) return touchOffset
    val directBox = boxes.find { box ->
        val expanded = Rect(box.bounds.left - 20f, box.bounds.top - 28f, box.bounds.right + 20f, box.bounds.bottom + 28f)
        expanded.contains(touchOffset)
    }
    if (directBox != null) {
        return directBox.bounds.center
    }
    if (touchOffset.y < defaultPoint.y + 180f) {
        val closest = boxes.filter { it.text.isNotBlank() }.minByOrNull { abs(it.bounds.center.x - touchOffset.x) }
        if (closest != null && abs(closest.bounds.center.x - touchOffset.x) < closest.bounds.width * 2f) {
            return closest.bounds.center
        }
    }
    return touchOffset
}

@Composable
private fun ArcherAimControllerWheel(
    modifier: Modifier = Modifier,
    onMove: (normX: Float, normY: Float) -> Unit,
    onRelease: () -> Unit
) {
    val sizeDp = 100.dp
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    val radiusPx = sizePx / 2f
    val maxThumbOffset = radiusPx * 0.52f

    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding, thumbOffset) {
        if (isHolding) {
            while (isActive && isHolding) {
                if (thumbOffset.getDistance() > 4f) {
                    val normX = (thumbOffset.x / maxThumbOffset).coerceIn(-1f, 1f)
                    val normY = (thumbOffset.y / maxThumbOffset).coerceIn(-1f, 1f)
                    onMove(normX, normY)
                }
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val diff = down.position - center
                    val dist = diff.getDistance()
                    val angle = atan2(diff.y, diff.x)
                    val clampedDist = dist.coerceAtMost(maxThumbOffset)
                    thumbOffset = Offset(cos(angle) * clampedDist, sin(angle) * clampedDist)
                    isHolding = true

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            break
                        }
                        val curDiff = change.position - center
                        val curDist = curDiff.getDistance()
                        val curAngle = atan2(curDiff.y, curDiff.x)
                        val curClampedDist = curDist.coerceAtMost(maxThumbOffset)
                        thumbOffset = Offset(cos(curAngle) * curClampedDist, sin(curAngle) * curClampedDist)
                        change.consume()
                    }

                    isHolding = false
                    thumbOffset = Offset.Zero
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width / 2f

            // Ambient glow shadow
            drawCircle(
                color = Color(0x66000000),
                radius = baseRadius + 3f,
                center = center + Offset(0f, 4f)
            )

            // Outer dark glassmorphism background (Dark Slate to Deep Blue)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xE61E293B), Color(0xF50F172A)),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius - 1f,
                center = center
            )

            // Glassmorphism subtle border with cyan glow
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = if (isHolding) {
                        listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF38BDF8))
                    } else {
                        listOf(Color(0x5538BDF8), Color(0x3394A3B8), Color(0x5538BDF8))
                    },
                    center = center
                ),
                radius = baseRadius - 2f,
                center = center,
                style = Stroke(width = if (isHolding) 2.5f else 1.5f)
            )

            if (isHolding) {
                // Active cyan energy field
                drawCircle(
                    color = Color(0x2238BDF8),
                    radius = baseRadius * 0.85f,
                    center = center
                )
            }

            // 4 Cardinal Direction Arrows with glowing press feedback
            val arrowColor = Color(0x7794A3B8)
            val activeColor = Color(0xFF38BDF8)
            val arrowDist = baseRadius * 0.68f

            val isUpActive = isHolding && thumbOffset.y < -8f
            val isDownActive = isHolding && thumbOffset.y > 8f
            val isLeftActive = isHolding && thumbOffset.x < -8f
            val isRightActive = isHolding && thumbOffset.x > 8f

            // Up
            drawDirectionArrow(
                center = Offset(center.x, center.y - arrowDist),
                angleDeg = 0f,
                color = if (isUpActive) activeColor else arrowColor,
                isActive = isUpActive
            )
            // Down
            drawDirectionArrow(
                center = Offset(center.x, center.y + arrowDist),
                angleDeg = 180f,
                color = if (isDownActive) activeColor else arrowColor,
                isActive = isDownActive
            )
            // Left
            drawDirectionArrow(
                center = Offset(center.x - arrowDist, center.y),
                angleDeg = 270f,
                color = if (isLeftActive) activeColor else arrowColor,
                isActive = isLeftActive
            )
            // Right
            drawDirectionArrow(
                center = Offset(center.x + arrowDist, center.y),
                angleDeg = 90f,
                color = if (isRightActive) activeColor else arrowColor,
                isActive = isRightActive
            )

            // Inner Thumb Knob
            val thumbCenter = center + thumbOffset
            val thumbRadius = baseRadius * 0.33f

            // Knob drop shadow
            drawCircle(
                color = Color(0x66000000),
                radius = thumbRadius + 3f,
                center = thumbCenter + Offset(0f, 3f)
            )
            // Knob body (Metallic dark glass with cyan active)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isHolding) listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                    else listOf(Color(0xFF475569), Color(0xFF1E293B)),
                    center = thumbCenter,
                    radius = thumbRadius
                ),
                radius = thumbRadius,
                center = thumbCenter
            )
            // Knob border
            drawCircle(
                color = if (isHolding) Color(0xFFBAE6FD) else Color(0x6638BDF8),
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 1.5f)
            )
            // Knob center glowing pinpoint
            drawCircle(
                color = if (isHolding) Color.White else Color(0xFF38BDF8),
                radius = 3.5f,
                center = thumbCenter
            )
            if (isHolding) {
                drawCircle(
                    color = Color(0x6638BDF8),
                    radius = 7f,
                    center = thumbCenter
                )
            }
        }
    }
}

private fun DrawScope.drawDirectionArrow(
    center: Offset,
    angleDeg: Float,
    color: Color,
    isActive: Boolean = false
) {
    rotate(angleDeg, pivot = center) {
        val arrowScale = if (isActive) 1.3f else 1.0f
        if (isActive) {
            // Glowing energy halo on active press
            drawCircle(
                color = Color(0x5538BDF8),
                radius = 11f,
                center = center
            )
        }
        val path = Path().apply {
            moveTo(center.x, center.y - (7.5f * arrowScale))
            lineTo(center.x + (6.5f * arrowScale), center.y + (4.5f * arrowScale))
            lineTo(center.x - (6.5f * arrowScale), center.y + (4.5f * arrowScale))
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun ArcherAimGameView(
    gameState: ArcherGameState,
    onHit: (String) -> Unit,
    onMiss: (String) -> Unit,
    onRoundFinished: () -> Unit = {},
    onPlayAgain: () -> Unit,
    onBackToConfig: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val ttsManager = remember { TtsManager(context) }
    val haptic = remember { HapticHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    var currentIndex by remember(gameState) { mutableIntStateOf(0) }
    var hits by remember(gameState) { mutableIntStateOf(0) }
    var misses by remember(gameState) { mutableIntStateOf(0) }
    var streak by remember(gameState) { mutableIntStateOf(0) }
    var coins by remember(gameState) { mutableIntStateOf(10587) }

    var isRoundResolved by remember(gameState, currentIndex) { mutableStateOf(false) }
    var roundSuccess by remember(gameState, currentIndex) { mutableStateOf<Boolean?>(null) }
    var hitLetterFeedback by remember(gameState, currentIndex) { mutableStateOf<String?>(null) }
    var hitMeaningIndex by remember(gameState, currentIndex) { mutableStateOf<Int?>(null) }

    val isFinished = currentIndex >= gameState.targets.size && gameState.targets.isNotEmpty()

    LaunchedEffect(isFinished) {
        if (isFinished) {
            ArcherAimStats.recordRound(context, hits, misses)
            onRoundFinished()
            haptic.ratingSelected()
        }
    }

    if (isFinished) {
        // Complete view
        ArcheryGameOverView(
            hits = hits,
            misses = misses,
            coins = coins,
            totalTargets = gameState.targets.size,
            onPlayAgain = onPlayAgain,
            onBackToConfig = onBackToConfig
        )
        return
    }

    val currentTarget = gameState.targets.getOrNull(currentIndex) ?: return
    val isMeaningMatch = (gameState.mode == ArcherGameMode.MEANING_MATCH)
    val wordSegments = remember(currentTarget.place1Text) {
        segmentGraphemeClusters(currentTarget.place1Text.trim())
    }
    val targetSegment = currentTarget.targetSegment.ifBlank { currentTarget.place1Text }

    // Aiming & flight states
    var aimOffset by remember(currentIndex) { mutableStateOf<Offset?>(null) }
    var isAiming by remember(currentIndex) { mutableStateOf(false) }
    var isArrowFlying by remember(currentIndex) { mutableStateOf(false) }
    var flightProgress by remember(currentIndex) { mutableFloatStateOf(0f) }
    var flightStartOffset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var flightEndOffset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var stuckArrow by remember(currentIndex) { mutableStateOf<EmbeddedArrow?>(null) }
    var particles by remember(currentIndex) { mutableStateOf<List<GameParticle>>(emptyList()) }
    var letterBoxes by remember(currentIndex) { mutableStateOf<List<LetterTargetBox>>(emptyList()) }

    // Hit pulse ripple and Miss screen shake
    val missShakeAnim = remember { Animatable(0f) }
    var hitPulseRadius by remember(currentIndex) { mutableFloatStateOf(0f) }
    var hitPulseAlpha by remember(currentIndex) { mutableFloatStateOf(0f) }
    var hitImpactPoint by remember(currentIndex) { mutableStateOf<Offset?>(null) }

    // Accuracy scale bounce animation
    val totalAttempts = hits + misses
    val accuracyPercentage = if (totalAttempts > 0) ((hits.toFloat() / totalAttempts) * 100).toInt() else 100
    val accuracyBounce = remember { Animatable(1f) }
    LaunchedEffect(totalAttempts) {
        if (totalAttempts > 0) {
            accuracyBounce.snapTo(1.25f)
            accuracyBounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    // Particle animation loop
    LaunchedEffect(particles.isNotEmpty()) {
        while (particles.isNotEmpty()) {
            delay(16)
            particles = particles.mapNotNull { p ->
                val nextAlpha = p.alpha - 0.035f
                if (nextAlpha <= 0f) null
                else p.copy(
                    x = p.x + p.vx,
                    y = p.y + p.vy + 0.35f,
                    vx = p.vx * 0.96f,
                    vy = p.vy * 0.96f,
                    alpha = nextAlpha,
                    rotation = p.rotation + 4f
                )
            }
        }
    }

    // Wobble animation for stuck arrow
    val wobbleAnim = remember(stuckArrow) { Animatable(0f) }
    LaunchedEffect(stuckArrow) {
        if (stuckArrow != null) {
            wobbleAnim.snapTo(1f)
            wobbleAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Bow anchor point at bottom center
        val bowAnchor = Offset(widthPx * 0.5f, heightPx * 0.91f)

        // Default aim point rests centered on the target letter or word
        val defaultAim = Offset(widthPx * 0.5f, heightPx * 0.36f)

        // Compute letter boxes for target word across width (Letter Aim Mode)
        LaunchedEffect(widthPx, heightPx, wordSegments, targetSegment) {
            if (widthPx <= 0f || heightPx <= 0f || wordSegments.isEmpty()) return@LaunchedEffect
            val count = wordSegments.size
            val maxLetterW = (widthPx * 0.92f) / count.coerceAtLeast(1)
            val letterW = maxLetterW.coerceIn(32f, 68f)
            val letterH = letterW * 1.45f
            val totalWordW = count * letterW
            val startX = (widthPx - totalWordW) / 2f
            val wordCenterY = heightPx * 0.36f
            val wordTop = wordCenterY - (letterH * 0.5f)

            val boxes = wordSegments.mapIndexed { idx, seg ->
                val left = startX + (idx * letterW)
                val rect = Rect(left, wordTop, left + letterW, wordTop + letterH)
                LetterTargetBox(
                    text = seg,
                    index = idx,
                    bounds = rect,
                    isTarget = (seg == targetSegment)
                )
            }
            letterBoxes = boxes
        }

        // Compute 3 meaning target boards across width (Meaning Match Mode)
        val meaningBoxes = remember(widthPx, heightPx, currentTarget.options, isMeaningMatch) {
            if (!isMeaningMatch || widthPx <= 0f || heightPx <= 0f || currentTarget.options.isEmpty()) emptyList()
            else {
                val xPositions = listOf(0.18f, 0.50f, 0.82f)
                val yPositions = listOf(0.31f, 0.26f, 0.31f)
                val boardRadius = (widthPx * 0.13f).coerceIn(46f, 64f)
                val gap = 18f
                val plaqueHeight = 76f
                val plaqueWidth = (widthPx * 0.29f).coerceIn(100f, 138f)

                currentTarget.options.take(3).mapIndexed { idx, optionText ->
                    val cx = widthPx * xPositions[idx]
                    val cy = heightPx * yPositions[idx]
                    val plaqueTop = cy + boardRadius + gap
                    val plaqueLeft = cx - (plaqueWidth / 2f)
                    val boardRect = Rect(
                        minOf(cx - boardRadius, plaqueLeft),
                        cy - boardRadius,
                        maxOf(cx + boardRadius, plaqueLeft + plaqueWidth),
                        plaqueTop + plaqueHeight
                    )
                    MeaningTargetBox(
                        optionIndex = idx,
                        text = optionText,
                        label = when (idx) { 0 -> "A"; 1 -> "B"; else -> "C" },
                        center = Offset(cx, cy),
                        radius = boardRadius,
                        bounds = boardRect,
                        isCorrect = (idx == currentTarget.correctOptionIndex)
                    )
                }
            }
        }

        // Handle arrow launch - ONLY 1 CHANCE RULE
        fun launchArrow(targetDestination: Offset) {
            if (isArrowFlying || isRoundResolved) return
            isArrowFlying = true
            isAiming = false
            flightStartOffset = Offset(bowAnchor.x, bowAnchor.y - 40f)
            flightEndOffset = targetDestination
            haptic.cardSwipe()

            coroutineScope.launch {
                val flightAnim = Animatable(0f)
                flightAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutLinearInEasing)
                ) {
                    flightProgress = value
                }

                // Check collision at flight end
                isArrowFlying = false
                val impact = targetDestination

                val isDirectBullseye: Boolean
                val hitLabel: String?

                if (isMeaningMatch) {
                    val hitOption = meaningBoxes.find { it.bounds.contains(impact) }
                    hitMeaningIndex = hitOption?.optionIndex
                    isDirectBullseye = hitOption?.isCorrect == true
                    hitLabel = hitOption?.text
                } else {
                    val hitBox = letterBoxes.find { it.bounds.contains(impact) }
                    isDirectBullseye = hitBox?.isTarget == true
                    hitLabel = hitBox?.text
                }

                stuckArrow = EmbeddedArrow(
                    impactPoint = impact,
                    angleRad = atan2(impact.y - flightStartOffset.y, impact.x - flightStartOffset.x),
                    scale = 0.40f,
                    isBullseye = isDirectBullseye,
                    hitText = hitLabel,
                    wobble = 1f
                )

                // Spawn impact particles
                val newParticles = mutableListOf<GameParticle>()
                val colors = if (isDirectBullseye) {
                    listOf(Color(0xFFFFD700), Color(0xFF10B981), Color(0xFF38BDF8), Color(0xFFFFFFFF), Color(0xFFF59E0B))
                } else {
                    listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFCBD5E1), Color(0xFFFFFFFF))
                }
                for (i in 0 until (if (isDirectBullseye) 36 else 16)) {
                    val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val speed = Random.nextFloat() * (if (isDirectBullseye) 10f else 6f) + 2f
                    newParticles.add(
                        GameParticle(
                            x = impact.x,
                            y = impact.y,
                            vx = cos(angle) * speed,
                            vy = sin(angle) * speed,
                            color = colors.random(),
                            size = Random.nextFloat() * 6f + 3f,
                            alpha = 1f,
                            rotation = Random.nextFloat() * 360f
                        )
                    )
                }
                particles = newParticles

                if (isDirectBullseye) {
                    // DIRECT HIT: Trigger expanding shockwave ripple & flash
                    hitImpactPoint = impact
                    coroutineScope.launch {
                        hitPulseRadius = 0f
                        hitPulseAlpha = 1f
                        val pulseAnim = Animatable(0f)
                        pulseAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                        ) {
                            hitPulseRadius = value * 140f
                            hitPulseAlpha = (1f - value).coerceAtLeast(0f)
                        }
                    }
                    hits++
                    streak++
                    coins += 500
                    isRoundResolved = true
                    roundSuccess = true
                    hitLetterFeedback = "BULLSEYE! Target Hit!"
                    haptic.ratingSelected()
                    onHit(currentTarget.wordEntity.id)
                    ttsManager.speak(currentTarget.place1Text)
                } else {
                    // MISS: Trigger camera screen shake & red vignette flash
                    coroutineScope.launch {
                        missShakeAnim.snapTo(1f)
                        missShakeAnim.animateTo(0f, animationSpec = tween(durationMillis = 360, easing = LinearEasing))
                    }
                    misses++
                    streak = 0
                    isRoundResolved = true
                    roundSuccess = false
                    hitLetterFeedback = if (hitLabel != null) "Hit '$hitLabel'!" else "Missed target!"
                    haptic.cardFlip()
                    onMiss(currentTarget.wordEntity.id)
                    ttsManager.speak(currentTarget.place1Text)
                }
            }
        }

        // The Full 3D Archery Arena Canvas
        val shakeOffsetPx = (sin(missShakeAnim.value * 30f) * missShakeAnim.value * 12f).toInt()
        val currentTargetAim = aimOffset ?: defaultAim
        val lockedCrosshairAim = remember(currentTargetAim, meaningBoxes, isMeaningMatch) {
            if (isMeaningMatch && meaningBoxes.isNotEmpty()) {
                val closest = meaningBoxes.minByOrNull { (it.center - currentTargetAim).getDistance() }
                if (closest != null && (closest.center - currentTargetAim).getDistance() < closest.radius * 1.35f) {
                    closest.center
                } else currentTargetAim
            } else currentTargetAim
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(shakeOffsetPx, 0) }
                .pointerInput(isArrowFlying, isRoundResolved, letterBoxes, meaningBoxes, isMeaningMatch) {
                    if (isArrowFlying || isRoundResolved) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startTarget = findTargetPoint(down.position, letterBoxes, meaningBoxes, isMeaningMatch, defaultAim)
                        aimOffset = startTarget
                        isAiming = true
                        haptic.subtleTick()

                        var curAim = startTarget

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val newTarget = findTargetPoint(change.position, letterBoxes, meaningBoxes, isMeaningMatch, defaultAim)
                                if (newTarget != curAim) {
                                    curAim = newTarget
                                    aimOffset = curAim
                                    haptic.subtleTick()
                                }
                                change.consume()
                            } else {
                                // Released finger on canvas: launch arrow directly at target!
                                change.consume()
                                val finalTarget = curAim
                                isAiming = false
                                launchArrow(finalTarget)
                                break
                            }
                        }
                        isAiming = false
                    }
                }
        ) {
            // 1. SKY & DISTANT MOUNTAINS WITH PARALLAX
            val parallaxX = (((aimOffset ?: defaultAim).x / size.width.coerceAtLeast(1f)) - 0.5f) * 36f
            drawArcheryEnvironmentSkyAndMountains(size, parallaxX)

            // 2. FOREST TREELINE & PERSPECTIVE GRASS RANGE
            drawArcheryFieldAndLaneLines(size)

            // 3. TARGETS
            if (isMeaningMatch) {
                // Meaning Match Mode: 3 Archery Target Boards with Option Plaques
                drawMeaningTargetOptions(
                    options = meaningBoxes,
                    isRevealed = isRoundResolved,
                    isBullseye = (roundSuccess == true),
                    hitIndex = hitMeaningIndex
                )
            } else {
                // Letter Aim Mode: 3D Blocks with preserved Bengali 'কার' signs
                drawTargetWordLetters(
                    letters = letterBoxes,
                    targetSegment = targetSegment,
                    isRevealed = isRoundResolved,
                    isBullseye = (roundSuccess == true),
                    density = density
                )
            }

            // 4. STUCK ARROW ON TARGET
            stuckArrow?.let { arrow ->
                drawEmbeddedArrow(
                    arrow = arrow,
                    wobbleFactor = wobbleAnim.value
                )
            }

            // 5. FLYING ARROW IN 3D PERSPECTIVE
            if (isArrowFlying) {
                val currentX = flightStartOffset.x + (flightEndOffset.x - flightStartOffset.x) * flightProgress
                val currentY = flightStartOffset.y + (flightEndOffset.y - flightStartOffset.y) * flightProgress
                val currentScale = 1.0f - (0.60f * flightProgress)
                val currentAngle = atan2(flightEndOffset.y - flightStartOffset.y, flightEndOffset.x - flightStartOffset.x)

                drawRealisticArrow(
                    tipPosition = Offset(currentX, currentY),
                    angleRad = currentAngle,
                    scale = currentScale,
                    drawShaftShadow = true
                )
            }

            // 6. AIMING GUIDANCE & CROSSHAIR (LOCKED TO TARGET CENTER WHEN NEARBY)
            if (!isArrowFlying && !isRoundResolved) {
                drawAimCrosshair(lockedCrosshairAim, isAiming = isAiming)
                if (isAiming) {
                    drawTrajectoryGuide(bowAnchor, lockedCrosshairAim)
                }
            }

            // 7. HIT SHOCKWAVE PULSE & FLASH ON BULLSEYE
            if (hitPulseAlpha > 0f && hitImpactPoint != null) {
                val impactPt = hitImpactPoint!!
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x99FFD700), Color(0x6638BDF8), Color.Transparent),
                        center = impactPt,
                        radius = hitPulseRadius
                    ),
                    radius = hitPulseRadius,
                    center = impactPt
                )
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = hitPulseAlpha),
                    radius = hitPulseRadius,
                    center = impactPt,
                    style = Stroke(width = 3.5f * hitPulseAlpha)
                )
            }

            // 8. MISS CAMERA SHAKE RED VIGNETTE FLASH
            if (missShakeAnim.value > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x66EF4444).copy(alpha = missShakeAnim.value * 0.45f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.85f
                    ),
                    size = size
                )
            }

            // 9. PARTICLES
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(p.x, p.y)
                )
            }

            // 10. ARCHER POV: HAND & COMPOSITE BOW AT BOTTOM
            val aimPoint = if (isAiming) (aimOffset ?: defaultAim) else defaultAim
            val pullBackOffset = if (isAiming) 25f else 0f
            drawArcherBowAndHand(
                size = size,
                anchor = bowAnchor,
                aimPoint = aimPoint,
                isDrawn = isAiming,
                drawOffset = pullBackOffset
            )

            // 11. READY NOCKED ARROW ON BOW
            if (!isArrowFlying && stuckArrow == null && !isRoundResolved) {
                val nockAngle = atan2(aimPoint.y - bowAnchor.y, aimPoint.x - bowAnchor.x)
                val arrowTip = Offset(
                    bowAnchor.x + cos(nockAngle) * 160f,
                    bowAnchor.y + sin(nockAngle) * 160f
                )
                drawRealisticArrow(
                    tipPosition = arrowTip,
                    angleRad = nockAngle,
                    scale = 0.95f,
                    drawShaftShadow = true
                )
            }
        }

        // TOP HUD: MODERNIZED SEMI-TRANSPARENT DARK GRADIENT OVERLAY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE60F172A),
                            Color(0x990F172A),
                            Color(0x330F172A),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Header Row: Back button, Archer Aim Title with text-shadow, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToConfig,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x551E293B))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Archer Aim",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0x99000000),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onBackToConfig,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x551E293B))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit to Menu",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Status Pills in Glassmorphic Dark Style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accuracy Glass Pill with bounce animation
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x990F172A),
                    border = BorderStroke(1.dp, Color(0x4438BDF8)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$accuracyPercentage%",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.scale(accuracyBounce.value)
                        )
                        Text(
                            text = "($hits/$totalAttempts)",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xCC94A3B8)
                        )
                    }
                }

                // Level Glass Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x990F172A),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Level",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xBB94A3B8)
                        )
                        Text(
                            text = "${currentIndex + 1}",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Target Word Quest Parchment Scroll Banner
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7), Color(0xFFFDE68A))
                            )
                        )
                    ) {
                        if (isMeaningMatch) {
                            Column(
                                modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "TARGET WORD",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E),
                                    letterSpacing = 1.2.sp
                                )
                                val isBengaliWord = isBengaliText(currentTarget.place1Text)
                                AnimatedContent(
                                    targetState = currentTarget.place1Text,
                                    transitionSpec = {
                                        (fadeIn(tween(260)) + scaleIn(initialScale = 0.88f))
                                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 1.08f))
                                    },
                                    label = "TargetWordTransition"
                                ) { word ->
                                    Text(
                                        text = word,
                                        fontFamily = if (isBengaliWord) AikyaFontFamily else PoppinsFontFamily,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HIT THE",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF92400E),
                                    letterSpacing = 1.sp
                                )
                                val isBengaliSegment = isBengaliText(targetSegment)
                                Text(
                                    text = "'$targetSegment'!",
                                    fontFamily = if (isBengaliSegment) AikyaFontFamily else PoppinsFontFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1E293B),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 4: Meaningful, Readable Progress Bar (Point 10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Round ${currentIndex + 1}/${gameState.targets.size}",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xEEFFFFFF)
                )
                LinearProgressIndicator(
                    progress = { ((currentIndex + 1).toFloat() / gameState.targets.size).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0x33FFFFFF)
                )
            }

            // Streak indicator (if > 1)
            if (streak > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberLight,
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "🔥 $streak STREAK!",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // BOTTOM HUD: CONTROLLER WHEEL (RELEASE TO SHOOT AUTOMATICALLY)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 16.dp)
        ) {
            // Controller Wheel (Move to aim, release to shoot automatically)
            ArcherAimControllerWheel(
                onMove = { normX, normY ->
                    if (!isArrowFlying && !isRoundResolved) {
                        val current = aimOffset ?: defaultAim
                        val dist = hypot(normX, normY).coerceIn(0f, 1f)
                        val speedFactor = dist * dist // smooth quadratic response for precise aim
                        val baseSpeed = 3.6f // controllable, calm movement
                        val newX = (current.x + normX * speedFactor * baseSpeed).coerceIn(24f, widthPx - 24f)
                        val newY = (current.y + normY * speedFactor * baseSpeed).coerceIn(heightPx * 0.16f, heightPx * 0.74f)
                        aimOffset = Offset(newX, newY)
                        isAiming = true
                    }
                },
                onRelease = {
                    isAiming = false
                    // When player leaves the controller wheel, arrow launches automatically
                    if (!isArrowFlying && !isRoundResolved) {
                        launchArrow(aimOffset ?: defaultAim)
                    }
                }
            )
        }

        // HIT FEEDBACK / MEANING POPUP
        if (isRoundResolved) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, if (roundSuccess == true) Color(0xFF10B981) else Color(0xFFEF4444)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Badge icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (roundSuccess == true) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (roundSuccess == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (roundSuccess == true) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = if (roundSuccess == true) "🎯 BULLSEYE! TARGET HIT!" else "TARGET MISSED!",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (roundSuccess == true) Color(0xFF15803D) else Color(0xFFBE123C),
                            textAlign = TextAlign.Center
                        )

                        // The Target Word
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isBengaliWord = isBengaliText(currentTarget.place1Text)
                            Text(
                                text = currentTarget.place1Text,
                                fontFamily = if (isBengaliWord) AikyaFontFamily else PoppinsFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = SlateText
                            )
                            IconButton(
                                onClick = { ttsManager.speak(currentTarget.place1Text) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2FF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Listen",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Word Meaning
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Meaning",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateMuted
                                )
                                val isBengaliMeaning = isBengaliText(currentTarget.place2Text)
                                Text(
                                    text = currentTarget.place2Text,
                                    fontFamily = if (isBengaliMeaning) KalpurushFontFamily else PoppinsFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Next Target Button
                        Button(
                            onClick = {
                                currentIndex++
                                isRoundResolved = false
                                roundSuccess = null
                                stuckArrow = null
                                hitMeaningIndex = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (roundSuccess == true) Color(0xFF10B981) else IndigoPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (currentIndex + 1 < gameState.targets.size) "Next Target 🏹" else "Finish Round 🏆",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GRAPHICS & CANVAS DRAWING IMPLEMENTATIONS
// -------------------------------------------------------------

private fun DrawScope.drawArcheryEnvironmentSkyAndMountains(size: Size, parallaxX: Float = 0f) {
    val horizonY = size.height * 0.32f

    // Sky Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E3A8A),
                Color(0xFF0284C7),
                Color(0xFF38BDF8),
                Color(0xFFBAE6FD)
            ),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(size.width, horizonY)
    )

    // Distant Misty Mountain Peaks with smooth parallax
    val distShift = parallaxX * 0.35f
    val mountainPath = Path().apply {
        moveTo(-40f + distShift, horizonY)
        lineTo(-40f + distShift, horizonY - 90f)
        lineTo(size.width * 0.18f + distShift, horizonY - 145f)
        lineTo(size.width * 0.35f + distShift, horizonY - 100f)
        lineTo(size.width * 0.52f + distShift, horizonY - 170f)
        lineTo(size.width * 0.70f + distShift, horizonY - 110f)
        lineTo(size.width * 0.88f + distShift, horizonY - 150f)
        lineTo(size.width + 40f + distShift, horizonY - 80f)
        lineTo(size.width + 40f + distShift, horizonY)
        close()
    }
    drawPath(mountainPath, Color(0xFF334155).copy(alpha = 0.55f))

    // Fore Mountains with snow highlights and foreground parallax
    val foreShift = parallaxX * 0.75f
    val foreMountain = Path().apply {
        moveTo(-40f + foreShift, horizonY)
        lineTo(-40f + foreShift, horizonY - 60f)
        lineTo(size.width * 0.25f + foreShift, horizonY - 110f)
        lineTo(size.width * 0.48f + foreShift, horizonY - 50f)
        lineTo(size.width * 0.72f + foreShift, horizonY - 125f)
        lineTo(size.width + 40f + foreShift, horizonY - 70f)
        lineTo(size.width + 40f + foreShift, horizonY)
        close()
    }
    drawPath(foreMountain, Color(0xFF1E293B).copy(alpha = 0.70f))
}

private fun DrawScope.drawArcheryFieldAndLaneLines(size: Size) {
    val horizonY = size.height * 0.32f
    val fieldHeight = size.height - horizonY

    // Alternating mowing turf stripes in 3D perspective with subtle gradient depth
    val stripeCount = 9
    for (i in 0 until stripeCount) {
        val t0 = i.toFloat() / stripeCount
        val t1 = (i + 1).toFloat() / stripeCount
        val y0 = horizonY + (fieldHeight * t0 * t0) // quadratic perspective
        val y1 = horizonY + (fieldHeight * t1 * t1)

        val cTop = if (i % 2 == 0) Color(0xFF15803D) else Color(0xFF16A34A)
        val cBottom = if (i % 2 == 0) Color(0xFF166534) else Color(0xFF15803D)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(cTop, cBottom),
                startY = y0,
                endY = y1 + 2f
            ),
            topLeft = Offset(0f, y0),
            size = Size(size.width, y1 - y0 + 2f)
        )
    }

    // Dense Forest Treeline at Horizon
    val treeCount = 24
    for (i in 0 until treeCount) {
        val treeX = (size.width / treeCount) * (i + 0.5f)
        val treeH = 34f + (i % 5) * 8f
        val treePath = Path().apply {
            moveTo(treeX, horizonY)
            lineTo(treeX - 16f, horizonY)
            lineTo(treeX, horizonY - treeH)
            lineTo(treeX + 16f, horizonY)
            close()
        }
        val treeColor = if (i % 2 == 0) Color(0xFF14532D) else Color(0xFF166534)
        drawPath(treePath, treeColor)
    }
}

private fun DrawScope.drawTargetWordLetters(
    letters: List<LetterTargetBox>,
    targetSegment: String,
    isRevealed: Boolean,
    isBullseye: Boolean,
    density: androidx.compose.ui.unit.Density
) {
    val visibleBoxes = letters.filter { it.text.isNotBlank() }
    if (visibleBoxes.isEmpty()) return

    // Word Backdrop Stand / Plaque Bar
    val first = visibleBoxes.first().bounds
    val last = visibleBoxes.last().bounds
    val plaqueRect = Rect(
        first.left - 12f,
        first.top - 8f,
        last.right + 12f,
        first.bottom + 10f
    )

    // Draw deep shadow of plaque
    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(plaqueRect.left, plaqueRect.top + 6f),
        size = Size(plaqueRect.width, plaqueRect.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )

    // Draw dark navy plaque base
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
            startY = plaqueRect.top,
            endY = plaqueRect.bottom
        ),
        topLeft = Offset(plaqueRect.left, plaqueRect.top),
        size = Size(plaqueRect.width, plaqueRect.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(plaqueRect.left, plaqueRect.top),
        size = Size(plaqueRect.width, plaqueRect.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
        style = Stroke(width = 3f)
    )

    // Individual Syllable / Letter 3D Blocks with intact 'কার'
    letters.forEach { box ->
        if (box.text.isBlank()) return@forEach

        val rect = box.bounds
        val segText = box.text
        val isTarget = box.isTarget

        // 3D Block Letter background
        val letterColor = if (isRevealed && isTarget) {
            Color(0xFF10B981)
        } else {
            Color(0xFFFF7A00)
        }

        // Draw 3D shadow depth for the letter
        drawRoundRect(
            color = Color(0xFF0A192F),
            topLeft = Offset(rect.left + 2f, rect.top + 6f),
            size = Size(rect.width - 4f, rect.height - 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )

        // Draw letter block fill
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (isRevealed && isTarget) Color(0xFF34D399) else Color(0xFFFFA000),
                    letterColor
                ),
                startY = rect.top,
                endY = rect.bottom
            ),
            topLeft = Offset(rect.left + 2f, rect.top + 2f),
            size = Size(rect.width - 4f, rect.height - 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )

        // Border outline
        drawRoundRect(
            color = if (isRevealed && isTarget) Color(0xFF064E3B) else Color(0xFF0A2540),
            topLeft = Offset(rect.left + 2f, rect.top + 2f),
            size = Size(rect.width - 4f, rect.height - 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = Stroke(width = 3f)
        )

        // Highlight line at top
        drawLine(
            color = Color(0x88FFFFFF),
            start = Offset(rect.left + 8f, rect.top + 6f),
            end = Offset(rect.right - 8f, rect.top + 6f),
            strokeWidth = 2f
        )

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = if (isBengaliText(segText)) {
                (rect.height * 0.44f).coerceAtMost(rect.width * 0.65f)
            } else {
                (rect.height * 0.60f)
            }
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val textBaselineY = rect.center.y - ((paint.descent() + paint.ascent()) / 2f)

        // Text shadow / 3D stroke
        paint.color = android.graphics.Color.parseColor("#0A2540")
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 8f
        drawContext.canvas.nativeCanvas.drawText(
            segText,
            rect.center.x,
            textBaselineY,
            paint
        )

        // Text fill
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        drawContext.canvas.nativeCanvas.drawText(
            segText,
            rect.center.x,
            textBaselineY,
            paint
        )
    }
}

private fun DrawScope.drawMeaningTargetOptions(
    options: List<MeaningTargetBox>,
    isRevealed: Boolean,
    isBullseye: Boolean,
    hitIndex: Int?
) {
    if (options.isEmpty()) return

    options.forEach { target ->
        val center = target.center
        val r = target.radius

        // Drop shadow for the target board
        drawCircle(
            color = Color(0x66000000),
            radius = r + 4f,
            center = Offset(center.x + 2f, center.y + 6f)
        )

        // Target Board Outer Rim
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // Ring 1 (White outer)
        drawCircle(
            color = Color(0xFFF8FAFC),
            radius = r * 0.88f,
            center = center
        )
        // Ring 2 (Black)
        drawCircle(
            color = Color(0xFF1E293B),
            radius = r * 0.70f,
            center = center
        )
        // Ring 3 (Blue)
        drawCircle(
            color = Color(0xFF0284C7),
            radius = r * 0.52f,
            center = center
        )
        // Ring 4 (Red)
        drawCircle(
            color = Color(0xFFDC2626),
            radius = r * 0.35f,
            center = center
        )
        // Ring 5 (Gold Bullseye)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE047), Color(0xFFEAB308)),
                center = center,
                radius = r * 0.18f
            ),
            radius = r * 0.18f,
            center = center
        )

        // Rim border
        val rimBorderColor = if (isRevealed && target.isCorrect) {
            Color(0xFF10B981)
        } else if (isRevealed && hitIndex == target.optionIndex && !target.isCorrect) {
            Color(0xFFEF4444)
        } else {
            Color(0xFF38BDF8)
        }
        drawCircle(
            color = rimBorderColor,
            radius = r,
            center = center,
            style = Stroke(width = if (isRevealed && target.isCorrect) 5f else 2.5f)
        )

        // Hanging Plaque Card for Meaning Text (with ample vertical gap so no overlap!)
        val plaqueWidth = (r * 2.35f).coerceIn(100f, 138f)
        val plaqueHeight = 76f
        val gap = 18f
        val plaqueLeft = center.x - (plaqueWidth / 2f)
        val plaqueTop = center.y + r + gap

        // Golden chains with metallic links connecting target rim to plaque
        val chainLeftX = center.x - r * 0.45f
        val chainRightX = center.x + r * 0.45f
        val chainTopY = center.y + r * 0.85f

        // Left chain
        drawLine(Color(0xFFD97706), Offset(chainLeftX, chainTopY), Offset(plaqueLeft + 14f, plaqueTop), 3.5f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFDE68A), Offset(chainLeftX, chainTopY), Offset(plaqueLeft + 14f, plaqueTop), 1.5f, cap = StrokeCap.Round)

        // Right chain
        drawLine(Color(0xFFD97706), Offset(chainRightX, chainTopY), Offset(plaqueLeft + plaqueWidth - 14f, plaqueTop), 3.5f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFDE68A), Offset(chainRightX, chainTopY), Offset(plaqueLeft + plaqueWidth - 14f, plaqueTop), 1.5f, cap = StrokeCap.Round)

        // Plaque drop shadow
        drawRoundRect(
            color = Color(0x66000000),
            topLeft = Offset(plaqueLeft + 2f, plaqueTop + 4f),
            size = Size(plaqueWidth, plaqueHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )

        // Plaque background gradient
        val plaqueBgBrush = Brush.verticalGradient(
            colors = if (isRevealed && target.isCorrect) {
                listOf(Color(0xFF064E3B), Color(0xFF022C22))
            } else if (isRevealed && hitIndex == target.optionIndex && !target.isCorrect) {
                listOf(Color(0xFF7F1D1D), Color(0xFF450A0A))
            } else {
                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
            },
            startY = plaqueTop,
            endY = plaqueTop + plaqueHeight
        )
        drawRoundRect(
            brush = plaqueBgBrush,
            topLeft = Offset(plaqueLeft, plaqueTop),
            size = Size(plaqueWidth, plaqueHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = rimBorderColor,
            topLeft = Offset(plaqueLeft, plaqueTop),
            size = Size(plaqueWidth, plaqueHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = if (isRevealed && target.isCorrect) 2.5f else 1.5f)
        )

        // Option Badge ("A", "B", "C") with vibrant individual theme colors
        val badgeRadius = 12f
        val badgeCenter = Offset(plaqueLeft + 16f, plaqueTop + 16f)
        val badgeBgColor = when (target.label) {
            "A" -> Color(0xFFF59E0B) // Amber
            "B" -> Color(0xFF0284C7) // Sky Cyan
            else -> Color(0xFF10B981) // Emerald
        }
        drawCircle(
            color = badgeBgColor,
            radius = badgeRadius,
            center = badgeCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = badgeRadius,
            center = badgeCenter,
            style = Stroke(width = 1f)
        )
        val badgePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 19f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            color = if (target.label == "A") android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        drawContext.canvas.nativeCanvas.drawText(
            target.label,
            badgeCenter.x,
            badgeCenter.y - ((badgePaint.descent() + badgePaint.ascent()) / 2f),
            badgePaint
        )

        // Meaning Text using StaticLayout with auto-sizing so NO truncation occurs!
        drawWrappedBengaliText(
            canvas = drawContext.canvas.nativeCanvas,
            text = target.text,
            centerX = center.x,
            startY = plaqueTop + 24f,
            maxWidth = plaqueWidth - 12f,
            maxHeight = plaqueHeight - 26f,
            baseColor = android.graphics.Color.WHITE
        )
    }
}

private fun drawWrappedBengaliText(
    canvas: android.graphics.Canvas,
    text: String,
    centerX: Float,
    startY: Float,
    maxWidth: Float,
    maxHeight: Float,
    baseColor: Int = android.graphics.Color.WHITE
) {
    val textPaint = android.text.TextPaint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = baseColor
    }

    // Auto-calculate optimal font size
    var currentSize = when {
        text.length <= 10 -> 20f
        text.length <= 18 -> 17f
        text.length <= 28 -> 14.5f
        else -> 12.5f
    }

    val widthInt = maxWidth.toInt().coerceAtLeast(30)
    var layout: android.text.StaticLayout

    while (true) {
        textPaint.textSize = currentSize
        layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthInt)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.05f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(text, textPaint, widthInt, android.text.Layout.Alignment.ALIGN_CENTER, 1.05f, 0f, false)
        }

        if (layout.height <= maxHeight || currentSize <= 10.5f) {
            break
        }
        currentSize -= 1f
    }

    canvas.save()
    canvas.translate(centerX - (widthInt / 2f), startY + ((maxHeight - layout.height) / 2f).coerceAtLeast(0f))
    layout.draw(canvas)
    canvas.restore()
}

private fun DrawScope.drawRealisticArrow(
    tipPosition: Offset,
    angleRad: Float,
    scale: Float,
    drawShaftShadow: Boolean
) {
    val totalLength = 220f * scale
    val shaftWidth = 5.5f * scale
    val tailPosition = Offset(
        tipPosition.x - cos(angleRad) * totalLength,
        tipPosition.y - sin(angleRad) * totalLength
    )

    rotate(degrees = Math.toDegrees(angleRad.toDouble()).toFloat(), pivot = tipPosition) {
        // Drop shadow for 3D depth
        if (drawShaftShadow) {
            drawLine(
                color = Color(0x33000000),
                start = Offset(tipPosition.x - totalLength, tipPosition.y + 12f * scale),
                end = Offset(tipPosition.x, tipPosition.y + 12f * scale),
                strokeWidth = shaftWidth
            )
        }

        // Arrow Shaft (Dark Carbon Fiber)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF0F172A))
            ),
            start = Offset(tipPosition.x - totalLength + (14f * scale), tipPosition.y),
            end = Offset(tipPosition.x - (24f * scale), tipPosition.y),
            strokeWidth = shaftWidth
        )

        // Metallic Arrowhead (Broadhead)
        val headPath = Path().apply {
            moveTo(tipPosition.x, tipPosition.y) // sharp tip
            lineTo(tipPosition.x - (32f * scale), tipPosition.y - (14f * scale)) // upper barb
            lineTo(tipPosition.x - (24f * scale), tipPosition.y - (4f * scale))
            lineTo(tipPosition.x - (24f * scale), tipPosition.y + (4f * scale))
            lineTo(tipPosition.x - (32f * scale), tipPosition.y + (14f * scale)) // lower barb
            close()
        }
        drawPath(headPath, Color(0xFFE2E8F0)) // Metallic blade
        drawPath(headPath, Color(0xFF475569), style = Stroke(width = 1.5f * scale))

        // Center spine of broadhead
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(tipPosition.x, tipPosition.y),
            end = Offset(tipPosition.x - (24f * scale), tipPosition.y),
            strokeWidth = 2f * scale
        )

        // Fletching (Feathers in Warm Amber/Brown)
        val fletchStart = tipPosition.x - totalLength + (16f * scale)
        val fletchEnd = tipPosition.x - totalLength + (75f * scale)

        // Top feather vane
        val topVane = Path().apply {
            moveTo(fletchStart, tipPosition.y)
            lineTo(fletchStart + (10f * scale), tipPosition.y - (18f * scale))
            lineTo(fletchEnd, tipPosition.y - (12f * scale))
            lineTo(fletchEnd - (8f * scale), tipPosition.y)
            close()
        }
        drawPath(topVane, Color(0xFFD97706))
        drawPath(topVane, Color(0xFF78350F), style = Stroke(width = 1f * scale))

        // Bottom feather vane
        val bottomVane = Path().apply {
            moveTo(fletchStart, tipPosition.y)
            lineTo(fletchStart + (10f * scale), tipPosition.y + (18f * scale))
            lineTo(fletchEnd, tipPosition.y + (12f * scale))
            lineTo(fletchEnd - (8f * scale), tipPosition.y)
            close()
        }
        drawPath(bottomVane, Color(0xFFB45309))
        drawPath(bottomVane, Color(0xFF78350F), style = Stroke(width = 1f * scale))

        // Rear Nock
        drawCircle(
            color = Color(0xFFF1F5F9),
            radius = 4f * scale,
            center = Offset(tipPosition.x - totalLength + (10f * scale), tipPosition.y)
        )
    }
}

private fun DrawScope.drawEmbeddedArrow(
    arrow: EmbeddedArrow,
    wobbleFactor: Float
) {
    val wobbleAngle = arrow.angleRad + (sin(wobbleFactor * 24f) * 0.12f * wobbleFactor)
    drawRealisticArrow(
        tipPosition = arrow.impactPoint,
        angleRad = wobbleAngle,
        scale = arrow.scale,
        drawShaftShadow = false
    )
}

private fun DrawScope.drawAimCrosshair(targetPoint: Offset, isAiming: Boolean = false) {
    val radius = if (isAiming) 34f else 30f

    // 1. High-contrast ambient shadow / dark halo for guaranteed visibility over any surface
    drawCircle(
        color = Color(0x33000000),
        radius = radius + 10f,
        center = targetPoint
    )

    // 2. Outer contrasting white ring underneath
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = radius,
        center = targetPoint,
        style = Stroke(width = 5.5f)
    )

    // 3. Main colored reticle ring (IndigoPrimary in app theme)
    drawCircle(
        color = if (isAiming) IndigoPrimary else IndigoSecondary,
        radius = radius,
        center = targetPoint,
        style = Stroke(width = 3.5f)
    )

    // 4. Subtle inner target ring
    drawCircle(
        color = if (isAiming) IndigoLight.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f),
        radius = radius * 0.55f,
        center = targetPoint,
        style = Stroke(width = 1.5f)
    )

    // 5. High-visibility 4 Cardinal crosshair ticks with white contrast background
    val tickGap = radius * 0.72f
    val tickLen = 14f

    fun drawContrastedTick(start: Offset, end: Offset) {
        // White backing line
        drawLine(
            color = Color.White,
            start = start,
            end = end,
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        // Primary colored tick line
        drawLine(
            color = if (isAiming) IndigoPrimary else IndigoSecondary,
            start = start,
            end = end,
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    // Left tick
    drawContrastedTick(
        Offset(targetPoint.x - tickGap - tickLen, targetPoint.y),
        Offset(targetPoint.x - tickGap, targetPoint.y)
    )
    // Right tick
    drawContrastedTick(
        Offset(targetPoint.x + tickGap, targetPoint.y),
        Offset(targetPoint.x + tickGap + tickLen, targetPoint.y)
    )
    // Top tick
    drawContrastedTick(
        Offset(targetPoint.x, targetPoint.y - tickGap - tickLen),
        Offset(targetPoint.x, targetPoint.y - tickGap)
    )
    // Bottom tick
    drawContrastedTick(
        Offset(targetPoint.x, targetPoint.y + tickGap),
        Offset(targetPoint.x, targetPoint.y + tickGap + tickLen)
    )

    // 6. Center Bullseye Aim Point (High-visibility Red/Coral with crisp white border)
    // Outer white halo
    drawCircle(
        color = Color.White,
        radius = 6.5f,
        center = targetPoint
    )
    // Inner vibrant bullseye bead
    drawCircle(
        color = RoseError,
        radius = 4.5f,
        center = targetPoint
    )
    // Center pinpoint reflection dot
    drawCircle(
        color = Color.White,
        radius = 1.5f,
        center = targetPoint + Offset(-1f, -1f)
    )
}

private fun DrawScope.drawTrajectoryGuide(start: Offset, end: Offset) {
    val count = 14
    for (i in 1..count) {
        val t = i.toFloat() / count
        val x = start.x + (end.x - start.x) * t
        val y = start.y + (end.y - start.y) * t
        val alpha = (0.3f + 0.65f * t)

        // White shadow under trajectory bead
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.7f),
            radius = (2f + 2.5f * t) + 1.5f,
            center = Offset(x, y)
        )
        // Indigo primary trajectory bead
        drawCircle(
            color = IndigoPrimary.copy(alpha = alpha),
            radius = 2f + 2.5f * t,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawArcherBowAndHand(
    size: Size,
    anchor: Offset,
    aimPoint: Offset,
    isDrawn: Boolean,
    drawOffset: Float
) {
    val bowWidth = size.width * 0.92f
    val bowGripY = anchor.y + 15f

    // Bow string points
    val leftLimbTip = Offset(anchor.x - (bowWidth * 0.48f), anchor.y - 120f)
    val rightLimbTip = Offset(anchor.x + (bowWidth * 0.48f), anchor.y - 120f)
    val stringNock = Offset(anchor.x, anchor.y + drawOffset)

    // Bowstring
    drawLine(
        color = Color(0xEEFFFFFF),
        start = leftLimbTip,
        end = stringNock,
        strokeWidth = 2.5f
    )
    drawLine(
        color = Color(0xEEFFFFFF),
        start = rightLimbTip,
        end = stringNock,
        strokeWidth = 2.5f
    )

    // Composite Bow Limbs (Smooth curved arch)
    val leftLimbPath = Path().apply {
        moveTo(anchor.x - 40f, bowGripY)
        quadraticTo(
            anchor.x - (bowWidth * 0.28f),
            anchor.y + 10f,
            leftLimbTip.x,
            leftLimbTip.y
        )
    }
    drawPath(
        path = leftLimbPath,
        color = Color(0xFF451A03),
        style = Stroke(width = 14f, cap = StrokeCap.Round)
    )
    drawPath(
        path = leftLimbPath,
        color = Color(0xFFD97706),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )

    val rightLimbPath = Path().apply {
        moveTo(anchor.x + 40f, bowGripY)
        quadraticTo(
            anchor.x + (bowWidth * 0.28f),
            anchor.y + 10f,
            rightLimbTip.x,
            rightLimbTip.y
        )
    }
    drawPath(
        path = rightLimbPath,
        color = Color(0xFF451A03),
        style = Stroke(width = 14f, cap = StrokeCap.Round)
    )
    drawPath(
        path = rightLimbPath,
        color = Color(0xFFD97706),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )

    // Archer's Arm & Hand holding the center grip
    val handCenter = Offset(anchor.x - 20f, anchor.y + 40f)

    // Forearm
    val armPath = Path().apply {
        moveTo(handCenter.x - 20f, handCenter.y)
        lineTo(handCenter.x - 80f, size.height + 60f)
        lineTo(handCenter.x + 30f, size.height + 60f)
        lineTo(handCenter.x + 20f, handCenter.y)
        close()
    }
    drawPath(armPath, Color(0xFFFBBF24).copy(alpha = 0.85f)) // Skin tone base

    // Dark wrist guard / bracer
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(handCenter.x - 45f, handCenter.y + 25f),
        size = Size(65f, 50f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
    )
    // Bracer straps
    drawLine(Color(0xFF38BDF8), Offset(handCenter.x - 45f, handCenter.y + 40f), Offset(handCenter.x + 20f, handCenter.y + 40f), 3f)

    // Hand gripping riser
    drawCircle(
        color = Color(0xFFFBBF24).copy(alpha = 0.92f),
        radius = 24f,
        center = handCenter
    )

    // Bow Riser / Handle Block
    drawRoundRect(
        color = Color(0xFF292524),
        topLeft = Offset(anchor.x - 45f, anchor.y - 15f),
        size = Size(90f, 40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(anchor.x - 45f, anchor.y - 15f),
        size = Size(90f, 40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
        style = Stroke(width = 3f)
    )

    // Dynamic Aiming Spotlight Beam (tilts naturally toward aimPoint)
    val beamVector = Offset(aimPoint.x - anchor.x, aimPoint.y - (anchor.y - 20f))
    val beamAngle = atan2(beamVector.y, beamVector.x)
    val beamDist = beamVector.getDistance().coerceAtLeast(100f)

    rotate(degrees = Math.toDegrees(beamAngle.toDouble()).toFloat() + 90f, pivot = Offset(anchor.x, anchor.y - 20f)) {
        val conePath = Path().apply {
            moveTo(anchor.x - 14f, anchor.y - 20f)
            lineTo(anchor.x - 45f, anchor.y - 20f - beamDist)
            lineTo(anchor.x + 45f, anchor.y - 20f - beamDist)
            lineTo(anchor.x + 14f, anchor.y - 20f)
            close()
        }
        drawPath(
            path = conePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x6638BDF8),
                    Color(0x2238BDF8),
                    Color.Transparent
                ),
                startY = anchor.y - 20f,
                endY = anchor.y - 20f - beamDist
            )
        )
    }

    // Burnished Golden Crest Medallion flush on the wooden riser
    val medallionCenter = Offset(anchor.x, anchor.y + 5f)
    drawCircle(Color(0xFFD97706), 14f, medallionCenter)
    drawCircle(Color(0xFFB45309), 14f, medallionCenter, style = Stroke(2f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
            center = medallionCenter,
            radius = 11f
        ),
        radius = 11f,
        center = medallionCenter
    )
    // Stylized arrowhead crest emblem
    val crestPath = Path().apply {
        moveTo(medallionCenter.x, medallionCenter.y - 7f)
        lineTo(medallionCenter.x + 5f, medallionCenter.y + 4f)
        lineTo(medallionCenter.x, medallionCenter.y + 1f)
        lineTo(medallionCenter.x - 5f, medallionCenter.y + 4f)
        close()
    }
    drawPath(crestPath, Color(0xFF78350F))
}

// -------------------------------------------------------------
// GAME OVER VIEW
// -------------------------------------------------------------

@Composable
private fun ArcheryGameOverView(
    hits: Int,
    misses: Int,
    coins: Int,
    totalTargets: Int,
    onPlayAgain: () -> Unit,
    onBackToConfig: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🏆", fontSize = 36.sp)
                    }

                    Text(
                        text = "Archery Master!",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateText,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "You practiced $totalTargets target words with real archery aim!",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = SlateMuted,
                        textAlign = TextAlign.Center
                    )

                    // Hits vs Misses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Hits",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$hits",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = "Bullseye Hits",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Misses",
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$misses",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBE123C)
                                )
                                Text(
                                    text = "Misses",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFFBE123C)
                                )
                            }
                        }
                    }

                    val totalShots = hits + misses
                    val accuracy = if (totalShots > 0) (hits * 100 / totalShots) else 0
                    Text(
                        text = "Accuracy: $accuracy%",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onPlayAgain,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Play Again 🏹",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToConfig,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Game Settings & Options",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateText
                        )
                    }
                }
            }
        }
    }
}
