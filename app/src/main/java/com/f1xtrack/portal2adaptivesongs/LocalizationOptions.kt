package com.f1xtrack.portal2adaptivesongs

import android.content.Context

data class LanguageOption(
    val code: String,
    val label: String
)

fun buildLanguageOptions(context: Context): List<LanguageOption> {
    return listOf(
        LanguageOption("system", context.getString(R.string.language_system)),
        LanguageOption("ar", context.getString(R.string.language_arabic_native)),
        LanguageOption("de", context.getString(R.string.language_german_native)),
        LanguageOption("en", context.getString(R.string.language_english)),
        LanguageOption("es", context.getString(R.string.language_spanish_native)),
        LanguageOption("hi", context.getString(R.string.language_hindi_native)),
        LanguageOption("ja", context.getString(R.string.language_japanese_native)),
        LanguageOption("ko", context.getString(R.string.language_korean_native)),
        LanguageOption("pl", context.getString(R.string.language_polish_native)),
        LanguageOption("pt", context.getString(R.string.language_portuguese_native)),
        LanguageOption("ru", context.getString(R.string.language_russian)),
        LanguageOption("tr", context.getString(R.string.language_turkish_native)),
        LanguageOption("zh", context.getString(R.string.language_chinese_native))
    )
}
