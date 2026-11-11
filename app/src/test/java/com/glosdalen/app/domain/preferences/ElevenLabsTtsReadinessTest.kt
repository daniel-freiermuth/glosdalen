package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ElevenLabsTtsReadinessTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val preferences = ElevenLabsPreferences(InMemoryDataStore())

    @Test
    fun `a validated key without a voice is not ready for TTS`() = runBlocking {
        preferences.setApiKey("sk_tts_only")

        assertFalse(preferences.isTtsReady())
    }

    @Test
    fun `key plus voice is ready for TTS`() = runBlocking {
        preferences.setApiKey("sk_test")
        preferences.setVoiceId("voice123")

        assertTrue(preferences.isTtsReady())
    }

    @Test
    fun `a voice without a key is not ready for TTS`() = runBlocking {
        preferences.setVoiceId("voice123")

        assertFalse(preferences.isTtsReady())
    }
}
