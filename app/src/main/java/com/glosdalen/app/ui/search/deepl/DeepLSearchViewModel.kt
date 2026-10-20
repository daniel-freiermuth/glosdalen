package com.glosdalen.app.ui.search.deepl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiCard
import com.glosdalen.app.backend.anki.AnkiError
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.backend.elevenlabs.ElevenLabsError
import com.glosdalen.app.backend.elevenlabs.ElevenLabsRepository
import com.glosdalen.app.domain.preferences.FrontPreference
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.backend.deepl.*
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DeepLSearchViewModel @Inject constructor(
    private val deepLRepository: DeepLRepository,
    private val ankiRepository: AnkiRepository,
    private val elevenLabsRepository: ElevenLabsRepository,
    private val userPreferences: UserPreferences,
    private val templateResolver: DeckNameTemplateResolver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DeepLUiState())
    val uiState: StateFlow<DeepLUiState> = _uiState.asStateFlow()
    
    val deepLApiKey = userPreferences.getDeepLApiKey()
    val nativeLanguage = userPreferences.getNativeLanguage()
    val foreignLanguage = userPreferences.getForeignLanguage()
    val defaultDeckName = userPreferences.getDefaultDeckName()
    
    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnkiDroidAvailable = ankiRepository.isAnkiDroidAvailable(),
                isTtsConfigured = elevenLabsRepository.isConfigured()
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
    
    fun refreshLanguageState() {
        viewModelScope.launch {
            val native = nativeLanguage.first()
            val foreign = foreignLanguage.first()
            val currentState = _uiState.value
            
            // Refresh TTS configuration status (in case it was set up in settings)
            val ttsConfigured = elevenLabsRepository.isConfigured()
            
            // If current source language is not one of the configured languages,
            // reset to native language
            if (currentState.sourceLanguage != native && currentState.sourceLanguage != foreign) {
                _uiState.value = currentState.copy(
                    sourceLanguage = native,
                    translationResult = null,
                    error = null,
                    cardCreationResult = null,
                    cardsCreatedCount = 0,
                    isTtsConfigured = ttsConfigured
                )
            } else {
                _uiState.value = currentState.copy(isTtsConfigured = ttsConfigured)
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
            
            val result = deepLRepository.lookupWord(
                word = query,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = deepLTargetLanguage(_uiState.value.sourceLanguage, currentNative, currentForeign),
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
            
            val selectedTranslation = _uiState.value.selectedTranslation
            val currentNative = nativeLanguage.first()
            val currentForeign = foreignLanguage.first()
            val deckTemplate = defaultDeckName.first()
            val cardDirection = _uiState.value.selectedCardDirection
            
            // Resolve deck name template
            val deckName = templateResolver.resolveDeckName(deckTemplate, currentForeign)
            
            val (nativeWord, foreignWord) = nativeAndForeignWords(result, selectedTranslation, currentNative)
            
            // Generate audio files for the foreign word if ElevenLabs is configured
            val foreignAudioFile = if (elevenLabsRepository.isConfigured()) {
                try {
                    elevenLabsRepository.generateAudioFile(foreignWord, currentForeign.elevenLabsCode).getOrNull()
                } catch (e: Exception) {
                    android.util.Log.w("DeepLSearchViewModel", "Failed to generate audio for card", e)
                    null
                }
            } else null
            
            // Create cards based on user's direction preference
            val frontPref = userPreferences.getFrontPreference().first()
            val cardsToCreate = listOf(
                buildDeepLAnkiCard(
                    nativeWord = nativeWord,
                    foreignWord = foreignWord,
                    nativeLanguage = currentNative,
                    foreignLanguage = currentForeign,
                    cardDirection = cardDirection,
                    frontPreference = frontPref,
                    deckName = deckName,
                    foreignAudioFile = foreignAudioFile
                )
            )
            
            try {
                // For VIA_INTENT, force using Intent method
                val ankiResult = if (cardDirection == DeepLCardDirection.VIA_INTENT) {
                    ankiRepository.createCardViaIntent(cardsToCreate.first())
                } else if (cardsToCreate.size == 1) {
                    ankiRepository.createCard(cardsToCreate.first())
                } else {
                    ankiRepository.createCards(cardsToCreate)
                }
                
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = ankiResult,
                    cardsCreatedCount = cardsToCreate.size,
                    lastCardDirection = cardDirection,
                    hasCardBeenCreated = ankiResult.isSuccess
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingCard = false,
                    cardCreationResult = Result.failure(AnkiError.IntentFailed("Failed to create card: ${e.message}")),
                    cardsCreatedCount = 0,
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
    
    fun updateCardDirection(direction: DeepLCardDirection) {
        _uiState.value = _uiState.value.copy(
            selectedCardDirection = direction,
            hasCardBeenCreated = false,
            cardCreationResult = null
        )
    }
    
    fun retrySearch() {
        searchWord()
    }
    
    /**
     * Speak the given text using ElevenLabs TTS.
     * @param text The text to speak
     * @param language The language of the text (for proper pronunciation)
     */
    fun speakText(text: String, language: Language? = null) {
        if (_uiState.value.isTtsPlaying) {
            stopTts()
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTtsPlaying = true,
                ttsError = null
            )
            
            elevenLabsRepository.speakText(
                text = text,
                languageCode = language?.elevenLabsCode,
                onComplete = {
                    _uiState.value = _uiState.value.copy(isTtsPlaying = false)
                },
                onError = { error ->
                    val errorMessage = when (error) {
                        is ElevenLabsError.NoApiKey -> "TTS not configured. Set up in Settings → ElevenLabs TTS"
                        is ElevenLabsError.InvalidApiKey -> "Invalid ElevenLabs API key"
                        is ElevenLabsError.NetworkError -> "Network error"
                        is ElevenLabsError.QuotaExceeded -> "ElevenLabs character quota exceeded"
                        is ElevenLabsError.PlaybackError -> "Playback failed"
                        else -> "TTS error"
                    }
                    _uiState.value = _uiState.value.copy(
                        isTtsPlaying = false,
                        ttsError = errorMessage
                    )
                }
            )
        }
    }
    
    /**
     * Stop any currently playing TTS audio.
     */
    fun stopTts() {
        elevenLabsRepository.stopPlayback()
        _uiState.value = _uiState.value.copy(isTtsPlaying = false)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Stop any playing audio when ViewModel is cleared
        elevenLabsRepository.stopPlayback()
    }
}

/**
 * Target language for a DeepL lookup: the other configured language, or the foreign
 * language when [sourceLanguage] is neither native nor foreign.
 */
internal fun deepLTargetLanguage(
    sourceLanguage: Language,
    nativeLanguage: Language,
    foreignLanguage: Language
): Language = when (sourceLanguage) {
    nativeLanguage -> foreignLanguage
    foreignLanguage -> nativeLanguage
    else -> foreignLanguage // Default fallback
}

/**
 * Split a lookup result into (nativeWord, foreignWord).
 *
 * The translation is [selectedTranslation], else the first DeepL translation, else "".
 * A search from [nativeLanguage] keeps the original word on the native side; any other
 * source language is treated as a reverse (foreign → native) search.
 */
internal fun nativeAndForeignWords(
    entry: VocabularyEntry,
    selectedTranslation: String?,
    nativeLanguage: Language
): Pair<String, String> {
    val translation = selectedTranslation ?: entry.translations.firstOrNull()?.text ?: ""
    return if (entry.sourceLanguage == nativeLanguage) {
        // Original search: Native → Foreign
        entry.originalWord to translation
    } else {
        // Reverse search: Foreign → Native
        translation to entry.originalWord
    }
}

/**
 * Build the Anki note for a DeepL vocabulary pair.
 *
 * [frontPreference] only applies to [DeepLCardDirection.VIA_INTENT] and
 * [DeepLCardDirection.BOTH_DIRECTIONS]; [foreignAudioFile], when present, is attached to
 * whichever field holds [foreignWord].
 */
internal fun buildDeepLAnkiCard(
    nativeWord: String,
    foreignWord: String,
    nativeLanguage: Language,
    foreignLanguage: Language,
    cardDirection: DeepLCardDirection,
    frontPreference: FrontPreference,
    deckName: String,
    foreignAudioFile: File?
): AnkiCard {
    val foreignOnFront = when (cardDirection) {
        DeepLCardDirection.NATIVE_TO_FOREIGN -> false
        DeepLCardDirection.FOREIGN_TO_NATIVE -> true
        DeepLCardDirection.VIA_INTENT,
        DeepLCardDirection.BOTH_DIRECTIONS -> frontPreference == FrontPreference.FOREIGN
    }
    val (frontSide, backSide) = if (foreignOnFront) foreignWord to nativeWord else nativeWord to foreignWord

    // The audio is generated for the foreign word, so it goes on the foreign side
    val audioFiles = if (foreignAudioFile == null) {
        emptyMap()
    } else {
        mapOf((if (foreignOnFront) "Front" else "Back") to foreignAudioFile)
    }

    val directionTag = when (cardDirection) {
        DeepLCardDirection.VIA_INTENT -> null
        DeepLCardDirection.NATIVE_TO_FOREIGN -> "native-to-foreign"
        DeepLCardDirection.FOREIGN_TO_NATIVE -> "foreign-to-native"
        DeepLCardDirection.BOTH_DIRECTIONS -> "bidirectional"
    }

    return AnkiCard(
        modelName = if (cardDirection == DeepLCardDirection.BOTH_DIRECTIONS) "Basic (and reversed card)" else "Basic",
        fields = mapOf("Front" to frontSide, "Back" to backSide),
        deckName = deckName,
        tags = listOfNotNull("glosdalen", "vocab", nativeLanguage.code, foreignLanguage.code, directionTag),
        audioFiles = audioFiles
    )
}

/**
 * Card direction for DeepL mode
 * Language-aware directions for vocabulary cards
 */
enum class DeepLCardDirection {
    NATIVE_TO_FOREIGN,  // Native language → Foreign language (via API)
    FOREIGN_TO_NATIVE,  // Foreign language → Native language (via API)
    BOTH_DIRECTIONS,    // Create cards in both directions (via API)
    VIA_INTENT          // Create via AnkiDroid Intent (user chooses direction)
}

data class DeepLUiState(
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
    val lastCardDirection: DeepLCardDirection? = null,
    val hasCardBeenCreated: Boolean = false,
    val contextQuery: String = "",
    val isContextExpanded: Boolean = false,
    val selectedCardDirection: DeepLCardDirection = DeepLCardDirection.NATIVE_TO_FOREIGN,
    // TTS state
    val isTtsConfigured: Boolean = false,
    val isTtsPlaying: Boolean = false,
    val ttsError: String? = null
)
