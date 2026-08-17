package com.omersusin.pitube.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure-Kotlin color math replicating androidx.core.graphics.ColorUtils
 * (which delegates to android.graphics.Color and is not JVM-testable).
 * Theme derivation must stay JVM-testable so IconThemeContrastTest can
 * assert icon contrast for every theme.
 */

internal fun Color.relativeLuminance(): Float {
    val r = toArgb() shr 16 and 0xFF
    val g = toArgb() shr 8 and 0xFF
    val b = toArgb() and 0xFF
    val red = linearize(r)
    val green = linearize(g)
    val blue = linearize(b)
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
}

private fun linearize(channel: Int): Float {
    val c = channel / 255.0f
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

/**
 * Android-compatible RGB->HSL. h in [0, 360), s/l in [0, 1]; alpha ignored.
 */
internal fun Color.toHsl(): FloatArray {
    val r = toArgb() shr 16 and 0xFF
    val g = toArgb() shr 8 and 0xFF
    val b = toArgb() and 0xFF

    val rf = r / 255.0f
    val gf = g / 255.0f
    val bf = b / 255.0f

    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    var h = 0f
    if (delta != 0f) {
        h = when (max) {
            rf -> (((gf - bf) / delta) % 6f)
            gf -> ((bf - rf) / delta) + 2f
            else -> ((rf - gf) / delta) + 4f
        }
        h *= 60f
        if (h < 0f) h += 360f
    }

    val l = (max + min) / 2f
    val s = if (delta == 0f) {
        0f
    } else {
        delta / (1f - abs(2f * l - 1f))
    }
    return floatArrayOf(h, s, l)
}

/**
 * Android-compatible HSL->RGB. h in [0, 360), s/l in [0, 1].
 */
internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hue = (h + 360f) % 360f
    val c = (1f - abs(2f * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - abs((hue / 60f % 2f) - 1f))

    val r: Float
    val g: Float
    val b: Float
    when (hue.toInt() / 60) {
        0 -> { r = c; g = x; b = 0f }
        1 -> { r = x; g = c; b = 0f }
        2 -> { r = 0f; g = c; b = x }
        3 -> { r = 0f; g = x; b = c }
        4 -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }

    fun quantize(v: Float): Int = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)

    return Color((quantize(r) shl 16) or (quantize(g) shl 8) or quantize(b))
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val high = max(la, lb)
    val low = min(la, lb)
    return (high + 0.05f) / (low + 0.05f)
}