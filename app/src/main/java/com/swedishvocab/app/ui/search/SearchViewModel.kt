package com.swedishvocab.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swedishvocab.app.data.model.*
import com.swedishvocab.app.data.repository.VocabularyRepository
import com.swedishvocab.app.data.repository.AnkiRepository
import com.swedishvocab.app.domain.preferences.UserPreferences
import com.swedishvocab.app.domain.template.DeckNameTemplateResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val ankiRepository: AnkiRepository,
    private val userPreferences: UserPreferences,
    private val templateResolver: DeckNameTemplateResolver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val deepLApiKey = userPreferences.getDeepLApiKey()
    val nativeLanguage = userPreferences.getNativeLanguage()
    val foreignLanguage = userPreferences.getForeignLanguage()
    val defaultDeckName = userPreferences.getDefaultDeckName()
    val defaultCardDirection = userPreferences.getDefaultCardDirection()
    
    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnkiDroidAvailable = ankiRepository.isAnkiDroidAvailable(),
                ankiImplementationType = ankiRepository.getImplementationType().name
            )
        }
        
        // React to language preference changes and update source language accordingly
        viewModelScope.launch {
            combine(nativeLanguage, foreignLanguage) { native, foreign ->
                Pair(native, foreign)
            }.collect { (native, foreign) ->
                val currentState = _uiState.value
                
                // If current source language is not one of the configured languages,
                // reset to native language
                if (currentState.sourceLanguage != native && currentState.sourceLanguage != foreign) {
                    _uiState.value = currentState.copy(
                        sourceLanguage = native,
                        translationResult = null,
                        error = null,
                        cardCreationResult = null,
                        cardsCreatedCount = 0
                    )
                }
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            translationResult = null,
            error = null,
            cardCreationResult = null,
            cardsCreatedCount = 0,
            selectedTranslation = null
        )
    }
    
    fun updateSourceLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(
            sourceLanguage = language,
            translationResult = null,
            error = null,
            cardCreationResult = null,
            cardsCreatedCount = 0,
            selectedTranslation = null
        )
    }
    
    fun updateForeignLanguage(language: Language) {
        viewModelScope.launch {
            userPreferences.setForeignLanguage(language)
        }
        // Clear translation results when foreign language changes
        _uiState.value = _uiState.value.copy(
            translationResult = null,
            error = null,
            cardCreationResult = null,
            cardsCreatedCount = 0,
            selectedTranslation = null,
            hasCardBeenCreated = false
        )
    }
    
    fun cycleForeignLanguage() {
        viewModelScope.launch {
            val currentForeign = foreignLanguage.first()
            val availableLanguages = Language.values().filter { it != nativeLanguage.first() }
            val currentIndex = availableLanguages.indexOf(currentForeign)
            val nextIndex = (currentIndex + 1) % availableLanguages.size
            val nextLanguage = availableLanguages[nextIndex]
            
            userPreferences.setForeignLanguage(nextLanguage)
        }
    }
    
    fun refreshLanguageState() {
        viewModelScope.launch {
            val native = nativeLanguage.first()
            val foreign = foreignLanguage.first()
            val currentState = _uiState.value
            
            // If current source language is not one of the configured languages,
            // reset to native language
            if (currentState.sourceLanguage != native && currentState.sourceLanguage != foreign) {
                _uiState.value = currentState.copy(
                    sourceLanguage = native,
                    translationResult = null,
                    error = null,
                    cardCreationResult = null,
                    cardsCreatedCount = 0
                )
            }
        }
    }
    
    fun updateContextQuery(context: String) {
        _uiState.value = _uiState.value.copy(
            contextQuery = context,
            translationResult = null,
            error = null,
            cardCreationResult = null,
            cardsCreatedCount = 0,
            selectedTranslation = null,
            hasCardBeenCreated = false
        )
    }
    
    fun toggleContextExpanded() {
        val newExpandedState = !_uiState.value.isContextExpanded
        _uiState.value = _uiState.value.copy(
            isContextExpanded = newExpandedState,
            // Clear context when collapsing
            contextQuery = if (newExpandedState) _uiState.value.contextQuery else "",
            // Clear translation results when hiding context
            translationResult = if (newExpandedState) _uiState.value.translationResult else null,
            error = if (newExpandedState) _uiState.value.error else null,
            cardCreationResult = if (newExpandedState) _uiState.value.cardCreationResult else null,
            cardsCreatedCount = if (newExpandedState) _uiState.value.cardsCreatedCount else 0,
            selectedTranslation = if (newExpandedState) _uiState.value.selectedTranslation else null,
            hasCardBeenCreated = if (newExpandedState) _uiState.value.hasCardBeenCreated else false
        )
    }
    
    fun searchWord() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                translationResult = null,
                selectedTranslation = null,
                hasCardBeenCreated = false
            )
            
            val currentNative = nativeLanguage.first()
            val currentForeign = foreignLanguage.first()
            val modelType = userPreferences.getDeepLModelType().first()
            
            val result = vocabularyRepository.lookupWord(
                word = query,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = when (_uiState.value.sourceLanguage) {
                    currentNative -> currentForeign
                    currentForeign -> currentNative
                    else -> currentForeign // Default fallback
                },
                modelType = modelType,
                context = _uiState.value.contextQuery.takeIf { it.isNotBlank() }
            )
            
            result.fold(
                onSuccess = { vocabularyEntry ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        translationResult = vocabularyEntry
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error as? VocabularyError
                    )
                }
            )
        }
    }
    
    fun createAnkiCard() {
        val result = _uiState.value.translationResult ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingCard = true)
            
            val translation = _uiState.value.selectedTranslation ?: result.translations.firstOrNull()?.text ?: ""
            val currentNative = nativeLanguage.first()
            val currentForeign = foreignLanguage.first()
            val deckTemplate = defaultDeckName.first()
            val cardDirection = defaultCardDirection.first()
            
            // Resolve deck name template
            val searchContext = SearchContext(
                nativeLanguage = currentNative,
                foreignLanguage = currentForeign,
                sourceLanguage = result.sourceLanguage,
                targetLanguage = when (result.sourceLanguage) {
                    currentNative -> currentForeign
                    currentForeign -> currentNative
                    else -> currentForeign
                },
                context = _uiState.value.contextQuery.takeIf { it.isNotBlank() }
            )
            
            val deckName = templateResolver.resolveDeckName(deckTemplate, searchContext)
            
            // Determine which word is native and which is foreign
            val (nativeWord, foreignWord) = if (result.sourceLanguage == currentNative) {
                // Original search: Native → Foreign
                result.originalWord to translation
            } else {
                // Reverse search: Foreign → Native  
                translation to result.originalWord
            }
            
            // Create cards based on user's direction preference
            val cardsToCreate = when (cardDirection) {
                CardDirection.NATIVE_TO_FOREIGN -> {
                    listOf(
                        AnkiCard(
                            fields = mapOf("Front" to nativeWord, "Back" to foreignWord),
                            deckName = deckName,
                            tags = listOf("glosdalen", "vocab", currentNative.code, currentForeign.code, "native-to-foreign")
                        )
                    )
                }
                CardDirection.FOREIGN_TO_NATIVE -> {
                    listOf(
                        AnkiCard(
                            fields = mapOf("Front" to foreignWord, "Back" to nativeWord),
                            deckName = deckName,
                            tags = listOf("glosdalen", "vocab", currentNative.code, currentForeign.code, "foreign-to-native")
                        )
                    )
                }
                CardDirection.BOTH_DIRECTIONS -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic (and reversed card)",
                            fields = mapOf("Front" to nativeWord, "Back" to foreignWord),
                            deckName = deckName,
                            tags = listOf("glosdalen", "vocab", currentNative.code, currentForeign.code, "bidirectional")
                        )
                    )
                }
            }
            
            try {
                val ankiResult = if (cardsToCreate.size == 1) {
                    ankiRepository.createCard(cardsToCreate.first())
                } else {
                    ankiRepository.createCards(cardsToCreate)
                }
                
                // Update implementation type in case permissions were granted during card creation
                val currentImplementationType = ankiRepository.getImplementationType().name
                
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = ankiResult,
                    cardsCreatedCount = cardsToCreate.size,
                    ankiImplementationType = currentImplementationType,
                    lastCardDirection = cardDirection,
                    hasCardBeenCreated = ankiResult.isSuccess
                )
            } catch (e: Exception) {
                // Update implementation type in case permissions state changed
                val currentImplementationType = ankiRepository.getImplementationType().name
                
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = Result.failure(AnkiError.IntentFailed("Failed to create card: ${e.message}")),
                    cardsCreatedCount = 0,
                    ankiImplementationType = currentImplementationType,
                    lastCardDirection = cardDirection
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearCardCreationResult() {
        _uiState.value = _uiState.value.copy(cardCreationResult = null, cardsCreatedCount = 0)
    }
    
    fun selectTranslation(translation: String) {
        _uiState.value = _uiState.value.copy(selectedTranslation = translation)
    }
    
    fun retrySearch() {
        searchWord()
    }
    
    fun refreshAnkiStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnkiDroidAvailable = ankiRepository.isAnkiDroidAvailable(),
                ankiImplementationType = ankiRepository.getImplementationType().name
            )
        }
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val sourceLanguage: Language = Language.GERMAN,
    val isLoading: Boolean = false,
    val translationResult: VocabularyEntry? = null,
    val error: VocabularyError? = null,
    val isAnkiDroidAvailable: Boolean = false,
    val isCreatingCard: Boolean = false,
    val cardCreationResult: Result<Unit>? = null,
    val cardsCreatedCount: Int = 0,
    val selectedTranslation: String? = null,
    val ankiImplementationType: String = "UNKNOWN",
    val lastCardDirection: CardDirection? = null,
    val hasCardBeenCreated: Boolean = false,
    val contextQuery: String = "",
    val isContextExpanded: Boolean = false
)
