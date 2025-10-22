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
                    nativeLanguage = native,
                    foreignLanguage = foreign,
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
        nativeLanguage: Language,
        foreignLanguage: Language,
        context: String? = null
    ): String {
        return buildString {
            appendLine("You are a helpful language learning assistant specializing in translation and learning.")
            appendLine()
            appendLine("The users native language is ${nativeLanguage.displayName} and they are learning ${foreignLanguage.displayName}.")
            
            // User-provided general instructions: ${context.generalInstructions}
            if (context != null) {
                appendLine()
                appendLine("Specific context for this query: $context")
            }
            
            appendLine()
            appendLine("User query (${sourceLanguage.displayName}): \" $query \"")
            
            appendLine()
            appendLine("Please provide:")
            appendLine("- Direct translation if applicable")
            appendLine("- Grammar explanations when relevant")
            appendLine("- Usage examples")
            appendLine("- Cultural context when helpful")
            appendLine("- Alternative expressions")
            appendLine("- Common collocations or related vocabulary")
            appendLine()
            appendLine("Keep the response concise and practical for language learning.")
            appendLine()
            appendLine("Please answer in four sections (H1, separated by `--`).")
            appendLine("It is important that you follow the structure exactly.")
            appendLine("- A section \"Answer\",")
            appendLine("- a section \"Front side\",")
            appendLine("- a section \"Back side\",")
            appendLine("- a section \"Explanation / Remarks / Extra\" with a concise explanation or interesting remarks if useful.")
            appendLine()
            appendLine()
            appendLine()
            appendLine("Feel very free to keep the last section empty.")
            appendLine("")
            appendLine("Keep your answer concise.")
            appendLine("")
            appendLine("Remember that you're part of an app for quick lookups.")
            appendLine("If applicable, the foreign word shall be on the front side.")
            appendLine("")
            appendLine("It is not in the spirit of flash cards to have the foreign word on the same side as a native.")
            appendLine("")
            appendLine("Prefer idiomatic expressions over word-by-word translations.")
        }
    }
    
    fun clearResponse() {
        _uiState.update { it.copy(response = "", error = null) }
    }
}
