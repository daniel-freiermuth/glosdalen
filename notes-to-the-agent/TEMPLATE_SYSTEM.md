# Deck Name Template System

## 🎯 Overview
The app features a sophisticated template system for dynamic AnkiDroid deck naming, allowing users to create contextually-aware deck names that adapt to the current translation context.

## 🏗️ Architecture

### Core Components
1. **DeckNameTemplateResolver** - Template variable resolution engine
2. **DeckNameTemplateField** - Original template input component (legacy)
3. **DeckNameFieldWithDropdown** - Modern dropdown-style deck selector with templates
4. **TemplateInfo** - Data class defining available variables

### Template Resolution Flow
```
User Input: "Vocabulary::{foreign_local}"
    ↓ (German→Swedish context)
DeckNameTemplateResolver.resolveDeckName()
    ↓ (Variable substitution)
Output: "Vocabulary::tyska"
```

## 📋 Available Template Variables

### Language Variables (Lowercase)
- `{foreign_native}` → "deutsch" (Language in its native form)
- `{foreign_english}` → "german" (Language name in English)  
- `{foreign_local}` → "tyska" (Language name in user's native language)

### Language Variables (Capitalized)
- `{Foreign_native}` → "Deutsch" (Capitalized native form)
- `{Foreign_english}` → "German" (Capitalized English)
- `{Foreign_local}` → "Tyska" (Capitalized in user's language)

### Language Codes
- `{foreign_code_native}` → "DE" (ISO language code)
- `{foreign_code_english}` → "DE" (Same, for consistency)

### Date Variables
- `{day}` → "01" (Current day, zero-padded)
- `{month}` → "10" (Current month, zero-padded)
- `{year}` → "2025" (Current year)
- `{week}` → "40" (Week of year)

## 🎨 UI Implementation

### Modern Dropdown Interface
The `DeckNameFieldWithDropdown` component provides:

**Main Text Field:**
- Dropdown appearance with arrow icon
- Real-time template preview
- Proper cursor management with `TextFieldValue`
- Support for both manual typing and template insertion

**Available Decks Dropdown:**
- Shows existing AnkiDroid decks when API is available
- Click to select existing deck
- Refresh functionality within dropdown

**Collapsible Template Help:**
- "Show Help" button reveals template variables
- Click any variable to insert at cursor position
- Live preview of resolved templates

### Cursor Management Strategy
Critical for smooth user experience:

```kotlin
// TextFieldValue for cursor tracking
var textFieldValue by remember { 
    mutableStateOf(TextFieldValue(selectedDeckName, TextRange(selectedDeckName.length))) 
}

// Prevent external interference during typing
var isUserTyping by remember { mutableStateOf(false) }

LaunchedEffect(selectedDeckName) {
    if (!isUserTyping && textFieldValue.text != selectedDeckName) {
        textFieldValue = TextFieldValue(selectedDeckName, TextRange(selectedDeckName.length))
    }
}
```

**Key Principles:**
- Use `TextFieldValue` instead of `String` for cursor position tracking
- Track typing state to prevent external updates during user input
- Debounced sync with 500ms delay after typing stops
- Insert templates at exact cursor position, not at end

## 🔧 Implementation Details

### Template Resolution Algorithm
```kotlin
fun resolveDeckName(templateString: String, foreignLanguage: Language): String {
    var resolved = templateString
    
    // Language name substitution (lowercase then uppercase)
    resolved = resolved.replace("{foreign_native}", 
        foreignLanguage.nativeName.lowercase())
    resolved = resolved.replace("{Foreign_native}", 
        foreignLanguage.nativeName)
    
    // Date substitution
    val now = LocalDate.now()
    resolved = resolved.replace("{day}", String.format("%02d", now.dayOfMonth))
    // ... (continue for all variables)
    
    return resolved.trim()
}
```

### Cursor Position Insertion
```kotlin
fun insertTemplate(template: String) {
    val currentSelection = textFieldValue.selection
    val currentText = textFieldValue.text
    
    val beforeCursor = currentText.substring(0, currentSelection.start)
    val afterCursor = currentText.substring(currentSelection.end)
    val newText = beforeCursor + template + afterCursor
    
    val newCursorPosition = currentSelection.start + template.length
    val newTextFieldValue = TextFieldValue(
        text = newText,
        selection = TextRange(newCursorPosition)
    )
    
    textFieldValue = newTextFieldValue
    onDeckNameChange(newText)
}
```

## 📚 Usage Examples

### Common Template Patterns
- `"Vocabulary::{foreign_local}"` → "Vocabulary::tyska"
- `"{year}::{Foreign_native}"` → "2025::Deutsch" 
- `"Learn {foreign_english} - {month}/{year}"` → "Learn german - 10/2025"
- `"{Foreign_local} Cards"` → "Tyska Cards"

### Context-Aware Resolution
Templates resolve differently based on translation direction:

**German → Swedish:**
- `{foreign_local}` = "tyska" (German in Swedish)
- `{Foreign_native}` = "Deutsch"

**Swedish → German:**  
- `{foreign_local}` = "schwedisch" (Swedish in German)
- `{Foreign_native}` = "Svenska"

## 🚨 Critical Considerations

### Performance
- Template resolution is debounced (300ms) to prevent excessive computation
- Preview updates are isolated from main text field state
- Cursor tracking minimal overhead with proper state management

### Error Handling
- Invalid templates gracefully degrade to original input
- Malformed date/time operations are caught and handled
- Missing template variables are left unchanged rather than throwing errors

### Accessibility
- All template variables have clear descriptions
- Keyboard navigation works properly with cursor management
- Screen reader friendly with proper content descriptions

## 🔄 Migration Notes

### From Legacy Component
The original `DeckNameTemplateField` is preserved but superseded by `DeckNameFieldWithDropdown`. Key improvements:

1. **Better UX**: Dropdown-style interface vs. basic text field
2. **Deck Integration**: Shows available AnkiDroid decks inline
3. **Advanced Cursor Management**: Prevents jumping during fast typing
4. **Progressive Disclosure**: Template help is collapsible, not always visible

### State Management Evolution
```kotlin
// Legacy: Simple string (cursor issues)
var deckName by remember { mutableStateOf("") }

// Modern: TextFieldValue with cursor tracking
var textFieldValue by remember { 
    mutableStateOf(TextFieldValue("", TextRange(0))) 
}
```

## 🛠️ Future Enhancements

### Potential Template Variables
- `{source_language}` - Source language of current translation
- `{target_language}` - Target language of current translation  
- `{time}` - Current time (HH:MM)
- `{username}` - User's name from settings
- `{difficulty}` - Word difficulty level (if implemented)

### Advanced Features
- **Conditional Templates**: `{if:bidirectional}Advanced{else}Basic{endif}`
- **Custom Variables**: User-defined template variables
- **Template Library**: Predefined popular templates
- **Template Validation**: Real-time syntax checking
