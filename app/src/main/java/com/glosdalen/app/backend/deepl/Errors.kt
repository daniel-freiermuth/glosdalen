package com.glosdalen.app.backend.deepl

sealed class VocabularyError : Exception() {
    object NetworkError : VocabularyError()
    object ApiError : VocabularyError()
    object InvalidApiKey : VocabularyError()
    object UnsupportedLanguagePair : VocabularyError()
    data class ApiLimitExceeded(val retryAfter: Long?) : VocabularyError()
    data class UnknownError(override val message: String?) : VocabularyError()
}
