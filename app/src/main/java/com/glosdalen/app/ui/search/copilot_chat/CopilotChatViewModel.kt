package com.glosdalen.app.ui.search.copilot_chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CopilotChatUiState(
    val query: String = "",
    val sourceLanguage: Language = Language.GERMAN,
    val contextQuery: String = "",
    val isContextExpanded: Boolean = false,
    val response: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CopilotChatViewModel @Inject constructor(
    private val userPreferences: UserPreferences
    // TODO: Add Copilot Chat repository when backend is ready
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CopilotChatUiState())
    val uiState: StateFlow<CopilotChatUiState> = _uiState.asStateFlow()
    
    val nativeLanguage = userPreferences.getNativeLanguage()
    val foreignLanguage = userPreferences.getForeignLanguage()
    
    init {
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
                        response = "",
                        error = null
                    )
                }
            }
        }
    }
    
    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }
    
    fun updateSourceLanguage(language: Language) {
        _uiState.update { 
            it.copy(
                sourceLanguage = language,
                response = "",
                error = null
            ) 
        }
    }
    
    fun updateForeignLanguage(language: Language) {
        viewModelScope.launch {
            userPreferences.setForeignLanguage(language)
        }
        // Clear response when foreign language changes
        _uiState.update { 
            it.copy(
                response = "",
                error = null
            ) 
        }
    }
    
    fun updateContextQuery(context: String) {
        _uiState.update { 
            it.copy(
                contextQuery = context,
                response = "",
                error = null
            ) 
        }
    }
    
    fun toggleContextExpanded() {
        val newExpandedState = !_uiState.value.isContextExpanded
        _uiState.update { 
            it.copy(
                isContextExpanded = newExpandedState,
                contextQuery = if (newExpandedState) it.contextQuery else "",
                response = if (!newExpandedState) "" else it.response,
                error = if (!newExpandedState) null else it.error
            ) 
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
                    response = "",
                    error = null
                )
            }
        }
    }
    
    fun sendQuery() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // TODO: Implement actual Copilot Chat API call
                // For now, just simulate a response
                kotlinx.coroutines.delay(1000)
                
                val contextInfo = if (_uiState.value.contextQuery.isNotBlank()) {
                    "\n\nContext provided: \"${_uiState.value.contextQuery}\""
                } else {
                    ""
                }
                
                val mockResponse = "This is a placeholder response for: \"$query\"\n\n" +
                    "Source language: ${_uiState.value.sourceLanguage.displayName}" +
                    contextInfo + "\n\n" +
                    "The Copilot Chat backend integration is pending implementation."
                
                _uiState.update { 
                    it.copy(
                        response = mockResponse,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }
    
    fun clearResponse() {
        _uiState.update { it.copy(response = "", error = null) }
    }
}
