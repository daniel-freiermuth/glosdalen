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
    SWEDISH("SV", "Swedish")
}

enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, INTERJECTION
}

enum class DataSource {
    DEEPL, SVENSKA_ORDBOKEN, LLM, DICT_CC, UNKNOWN
}
