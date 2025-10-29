# Language-Specific Instructions Feature

## Overview
Added a new settings page that allows users to configure per-language specific instructions for GitHub Copilot. These instructions are combined with the general Copilot instructions to provide tailored guidance for each language being learned.

## Files Created

### 1. LanguageInstructionsPreferences.kt
**Location:** `app/src/main/java/com/glosdalen/app/domain/preferences/LanguageInstructionsPreferences.kt`

**Purpose:** Manages storage and retrieval of language-specific instructions

**Key Features:**
- Stores instructions as JSON mapping language codes to instruction strings
- Provides default instructions for common languages (Swedish, German, French, Spanish)
- Supports CRUD operations for language instructions
- Uses DataStore for persistence

**Default Instructions Included:**
- **Swedish**: Gender (en/ett), verb groups, compound word composition
- **German**: Articles (der/die/das), plural forms, separable verbs, case requirements
- **French**: Gender (le/la), irregular plurals, liaison, verb conjugation groups
- **Spanish**: Gender (el/la), irregular plurals, regional variations, stem changes

**API:**
```kotlin
fun getInstructions(language: Language): Flow<String>
fun getAllInstructions(): Flow<Map<Language, String>>
suspend fun setInstructions(language: Language, instructions: String)
fun getDefaultInstructions(language: Language): String
fun hasCustomInstructions(language: Language): Flow<Boolean>
```

### 2. LanguageInstructionsViewModel.kt
**Location:** `app/src/main/java/com/glosdalen/app/ui/settings/LanguageInstructionsViewModel.kt`

**Purpose:** Manages state for the language instructions settings screen

**Features:**
- Tracks currently selected language for editing
- Loads instructions for the selected language
- Provides language status indicators (which languages have custom instructions)
- Operations: select language, update/clear/load default instructions

**State:**
```kotlin
val selectedLanguage: StateFlow<Language>
val currentInstructions: StateFlow<String>
val languageStatuses: StateFlow<Map<Language, Boolean>>
```

### 3. LanguageInstructionsScreen.kt
**Location:** `app/src/main/java/com/glosdalen/app/ui/settings/LanguageInstructionsScreen.kt`

**Purpose:** UI for configuring per-language instructions

**Features:**
- **Info Section**: Explains the feature purpose
- **Language Selector**: Dropdown with all supported languages
  - Shows checkmark indicator for languages with custom instructions
- **Instructions Editor**: Multi-line text field for editing instructions
  - Shows character count
  - Displays current instruction status
- **Quick Actions**: 
  - "Load Default Instructions" button (when defaults available)
  - Clear button (icon button, shows confirmation dialog)
- Material Design 3 styling with proper error handling

## Integration

### Modified Files

**UserPreferences.kt:**
- Added `LanguageInstructionsPreferences` as a dependency
- Exposed language instructions methods through the facade

**SettingsScreen.kt:**
- Added `LanguageInstructions` to the `SettingsPage` enum
- Added navigation card: "Language Instructions - Per-language specific guidance for Copilot"
- Integrated routing to the new screen

**CopilotChatViewModel.kt:**
- Modified `buildPrompt()` to accept `languageInstructions` parameter
- Fetches language-specific instructions for the foreign language
- Appends language instructions to the prompt after general instructions

## User Flow

### Configuring Language Instructions

1. Navigate to Settings → Language Instructions
2. See info card explaining the feature
3. Select a language from the dropdown
   - Languages with custom instructions show a checkmark
4. Enter or edit instructions in the text field
5. Instructions auto-save as you type
6. Optional: Load default instructions (if available)
7. Optional: Clear instructions (shows confirmation dialog)

### How It Works When Translating

When a user queries Copilot for translation:
1. System retrieves general Copilot instructions
2. System retrieves language-specific instructions for the foreign language
3. Both are combined in the prompt sent to Copilot:
   ```
   General instructions from the user: <general instructions>
   Language-specific instructions for Swedish: <Swedish-specific instructions>
   ```
4. Copilot responses follow both sets of instructions

## UI Components

### Language Selector Section
- Card with title "Select Language"
- ExposedDropdownMenu showing all languages
- Visual indicator (checkmark) for languages with custom instructions
- Sorted alphabetically by display name

### Instructions Editor Section
- Card with title "Instructions for [Language]"
- Multi-line OutlinedTextField (5-10 lines)
- Character count or status message
- Delete button (icon, top-right, only visible when instructions exist)
- Quick actions section with "Load Default Instructions" button

### Info Section
- Primary container card
- Bold title with explanation
- Guides users on the feature purpose

## Data Storage

Instructions are stored in DataStore as JSON:
```json
{
  "instructions": {
    "SV": "Swedish-specific instructions...",
    "DE": "German-specific instructions...",
    "ES": "Spanish-specific instructions..."
  }
}
```

- Empty strings remove the language entry
- Data persists across app restarts
- Uses kotlinx.serialization for JSON handling

## Technical Details

### State Management
- Uses StateFlow for reactive UI updates
- `flatMapLatest` operator for switching between languages
- Proper coroutine scope management with `viewModelScope`

### Architecture
- Follows existing MVVM pattern
- Separates concerns: Preferences (data), ViewModel (logic), Screen (UI)
- Dependency injection via Hilt
- Follows Material Design 3 guidelines

### Error Handling
- Graceful handling of JSON parse errors (returns empty map)
- Confirmation dialog before clearing instructions
- Auto-saves prevent data loss

## Example Use Cases

### Learning Swedish
Instructions might include:
```
- Always include "en/ett" for nouns
- Mention verb groups (1, 2, 3, or irregular)
- Show compound word breakdowns
- Include pronunciation tips for difficult sounds
```

### Learning German
Instructions might include:
```
- Always provide der/die/das article
- Show plural forms
- Indicate if verb is separable
- Note case requirements (Akkusativ/Dativ)
```

## Benefits

1. **Personalization**: Tailor Copilot responses to specific language learning needs
2. **Consistency**: Ensure Copilot always provides the information you find most helpful
3. **Flexibility**: Different instructions for different languages
4. **Convenience**: Pre-loaded defaults for common languages
5. **Integration**: Seamlessly combines with general Copilot instructions

## Testing Recommendations

1. **Basic Flow**
   - Navigate to Language Instructions settings
   - Select different languages
   - Enter instructions, verify they save
   - Switch languages, verify instructions persist

2. **Default Instructions**
   - Load default instructions for Swedish, German, French, Spanish
   - Verify they appear in the text field
   - Verify they can be edited

3. **Clear Functionality**
   - Add instructions, then clear them
   - Verify confirmation dialog appears
   - Verify instructions are removed after confirmation

4. **Integration with Copilot**
   - Set instructions for a language
   - Make a Copilot query in that language
   - Verify the response follows the instructions

5. **Visual Indicators**
   - Set instructions for some languages, not others
   - Verify checkmarks appear only for configured languages
   - Verify the indicators update when instructions are cleared

## Future Enhancements

Possible improvements:
1. Import/export instructions (share between devices)
2. Community-curated instruction templates
3. Preview how instructions affect Copilot responses
4. Language-specific placeholders in the text field
5. Instruction templates for different proficiency levels
6. Analytics showing which instructions are most effective
