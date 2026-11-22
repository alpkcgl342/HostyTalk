package com.hostytalk.app.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class Language(val code: String, val nativeName: String, val flag: String)

/** Uygulamanın ve çevirinin desteklediği diller. Sunucudaki app/languages.py ile aynı olmalı. */
object Languages {
    val all = listOf(
        Language("tr", "Türkçe", "🇹🇷"),
        Language("en", "English", "🇬🇧"),
        Language("ar", "العربية", "🇸🇦"),
        Language("fr", "Français", "🇫🇷"),
        Language("ru", "Русский", "🇷🇺"),
    )

    fun of(code: String?): Language = all.firstOrNull { it.code == code } ?: all[1]

    /** Uygulama arayüz dili (Android 13+ sistem ayarıyla da senkron). */
    fun appLanguage(): String? = AppCompatDelegate.getApplicationLocales()[0]?.language

    fun setAppLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }
}
