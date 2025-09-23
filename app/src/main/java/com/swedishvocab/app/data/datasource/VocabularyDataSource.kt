package com.swedishvocab.app.data.datasource

import com.swedishvocab.app.data.model.*

interface VocabularyDataSource {
    suspend fun translate(
        word: String, 
        sourceLanguage: Language, 
        targetLanguage: Language,
        apiKey: String
    ): Result<VocabularyEntry>
    
    val supportedLanguagePairs: Set<Pair<Language, Language>>
    val dataSource: DataSource
}
