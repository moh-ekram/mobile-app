package com.example.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Provides subtle and tactile haptic feedback for flashcard gestures and interactions.
 */
class HapticHelper(context: Context) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Subtle tick for card flips, small icon taps, and quick toggles.
     */
    fun subtleTick() {
        vibrate(tickEffect, fallbackDurationMs = 10, fallbackAmplitude = 50)
    }

    /**
     * Crisp, tactile feedback for card swipes (Next / Previous card).
     */
    fun cardSwipe() {
        vibrate(clickEffect, fallbackDurationMs = 16, fallbackAmplitude = 90)
    }

    /**
     * Distinct tactile pulse for rating selections (Know, Confusion, Don't Know, Skip).
     */
    fun ratingSelected() {
        vibrate(clickEffect, fallbackDurationMs = 20, fallbackAmplitude = 110)
    }

    /**
     * Light tactile click for card flip interaction.
     */
    fun cardFlip() {
        vibrate(tickEffect, fallbackDurationMs = 12, fallbackAmplitude = 60)
    }

    private fun vibrate(effect: VibrationEffect?, fallbackDurationMs: Long, fallbackAmplitude: Int) {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && effect != null) {
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(fallbackDurationMs, fallbackAmplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(fallbackDurationMs)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private val tickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } catch (_: Exception) {
                null
            }
        } else null

        private val clickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } catch (_: Exception) {
                null
            }
        } else null
    }
}
