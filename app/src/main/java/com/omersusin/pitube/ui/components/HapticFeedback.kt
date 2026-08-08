package com.omersusin.pitube.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedback {
    fun performClick(context: Context) {
        performHaptic(context, VibrationEffect.KEYBOARD_TAP)
    }

    fun performLongPress(context: Context) {
        performHaptic(context, VibrationEffect.LONG_PRESS)
    }

    fun performDoubleTap(context: Context) {
        performHaptic(context, VibrationEffect.CONFIRM)
    }

    private fun performHaptic(context: Context, effectId: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
        } catch (_: Exception) { }
    }
}
