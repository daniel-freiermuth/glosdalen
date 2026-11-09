package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.backend.deepl.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Norwegian was persisted as "NO" up to v2.3.0 and as "NB" since v2.4.0.
 * Values written by older versions must still resolve to [Language.NORWEGIAN].
 */
class LegacyLanguageCodeTest {

    private class InMemoryDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private val instructionsKey = stringPreferencesKey("language_instructions_json")

    private fun userPreferences(dataStore: DataStore<Preferences>) = UserPreferences(
        dataStore,
        DeepLPreferences(dataStore),
        AnkiPreferences(dataStore),
        CopilotPreferences(dataStore),
        CopilotLanguagePreferences(dataStore),
        CopilotKnowledgePreferences(dataStore),
        LanguageInstructionsPreferences(dataStore)
    )

    @Test
    @DisplayName("Legacy \"NO\" native and foreign language resolve to Norwegian")
    fun legacyLanguageSelection() = runBlocking {
        val dataStore = InMemoryDataStore(
            mutablePreferencesOf(
                stringPreferencesKey("native_language") to "NO",
                stringPreferencesKey("foreign_language") to "NO"
            )
        )
        val prefs = userPreferences(dataStore)

        assertEquals(Language.NORWEGIAN, prefs.getNativeLanguage().first())
        assertEquals(Language.NORWEGIAN, prefs.getForeignLanguage().first())
    }

    @Test
    @DisplayName("Instructions stored under legacy \"NO\" are returned for Norwegian")
    fun legacyInstructionsAreReadable() = runBlocking {
        val dataStore = InMemoryDataStore(
            mutablePreferencesOf(instructionsKey to """{"instructions":{"NO":"bokmål only"}}""")
        )
        val prefs = LanguageInstructionsPreferences(dataStore)

        assertEquals("bokmål only", prefs.getInstructions(Language.NORWEGIAN).first())
        assertEquals(mapOf(Language.NORWEGIAN to "bokmål only"), prefs.getAllInstructions().first())
    }

    @Test
    @DisplayName("Clearing Norwegian instructions also removes the legacy \"NO\" entry")
    fun clearingRemovesLegacyEntry() = runBlocking {
        val dataStore = InMemoryDataStore(
            mutablePreferencesOf(instructionsKey to """{"instructions":{"NO":"bokmål only"}}""")
        )
        val prefs = LanguageInstructionsPreferences(dataStore)

        prefs.setInstructions(Language.NORWEGIAN, "")

        assertEquals("", prefs.getInstructions(Language.NORWEGIAN).first())
        assertEquals(emptyMap<Language, String>(), prefs.getAllInstructions().first())
    }

    @Test
    @DisplayName("Updating Norwegian instructions replaces the legacy \"NO\" entry")
    fun updatingReplacesLegacyEntry() = runBlocking {
        val dataStore = InMemoryDataStore(
            mutablePreferencesOf(instructionsKey to """{"instructions":{"NO":"old"}}""")
        )
        val prefs = LanguageInstructionsPreferences(dataStore)

        prefs.setInstructions(Language.NORWEGIAN, "new")

        assertEquals("new", prefs.getInstructions(Language.NORWEGIAN).first())
        assertEquals(mapOf(Language.NORWEGIAN to "new"), prefs.getAllInstructions().first())
    }
}
