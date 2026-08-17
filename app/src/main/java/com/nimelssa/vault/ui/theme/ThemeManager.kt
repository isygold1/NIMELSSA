package com.nimelssa.vault.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    AUTO, DARK, LIGHT
}

/**
 * Singleton holder for the user's theme preference.
 *
 * The choice is persisted in SharedPreferences and mirrored to a
 * StateFlow so the root [NIMELSSATheme] can re-compose instantly
 * when the user switches modes in Settings.
 *
 * Pattern intentionally mirrors [com.nimelssa.vault.data.UserSession]:
 * no ViewModel needed for a single app-wide preference.
 */
object ThemeManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    private var prefs: android.content.SharedPreferences? = null

    private val _mode = MutableStateFlow(ThemeMode.AUTO)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Call once from Application/Activity startup before content is composed. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val stored = p.getString(KEY_THEME_MODE, null)
        _mode.value = when (stored) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.AUTO
        }
    }

    fun setMode(newMode: ThemeMode) {
        _mode.value = newMode
        prefs?.edit()?.putString(KEY_THEME_MODE, when (newMode) {
            ThemeMode.AUTO -> "auto"
            ThemeMode.DARK -> "dark"
            ThemeMode.LIGHT -> "light"
        })?.apply()
    }
}