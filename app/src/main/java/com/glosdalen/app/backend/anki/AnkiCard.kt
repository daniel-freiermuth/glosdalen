package com.glosdalen.app.backend.anki

import java.io.File

/**
 * Note types the app creates. [modelName] is the English built-in AnkiDroid name;
 * lookup of localized variants is handled by AnkiApiRepository.ensureModelExists.
 */
enum class AnkiNoteType(val modelName: String) {
    BASIC("Basic"),
    BASIC_AND_REVERSED("Basic (and reversed card)")
}

data class AnkiCard(
    val noteType: AnkiNoteType,
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    val deckName: String,
    val frontAudio: File? = null,
    val backAudio: File? = null
)

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