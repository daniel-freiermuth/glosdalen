package com.glosdalen.app.backend.deepl

data class SearchContext(
    val nativeLanguage: Language,
    val foreignLanguage: Language,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val context: String?
)
