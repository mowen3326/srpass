package app.srpass.kykfz01.ui.settings

import android.content.Context
import app.srpass.kykfz01.ui.theme.ThemeMode

/** Persists the user's theme preference (follow-system / light / dark). */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences("srpass_ui", Context.MODE_PRIVATE)

    fun get(): ThemeMode {
        val v = prefs.getString(KEY, null) ?: ThemeMode.SYSTEM.name
        return ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.SYSTEM
    }

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    fun getDynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC, false)
    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color_v2"
    }
}
