package com.glosdalen.app.backend.deepl

data class VocabularyEntry(
    val originalWord: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val translations: List<Translation>,
)

data class Translation(
    val text: String,
    val context: String? = null
)

enum class Language(
    val code: String, 
    val displayName: String, 
    val nativeName: String,
    /** ISO 639-1 language code for ElevenLabs TTS. Null if not supported by ElevenLabs. */
    val elevenLabsCode: String? = null,
    private val localizedNames: Map<String, String> = emptyMap()
) {
    // All DeepL-supported languages (alphabetically by display name)
    // ElevenLabs supports: English, Japanese, Chinese, German, Hindi, French, Korean, 
    // Portuguese, Italian, Spanish, Indonesian, Dutch, Turkish, Filipino, Polish, 
    // Swedish, Bulgarian, Romanian, Arabic, Czech, Greek, Finnish, Croatian, Malay, 
    // Slovak, Danish, Tamil, Ukrainian, Russian, Hungarian, Norwegian, Vietnamese
    // ARABIC("AR", "Arabic", "العربية", "ar"), deactivated because RTL
    BULGARIAN("BG", "Bulgarian", "Български", "bg"),
    CHINESE_SIMPLIFIED("ZH", "Chinese (Simplified)", "简体中文", "zh"),
    CHINESE_TRADITIONAL("ZH-HANT", "Chinese (Traditional)", "繁體中文", "zh"),
    CZECH("CS", "Czech", "Čeština", "cs"),
    GERMAN("DE", "German", "Deutsch", "de"),
    ENGLISH("EN", "English", "English", "en"),
    SPANISH("ES", "Spanish", "Español", "es"),
    ESTONIAN("ET", "Estonian", "Eesti", null), // Not supported by ElevenLabs
    FRENCH("FR", "French", "Français", "fr"),
    GREEK("EL", "Greek", "Ελληνικά", "el"),
    // HEBREW("HE", "Hebrew", "עברית"), deactivated because RTL
    HUNGARIAN("HU", "Hungarian", "Magyar", "hu"),
    INDONESIAN("ID", "Indonesian", "Indonesia", "id"),
    ITALIAN("IT", "Italian", "Italiano", "it"),
    DUTCH("NL", "Dutch", "Nederlands", "nl"),
    JAPANESE("JA", "Japanese", "日本語", "ja"),
    KOREAN("KO", "Korean", "한국어", "ko"),
    LATVIAN("LV", "Latvian", "Latviešu", null), // Not supported by ElevenLabs
    LITHUANIAN("LT", "Lithuanian", "Lietuvių", null), // Not supported by ElevenLabs
    POLISH("PL", "Polish", "Polski", "pl"),
    PORTUGUESE("PT", "Portuguese", "Português", "pt"),
    ROMANIAN("RO", "Romanian", "Română", "ro"),
    RUSSIAN("RU", "Russian", "Русский", "ru"),
    SLOVAK("SK", "Slovak", "Slovenčina", "sk"),
    SLOVENIAN("SL", "Slovenian", "Slovenščina", null), // Not supported by ElevenLabs
    SWEDISH("SV", "Swedish", "Svenska", "sv"),
    DANISH("DA", "Danish", "Dansk", "da"),
    NORWEGIAN("NB", "Norwegian", "Norsk", "no"),
    FINNISH("FI", "Finnish", "Suomi", "fi"),
    TURKISH("TR", "Turkish", "Türkçe", "tr"),
    UKRAINIAN("UK", "Ukrainian", "Українська", "uk");
    
    companion object {
        /**
         * Find Language by ElevenLabs language code.
         * Returns null if no matching language is found.
         */
        fun fromElevenLabsCode(code: String): Language? {
            return entries.find { it.elevenLabsCode == code.lowercase() }
        }
    }
}

enum class DeepLModelType(val value: String, val displayName: String, val description: String) {
    DEFAULT("", "Default", "Standard DeepL model"),
    QUALITY_OPTIMIZED("quality_optimized", "Quality Optimized", "Higher quality translations, may be slower"),
    PREFER_QUALITY_OPTIMIZED("prefer_quality_optimized", "Prefer Quality", "Quality optimized if available, otherwise default"),
    LATENCY_OPTIMIZED("latency_optimized", "Speed Optimized", "Faster translations, standard quality")
}
