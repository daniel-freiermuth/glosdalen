package com.swedishvocab.app.data.datasource

import com.swedishvocab.app.data.model.*
import com.swedishvocab.app.data.network.*
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLDataSource @Inject constructor(
    private val apiService: DeepLApiService
) : VocabularyDataSource {
    
    override val supportedLanguagePairs = setOf(
        Language.GERMAN to Language.SWEDISH,
        Language.SWEDISH to Language.GERMAN
    )
    
    override val dataSource = DataSource.DEEPL
    
    override suspend fun translate(
        word: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        apiKey: String,
        modelType: DeepLModelType
    ): Result<VocabularyEntry> {
        if (!supportedLanguagePairs.contains(sourceLanguage to targetLanguage)) {
            return Result.failure(VocabularyError.UnsupportedLanguagePair)
        }
        
        return try {
            val request = DeepLTranslateRequest(
                text = listOf(word),
                source_lang = sourceLanguage.code,
                target_lang = targetLanguage.code,
                model_type = if (modelType.value.isNotEmpty()) modelType.value else null
            )
            
            val response = apiService.translate("DeepL-Auth-Key $apiKey", request)
            
            val vocabularyEntry = VocabularyEntry(
                originalWord = word,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                translations = response.translations.map { 
                    Translation(text = it.text)
                },
                metadata = VocabularyMetadata(source = DataSource.DEEPL)
            )
            
            Result.success(vocabularyEntry)
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> Result.failure(VocabularyError.InvalidApiKey)
                429 -> {
                    val retryAfter = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                    Result.failure(VocabularyError.ApiLimitExceeded(retryAfter))
                }
                else -> Result.failure(VocabularyError.ApiError)
            }
        } catch (e: IOException) {
            Result.failure(VocabularyError.NetworkError)
        } catch (e: Exception) {
            Result.failure(VocabularyError.UnknownError(e.message))
        }
    }
}
