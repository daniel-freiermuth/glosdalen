package com.glosdalen.app.ui.search.copilot_chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiCard
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.backend.deepl.SearchContext
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import com.glosdalen.app.libs.copilot.CopilotChat
import com.glosdalen.app.libs.copilot.CopilotException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * JSON response structure from the LLM
 */
@Serializable
data class CopilotJsonResponse(
    val answer: String,
    val flashcards: List<FlashCardJson> = emptyList(),
    val explanation: String = ""
)

@Serializable
data class FlashCardJson(
    val front: String,
    val back: String,
    val note: String = ""
)

/**
 * Internal representation used by the UI
 */
data class FlashCard(
    val frontSide: String,
    val backSide: String,
    val note: String = ""
)

data class ParsedCopilotResponse(
    val directAnswer: String,
    val cards: List<FlashCard>,
    val additionalInfo: String
)

/**
 * Card direction for Copilot Chat mode
 * Generic front/back directions since cards are already defined by the LLM
 */
enum class CopilotCardDirection {
    FRONT_TO_BACK,      // Use LLM's front → back as-is (via API)
    BACK_TO_FRONT,      // Reverse: LLM's back → front (via API)
    BOTH_DIRECTIONS,    // Create cards in both directions (via API)
    VIA_INTENT          // Create via AnkiDroid Intent (user chooses direction)
}

data class CopilotChatUiState(
    val query: String = "",
    val sourceLanguage: Language = Language.GERMAN,
    val contextQuery: String = "",
    val isContextExpanded: Boolean = false,
    val response: String = "",
    val parsedResponse: ParsedCopilotResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val isAdditionalInfoExpanded: Boolean = false,
    val isCreatingCard: Boolean = false,
    val createdCardIndices: Set<Int> = emptySet(), // Track which cards have been created
    val isAnkiDroidAvailable: Boolean = false,
    val selectedCardDirection: CopilotCardDirection = CopilotCardDirection.FRONT_TO_BACK,
    val availableModels: List<com.glosdalen.app.libs.copilot.models.CopilotModel> = emptyList(),
    val selectedModelId: String = com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL,
    val isLoadingModels: Boolean = false,
    val temperature: Float = com.glosdalen.app.domain.preferences.CopilotPreferences.DEFAULT_TEMPERATURE,
    val showIntroDialog: Boolean = false
)

