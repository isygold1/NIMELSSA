package com.nimelssa.vault.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OrientationMode {
    AUTO, PORTRAIT, LANDSCAPE
}

/**
 * Singleton holder for the user's PDF viewer orientation preference.
 *
 * Persisted in SharedPreferences and mirrored to a [StateFlow] so
 * PdfReaderScreen can react instantly when the user switches in Settings.
 */
object OrientationManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_ORIENTATION = "orientation_mode"

    private var prefs: android.content.SharedPreferences? = null

    private val _mode = MutableStateFlow(OrientationMode.AUTO)
    val mode: StateFlow<OrientationMode> = _mode.asStateFlow()

    /** Call once from Application/Activity startup before content is composed. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val stored = p.getString(KEY_ORIENTATION, null)
        _mode.value = when (stored) {
            "portrait" -> OrientationMode.PORTRAIT
            "landscape" -> OrientationMode.LANDSCAPE
            else -> OrientationMode.AUTO
        }
    }

    fun setMode(newMode: OrientationMode) {
        _mode.value = newMode
        prefs?.edit()?.putString(KEY_ORIENTATION, when (newMode) {
            OrientationMode.AUTO -> "auto"
            OrientationMode.PORTRAIT -> "portrait"
            OrientationMode.LANDSCAPE -> "landscape"
        })?.apply()
    }
}
