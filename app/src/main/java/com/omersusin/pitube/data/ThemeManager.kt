package com.omersusin.pitube.data

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppTheme(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val isDark: Boolean
) {
    LIGHT("Light", Color(0xFF6200EE), Color(0xFF03DAC6), Color(0xFFFFFFFF), Color(0xFFF5F5F5), false),
    DARK("Dark", Color(0xFFBB86FC), Color(0xFF03DAC6), Color(0xFF121212), Color(0xFF1E1E1E), true),
    OLED_BLACK("OLED Black", Color(0xFFBB86FC), Color(0xFF03DAC6), Color(0xFF000000), Color(0xFF0A0A0A), true),
    OCEAN_BLUE("Ocean Blue", Color(0xFF0066CC), Color(0xFF00ACC1), Color(0xFF0A1929), Color(0xFF132F4C), true),
    FOREST_GREEN("Forest Green", Color(0xFF2E7D32), Color(0xFF66BB6A), Color(0xFF0D1F0D), Color(0xFF1A2E1A), true),
    SUNSET_ORANGE("Sunset Orange", Color(0xFFE65100), Color(0xFFFF9800), Color(0xFF1F0F00), Color(0xFF2E1A0A), true),
    PURPLE_NEBULA("Purple Nebula", Color(0xFF6A1B9A), Color(0xFFAB47BC), Color(0xFF1A0A1F), Color(0xFF2E1A2E), true),
    MIDNIGHT_BLACK("Midnight Black", Color(0xFF90CAF9), Color(0xFF64B5F6), Color(0xFF0A0A0A), Color(0xFF141414), true),
    ROSE_GOLD("Rose Gold", Color(0xFFD81B60), Color(0xFFF06292), Color(0xFF1F0A14), Color(0xFF2E1420), true),
    ARCTIC_ICE("Arctic Ice", Color(0xFF0288D1), Color(0xFF4FC3F7), Color(0xFFE3F2FD), Color(0xFFBBDEFB), false),
    CRIMSON_RED("Crimson Red", Color(0xFFC62828), Color(0xFFEF5350), Color(0xFF1F0A0A), Color(0xFF2E1414), true)
}

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"

    fun getTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME, null) ?: return AppTheme.DARK
        return AppTheme.values().find { it.name == themeName } ?: AppTheme.DARK
    }

    fun setTheme(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }
}
