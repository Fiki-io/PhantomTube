package com.phantom.tube.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

data class LanguageOption(
    val code: String,
    val displayName: String,
    val flag: String
)

object LocaleHelper {

    val SUPPORTED_LANGUAGES = listOf(
        LanguageOption("id", "Bahasa Indonesia", "ID"),
        LanguageOption("en", "English", "EN"),
        LanguageOption("ja", "日本語 (Japanese)", "JA"),
        LanguageOption("es", "Español (Spanish)", "ES"),
        LanguageOption("ru", "Русский (Russian)", "RU")
    )

    val SUPPORTED_LANGUAGE_CODES = SUPPORTED_LANGUAGES.map { it.code }.toSet()

    /**
     * Otomatis deteksi bahasa sistem Android user pada first install.
     * Jika bahasa sistem tidak ada di daftar terdaftar, otomatis fallback ke bahasa Inggris ("en").
     */
    fun resolveDefaultLanguage(): String {
        return try {
            val systemLanguage = Locale.getDefault().language.lowercase()
            when (systemLanguage) {
                "id", "in" -> "id"
                "ja" -> "ja"
                "es" -> "es"
                "ru" -> "ru"
                "en" -> "en"
                else -> "en" // Otomatis fallback ke Inggris default bila bahasa sistem tidak terdaftar
            }
        } catch (_: Exception) {
            "en"
        }
    }

    fun getLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "en" -> Locale("en")
            "ja" -> Locale("ja")
            "es" -> Locale("es")
            "ru" -> Locale("ru")
            else -> Locale("in") // Android standard for Indonesian
        }
    }

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}
