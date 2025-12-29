package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences for ElevenLabs TTS (Text-to-Speech) integration.
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
        
        // Default voice - "Rachel" is a popular multilingual voice
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
        const val DEFAULT_VOICE_NAME = "Rachel"
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
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
    
    fun getVoiceId(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_VOICE_ID] ?: DEFAULT_VOICE_ID
        }
    }
    
    suspend fun setVoiceId(voiceId: String) {
        dataStore.edit { preferences ->
            preferences[ELEVENLABS_VOICE_ID] = voiceId
        }
    }
    
    fun getVoiceName(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[ELEVENLABS_VOICE_NAME] ?: DEFAULT_VOICE_NAME
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
