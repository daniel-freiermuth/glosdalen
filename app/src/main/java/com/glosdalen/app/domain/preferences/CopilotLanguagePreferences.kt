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
class CopilotLanguagePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val COPILOT_LANGUAGE_INSTRUCTIONS = stringPreferencesKey("copilot_language_instructions")
        private val COPILOT_LANGUAGE_SHOW_INTRO_DIALOG = stringPreferencesKey("copilot_language_show_intro_dialog")
        
        const val DEFAULT_LANGUAGE_INSTRUCTIONS = """Please provide:
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
     * Get the language-specific instructions for Copilot queries.
     * Returns default language instructions if none are set.
     */
    fun getLanguageInstructions(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_LANGUAGE_INSTRUCTIONS] ?: DEFAULT_LANGUAGE_INSTRUCTIONS
        }
    }
    
    /**
     * Set the language-specific instructions for Copilot queries.
     */
    suspend fun setLanguageInstructions(instructions: String) {
        dataStore.edit { preferences ->
            preferences[COPILOT_LANGUAGE_INSTRUCTIONS] = instructions
        }
    }
    
    /**
     * Check if the language mode intro dialog should be shown.
     * Returns true if user hasn't dismissed it permanently.
     */
    fun shouldShowIntroDialog(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_LANGUAGE_SHOW_INTRO_DIALOG] != "never"
        }
    }
    
    /**
     * Set whether to show the language mode intro dialog.
     * Pass "never" to never show again, or "show" to show next time.
     */
    suspend fun setShowIntroDialog(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[COPILOT_LANGUAGE_SHOW_INTRO_DIALOG] = if (show) "show" else "never"
        }
    }
}
