package com.glosdalen.app.backend.anki

data class AnkiCard(
    val modelName: String,
    val fields: Map<String, String>,
    val tags: List<String> = emptyList(),
    val deckName: String,
)

enum class CardType {
    UNIDIRECTIONAL,
    BIDIRECTIONAL
}

enum class CardDirection {
    NATIVE_TO_FOREIGN,  // Native language on front → Foreign on back
    FOREIGN_TO_NATIVE,  // Foreign language on front → Native on back  
    BOTH_DIRECTIONS     // Create cards in both directions (bidirectional)
}

sealed class AnkiError : Exception() {
    object AnkiDroidNotInstalled : AnkiError()
    data class IntentFailed(val reason: String?) : AnkiError()
    
    // API-specific errors
    data class ApiNotAvailable(val reason: String) : AnkiError()
    data class PermissionDenied(val reason: String) : AnkiError()
    data class ApiError(val reason: String) : AnkiError()
    data class DeckCreationFailed(val reason: String) : AnkiError()
    data class ModelCreationFailed(val reason: String) : AnkiError()
    data class CardCreationFailed(val reason: String) : AnkiError()
}