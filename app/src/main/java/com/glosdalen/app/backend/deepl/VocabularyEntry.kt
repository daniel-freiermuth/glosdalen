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
    private val localizedNames: Map<String, String> = emptyMap()
) {
    // All DeepL-supported languages (alphabetically by display name)
    // ARABIC("AR", "Arabic", "العربية"), deactivated because RTL
    BULGARIAN("BG", "Bulgarian", "Български"),
    CHINESE_SIMPLIFIED("ZH", "Chinese (Simplified)", "简体中文"),
    CHINESE_TRADITIONAL("ZH-HANT", "Chinese (Traditional)", "繁體中文"),
    CZECH("CS", "Czech", "Čeština"),
    GERMAN("DE", "German", "Deutsch"),
    ENGLISH("EN", "English", "English"),
    SPANISH("ES", "Spanish", "Español"),
    ESTONIAN("ET", "Estonian", "Eesti"),
    FRENCH("FR", "French", "Français"),
    GREEK("EL", "Greek", "Ελληνικά"),
    // HEBREW("HE", "Hebrew", "עברית"), deactivated because RTL
    HUNGARIAN("HU", "Hungarian", "Magyar"),
    INDONESIAN("ID", "Indonesian", "Indonesia"),
    ITALIAN("IT", "Italian", "Italiano"),
    DUTCH("NL", "Dutch", "Nederlands"),
    JAPANESE("JA", "Japanese", "日本語"),
    KOREAN("KO", "Korean", "한국어"),
    LATVIAN("LV", "Latvian", "Latviešu"),
    LITHUANIAN("LT", "Lithuanian", "Lietuvių"),
    POLISH("PL", "Polish", "Polski"),
    PORTUGUESE("PT", "Portuguese", "Português"),
    ROMANIAN("RO", "Romanian", "Română"),
    RUSSIAN("RU", "Russian", "Русский"),
    SLOVAK("SK", "Slovak", "Slovenčina"),
    SLOVENIAN("SL", "Slovenian", "Slovenščina"),
    SWEDISH("SV", "Swedish", "Svenska"),
    DANISH("DA", "Danish", "Dansk"),
    NORWEGIAN("NB", "Norwegian", "Norsk"),
    FINNISH("FI", "Finnish", "Suomi"),
    TURKISH("TR", "Turkish", "Türkçe"),
    UKRAINIAN("UK", "Ukrainian", "Українська");
    
}

enum class DeepLModelType(val value: String, val displayName: String, val description: String) {
    DEFAULT("", "Default", "Standard DeepL model"),
    QUALITY_OPTIMIZED("quality_optimized", "Quality Optimized", "Higher quality translations, may be slower"),
    PREFER_QUALITY_OPTIMIZED("prefer_quality_optimized", "Prefer Quality", "Quality optimized if available, otherwise default"),
    LATENCY_OPTIMIZED("latency_optimized", "Speed Optimized", "Faster translations, standard quality")
}
