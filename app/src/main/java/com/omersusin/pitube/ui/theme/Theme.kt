package com.omersusin.pitube.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, AMOLED,
    OCEAN_BLUE, FOREST_GREEN, SUNSET_ORANGE, PURPLE_NEBULA, MIDNIGHT_BLACK,
    ROSE_GOLD, ARCTIC_ICE, CRIMSON_RED, ROYAL_GOLD, NORDIC_HORIZON,
    ESPRESSO, GUNMETAL, MINT_LIGHT, ROSE_LIGHT, SKY_LIGHT, CREAM_LIGHT
}

enum class ThemeVariant { LIGHT, DARK, AMOLED }

data class ExtendedColors(
    val textSecondary: Color,
    val border: Color,
    val success: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        textSecondary = Color.Unspecified,
        border = Color.Unspecified,
        success = Color.Unspecified
    )
}

private fun Color.adjust(
    saturationFactor: Float = 1.0f,
    lightnessFactor: Float = 1.0f,
    lightnessOverride: Float? = null
): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationFactor).coerceIn(0.0f, 1.0f)
    hsl[2] = lightnessOverride ?: (hsl[2] * lightnessFactor).coerceIn(0.0f, 1.0f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun ColorScheme.complete(isDark: Boolean, isOled: Boolean = false): ColorScheme {
    val primaryContainerColor = if (isDark) {
        if (isOled) Color.Black else primary.adjust(saturationFactor = 0.45f, lightnessOverride = 0.15f)
    } else {
        primary.adjust(saturationFactor = 0.35f, lightnessOverride = 0.94f)
    }
    val onPrimaryContainerColor = if (isDark) {
        primary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        primary.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }
    val secondaryContainerColor = if (isDark) {
        if (isOled) Color(0xFF161616) else secondary.adjust(saturationFactor = 0.40f, lightnessOverride = 0.14f)
    } else {
        secondary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.94f)
    }
    val onSecondaryContainerColor = if (isDark) {
        secondary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        secondary.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }
    val surfaceVariantColor = if (isDark) {
        if (isOled) Color(0xFF0C0C0C) else surface.adjust(lightnessOverride = 0.12f)
    } else {
        surface.adjust(saturationFactor = 0.10f, lightnessOverride = 0.92f)
    }
    val onSurfaceVariantColor = if (isDark) {
        onSurface.adjust(lightnessOverride = 0.75f)
    } else {
        onSurface.adjust(lightnessOverride = 0.35f)
    }
    val outlineColor = if (isDark) {
        surface.adjust(lightnessOverride = 0.38f)
    } else {
        surface.adjust(lightnessOverride = 0.50f)
    }
    val outlineVariantColor = if (isDark) {
        surface.adjust(lightnessOverride = 0.22f)
    } else {
        surface.adjust(lightnessOverride = 0.85f)
    }
    val surfaceContainerLowestColor = if (isDark) Color.Black else Color.White
    val surfaceContainerLowColor = if (isDark) {
        if (isOled) Color(0xFF0A0A0A) else surface.adjust(lightnessOverride = 0.06f)
    } else {
        surface.adjust(lightnessOverride = 0.96f)
    }
    val surfaceContainerColor = if (isDark) {
        if (isOled) Color(0xFF0F0F0F) else surface.adjust(lightnessOverride = 0.08f)
    } else {
        surface.adjust(lightnessOverride = 0.94f)
    }
    val surfaceContainerHighColor = if (isDark) {
        if (isOled) Color(0xFF161616) else surface.adjust(lightnessOverride = 0.10f)
    } else {
        surface.adjust(lightnessOverride = 0.92f)
    }
    val surfaceContainerHighestColor = if (isDark) {
        if (isOled) Color(0xFF202020) else surface.adjust(lightnessOverride = 0.14f)
    } else {
        surface.adjust(lightnessOverride = 0.90f)
    }
    return this.copy(
        primaryContainer = primaryContainerColor,
        onPrimaryContainer = onPrimaryContainerColor,
        secondaryContainer = secondaryContainerColor,
        onSecondaryContainer = onSecondaryContainerColor,
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = onSurfaceVariantColor,
        outline = outlineColor,
        outlineVariant = outlineVariantColor,
        surfaceContainerLowest = surfaceContainerLowestColor,
        surfaceContainerLow = surfaceContainerLowColor,
        surfaceContainer = surfaceContainerColor,
        surfaceContainerHigh = surfaceContainerHighColor,
        surfaceContainerHighest = surfaceContainerHighestColor
    )
}

