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

enum class Language(
    val code: String, 
    val displayName: String, 
    val nativeName: String,
    private val localizedNames: Map<String, String> = emptyMap()
) {
    GERMAN("DE", "German", "Deutsch", mapOf(
        "SV" to "Tyska",
        "ES" to "Alemán", 
        "FR" to "Allemand",
        "EN" to "German"
    )),
    ENGLISH("EN", "English", "English", mapOf(
        "DE" to "Englisch",
        "SV" to "Engelska",
        "ES" to "Inglés",
        "FR" to "Anglais"
    )),
    SPANISH("ES", "Spanish", "Español", mapOf(
        "DE" to "Spanisch",
        "SV" to "Spanska",
        "FR" to "Espagnol", 
        "EN" to "Spanish"
    )),
    FRENCH("FR", "French", "Français", mapOf(
        "DE" to "Französisch",
        "SV" to "Franska", 
        "ES" to "Francés",
        "EN" to "French"
    )),
    ITALIAN("IT", "Italian", "Italiano", mapOf(
        "DE" to "Italienisch",
        "SV" to "Italienska",
        "ES" to "Italiano",
        "EN" to "Italian"
    )),
    DUTCH("NL", "Dutch", "Nederlands", mapOf(
        "DE" to "Niederländisch",
        "SV" to "Nederländska",
        "ES" to "Holandés",
        "EN" to "Dutch"
    )),
    POLISH("PL", "Polish", "Polski", mapOf(
        "DE" to "Polnisch",
        "SV" to "Polska",
        "ES" to "Polaco",
        "EN" to "Polish"
    )),
    PORTUGUESE("PT", "Portuguese", "Português", mapOf(
        "DE" to "Portugiesisch",
        "SV" to "Portugisiska",
        "ES" to "Portugués",
        "EN" to "Portuguese"
    )),
    RUSSIAN("RU", "Russian", "Русский", mapOf(
        "DE" to "Russisch",
        "SV" to "Ryska",
        "ES" to "Ruso",
        "EN" to "Russian"
    )),
    SWEDISH("SV", "Swedish", "Svenska", mapOf(
        "DE" to "Schwedisch",
        "EN" to "Swedish",
        "ES" to "Sueco",
        "FR" to "Suédois"
    )),
    DANISH("DA", "Danish", "Dansk", mapOf(
        "DE" to "Dänisch",
        "SV" to "Danska",
        "ES" to "Danés",
        "EN" to "Danish"
    )),
    NORWEGIAN("NO", "Norwegian", "Norsk", mapOf(
        "DE" to "Norwegisch",
        "SV" to "Norska",
        "ES" to "Noruego",
        "EN" to "Norwegian"
    )),
    FINNISH("FI", "Finnish", "Suomi", mapOf(
        "DE" to "Finnisch",
        "SV" to "Finska",
        "ES" to "Finlandés",
        "EN" to "Finnish"
    ));
    
    fun getNameInLanguage(language: Language): String {
        return localizedNames[language.code] ?: displayName
    }
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
