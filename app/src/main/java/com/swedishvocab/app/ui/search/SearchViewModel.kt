package com.swedishvocab.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swedishvocab.app.anki.AnkiIntegration
import com.swedishvocab.app.data.model.*
import com.swedishvocab.app.data.repository.VocabularyRepository
import com.swedishvocab.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val ankiIntegration: AnkiIntegration,
    private val userPreferences: UserPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val defaultDeckName = userPreferences.getDefaultDeckName()
    val defaultCardType = userPreferences.getDefaultCardType()
    val isFirstLaunch = userPreferences.isFirstLaunch()
    
    init {
        _uiState.value = _uiState.value.copy(
            isAnkiDroidAvailable = ankiIntegration.isAnkiDroidInstalled()
        )
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
    
    fun updateSourceLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(sourceLanguage = language)
    }
    
    fun searchWord() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                translationResult = null
            )
            
            val result = vocabularyRepository.lookupWord(
                word = query,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = when (_uiState.value.sourceLanguage) {
                    Language.GERMAN -> Language.SWEDISH
                    Language.SWEDISH -> Language.GERMAN
                }
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
    
    fun createAnkiCard(cardType: CardType) {
        val result = _uiState.value.translationResult ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingCard = true)
            
            val deckName = defaultDeckName.first()
            val translation = result.translations.firstOrNull()?.text ?: ""
            
            val card = when (result.sourceLanguage) {
                Language.GERMAN -> GermanSwedishCard(
                    germanWord = result.originalWord,
                    swedishTranslation = translation,
                    deckName = deckName,
                    cardType = cardType
                )
                Language.SWEDISH -> GermanSwedishCard(
                    germanWord = translation,
                    swedishTranslation = result.originalWord,
                    deckName = deckName,
                    cardType = cardType
                )
            }
            
            val ankiResult = ankiIntegration.createMultipleCards(card.toAnkiCards())
            
            _uiState.value = _uiState.value.copy(
                isCreatingCard = false,
                cardCreationResult = ankiResult
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearCardCreationResult() {
        _uiState.value = _uiState.value.copy(cardCreationResult = null)
    }
    
    fun retrySearch() {
        searchWord()
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
    val cardCreationResult: Result<Unit>? = null
)
