package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.backend.deepl.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-language specific instructions for Copilot queries.
 * Each language can have custom instructions that will be appended to the general instructions.
 */
@Singleton
class LanguageInstructionsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val LANGUAGE_INSTRUCTIONS = stringPreferencesKey("language_instructions_json")
        
        // Default instructions per language
        private val DEFAULT_INSTRUCTIONS = mapOf(
            Language.SWEDISH to """
                Swedish-specific notes:
                - Include information about "en/ett" gender for nouns
                - Mention if a verb is a group 1, 2, or 3 verb
                - For compound words, show how they're composed
            """.trimIndent(),
            
            Language.GERMAN to """
                German-specific notes:
                - Always include the article (der/die/das) for nouns
                - Show the plural form for nouns
                - Indicate if a verb is separable
                - Note verb case requirements (e.g., dative/accusative)
            """.trimIndent(),
            
            Language.FRENCH to """
                French-specific notes:
                - Include gender (le/la) for nouns
                - Show plural forms when irregular
                - Note any liaison requirements
                - Indicate verb conjugation group
            """.trimIndent(),
            
            Language.SPANISH to """
                Spanish-specific notes:
                - Include gender (el/la) for nouns
                - Show plural forms when irregular
                - Note any regional variations (Spain vs. Latin America)
                - Indicate if verbs have stem changes
            """.trimIndent()
        )
    }
    
    @Serializable
    private data class LanguageInstructionsData(
        val instructions: Map<String, String> = emptyMap()
    )
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get instructions for a specific language.
     * Returns empty string if no custom instructions are set.
     */
    fun getInstructions(language: Language): Flow<String> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[LANGUAGE_INSTRUCTIONS]
            if (jsonString != null) {
                try {
                    val data = json.decodeFromString<LanguageInstructionsData>(jsonString)
                    data.instructions[language.code] ?: ""
                } catch (e: Exception) {
                    ""
                }
            } else {
                ""
            }
        }
    }
    
    /**
     * Get all language instructions as a map.
     */
    fun getAllInstructions(): Flow<Map<Language, String>> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[LANGUAGE_INSTRUCTIONS]
            if (jsonString != null) {
                try {
                    val data = json.decodeFromString<LanguageInstructionsData>(jsonString)
                    // Convert string keys back to Language enum
                    data.instructions.mapNotNull { (code, instructions) ->
                        Language.values().find { it.code == code }?.let { it to instructions }
                    }.toMap()
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        }
    }
    
    /**
     * Set instructions for a specific language.
     */
    suspend fun setInstructions(language: Language, instructions: String) {
        dataStore.edit { preferences ->
            val jsonString = preferences[LANGUAGE_INSTRUCTIONS]
            val currentData = if (jsonString != null) {
                try {
                    json.decodeFromString<LanguageInstructionsData>(jsonString)
                } catch (e: Exception) {
                    LanguageInstructionsData()
                }
            } else {
                LanguageInstructionsData()
            }
            
            val updatedInstructions = currentData.instructions.toMutableMap()
            if (instructions.isBlank()) {
                // Remove entry if instructions are empty
                updatedInstructions.remove(language.code)
            } else {
                updatedInstructions[language.code] = instructions
            }
            
            val newData = LanguageInstructionsData(updatedInstructions)
            preferences[LANGUAGE_INSTRUCTIONS] = json.encodeToString(newData)
        }
    }
    
    /**
     * Get the default instructions for a language.
     */
    fun getDefaultInstructions(language: Language): String {
        return DEFAULT_INSTRUCTIONS[language] ?: ""
    }
    
    /**
     * Check if a language has custom instructions set.
     */
    fun hasCustomInstructions(language: Language): Flow<Boolean> {
        return getInstructions(language).map { it.isNotBlank() }
    }
}