// Color Schemes
private val LightColorScheme = lightColorScheme(
    primary = LightThemeColors.Primary,
    onPrimary = LightThemeColors.OnPrimary,
    secondary = LightThemeColors.Secondary,
    onSecondary = LightThemeColors.OnSecondary,
    background = LightThemeColors.Background,
    onBackground = LightThemeColors.Text,
    surface = LightThemeColors.Surface,
    onSurface = LightThemeColors.Text,
    error = LightThemeColors.Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkThemeColors.Primary,
    onPrimary = DarkThemeColors.OnPrimary,
    secondary = DarkThemeColors.Secondary,
    onSecondary = DarkThemeColors.OnSecondary,
    background = DarkThemeColors.Background,
    onBackground = DarkThemeColors.Text,
    surface = DarkThemeColors.Surface,
    onSurface = DarkThemeColors.Text,
    error = DarkThemeColors.Error,
    onError = Color.Black
)

private val OLEDColorScheme = darkColorScheme(
    primary = OLEDThemeColors.Primary,
    onPrimary = OLEDThemeColors.OnPrimary,
    secondary = OLEDThemeColors.Secondary,
    onSecondary = OLEDThemeColors.OnSecondary,
    background = OLEDThemeColors.Background,
    onBackground = OLEDThemeColors.Text,
    surface = OLEDThemeColors.Surface,
    onSurface = OLEDThemeColors.Text,
    error = OLEDThemeColors.Error,
    onError = Color.White
)

private val OceanBlueColorScheme = darkColorScheme(
    primary = OceanBlueThemeColors.Primary,
    onPrimary = OceanBlueThemeColors.OnPrimary,
    secondary = OceanBlueThemeColors.Secondary,
    onSecondary = OceanBlueThemeColors.OnSecondary,
    background = OceanBlueThemeColors.Background,
    onBackground = OceanBlueThemeColors.Text,
    surface = OceanBlueThemeColors.Surface,
    onSurface = OceanBlueThemeColors.Text,
    error = OceanBlueThemeColors.Error,
    onError = Color.White
)

private val ForestGreenColorScheme = darkColorScheme(
    primary = ForestGreenThemeColors.Primary,
    onPrimary = ForestGreenThemeColors.OnPrimary,
    secondary = ForestGreenThemeColors.Secondary,
    onSecondary = ForestGreenThemeColors.OnSecondary,
    background = ForestGreenThemeColors.Background,
    onBackground = ForestGreenThemeColors.Text,
    surface = ForestGreenThemeColors.Surface,
    onSurface = ForestGreenThemeColors.Text,
    error = ForestGreenThemeColors.Error,
    onError = Color.White
)

private val SunsetOrangeColorScheme = darkColorScheme(
    primary = SunsetOrangeThemeColors.Primary,
    onPrimary = SunsetOrangeThemeColors.OnPrimary,
    secondary = SunsetOrangeThemeColors.Secondary,
    onSecondary = SunsetOrangeThemeColors.OnSecondary,
    background = SunsetOrangeThemeColors.Background,
    onBackground = SunsetOrangeThemeColors.Text,
    surface = SunsetOrangeThemeColors.Surface,
    onSurface = SunsetOrangeThemeColors.Text,
    error = SunsetOrangeThemeColors.Error,
    onError = Color.White
)

private val PurpleNebulaColorScheme = darkColorScheme(
    primary = PurpleNebulaThemeColors.Primary,
    onPrimary = PurpleNebulaThemeColors.OnPrimary,
    secondary = PurpleNebulaThemeColors.Secondary,
    onSecondary = PurpleNebulaThemeColors.OnSecondary,
    background = PurpleNebulaThemeColors.Background,
    onBackground = PurpleNebulaThemeColors.Text,
    surface = PurpleNebulaThemeColors.Surface,
    onSurface = PurpleNebulaThemeColors.Text,
    error = PurpleNebulaThemeColors.Error,
    onError = Color.White
)

private val MidnightBlackColorScheme = darkColorScheme(
    primary = MidnightBlackThemeColors.Primary,
    onPrimary = MidnightBlackThemeColors.OnPrimary,
    secondary = MidnightBlackThemeColors.Secondary,
    onSecondary = MidnightBlackThemeColors.OnSecondary,
    background = MidnightBlackThemeColors.Background,
    onBackground = MidnightBlackThemeColors.Text,
    surface = MidnightBlackThemeColors.Surface,
    onSurface = MidnightBlackThemeColors.Text,
    error = MidnightBlackThemeColors.Error,
    onError = Color.White
)

private val RoseGoldColorScheme = darkColorScheme(
    primary = RoseGoldThemeColors.Primary,
    onPrimary = RoseGoldThemeColors.OnPrimary,
    secondary = RoseGoldThemeColors.Secondary,
    onSecondary = RoseGoldThemeColors.OnSecondary,
    background = RoseGoldThemeColors.Background,
    onBackground = RoseGoldThemeColors.Text,
    surface = RoseGoldThemeColors.Surface,
    onSurface = RoseGoldThemeColors.Text,
    error = RoseGoldThemeColors.Error,
    onError = Color.White
)

