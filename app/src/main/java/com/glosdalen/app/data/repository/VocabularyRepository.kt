package com.glosdalen.app.data.repository

import com.glosdalen.app.data.datasource.VocabularyDataSource
import com.glosdalen.app.data.model.*
import com.glosdalen.app.domain.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabularyRepository @Inject constructor(
    private val deepLDataSource: VocabularyDataSource,
    private val userPreferences: UserPreferences
) {
    
    suspend fun lookupWord(
        word: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        modelType: DeepLModelType = DeepLModelType.DEFAULT,
        context: String? = null
    ): Result<VocabularyEntry> {
        val apiKey = userPreferences.getDeepLApiKey().first()
        
        if (apiKey.isBlank()) {
            return Result.failure(VocabularyError.InvalidApiKey)
        }
        
        return deepLDataSource.translate(word, sourceLanguage, targetLanguage, apiKey, modelType, context)
    }
    
    suspend fun validateApiKey(apiKey: String): Result<Unit> {
        return try {
            // Test with a simple translation using default model type
            val result = deepLDataSource.translate("test", Language.GERMAN, Language.SWEDISH, apiKey, DeepLModelType.DEFAULT)
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                result.map { }
            }
        } catch (e: Exception) {
            Result.failure(VocabularyError.InvalidApiKey)
        }
    }
}
