package com.example.domain

import com.example.data.local.LanguagePolicy

enum class AppLanguage {
    ENGLISH,
    BENGALI
}

object LanguageHelper {
    private val bengaliRegex = Regex("[\\u0980-\\u09FF]")

    fun detectLanguage(text: String): AppLanguage {
        val bengaliCount = bengaliRegex.findAll(text).count()
        return if (bengaliCount >= 3) AppLanguage.BENGALI else AppLanguage.ENGLISH
    }

    fun resolveReplyLanguage(input: String, policy: LanguagePolicy): AppLanguage {
        return when (policy) {
            LanguagePolicy.ENGLISH -> AppLanguage.ENGLISH
            LanguagePolicy.BENGALI -> AppLanguage.BENGALI
            LanguagePolicy.AUTO_DETECT -> detectLanguage(input)
        }
    }

    fun bilingual(
        lang: AppLanguage,
        english: String,
        bengali: String
    ): String = if (lang == AppLanguage.BENGALI) bengali else english
}
