package com.omersusin.pitube.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedback {
    fun performClick(context: Context) {
        performHaptic(context, 0) // KEYBOARD_TAP equivalent
    }

    fun performLongPress(context: Context) {
        performHaptic(context, 0) // Long press equivalent
    }

    fun performDoubleTap(context: Context) {
        performHaptic(context, 0) // Confirm equivalent
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) { }
    }
}
