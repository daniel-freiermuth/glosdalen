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
    
    val deepLApiKey = userPreferences.getDeepLApiKey()
    
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
    
    fun createAnkiCard() {
        val result = _uiState.value.translationResult ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingCard = true)
            
            val translation = result.translations.firstOrNull()?.text ?: ""
            
            // Create simple card for AnkiDroid sharing
            val ankiCard = AnkiCard(
                deckName = "German::Swedish", // Default deck name
                fields = mapOf(
                    "Front" to result.originalWord,
                    "Back" to translation
                )
            )
            
            try {
                val ankiResult = ankiIntegration.createAnkiCards(ankiCard)
                
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = ankiResult
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = Result.failure(AnkiError.IntentFailed("Failed to create card: ${e.message}"))
                )
            }
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
