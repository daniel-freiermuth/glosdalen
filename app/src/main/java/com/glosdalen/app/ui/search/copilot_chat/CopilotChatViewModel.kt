package com.glosdalen.app.ui.search.copilot_chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.libs.copilot.CopilotChat
import com.glosdalen.app.libs.copilot.CopilotException
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
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class CopilotChatViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val copilot: CopilotChat
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CopilotChatUiState())
    val uiState: StateFlow<CopilotChatUiState> = _uiState.asStateFlow()
    
    val nativeLanguage = userPreferences.getNativeLanguage()
    val foreignLanguage = userPreferences.getForeignLanguage()
    
    init {
        // Check authentication status
        viewModelScope.launch {
            val isAuth = copilot.isAuthenticated()
            _uiState.update { it.copy(isAuthenticated = isAuth) }
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
                // Check authentication first
                if (!copilot.isAuthenticated()) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Please sign in to GitHub Copilot in Settings"
                        )
                    }
                    return@launch
                }
                
                // Get target language for the prompt
                val native = nativeLanguage.first()
                val foreign = foreignLanguage.first()
                val targetLanguage = when (_uiState.value.sourceLanguage) {
                    native -> foreign
                    foreign -> native
                    else -> foreign
                }
                
                // Build the prompt for translation/vocabulary assistance
                val prompt = buildPrompt(
                    query = query,
                    sourceLanguage = _uiState.value.sourceLanguage,
                    targetLanguage = targetLanguage,
                    context = _uiState.value.contextQuery.takeIf { it.isNotBlank() }
                )
                
                // Send to Copilot
                val result = copilot.chat(prompt)
                
                result.fold(
                    onSuccess = { response ->
                        _uiState.update { 
                            it.copy(
                                response = response,
                                isLoading = false
                            )
                        }
                    },
                    onFailure = { error ->
                        val errorMessage = when (error) {
                            is CopilotException.AuthException.InvalidToken ->
                                "Please sign in to GitHub Copilot in Settings"
                            is CopilotException.AuthException.TokenExpired ->
                                "Session expired. Please sign in again in Settings"
                            is CopilotException.NetworkException.NoConnection ->
                                "No internet connection. Please check your network."
                            is CopilotException.NetworkException.Timeout ->
                                "Request timed out. Please try again."
                            is CopilotException.NetworkException.RateLimited ->
                                "Rate limited. Please try again later."
                            else -> error.message ?: "Failed to get response from Copilot"
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = errorMessage
                            )
                        }
                    }
                )
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
    
    private fun buildPrompt(
        query: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        context: String? = null
    ): String {
        return buildString {
            appendLine("You are a language learning assistant helping with ${sourceLanguage.displayName} to ${targetLanguage.displayName} vocabulary.")
            appendLine()
            appendLine("User query: $query")
            appendLine("Source language: ${sourceLanguage.displayName}")
            appendLine("Target language: ${targetLanguage.displayName}")
            
            if (context != null) {
                appendLine()
                appendLine("Additional context: $context")
            }
            
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Translation(s) of the word or phrase")
            appendLine("2. Example sentences in both languages")
            appendLine("3. Any relevant grammar notes or usage tips")
            appendLine("4. Common collocations or related vocabulary")
            appendLine()
            appendLine("Keep the response concise and practical for language learning.")
        }
    }
    
    fun clearResponse() {
        _uiState.update { it.copy(response = "", error = null) }
    }
}
