package eu.kanade.translation.recognizer

import com.google.mlkit.nl.translate.TranslateLanguage
import tachiyomi.core.common.preference.Preference

enum class TextRecognizerLanguage(var code: String, val label: String) {
    AUTO_DETECT("auto", "Auto-Detect"),
    CHINESE(TranslateLanguage.CHINESE, "Chinese (trad/sim)"),
    JAPANESE(TranslateLanguage.JAPANESE, "Japanese"),
    KOREAN(TranslateLanguage.KOREAN, "Korean"),
    INDONESIAN(TranslateLanguage.INDONESIAN, "Indonesian"),
    SPANISH(TranslateLanguage.SPANISH, "Spanish"),
    ENGLISH(TranslateLanguage.ENGLISH, "English"),
    ;

    /**
     * Convert MLKit language constant to ISO 639-1 code for Google Cloud Translation API
     * MLKit uses internal constants, but Cloud API expects standard language codes
     */
    fun toCloudApiCode(): String {
        return when (this) {
            AUTO_DETECT -> "auto"
            CHINESE -> "zh"  // Chinese (simplified and traditional both use 'zh')
            JAPANESE -> "ja"
            KOREAN -> "ko"
            INDONESIAN -> "id"
            SPANISH -> "es"
            ENGLISH -> "en"
        }
    }

    companion object {
        fun fromPref(pref: Preference<String>): TextRecognizerLanguage {
            val name = pref.get()
            var lang = entries.firstOrNull { it.name.equals(name, true) }
            if (lang == null) {
                pref.set(AUTO_DETECT.name)
                return AUTO_DETECT
            }
            return lang
        }
    }
}
