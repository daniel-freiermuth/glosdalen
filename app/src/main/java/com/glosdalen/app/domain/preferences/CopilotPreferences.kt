package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        const val AUTO_MODEL = "auto" // Special value for automatic model selection
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
}
