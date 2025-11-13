package com.glosdalen.app.ui.search.copilot_knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiCard
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.libs.copilot.CopilotChat
import com.glosdalen.app.libs.copilot.CopilotException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * JSON response structure from the LLM for general knowledge queries
 */
@Serializable
data class KnowledgeJsonResponse(
    val answer: String,
    val flashcards: List<KnowledgeFlashCardJson> = emptyList(),
    val explanation: String = "",
    val suggestedDeck: String = ""
)

@Serializable
data class KnowledgeFlashCardJson(
    val front: String,
    val back: String,
    val note: String = ""
)

/**
 * Internal representation used by the UI
 */
data class KnowledgeFlashCard(
    val frontSide: String,
    val backSide: String,
    val note: String = ""
)

data class ParsedKnowledgeResponse(
    val directAnswer: String,
    val cards: List<KnowledgeFlashCard>,
    val additionalInfo: String,
    val suggestedDeck: String
)

/**
 * Card direction for Knowledge mode
 * Generic front/back directions since cards are already defined by the LLM
 */
enum class KnowledgeCardDirection {
    FRONT_TO_BACK,      // Use LLM's front → back as-is (via API)
    BACK_TO_FRONT,      // Reverse: LLM's back → front (via API)
    BOTH_DIRECTIONS,    // Create cards in both directions (via API)
    VIA_INTENT          // Create via AnkiDroid Intent (user chooses direction)
}

data class CopilotKnowledgeUiState(
    val query: String = "",
    val contextQuery: String = "",
    val isContextExpanded: Boolean = false,
    val response: String = "",
    val parsedResponse: ParsedKnowledgeResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val isAdditionalInfoExpanded: Boolean = false,
    val isCreatingCard: Boolean = false,
    val createdCardIndices: Set<Int> = emptySet(),
    val isAnkiDroidAvailable: Boolean = false,
    val selectedCardDirection: KnowledgeCardDirection = KnowledgeCardDirection.FRONT_TO_BACK,
    val availableModels: List<com.glosdalen.app.libs.copilot.models.CopilotModel> = emptyList(),
    val selectedModelId: String = com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL,
    val isLoadingModels: Boolean = false,
    val temperature: Float = com.glosdalen.app.domain.preferences.CopilotPreferences.DEFAULT_TEMPERATURE,
    val showIntroDialog: Boolean = false
)