private val ArcticIceColorScheme = darkColorScheme(
    primary = ArcticIceThemeColors.Primary,
    onPrimary = ArcticIceThemeColors.OnPrimary,
    secondary = ArcticIceThemeColors.Secondary,
    onSecondary = ArcticIceThemeColors.OnSecondary,
    background = ArcticIceThemeColors.Background,
    onBackground = ArcticIceThemeColors.Text,
    surface = ArcticIceThemeColors.Surface,
    onSurface = ArcticIceThemeColors.Text,
    error = ArcticIceThemeColors.Error,
    onError = Color.White
)

private val CrimsonRedColorScheme = darkColorScheme(
    primary = CrimsonRedThemeColors.Primary,
    onPrimary = CrimsonRedThemeColors.OnPrimary,
    secondary = CrimsonRedThemeColors.Secondary,
    onSecondary = CrimsonRedThemeColors.OnSecondary,
    background = CrimsonRedThemeColors.Background,
    onBackground = CrimsonRedThemeColors.Text,
    surface = CrimsonRedThemeColors.Surface,
    onSurface = CrimsonRedThemeColors.Text,
    error = CrimsonRedThemeColors.Error,
    onError = Color.White
)

private val RoyalGoldColorScheme = darkColorScheme(
    primary = RoyalGoldThemeColors.Primary,
    onPrimary = RoyalGoldThemeColors.OnPrimary,
    secondary = RoyalGoldThemeColors.Secondary,
    onSecondary = RoyalGoldThemeColors.OnSecondary,
    background = RoyalGoldThemeColors.Background,
    onBackground = RoyalGoldThemeColors.Text,
    surface = RoyalGoldThemeColors.Surface,
    onSurface = RoyalGoldThemeColors.Text,
    error = RoyalGoldThemeColors.Error,
    onError = Color.Black
)

private val NordicHorizonColorScheme = darkColorScheme(
    primary = NordicHorizonThemeColors.Primary,
    onPrimary = NordicHorizonThemeColors.OnPrimary,
    secondary = NordicHorizonThemeColors.Secondary,
    onSecondary = NordicHorizonThemeColors.OnSecondary,
    background = NordicHorizonThemeColors.Background,
    onBackground = NordicHorizonThemeColors.Text,
    surface = NordicHorizonThemeColors.Surface,
    onSurface = NordicHorizonThemeColors.Text,
    error = NordicHorizonThemeColors.Error,
    onError = Color.Black
)

private val EspressoColorScheme = darkColorScheme(
    primary = EspressoThemeColors.Primary,
    onPrimary = EspressoThemeColors.OnPrimary,
    secondary = EspressoThemeColors.Secondary,
    onSecondary = EspressoThemeColors.OnSecondary,
    background = EspressoThemeColors.Background,
    onBackground = EspressoThemeColors.Text,
    surface = EspressoThemeColors.Surface,
    onSurface = EspressoThemeColors.Text,
    error = EspressoThemeColors.Error,
    onError = Color.White
)

private val GunmetalColorScheme = darkColorScheme(
    primary = GunmetalThemeColors.Primary,
    onPrimary = GunmetalThemeColors.OnPrimary,
    secondary = GunmetalThemeColors.Secondary,
    onSecondary = GunmetalThemeColors.OnSecondary,
    background = GunmetalThemeColors.Background,
    onBackground = GunmetalThemeColors.Text,
    surface = GunmetalThemeColors.Surface,
    onSurface = GunmetalThemeColors.Text,
    error = GunmetalThemeColors.Error,
    onError = Color.White
)

private val MintLightColorScheme = lightColorScheme(
    primary = MintLightThemeColors.Primary,
    onPrimary = MintLightThemeColors.OnPrimary,
    secondary = MintLightThemeColors.Secondary,
    onSecondary = MintLightThemeColors.OnSecondary,
    background = MintLightThemeColors.Background,
    onBackground = MintLightThemeColors.Text,
    surface = MintLightThemeColors.Surface,
    onSurface = MintLightThemeColors.Text,
    error = MintLightThemeColors.Error
)

private val RoseLightColorScheme = lightColorScheme(
    primary = RoseLightThemeColors.Primary,
    onPrimary = RoseLightThemeColors.OnPrimary,
    secondary = RoseLightThemeColors.Secondary,
    onSecondary = RoseLightThemeColors.OnSecondary,
    background = RoseLightThemeColors.Background,
    onBackground = RoseLightThemeColors.Text,
    surface = RoseLightThemeColors.Surface,
    onSurface = RoseLightThemeColors.Text,
    error = RoseLightThemeColors.Error
)

