package com.glosdalen.app.backend.anki

import java.io.File

data class AnkiCard(
    val modelName: String,
    val fields: Map<String, String>,
    val tags: List<String> = emptyList(),
    val deckName: String,
    val audioFiles: Map<String, File> = emptyMap() // Map of field name to audio file
)

sealed class AnkiError(message: String? = null) : Exception(message) {
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

/**
 * User-facing message for an Anki failure. Exhaustive over [AnkiError] so a new
 * subtype cannot silently fall through to a generic message.
 */
fun AnkiError.toUserMessage(): String = when (this) {
    AnkiError.AnkiDroidNotInstalled ->
        "AnkiDroid is not installed. Please install it from the Play Store."
    is AnkiError.PermissionDenied ->
        "AnkiDroid permission required. Please grant access in settings."
    is AnkiError.DeckCreationFailed -> reason
    is AnkiError.ModelCreationFailed -> "Could not set up the Anki note type: $reason"
    is AnkiError.IntentFailed -> "Error when creating card: ${reason ?: "Unknown error"}"
    is AnkiError.ApiNotAvailable -> "Error when creating card: $reason"
    is AnkiError.ApiError -> "Error when creating card: $reason"
    is AnkiError.CardCreationFailed -> "Error when creating card: $reason"
}

/** User-facing message for any card-creation failure; non-[AnkiError] throwables get a generic message. */
fun Throwable.toCardCreationErrorMessage(): String =
    (this as? AnkiError)?.toUserMessage()
        ?: "Error when creating card: ${message ?: "Unknown error"}"
