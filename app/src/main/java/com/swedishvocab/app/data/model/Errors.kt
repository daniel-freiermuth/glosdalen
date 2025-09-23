package com.swedishvocab.app.data.model

sealed class VocabularyError : Exception() {
    object NetworkError : VocabularyError()
    object ApiError : VocabularyError()
    object InvalidApiKey : VocabularyError()
    object UnsupportedLanguagePair : VocabularyError()
    data class ApiLimitExceeded(val retryAfter: Long?) : VocabularyError()
    data class UnknownError(val message: String?) : VocabularyError()
}

sealed class AnkiError : Exception() {
    object AnkiDroidNotInstalled : AnkiError()
    data class IntentFailed(val reason: String?) : AnkiError()
}
