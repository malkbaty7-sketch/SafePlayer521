package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    const val PREFS_NAME = "safeplayer_theme_preferences"
    const val KEY_THEME_COLOR = "app_theme_color"
    const val KEY_THEME_MODE = "app_theme_mode" // "system", "light", "dark"

    const val COLOR_BRONZE = "bronze"
    const val COLOR_OCEAN = "ocean"
    const val COLOR_FOREST = "forest"
    const val COLOR_SLATE = "slate"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private val _themeColor = MutableStateFlow(COLOR_BRONZE)
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    private val _themeMode = MutableStateFlow(MODE_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeColor.value = prefs.getString(KEY_THEME_COLOR, COLOR_BRONZE) ?: COLOR_BRONZE
        _themeMode.value = prefs.getString(KEY_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun setThemeColor(context: Context, color: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_COLOR, color).apply()
        _themeColor.value = color
    }

    fun setThemeMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun toggleDarkMode(context: Context) {
        val currentMode = _themeMode.value
        val newMode = if (currentMode == MODE_DARK) MODE_LIGHT else MODE_DARK
        setThemeMode(context, newMode)
    }
}
