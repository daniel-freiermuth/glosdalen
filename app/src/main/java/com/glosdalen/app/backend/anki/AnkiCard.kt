package com.glosdalen.app.backend.anki

import java.io.File

data class AnkiCard(
    val modelName: String,
    val fields: Map<String, String>,
    val tags: List<String> = emptyList(),
    val deckName: String,
    val audioFiles: Map<String, File> = emptyMap() // Map of field name to audio file
)

sealed class AnkiError(message: String?) : Exception(message) {
    object AnkiDroidNotInstalled : AnkiError("AnkiDroid is not installed")
    data class IntentFailed(val reason: String?) : AnkiError(reason)
    
    // API-specific errors
    data class ApiNotAvailable(val reason: String) : AnkiError(reason)
    data class PermissionDenied(val reason: String) : AnkiError(reason)
    data class ApiError(val reason: String) : AnkiError(reason)
    data class DeckCreationFailed(val reason: String) : AnkiError(reason)
    data class ModelCreationFailed(val reason: String) : AnkiError(reason)
    data class CardCreationFailed(val reason: String) : AnkiError(reason)
}