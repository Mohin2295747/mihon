package eu.kanade.translation.recognizer

import com.google.mlkit.nl.translate.TranslateLanguage
import tachiyomi.core.common.preference.Preference

enum class TextRecognizerLanguage(var code: String, val label: String) {
    AUTO_DETECT("auto", "Auto-Detect"),
    CHINESE(TranslateLanguage.CHINESE, "Chinese (trad/sim)"),
    JAPANESE(TranslateLanguage.JAPANESE, "Japanese"),
    KOREAN(TranslateLanguage.KOREAN, "Korean"),
    INDONESIAN(TranslateLanguage.INDONESIAN, "Indonesian"),
    ENGLISH(TranslateLanguage.ENGLISH, "English"),
    ;

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
