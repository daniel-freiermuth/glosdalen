package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CopilotPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val COPILOT_SELECTED_MODEL = stringPreferencesKey("copilot_selected_model")
        private val COPILOT_TEMPERATURE = floatPreferencesKey("copilot_temperature")
        const val AUTO_MODEL = "auto" // Special value for automatic model selection
        const val DEFAULT_TEMPERATURE = 0.6f // Balanced creativity and consistency
    }
    
    /**
     * Get the selected Copilot model ID.
     * Returns "auto" for automatic selection, or a specific model ID.
     */
    fun getSelectedModel(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_SELECTED_MODEL] ?: AUTO_MODEL
        }
    }
    
    /**
     * Set the selected Copilot model.
     * Use "auto" for automatic selection, or provide a specific model ID.
     */
    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { preferences ->
            preferences[COPILOT_SELECTED_MODEL] = modelId
        }
    }
    
    /**
     * Get the temperature setting for Copilot queries.
     * Temperature controls randomness: 0.0 = deterministic, 1.0 = very creative
     * Default is 0.6 for balanced responses.
     */
    fun getTemperature(): Flow<Float> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_TEMPERATURE] ?: DEFAULT_TEMPERATURE
        }
    }
    
    /**
     * Set the temperature for Copilot queries.
     * Value should be between 0.0 (deterministic) and 1.0 (creative).
     */
    suspend fun setTemperature(temperature: Float) {
        dataStore.edit { preferences ->
            preferences[COPILOT_TEMPERATURE] = temperature.coerceIn(0.0f, 1.0f)
        }
    }
}
