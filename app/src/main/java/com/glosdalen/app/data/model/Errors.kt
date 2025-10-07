package com.glosdalen.app.data.model

sealed class VocabularyError : Exception() {
    object NetworkError : VocabularyError()
    object ApiError : VocabularyError()
    object InvalidApiKey : VocabularyError()
    object UnsupportedLanguagePair : VocabularyError()
    data class ApiLimitExceeded(val retryAfter: Long?) : VocabularyError()
    data class UnknownError(override val message: String?) : VocabularyError()
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
