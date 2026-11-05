package com.example.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean = false
)

object LanguageManager {
    const val CODE_SYSTEM = "system"
    const val CODE_ARABIC = "ar"
    const val CODE_ENGLISH = "en"
    const val CODE_SPANISH = "es"
    const val CODE_FRENCH = "fr"
    const val CODE_GERMAN = "de"
    const val CODE_CHINESE = "zh"

    val SUPPORTED_LANGUAGES = listOf(
        AppLanguage(CODE_SYSTEM, "لغة الجهاز الافتراضية", "System Default"),
        AppLanguage(CODE_ARABIC, "العربية", "Arabic", isRtl = true),
        AppLanguage(CODE_ENGLISH, "English", "English"),
        AppLanguage(CODE_SPANISH, "Español", "Spanish"),
        AppLanguage(CODE_FRENCH, "Français", "French"),
        AppLanguage(CODE_GERMAN, "Deutsch", "German"),
        AppLanguage(CODE_CHINESE, "中文 (简体)", "Chinese (Simplified)")
    )

    private const val PREFS_NAME = "safeplayer_preferences"
    private const val KEY_LANGUAGE = "app_selected_language"

    private val _currentLanguageCode = MutableStateFlow(CODE_SYSTEM)
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    fun init(context: Context) {
        _currentLanguageCode.value = getSavedLanguageCode(context)
    }

    fun getSavedLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, CODE_SYSTEM) ?: CODE_SYSTEM
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        val code = getSavedLanguageCode(context)
        return SUPPORTED_LANGUAGES.find { it.code == code } ?: SUPPORTED_LANGUAGES.first()
    }

    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        _currentLanguageCode.value = languageCode

        val targetLocale = getTargetLocale(context, languageCode)
        Locale.setDefault(targetLocale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (languageCode == CODE_SYSTEM || languageCode.isBlank()) {
                    localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    localeManager?.applicationLocales = LocaleList(targetLocale)
                }
            } catch (_: Exception) {}
        }
    }

    fun getSystemLocale(context: Context? = null): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val systemLocales = Resources.getSystem().configuration.locales
            if (!systemLocales.isEmpty) {
                val loc = systemLocales.get(0)
                if (loc != null && loc.language.isNotBlank()) return loc
            }
            val defaultList = LocaleList.getDefault()
            if (!defaultList.isEmpty) {
                val loc = defaultList.get(0)
                if (loc != null && loc.language.isNotBlank()) return loc
            }
        }
        if (context != null) {
            val appLocales = context.applicationContext.resources.configuration.locales
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !appLocales.isEmpty) {
                val loc = appLocales.get(0)
                if (loc != null && loc.language.isNotBlank()) return loc
            }
        }
        return Locale.getDefault()
    }

    fun getTargetLocale(context: Context, languageCode: String = getSavedLanguageCode(context)): Locale {
        return if (languageCode == CODE_SYSTEM || languageCode.isBlank()) {
            getSystemLocale(context)
        } else {
            when (languageCode) {
                CODE_ARABIC -> Locale("ar")
                CODE_ENGLISH -> Locale("en")
                CODE_SPANISH -> Locale("es")
                CODE_FRENCH -> Locale("fr")
                CODE_GERMAN -> Locale("de")
                CODE_CHINESE -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.forLanguageTag(languageCode)
            }
        }
    }

    fun getLayoutDirection(context: Context, languageCode: String = getSavedLanguageCode(context)): LayoutDirection {
        val locale = getTargetLocale(context, languageCode)
        val dir = android.text.TextUtils.getLayoutDirectionFromLocale(locale)
        return if (dir == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    fun createLocalizedContext(context: Context, languageCode: String = getSavedLanguageCode(context)): Context {
        return try {
            val targetLocale = getTargetLocale(context, languageCode)
            Locale.setDefault(targetLocale)

            val config = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(targetLocale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = targetLocale
            }
            config.setLayoutDirection(targetLocale)

            try {
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
            } catch (_: Exception) {}

            context.createConfigurationContext(config)
        } catch (_: Exception) {
            context
        }
    }

    fun applyLocale(context: Context, languageCode: String = getSavedLanguageCode(context)): Context {
        return createLocalizedContext(context, languageCode)
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
