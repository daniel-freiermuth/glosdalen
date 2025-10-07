package com.glosdalen.app.data.datasource

import com.glosdalen.app.data.model.*
import com.glosdalen.app.data.network.*
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLDataSource @Inject constructor(
    private val apiService: DeepLApiService,
    private val userPreferences: com.glosdalen.app.domain.preferences.UserPreferences
) : VocabularyDataSource {
    
    override val supportedLanguagePairs = run {
        val languages = Language.values().toList()
        val pairs = mutableSetOf<Pair<Language, Language>>()
        
        // Add all bidirectional language pairs
        for (source in languages) {
            for (target in languages) {
                if (source != target) {
                    pairs.add(source to target)
                }
            }
        }
        pairs.toSet()
    }
    
    override val dataSource = DataSource.DEEPL
    
    override suspend fun translate(
        word: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        apiKey: String,
        modelType: DeepLModelType,
        context: String?
    ): Result<VocabularyEntry> {
        if (!supportedLanguagePairs.contains(sourceLanguage to targetLanguage)) {
            return Result.failure(VocabularyError.UnsupportedLanguagePair)
        }
        
        return try {
            // Check user preference for multiple formalities
            val enableMultipleFormalities = userPreferences.getEnableMultipleFormalities().first()
            
            // Languages that support formality parameter
            val formalitySupported = setOf(
                Language.GERMAN, Language.FRENCH, Language.ITALIAN, Language.SPANISH, 
                Language.PORTUGUESE, Language.RUSSIAN, Language.POLISH, Language.DUTCH
            )
            
            val allTranslations = mutableListOf<Translation>()
            val uniqueTranslations = mutableSetOf<String>()
            
            // Strategy: Try different formalities if supported AND enabled by user
            val formalities = if (enableMultipleFormalities && formalitySupported.contains(targetLanguage)) {
                listOf(
                    null,          // Default/neutral
                    "more",        // More formal
                    "less"         // Less formal  
                )
            } else {
                listOf(null)   // Only default if disabled or language doesn't support formality
            }
            
            // Make requests with different formalities to get varied translations
            for (formality in formalities) {
                try {
                    val request = DeepLTranslateRequest(
                        text = listOf(word),
                        source_lang = sourceLanguage.code,
                        target_lang = targetLanguage.code,
                        model_type = if (modelType.value.isNotEmpty()) modelType.value else null,
                        formality = formality,
                        split_sentences = "0", // Don't split sentences - treat as one context
                        context = context?.takeIf { it.isNotBlank() }
                    )
                    
                    val response = apiService.translate("DeepL-Auth-Key $apiKey", request)
                    
                    // Add unique translations only
                    response.translations.forEach { translation ->
                        if (uniqueTranslations.add(translation.text.lowercase())) {
                            allTranslations.add(Translation(text = translation.text))
                        }
                    }
                } catch (e: HttpException) {
                    // If formality not supported, continue with other options
                    if (e.code() == 400) continue
                    else throw e
                } catch (e: Exception) {
                    // Continue with other formalities if one fails
                    continue
                }
            }
            
            // Fallback: if no translations found, make one simple request
            if (allTranslations.isEmpty()) {
                val request = DeepLTranslateRequest(
                    text = listOf(word),
                    source_lang = sourceLanguage.code,
                    target_lang = targetLanguage.code,
                    model_type = if (modelType.value.isNotEmpty()) modelType.value else null,
                    split_sentences = "0", // Don't split sentences - treat as one context
                    context = context?.takeIf { it.isNotBlank() }
                )
                
                val response = apiService.translate("DeepL-Auth-Key $apiKey", request)
                allTranslations.addAll(response.translations.map { Translation(text = it.text) })
            }
            
            val vocabularyEntry = VocabularyEntry(
                originalWord = word,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                translations = allTranslations,
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
