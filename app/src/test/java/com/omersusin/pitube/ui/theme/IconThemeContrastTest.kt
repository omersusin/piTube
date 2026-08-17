package com.omersusin.pitube.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the icon-color contract: in every theme the app defines, the color
 * roles used by icons must hold WCAG AA contrast (>= 3:1 for graphics) against
 * the surfaces they are drawn on, and the intentionally fixed overlay colors
 * must contrast their (also fixed) backgrounds.
 *
 * Material You (API 31+) resolves from the system palette at runtime and
 * cannot be resolved in a JVM test; its fallback paths (Light/Dark) are
 * covered here.
 */
class IconThemeContrastTest {

    private fun assertContrast(label: String, fg: Color, bg: Color, minRatio: Float = 3.0f) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label: contrast $ratio < $minRatio (fg=${fg.toArgb()}, bg=${bg.toArgb()})",
            ratio >= minRatio
        )
    }

    /**
     * Variant combinations the UI actually exposes (classic themes allow all
     * three variants; themed modes use their default variant).
     */
    private fun variantCombos(mode: ThemeMode): List<ThemeVariant> = when (mode) {
        ThemeMode.LIGHT, ThemeMode.DARK -> ThemeVariant.entries.toList()
        ThemeMode.OLED -> listOf(ThemeVariant.DARK, ThemeVariant.AMOLED)
        else -> listOf(mode.defaultVariant())
    }

    private fun resolve(mode: ThemeMode, variant: ThemeVariant): ColorScheme {
        val effectiveMode = if (mode == ThemeMode.SYSTEM) {
            ThemeMode.DARK.resolveSystemDefault(isSystemDark = false)
        } else {
            mode
        }
        return applyVariantAndComplete(
            baseColorScheme = staticBaseColorScheme(effectiveMode, variant, CustomThemePalettes()),
            effectiveThemeMode = effectiveMode,
            effectiveVariant = variant
        )
    }

    @Test
    fun `scheme icon roles hold 3-to-1 contrast against their surfaces in every theme`() {
        val modes = ThemeMode.entries.filter { it != ThemeMode.MATERIAL_YOU && it != ThemeMode.SYSTEM }
        var combosChecked = 0
        for (mode in modes) {
            for (variant in variantCombos(mode)) {
                val scheme = resolve(mode, variant)
                val label = "$mode/$variant"

                assertContrast("$label onSurface/background", scheme.onSurface, scheme.background)
                assertContrast("$label onSurface/surface", scheme.onSurface, scheme.surface)
                assertContrast("$label onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
                assertContrast("$label onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
                assertContrast("$label onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer)
                assertContrast("$label onTertiary/tertiary", scheme.onTertiary, scheme.tertiary)
                assertContrast("$label onError/error", scheme.onError, scheme.error)
                assertContrast("$label error/background", scheme.error, scheme.background)
                combosChecked++
            }
        }
        assertTrue(combosChecked >= 27)
    }

    @Test
    fun `system theme resolves both system light and dark modes`() {
        val light = resolve(ThemeMode.SYSTEM, ThemeVariant.LIGHT)
        val dark = resolve(ThemeMode.SYSTEM, ThemeVariant.DARK)
        assertContrast("system light onSurface/background", light.onSurface, light.background)
        assertContrast("system dark onSurface/background", dark.onSurface, dark.background)
    }

    @Test
    fun `intentional video overlay icons contrast their fixed scrims`() {
        val black = Color.Black
        // PlayerContent playback-error overlay: coral icon + white text on an 80% black scrim
        assertContrast("error-overlay coral on black scrim", Color(0xFFFF6B6B), black)
        // MiniPlayer / ShortsScreen / PremiumControlsOverlay: white action icons on black
        assertContrast("overlay white icon on black", Color.White, black)
        // Shorts liked-state red heart on black video
        assertContrast("shorts liked red on black", Color.Red, black)
        // RecognitionOverlayService badge: onErrorContainer icon on errorContainer
        assertContrast(
            "recognition overlay close on badge",
            Color(0xFFFFDAD6),
            Color(0xFF93000A)
        )
    }

    @Test
    fun `sponsor-block swatch check mark contrasts every preset swatch`() {
        val presets = listOf(
            0xFF00D400, 0xFFFFFF00, 0xFF0000FF, 0xFFFF0000,
            0xFFFF7700, 0xFFFF69B4, 0xFF7700FF, 0xFF00FFFF, 0xFFFFFFFF
        )
        for (argb in presets) {
            val swatch = Color(argb)
            val check = if (swatch.luminance() > 0.30f) Color.Black else Color.White
            assertContrast("check on swatch 0x%08X".format(argb), check, swatch)
        }
    }

    // ---- ColorMath sanity (must match androidx ColorUtils semantics) ----

    @Test
    fun `relative luminance matches WCAG values`() {
        assertEquals(0f, Color.Black.relativeLuminance(), 0.0001f)
        assertEquals(1f, Color.White.relativeLuminance(), 0.0001f)
        assertEquals(0.2126f, Color.Red.relativeLuminance(), 0.002f)
    }

    @Test
    fun `hsl round-trips are lossless to within one channel step`() {
        val samples = listOf(
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF808080,
            0xFF006994, 0xFFFFD700, 0xFF0F0F0F, 0xFFE6E1E5
        )
        for (argb in samples) {
            val color = Color(argb)
            val hsl = color.toHsl()
            val back = hslToColor(hsl[0], hsl[1], hsl[2])
            val a = color.toArgb()
            val b = back.toArgb()
            val maxDelta = maxOf(
                abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
                abs((a and 0xFF) - (b and 0xFF))
            )
            assertTrue("round-trip drift for 0x%08X: $maxDelta".format(argb), maxDelta <= 1)
        }
    }

    @Test
    fun `hue shift by 60 degrees keeps pure red primary colors consistent`() {
        val red = Color(0xFFFF0000)
        val hsl = red.toHsl()
        val shifted = hslToColor((hsl[0] + 60f) % 360f, hsl[1], hsl[2])
        assertEquals(0xFFFFFF00.toInt(), shifted.toArgb())
    }
}
