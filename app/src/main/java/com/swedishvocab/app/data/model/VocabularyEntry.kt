package com.swedishvocab.app.data.model

data class VocabularyEntry(
    val originalWord: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val translations: List<Translation>,
    val metadata: VocabularyMetadata = VocabularyMetadata()
)

data class Translation(
    val text: String,
    val confidence: Float? = null,
    val context: String? = null
)

data class VocabularyMetadata(
    val pronunciation: String? = null,
    val partOfSpeech: PartOfSpeech? = null,
    val examples: List<String> = emptyList(),
    val etymology: String? = null,
    val source: DataSource = DataSource.UNKNOWN
)

enum class Language(val code: String, val displayName: String) {
    GERMAN("DE", "German"),
    ENGLISH("EN", "English"),
    SPANISH("ES", "Spanish"),
    FRENCH("FR", "French"),
    ITALIAN("IT", "Italian"),
    DUTCH("NL", "Dutch"),
    POLISH("PL", "Polish"),
    PORTUGUESE("PT", "Portuguese"),
    RUSSIAN("RU", "Russian"),
    SWEDISH("SV", "Swedish"),
    DANISH("DA", "Danish"),
    NORWEGIAN("NO", "Norwegian"),
    FINNISH("FI", "Finnish")
}

enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, INTERJECTION
}

enum class DataSource {
    DEEPL, SVENSKA_ORDBOKEN, LLM, DICT_CC, UNKNOWN
}

enum class DeepLModelType(val value: String, val displayName: String, val description: String) {
    DEFAULT("", "Default", "Standard DeepL model"),
    QUALITY_OPTIMIZED("quality_optimized", "Quality Optimized", "Higher quality translations, may be slower"),
    PREFER_QUALITY_OPTIMIZED("prefer_quality_optimized", "Prefer Quality", "Quality optimized if available, otherwise default"),
    LATENCY_OPTIMIZED("latency_optimized", "Speed Optimized", "Faster translations, standard quality")
}