@HiltViewModel
class CopilotKnowledgeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val copilot: CopilotChat,
    private val ankiRepository: AnkiRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CopilotKnowledgeUiState())
    val uiState: StateFlow<CopilotKnowledgeUiState> = _uiState.asStateFlow()
    
    // Track the current query job for cancellation
    private var queryJob: kotlinx.coroutines.Job? = null
    
    init {
        // Check authentication status (models loaded via recheckAuthenticationStatus on screen resume)
        viewModelScope.launch {
            // Initialize card direction from preferences
            val isAuth = copilot.isAuthenticated()
            val ankiAvailable = ankiRepository.isAnkiDroidAvailable()
            val selectedModel = userPreferences.getCopilotSelectedModel().first()
            val temperature = userPreferences.getCopilotTemperature().first()
            val shouldShowIntro = userPreferences.shouldShowCopilotKnowledgeIntroDialog().first()
            _uiState.update { it.copy(
                isAuthenticated = isAuth,
                isAnkiDroidAvailable = ankiAvailable,
                selectedModelId = selectedModel,
                temperature = temperature,
                showIntroDialog = shouldShowIntro,
            ) }
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
    
    fun updateContextQuery(context: String) {
        _uiState.update { it.copy(contextQuery = context) }
    }
    
    fun toggleAdditionalInfo() {
        _uiState.update { it.copy(isAdditionalInfoExpanded = !it.isAdditionalInfoExpanded) }
    }
    
    fun toggleContextExpanded() {
        _uiState.update { 
            val newExpandedState = !it.isContextExpanded
            it.copy(
                isContextExpanded = newExpandedState,
                contextQuery = if (newExpandedState) it.contextQuery else "",
                response = if (!newExpandedState) "" else it.response,
                error = if (!newExpandedState) null else it.error
            ) 
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
            userPreferences.setShowCopilotKnowledgeIntroDialog(showAgain)
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
                
                // Get general knowledge instructions
                val knowledgeInstructions = userPreferences.getCopilotKnowledgeInstructions().first()
                
                // Get deck template for LLM guidance
                val deckTemplate = userPreferences.getCopilotKnowledgeDeckTemplate().first()
                
                // Build prompt for general knowledge query
                val prompt = buildKnowledgePrompt(
                    query = query,
                    context = _uiState.value.contextQuery.takeIf { it.isNotBlank() },
                    instructions = knowledgeInstructions,
                    deckTemplate = deckTemplate
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
                                isLoading = false,
                                response = response,
                                parsedResponse = parsed,
                                error = null
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
                        error = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun buildKnowledgePrompt(
        query: String,
        context: String?,
        instructions: String,
        deckTemplate: String
    ): String {
        return buildString {
            appendLine("You are a helpful knowledge assistant for creating study flashcards.")
            appendLine()
            appendLine("Instructions:")
            appendLine(instructions)
            appendLine()
            
            if (!context.isNullOrBlank()) {
                appendLine("Additional Context: $context")
                appendLine()
            }
            
            if (deckTemplate.isNotBlank()) {
                appendLine("Deck Name Guideline by the user: $deckTemplate")
                // appendLine("(Use this as inspiration/template for the deck name.)")
                appendLine()
            }
            
            appendLine("User Query: $query")
            appendLine()
            appendLine("Please respond in the following JSON format:")
            appendLine("""{
  "answer": "Brief, direct answer to the question",
  "suggestedDeck": "Anki deck name for these cards (e.g., 'Biology', 'World History', 'Physics::Mechanics')",
  "flashcards": [
    {
      "front": "Question or term",
      "back": "Answer or definition",
      "note": "Optional: Additional context or explanation"
    }
  ],
  "explanation": "Optional: Additional detailed explanation or context"
}""")
        }
    }
    
    fun cancelQuery() {
        queryJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }
    
    fun clearResponse() {
        _uiState.update { 
            it.copy(
                response = "",
                parsedResponse = null,
                error = null,
                createdCardIndices = emptySet()
            )
        }
    }
    
    fun updateCardDirection(direction: KnowledgeCardDirection) {
        _uiState.update { 
            it.copy(
                selectedCardDirection = direction,
                createdCardIndices = emptySet()
            )
        }
    }
    
    fun createAnkiCard(cardIndex: Int) {
        val parsed = _uiState.value.parsedResponse ?: return
        if (cardIndex !in parsed.cards.indices) return
        
        val card = parsed.cards[cardIndex]
        val cardDirection = _uiState.value.selectedCardDirection
        
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingCard = true, error = null) }
            
            val deckTemplate = userPreferences.getDefaultDeckName().first()
            val parsed = _uiState.value.parsedResponse ?: return@launch
            
            // Use LLM's suggested deck, or fall back to template/default
            val deckName = when {
                parsed.suggestedDeck.isNotBlank() -> parsed.suggestedDeck
                deckTemplate.contains("{") -> "Glosdalen::General Knowledge"
                else -> deckTemplate
            }
            
            // Create cards based on direction
            val cardsToCreate = when (cardDirection) {
                KnowledgeCardDirection.VIA_INTENT -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "knowledge", "copilot")
                        )
                    )
                }
                KnowledgeCardDirection.FRONT_TO_BACK -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "knowledge", "copilot")
                        )
                    )
                }
                KnowledgeCardDirection.BACK_TO_FRONT -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic",
                            fields = mapOf("Front" to card.backSide, "Back" to card.frontSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "knowledge", "copilot", "reversed")
                        )
                    )
                }
                KnowledgeCardDirection.BOTH_DIRECTIONS -> {
                    listOf(
                        AnkiCard(
                            modelName = "Basic (and reversed card)",
                            fields = mapOf("Front" to card.frontSide, "Back" to card.backSide),
                            deckName = deckName,
                            tags = listOf("glosdalen", "knowledge", "copilot", "bidirectional")
                        )
                    )
                }
            }
            
            val result = if (cardDirection == KnowledgeCardDirection.VIA_INTENT) {
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
    
    private fun parseResponse(response: String): ParsedKnowledgeResponse? {
        return try {
            // Configure JSON parser to be lenient
            val json = Json { 
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            // Try to extract JSON from response (in case it's wrapped in markdown)
            val jsonContent = extractJsonFromResponse(response)
            
            val parsedJson = json.decodeFromString<KnowledgeJsonResponse>(jsonContent)
            
            ParsedKnowledgeResponse(
                directAnswer = parsedJson.answer,
                cards = parsedJson.flashcards.map { 
                    KnowledgeFlashCard(
                        frontSide = it.front,
                        backSide = it.back,
                        note = it.note
                    )
                },
                additionalInfo = parsedJson.explanation,
                suggestedDeck = parsedJson.suggestedDeck
            )
        } catch (e: Exception) {
            // If parsing fails, return null and show raw response
            null
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        // Look for JSON block in markdown code fences
        val jsonBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)```".toRegex()
        val match = jsonBlockRegex.find(response)
        
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // Try to find JSON object directly
            val start = response.indexOf('{')
            val end = response.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) {
                response.substring(start, end + 1)
            } else {
                response
            }
        }
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
}
