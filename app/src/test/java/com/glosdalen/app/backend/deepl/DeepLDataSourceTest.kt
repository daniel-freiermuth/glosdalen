package com.glosdalen.app.backend.deepl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.glosdalen.app.domain.preferences.AnkiPreferences
import com.glosdalen.app.domain.preferences.CopilotKnowledgePreferences
import com.glosdalen.app.domain.preferences.CopilotLanguagePreferences
import com.glosdalen.app.domain.preferences.CopilotPreferences
import com.glosdalen.app.domain.preferences.DeepLPreferences
import com.glosdalen.app.domain.preferences.LanguageInstructionsPreferences
import com.glosdalen.app.domain.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Contract of [DeepLDataSource.translate] for DeepL HTTP error statuses
 * (https://developers.deepl.com/docs/best-practices/error-handling).
 */
class DeepLDataSourceTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FailingApiService(private val error: HttpException) : DeepLApiService {
        var calls = 0
        override suspend fun translate(authKey: String, request: DeepLTranslateRequest): DeepLTranslateResponse {
            calls++
            throw error
        }
    }

    private fun userPreferences(enableMultipleFormalities: Boolean): UserPreferences {
        val dataStore = InMemoryDataStore()
        val deepLPreferences = DeepLPreferences(dataStore)
        runBlocking { deepLPreferences.setEnableMultipleFormalities(enableMultipleFormalities) }
        return UserPreferences(
            dataStore,
            deepLPreferences,
            AnkiPreferences(dataStore),
            CopilotPreferences(dataStore),
            CopilotLanguagePreferences(dataStore),
            CopilotKnowledgePreferences(dataStore),
            LanguageInstructionsPreferences(dataStore)
        )
    }

    private fun httpError(code: Int, vararg headers: Pair<String, String>): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("HTTP $code")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api-free.deepl.com/v2/translate").build())
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val body = """{"message":"error $code"}""".toResponseBody(null)
        return HttpException(Response.error<DeepLTranslateResponse>(body, raw))
    }

    private fun translate(
        api: DeepLApiService,
        targetLanguage: Language = Language.SWEDISH,
        enableMultipleFormalities: Boolean = false
    ): Throwable? = runBlocking {
        val sourceLanguage = if (targetLanguage == Language.GERMAN) Language.SWEDISH else Language.GERMAN
        DeepLDataSource(api, userPreferences(enableMultipleFormalities))
            .translate("Haus", sourceLanguage, targetLanguage, "key", DeepLModelType.DEFAULT, null)
            .exceptionOrNull()
    }

    @Test
    fun `quota exceeded (456) is reported as an API limit, not a generic API error`() {
        assertEquals(VocabularyError.ApiLimitExceeded(null), translate(FailingApiService(httpError(456))))
    }

    @Test
    fun `quota exceeded during formality variants stops after the first request`() {
        val api = FailingApiService(httpError(456))
        val error = translate(api, targetLanguage = Language.GERMAN, enableMultipleFormalities = true)
        assertEquals(VocabularyError.ApiLimitExceeded(null), error)
        assertEquals(1, api.calls)
    }

    @Test
    fun `too many requests (429) carries Retry-After`() {
        assertEquals(
            VocabularyError.ApiLimitExceeded(30),
            translate(FailingApiService(httpError(429, "Retry-After" to "30")))
        )
    }

    @Test
    fun `transient server error (500) stays a generic API error`() {
        assertEquals(VocabularyError.ApiError, translate(FailingApiService(httpError(500))))
    }
}
