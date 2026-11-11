package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences for ElevenLabs TTS (Text-to-Speech) integration.
 *
 * There is no built-in default voice: the voice is chosen from the account's `v2/voices` list.
 */
@Singleton
class ElevenLabsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val ELEVENLABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        private val ELEVENLABS_VOICE_ID = stringPreferencesKey("elevenlabs_voice_id")
        private val ELEVENLABS_VOICE_NAME = stringPreferencesKey("elevenlabs_voice_name")
        private val ELEVENLABS_MODEL_ID = stringPreferencesKey("elevenlabs_model_id")
        private val ELEVENLABS_IMPLICIT_VOICE_PINNED = booleanPreferencesKey("elevenlabs_implicit_voice_pinned")
        
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
    }
    
    /**
     * One-time migration for installs that configured an API key while the app still had a
     * built-in default voice ("Rachel", `21m00Tcm4TlvDq8ikWAM`) and never picked one.
     *
     * Those users relied on the fallback that no longer exists. Persisting the old ID keeps their
     * audio unchanged (ElevenLabs routes the retired Rachel ID to its replacement server-side);
     * the settings screen then flags it as unavailable so they can pick a current voice.
     * Runs once per install; installs without an API key are only marked as migrated.
     */
    object PinImplicitDefaultVoiceMigration : DataMigration<Preferences> {
        const val LEGACY_DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
        const val LEGACY_DEFAULT_VOICE_NAME = "Rachel"
        
        override suspend fun shouldMigrate(currentData: Preferences): Boolean =
            currentData[ELEVENLABS_IMPLICIT_VOICE_PINNED] != true
        
        override suspend fun migrate(currentData: Preferences): Preferences {
            val migrated = currentData.toMutablePreferences()
            if (!currentData[ELEVENLABS_API_KEY].isNullOrBlank() && currentData[ELEVENLABS_VOICE_ID] == null) {
                migrated[ELEVENLABS_VOICE_ID] = LEGACY_DEFAULT_VOICE_ID
                migrated[ELEVENLABS_VOICE_NAME] = LEGACY_DEFAULT_VOICE_NAME
            }
            migrated[ELEVENLABS_IMPLICIT_VOICE_PINNED] = true
            return migrated.toPreferences()
        }
        
        override suspend fun cleanUp() = Unit
    }
    
    /**
     * TTS can run only with both an API key and a chosen voice; there is no fallback voice.
     */
    suspend fun isTtsReady(): Boolean {
        val preferences = dataStore.data.first()
        return !preferences[ELEVENLABS_API_KEY].isNullOrBlank() && preferences[ELEVENLABS_VOICE_ID] != null
    }
    
    fun getApiKey(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_API_KEY] ?: ""
        }
    }
    
    suspend fun setApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[ELEVENLABS_API_KEY] = apiKey
        }
    }
    
    /** The selected voice ID, or null when no voice has been chosen yet. */
    fun getVoiceId(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_VOICE_ID]
        }
    }
    
    suspend fun setVoiceId(voiceId: String) {
        dataStore.edit { preferences ->
            preferences[ELEVENLABS_VOICE_ID] = voiceId
        }
    }
    
    /** The selected voice's display name, or null when no voice has been chosen yet. */
    fun getVoiceName(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_VOICE_NAME]
        }
    }
    
    suspend fun setVoiceName(voiceName: String) {
        dataStore.edit { preferences ->
            preferences[ELEVENLABS_VOICE_NAME] = voiceName
        }
    }
    
    fun getModelId(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_MODEL_ID] ?: DEFAULT_MODEL_ID
        }
    }
    
    suspend fun setModelId(modelId: String) {
        dataStore.edit { preferences ->
            preferences[ELEVENLABS_MODEL_ID] = modelId
        }
    }
}
