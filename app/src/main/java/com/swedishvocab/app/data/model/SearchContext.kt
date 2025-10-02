package com.swedishvocab.app.data.model

data class SearchContext(
    val nativeLanguage: Language,
    val foreignLanguage: Language,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val context: String?
)
