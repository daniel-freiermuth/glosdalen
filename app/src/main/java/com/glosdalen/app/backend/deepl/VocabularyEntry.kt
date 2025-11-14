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
    GERMAN("DE", "German", "Deutsch"),
    ENGLISH("EN", "English", "English"),
    SPANISH("ES", "Spanish", "Español"),
    FRENCH("FR", "French", "Français"),
    ITALIAN("IT", "Italian", "Italiano"),
    DUTCH("NL", "Dutch", "Nederlands"),
    POLISH("PL", "Polish", "Polski"),
    PORTUGUESE("PT", "Portuguese", "Português"),
    RUSSIAN("RU", "Russian", "Русский"),
    SWEDISH("SV", "Swedish", "Svenska"),
    DANISH("DA", "Danish", "Dansk"),
    NORWEGIAN("NO", "Norwegian", "Norsk"),
    FINNISH("FI", "Finnish", "Suomi");
    
}

enum class DeepLModelType(val value: String, val displayName: String, val description: String) {
    DEFAULT("", "Default", "Standard DeepL model"),
    QUALITY_OPTIMIZED("quality_optimized", "Quality Optimized", "Higher quality translations, may be slower"),
    PREFER_QUALITY_OPTIMIZED("prefer_quality_optimized", "Prefer Quality", "Quality optimized if available, otherwise default"),
    LATENCY_OPTIMIZED("latency_optimized", "Speed Optimized", "Faster translations, standard quality")
}
