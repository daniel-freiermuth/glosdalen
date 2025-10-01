package com.swedishvocab.app.data.datasource

import com.swedishvocab.app.data.model.*

interface VocabularyDataSource {
    suspend fun translate(
        word: String, 
        sourceLanguage: Language, 
        targetLanguage: Language,
        apiKey: String,
        modelType: DeepLModelType = DeepLModelType.DEFAULT,
        context: String? = null
    ): Result<VocabularyEntry>
    
    val supportedLanguagePairs: Set<Pair<Language, Language>>
    val dataSource: DataSource
}