private val SkyLightColorScheme = lightColorScheme(
    primary = SkyLightThemeColors.Primary,
    onPrimary = SkyLightThemeColors.OnPrimary,
    secondary = SkyLightThemeColors.Secondary,
    onSecondary = SkyLightThemeColors.OnSecondary,
    background = SkyLightThemeColors.Background,
    onBackground = SkyLightThemeColors.Text,
    surface = SkyLightThemeColors.Surface,
    onSurface = SkyLightThemeColors.Text,
    error = SkyLightThemeColors.Error
)

private val CreamLightColorScheme = lightColorScheme(
    primary = CreamLightThemeColors.Primary,
    onPrimary = CreamLightThemeColors.OnPrimary,
    secondary = CreamLightThemeColors.Secondary,
    onSecondary = CreamLightThemeColors.OnSecondary,
    background = CreamLightThemeColors.Background,
    onBackground = CreamLightThemeColors.Text,
    surface = CreamLightThemeColors.Surface,
    onSurface = CreamLightThemeColors.Text,
    error = CreamLightThemeColors.Error
)

private fun ColorScheme.withVariant(variant: ThemeVariant): ColorScheme {
    val isDark = variant != ThemeVariant.LIGHT
    val isAmoled = variant == ThemeVariant.AMOLED
    if (variant == ThemeVariant.LIGHT) return complete(isDark = false)
    val base = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = if (ColorUtils.calculateLuminance(primary.toArgb()) > 0.45) Color.Black else Color.White,
            secondary = secondary,
            onSecondary = if (ColorUtils.calculateLuminance(secondary.toArgb()) > 0.45) Color.Black else Color.White,
            background = if (isAmoled) Color.Black else primary.adjust(saturationFactor = 0.12f, lightnessOverride = 0.055f),
            onBackground = Color(0xFFE6E1E5),
            surface = if (isAmoled) Color.Black else primary.adjust(saturationFactor = 0.10f, lightnessOverride = 0.08f),
            onSurface = Color(0xFFE6E1E5),
            error = error,
            onError = onError
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            onSecondary = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            error = error,
            onError = Color.White
        )
    }
    return base.complete(isDark = isDark, isOled = isAmoled)
}

fun resolveColorScheme(
    isSystemDark: Boolean,
    themeMode: ThemeMode
): ColorScheme {
    val effectiveMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
        else -> themeMode
    }
    val variant = when (effectiveMode) {
        ThemeMode.LIGHT, ThemeMode.MINT_LIGHT, ThemeMode.ROSE_LIGHT, ThemeMode.SKY_LIGHT, ThemeMode.CREAM_LIGHT -> ThemeVariant.LIGHT
        ThemeMode.AMOLED, ThemeMode.MIDNIGHT_BLACK -> ThemeVariant.AMOLED
        else -> ThemeVariant.DARK
    }
    val baseScheme = when (effectiveMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.AMOLED -> OLEDColorScheme
        ThemeMode.OCEAN_BLUE -> OceanBlueColorScheme
        ThemeMode.FOREST_GREEN -> ForestGreenColorScheme
        ThemeMode.SUNSET_ORANGE -> SunsetOrangeColorScheme
        ThemeMode.PURPLE_NEBULA -> PurpleNebulaColorScheme
        ThemeMode.MIDNIGHT_BLACK -> MidnightBlackColorScheme
        ThemeMode.ROSE_GOLD -> RoseGoldColorScheme
        ThemeMode.ARCTIC_ICE -> ArcticIceColorScheme
        ThemeMode.CRIMSON_RED -> CrimsonRedColorScheme
        ThemeMode.ROYAL_GOLD -> RoyalGoldColorScheme
        ThemeMode.NORDIC_HORIZON -> NordicHorizonColorScheme
        ThemeMode.ESPRESSO -> EspressoColorScheme
        ThemeMode.GUNMETAL -> GunmetalColorScheme
        ThemeMode.MINT_LIGHT -> MintLightColorScheme
        ThemeMode.ROSE_LIGHT -> RoseLightColorScheme
        ThemeMode.SKY_LIGHT -> SkyLightColorScheme
        ThemeMode.CREAM_LIGHT -> CreamLightColorScheme
        ThemeMode.SYSTEM -> if (isSystemDark) DarkColorScheme else LightColorScheme
    }
    return baseScheme.withVariant(variant)
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

val PiTubeTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

val PiTubeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PiTubeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = resolveColorScheme(isDark, themeMode)

    val extendedColors = ExtendedColors(
        textSecondary = colorScheme.onSurfaceVariant,
        border = colorScheme.outlineVariant,
        success = colorScheme.tertiary
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PiTubeTypography,
            shapes = PiTubeShapes,
            content = content
        )
    }
}
