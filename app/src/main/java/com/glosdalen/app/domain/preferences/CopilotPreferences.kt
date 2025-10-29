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
        private val COPILOT_GENERAL_INSTRUCTIONS = stringPreferencesKey("copilot_general_instructions")
        const val AUTO_MODEL = "auto" // Special value for automatic model selection
        const val DEFAULT_INSTRUCTIONS = """Please provide:
  - Direct translation if applicable
  - Grammar explanations when relevant
  - Usage examples
  - Cultural context when helpful
  - Alternative expressions
  - Common collocations or related vocabulary
            
Keep responses concise and practical for language learning - this is for quick lookups.

Note: If applicable, the foreign word shall be on the front side.
It is not in the spirit of flash cards to have the foreign word on the same side as a native.
Prefer idiomatic expressions over word-by-word translations."""
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
     * Get the general instructions for Copilot queries.
     * Returns default instructions if none are set.
     */
    fun getGeneralInstructions(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_GENERAL_INSTRUCTIONS] ?: DEFAULT_INSTRUCTIONS
        }
    }
    
    /**
     * Set the general instructions for Copilot queries.
     */
    suspend fun setGeneralInstructions(instructions: String) {
        dataStore.edit { preferences ->
            preferences[COPILOT_GENERAL_INSTRUCTIONS] = instructions
        }
    }
}