@HiltViewModel
class CopilotChatViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val copilot: CopilotChat,
    private val ankiRepository: AnkiRepository,
    private val templateResolver: DeckNameTemplateResolver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CopilotChatUiState())
    val uiState: StateFlow<CopilotChatUiState> = _uiState.asStateFlow()
    
    val nativeLanguage = userPreferences.getNativeLanguage()
    val foreignLanguage = userPreferences.getForeignLanguage()
    
    // Track the current query job for cancellation
    private var queryJob: kotlinx.coroutines.Job? = null
    
    init {
        // Check authentication status (models loaded via recheckAuthenticationStatus on screen resume)
        viewModelScope.launch {
            val isAuth = copilot.isAuthenticated()
            val ankiAvailable = ankiRepository.isAnkiDroidAvailable()
            val selectedModel = userPreferences.getCopilotSelectedModel().first()
            val temperature = userPreferences.getCopilotTemperature().first()
            val shouldShowIntro = userPreferences.shouldShowCopilotIntroDialog().first()
            _uiState.update { it.copy(
                isAuthenticated = isAuth,
                isAnkiDroidAvailable = ankiAvailable,
                selectedModelId = selectedModel,
                temperature = temperature,
                showIntroDialog = shouldShowIntro
            ) }
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
        _uiState.update { 
            it.copy(
                query = query,
                response = "",
                parsedResponse = null,
                error = null,
                createdCardIndices = emptySet()
            ) 
        }
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
    
    fun recheckAuthenticationStatus() {
        viewModelScope.launch {
            val isAuth = copilot.isAuthenticated()
            _uiState.update { it.copy(isAuthenticated = isAuth) }
            
            // Load models if authenticated
            if (isAuth && _uiState.value.availableModels.isEmpty()) {
                loadModels()
            }
        }
    }
    
    fun dismissIntroDialog(showAgain: Boolean) {
        viewModelScope.launch {
            userPreferences.setShowCopilotIntroDialog(showAgain)
            _uiState.update { it.copy(showIntroDialog = false) }
        }
    }
    
    fun sendQuery() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        
        // Cancel any existing query
        queryJob?.cancel()
        
        queryJob = viewModelScope.launch {
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
                
                // Get general instructions
                val generalInstructions = userPreferences.getCopilotGeneralInstructions().first()
                
                // Get language-specific instructions for the foreign language
                val languageInstructions = userPreferences.getLanguageInstructions(foreign).first()
                
                // Build the prompt for translation/vocabulary assistance
                val prompt = buildPrompt(
                    query = query,
                    sourceLanguage = _uiState.value.sourceLanguage,
                    targetLanguage = targetLanguage,
                    nativeLanguage = native,
                    foreignLanguage = foreign,
                    context = _uiState.value.contextQuery.takeIf { it.isNotBlank() },
                    generalInstructions = generalInstructions,
                    languageInstructions = languageInstructions
                )
                
                // Get selected model (null means auto)
                val selectedModel = userPreferences.getCopilotSelectedModel().first()
                val modelId = if (selectedModel == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                    null // Let the library choose
                } else {
                    selectedModel
                }
                
                // Get temperature setting
                val temperature = userPreferences.getCopilotTemperature().first()
                
                // Send to Copilot
                val result = copilot.chat(prompt, modelId, temperature)
                
                result.fold(
                    onSuccess = { response ->
                        val parsed = parseResponse(response)
                        _uiState.update { 
                            it.copy(
                                response = response,
                                parsedResponse = parsed,
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
    
    fun cancelQuery() {
        queryJob?.cancel()
        queryJob = null
        _uiState.update { 
            it.copy(
                isLoading = false,
                error = null
            )
        }
    }
    
    private fun buildPrompt(
        query: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        nativeLanguage: Language,
        foreignLanguage: Language,
        context: String? = null,
        generalInstructions: String,
        languageInstructions: String = ""
    ): String {
        return buildString {
            appendLine("You are a helpful language learning assistant specializing in translation and learning.")
            appendLine("You must respond with valid JSON only, following the exact schema provided below.")
            appendLine()
            appendLine("The user's native language is ${nativeLanguage.displayName} and they are learning ${foreignLanguage.displayName}.")
            
            if (generalInstructions.isNotBlank()) {
                appendLine()
                appendLine("General instructions from the user: $generalInstructions")
            }
            
            if (languageInstructions.isNotBlank()) {
                appendLine()
                appendLine("Language-specific instructions for ${foreignLanguage.displayName}: $languageInstructions")
            }
            
            if (context != null) {
                appendLine()
                appendLine("Specific context for this query: $context")
            }
            
            appendLine()
            appendLine("User query (${sourceLanguage.displayName}): \"$query\"")
            appendLine("Target language for translation: ${targetLanguage.displayName}")
            
            appendLine()
            appendLine("Response format - You MUST respond with valid JSON matching this exact schema:")
            appendLine("""
                {
                  "answer": "Your direct answer to the user's query",
                  "flashcards": [
                    {
                      "front": "Front side of the flashcard",
                      "back": "Back side of the flashcard",
                      "note": "Optional note about this card (can be empty string)"
                    }
                  ],
                  "explanation": "Optional additional explanation or interesting remarks (can be empty string)"
                }
            """.trimIndent())
            appendLine()
            appendLine("Important:")
            appendLine("- The 'flashcards' array can contain 0 or more cards (as many as you think would be helpful)")
            appendLine("- Each flashcard must have 'front' and 'back' fields")
            appendLine("- The 'note' field in flashcards is optional (use empty string if not needed)")
            appendLine("- The 'explanation' field is optional (use empty string if not needed)")
            appendLine("- Respond with ONLY the JSON object, no additional text before or after")
        }
    }
    
    fun clearResponse() {
        _uiState.update { it.copy(
            response = "", 
            parsedResponse = null, 
            error = null, 
            isAdditionalInfoExpanded = false,
            createdCardIndices = emptySet()
        ) }
    }
    
    fun toggleAdditionalInfo() {
        _uiState.update { it.copy(isAdditionalInfoExpanded = !it.isAdditionalInfoExpanded) }
    }
    
    fun updateCardDirection(direction: CopilotCardDirection) {
        _uiState.update { it.copy(
            selectedCardDirection = direction,
            createdCardIndices = emptySet() // Reset created cards when direction changes
        ) }
    }
    
    fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            
            val result = copilot.getModels()
            
            result.fold(
                onSuccess = { models ->
                    _uiState.update { it.copy(
                        availableModels = models,
                        isLoadingModels = false
                    )}
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoadingModels = false,
                        error = "Failed to load models: ${error.message}"
                    )}
                }
            )
        }
    }
    
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            userPreferences.setCopilotSelectedModel(modelId)
            _uiState.update { it.copy(selectedModelId = modelId) }
        }
    }
    
    fun createAnkiCard(cardIndex: Int) {
        val parsed = _uiState.value.parsedResponse ?: return
        
        if (cardIndex < 0 || cardIndex >= parsed.cards.size) {
            _uiState.update { it.copy(error = "Invalid card index") }
            return
        }
        
        val card = parsed.cards[cardIndex]
        if (card.frontSide.isBlank() || card.backSide.isBlank()) {
            _uiState.update { it.copy(error = "Cannot create card: missing front or back side") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingCard = true, error = null) }
            
            val native = nativeLanguage.first()
            val foreign = foreignLanguage.first()
            val deckTemplate = userPreferences.getDefaultDeckName().first()
            val cardDirection = _uiState.value.selectedCardDirection
            
            val searchContext = SearchContext(
                nativeLanguage = native,
                foreignLanguage = foreign,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = when (_uiState.value.sourceLanguage) {
                    native -> foreign
                    foreign -> native
                    else -> foreign
                },
                context = _uiState.value.contextQuery.takeIf { it.isNotBlank() }
            )
            
            val deckName = templateResolver.resolveDeckName(deckTemplate, searchContext)
            
            // Create cards based on direction
            val cardsToCreate = when (cardDirection) {
                CopilotCardDirection.VIA_INTENT -> {
                    // For Intent, use front→back as default
                    // AnkiDroid will handle the UI for user to choose direction
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "copilot", native.code, foreign.code)
                        )
                    )
                }
                CopilotCardDirection.FRONT_TO_BACK -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "copilot", native.code, foreign.code)
                        )
                    )
                }
                CopilotCardDirection.BACK_TO_FRONT -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.backSide, "Back" to card.frontSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "copilot", native.code, foreign.code, "reversed")
                        )
                    )
                }
                CopilotCardDirection.BOTH_DIRECTIONS -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic (and reversed card)",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "copilot", native.code, foreign.code, "bidirectional")
                        )
                    )
                }
            }
            
            // For VIA_INTENT, force using Intent method
            val result = if (cardDirection == CopilotCardDirection.VIA_INTENT) {
                ankiRepository.createCardViaIntent(cardsToCreate.first())
            } else {
                ankiRepository.createCards(cardsToCreate)
            }
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isCreatingCard = false,
                            createdCardIndices = it.createdCardIndices + cardIndex
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isCreatingCard = false,
                            error = error.message ?: "Failed to create Anki card"
                        )
                    }
                }
            )
        }
    }
    
    private fun parseResponse(response: String): ParsedCopilotResponse? {
        return try {
            // Configure JSON parser to be lenient
            val json = Json { 
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            // Try to extract JSON from the response (in case LLM added extra text)
            val jsonContent = extractJsonFromResponse(response)
            
            // Parse JSON response
            val copilotResponse = json.decodeFromString<CopilotJsonResponse>(jsonContent)
            
            // Convert to internal representation
            ParsedCopilotResponse(
                directAnswer = copilotResponse.answer,
                cards = copilotResponse.flashcards.map { flashcard ->
                    FlashCard(
                        frontSide = flashcard.front,
                        backSide = flashcard.back,
                        note = flashcard.note
                    )
                },
                additionalInfo = copilotResponse.explanation
            )
        } catch (e: Exception) {
            // Robust fallback: if JSON parsing fails, return response as-is
            // This ensures the user still sees something even if the LLM doesn't follow format
            ParsedCopilotResponse(
                directAnswer = "Error parsing response: ${e.message}\n\nRaw response:\n$response",
                cards = emptyList(),
                additionalInfo = ""
            )
        }
    }
    
    /**
     * Extract JSON content from response, handling cases where LLM adds extra text
     */
    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()
        
        // If response starts with {, assume it's pure JSON
        if (trimmed.startsWith("{")) {
            // Find the matching closing brace
            var braceCount = 0
            var endIndex = -1
            for (i in trimmed.indices) {
                when (trimmed[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endIndex = i
                            break
                        }
                    }
                }
            }
            return if (endIndex != -1) trimmed.substring(0, endIndex + 1) else trimmed
        }
        
        // Try to find JSON block in code fence
        val jsonBlockRegex = "```(?:json)?\\s*\\n(\\{[\\s\\S]*?\\})\\s*\\n```".toRegex()
        val match = jsonBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        
        // Try to find any JSON object
        val jsonObjectRegex = "(\\{[\\s\\S]*\\})".toRegex()
        val objectMatch = jsonObjectRegex.find(trimmed)
        if (objectMatch != null) {
            return objectMatch.groupValues[1]
        }
        
        // Return as-is and let JSON parser fail
        return trimmed
    }
}
