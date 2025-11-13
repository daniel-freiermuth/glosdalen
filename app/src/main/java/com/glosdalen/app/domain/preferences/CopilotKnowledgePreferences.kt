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
class CopilotKnowledgePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val COPILOT_KNOWLEDGE_INSTRUCTIONS = stringPreferencesKey("copilot_knowledge_instructions")
        private val COPILOT_KNOWLEDGE_SHOW_INTRO_DIALOG = stringPreferencesKey("copilot_knowledge_show_intro_dialog")
        private val COPILOT_KNOWLEDGE_DECK_TEMPLATE = stringPreferencesKey("copilot_knowledge_deck_template")
        
        const val DEFAULT_KNOWLEDGE_INSTRUCTIONS = """Please provide:
  - Clear, concise explanations
  - Key concepts and definitions
  - Practical examples when helpful
  - Context and background information
  - Related topics or concepts
  - Memory aids or mnemonics when applicable

Keep responses focused and practical for studying - this is for quick knowledge lookups.

Note: Design flashcards following best practices:
  - One concept per card
  - Question on front, answer on back
  - Use clear, specific wording
  - Include context when needed for clarity"""
    }
    
    /**
     * Get the knowledge-specific instructions for Copilot queries.
     * Returns default knowledge instructions if none are set.
     */
    fun getKnowledgeInstructions(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_KNOWLEDGE_INSTRUCTIONS] ?: DEFAULT_KNOWLEDGE_INSTRUCTIONS
        }
    }
    
    /**
     * Set the knowledge-specific instructions for Copilot queries.
     */
    suspend fun setKnowledgeInstructions(instructions: String) {
        dataStore.edit { preferences ->
            preferences[COPILOT_KNOWLEDGE_INSTRUCTIONS] = instructions
        }
    }
    
    /**
     * Check if the knowledge mode intro dialog should be shown.
     * Returns true if user hasn't dismissed it permanently.
     */
    fun shouldShowIntroDialog(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_KNOWLEDGE_SHOW_INTRO_DIALOG] != "never"
        }
    }
    
    /**
     * Set whether to show the knowledge mode intro dialog.
     * Pass "never" to never show again, or "show" to show next time.
     */
    suspend fun setShowIntroDialog(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[COPILOT_KNOWLEDGE_SHOW_INTRO_DIALOG] = if (show) "show" else "never"
        }
    }
    
    /**
     * Get the deck template for Knowledge mode.
     * This is passed to the LLM as a guideline/inspiration for deck naming.
     * Can be empty to let the LLM decide freely, or contain templates like "{topic}" or specific deck names.
     */
    fun getDeckTemplate(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[COPILOT_KNOWLEDGE_DECK_TEMPLATE] ?: ""
        }
    }
    
    /**
     * Set the deck template for Knowledge mode.
     */
    suspend fun setDeckTemplate(template: String) {
        dataStore.edit { preferences ->
            preferences[COPILOT_KNOWLEDGE_DECK_TEMPLATE] = template
        }
    }
}
