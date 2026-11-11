package com.glosdalen.app.domain.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.domain.preferences.ElevenLabsPreferences.PinImplicitDefaultVoiceMigration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PinImplicitDefaultVoiceMigrationTest {

    private val apiKey = stringPreferencesKey("elevenlabs_api_key")
    private val voiceId = stringPreferencesKey("elevenlabs_voice_id")
    private val voiceName = stringPreferencesKey("elevenlabs_voice_name")

    private fun runOnce(data: Preferences): Preferences = runBlocking {
        if (PinImplicitDefaultVoiceMigration.shouldMigrate(data)) PinImplicitDefaultVoiceMigration.migrate(data) else data
    }

    @Test
    fun `configured key without a chosen voice keeps the voice it was implicitly using`() {
        val migrated = runOnce(preferencesOf(apiKey to "sk_test"))

        assertEquals("21m00Tcm4TlvDq8ikWAM", migrated[voiceId])
        assertEquals("Rachel", migrated[voiceName])
    }

    @Test
    fun `explicitly chosen voice is left untouched`() {
        val migrated = runOnce(preferencesOf(apiKey to "sk_test", voiceId to "abc123", voiceName to "Mine"))

        assertEquals("abc123", migrated[voiceId])
        assertEquals("Mine", migrated[voiceName])
    }

    @Test
    fun `install without a key is never pinned, even after a key is added later`() {
        val firstLaunch = runOnce(preferencesOf())
        assertNull(firstLaunch[voiceId])

        // Key saved later, but voices did not load yet so no voice was chosen.
        val laterLaunch = firstLaunch.toMutablePreferences().apply { this[apiKey] = "sk_new" }.toPreferences()
        assertFalse(runBlocking { PinImplicitDefaultVoiceMigration.shouldMigrate(laterLaunch) })
        assertNull(runOnce(laterLaunch)[voiceId])
    }
}
