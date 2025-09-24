# Technical Decisions and Architecture

## 🎯 Core Design Philosophy
**"Technologies that provide strong compile-time correctness guarantees"** - This drove every technical choice in the project.

## 🏗️ Architecture Patterns

### MVVM + Repository Pattern
```
UI Layer (Compose) → ViewModel → Repository → Data Sources
                 ↖              ↗
                  StateFlow/LiveData
```

**Rationale**: Clear separation of concerns, testable business logic, reactive UI updates.

### Dependency Injection with Hilt
- **Scoping**: Application-scoped repositories, ViewModels scoped to Compose navigation
- **KSP over KAPT**: 2x faster builds, better Java 21 compatibility
- **Testing**: Easy mock injection for unit tests

## 🔒 Strong Typing Decisions

### Sealed Classes for Error Handling
```kotlin
sealed class VocabularyError {
    object NetworkError : VocabularyError()
    object InvalidApiKey : VocabularyError()
    data class ApiError(val message: String) : VocabularyError()
    object UnknownError : VocabularyError()
}
```
**Benefits**: Exhaustive when expressions, impossible invalid states, clear error categorization.

### Enums for Constants
```kotlin
enum class CardType { UNIDIRECTIONAL, BIDIRECTIONAL }
enum class Language(val code: String) {
    GERMAN("de"), SWEDISH("sv")
}
```
**Benefits**: Compile-time validation, IDE autocomplete, refactoring safety.

### Data Classes with Validation
```kotlin
data class VocabularyEntry(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language
) {
    init {
        require(sourceText.isNotBlank()) { "Source text cannot be blank" }
        require(translatedText.isNotBlank()) { "Translated text cannot be blank" }
    }
}
```

## 🌐 Network Architecture

### Retrofit + OkHttp Stack
```kotlin
@GET("v2/translate")
suspend fun translate(
    @Header("Authorization") authHeader: String,
    @Query("text") text: String,
    @Query("source_lang") sourceLang: String,
    @Query("target_lang") targetLang: String
): Response<DeepLTranslationResponse>
```

**Decisions**:
- **Suspend functions**: Natural coroutine integration
- **Response<T>**: Access to HTTP status codes for error handling  
- **Header injection**: Secure API key handling
- **Explicit parameters**: No magic strings, compile-time validation

### Error Handling Strategy
```kotlin
sealed class NetworkResult<T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val error: VocabularyError) : NetworkResult<T>()
}
```

**Flow**: Raw HTTP → NetworkResult → UI State → User Feedback

## 💾 Data Persistence

### DataStore over SharedPreferences
```kotlin
val apiKey: Flow<String> = dataStore.data
    .catch { exception ->
        if (exception is IOException) emit(emptyPreferences())
        else throw exception
    }
    .map { preferences -> preferences[API_KEY] ?: "" }
```

**Rationale**: 
- **Type safety**: Strongly typed preferences
- **Async by default**: No ANR risks
- **Error handling**: Built-in exception management
- **Testing**: Easy to mock and test

## 🎨 UI Architecture

### Compose-First Design
- **Stateless Composables**: Pure functions of their inputs
- **State hoisting**: ViewModels manage state, UI observes
- **Material Design 3**: Modern, accessible design system

### Navigation Strategy
```kotlin
NavHost(navController, startDestination = "search") {
    composable("search") { SearchScreen(...) }
    composable("settings") { SettingsScreen(...) }
}
```

**Simple navigation**: Only 2 screens, no complex deep linking needed.

### State Management
```kotlin
private val _uiState = MutableStateFlow(SearchUiState.Initial)
val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
```

**Pattern**: Private mutable state in ViewModel, public read-only StateFlow for UI.

## 🔗 External Integration

### AnkiDroid Intent Strategy
```kotlin
private fun createAnkiIntent(card: AnkiCard): Intent {
    return Intent("com.ichi2.anki.api.ADD_NOTE").apply {
        putExtra("deckName", card.deckName)
        putExtra("modelName", "Basic")
        putExtra("fields", arrayOf(card.front, card.back))
        putExtra("tags", arrayOf("swedish-vocab"))
    }
}
```

**Rationale**:
- **No dependencies**: No AnkiDroid SDK needed
- **Graceful degradation**: Fallback if AnkiDroid not installed  
- **User control**: User sees card before creation
- **Simple integration**: Standard Android Intent system

### API Integration Design
- **User-provided keys**: No server costs, no privacy concerns
- **Free tier friendly**: DeepL offers 500K chars/month free
- **Validation on entry**: Test API key immediately
- **Secure storage**: DataStore with no plaintext logging

## 🧪 Testing Strategy

### JUnit 5 Migration
**Modern testing**: Parameterized tests, display names, better assertions
```kotlin
@Test
@DisplayName("Should return network error when API is unreachable")
suspend fun shouldReturnNetworkError() { ... }
```

### Dependency Injection Testing
**Hilt testing**: Easy to swap real implementations with mocks
```kotlin
@HiltAndroidTest
class SearchViewModelTest { ... }
```

## 🚀 Performance Considerations

### Build Performance
- **KSP over KAPT**: ~2x faster annotation processing
- **Parallel builds**: Multiple modules can build simultaneously
- **Build caching**: Gradle cache enabled for incremental builds
- **Memory allocation**: 4GB heap prevents OOM during build

### Runtime Performance  
- **StateFlow**: Only emits when state actually changes
- **Compose recomposition**: Minimal recompositions with proper state design
- **Coroutines**: Non-blocking API calls with structured concurrency
- **No caching**: Keeps app simple, relies on network speed

## 🔄 Future-Proofing Decisions

### Modern Baseline
- **minSdk 26**: Android 8.0+ (95%+ device coverage)
- **Java 17**: Modern language features (records, sealed classes)  
- **Compose BOM**: Always use latest stable UI components
- **Material Design 3**: Future Android design direction

### Extensibility Points
- **Repository interface**: Easy to add new translation providers
- **Sealed error classes**: Easy to add new error types
- **Modular UI**: Easy to add new screens
- **Hilt modules**: Easy to add new dependencies

## 📏 Code Quality Decisions

### Kotlin Style
- **Explicit types**: When they improve readability
- **Immutable by default**: `val` over `var`, immutable data classes
- **Null safety**: Leveraged throughout, no `!!` operators
- **Extension functions**: For clean API wrappers

### Architecture Validation
- **No circular dependencies**: Clean layer separation maintained
- **Single responsibility**: Each class has one clear purpose  
- **Dependency inversion**: Abstractions don't depend on details
- **Testability**: Every business logic class is easily testable

This architecture provides the "strong compile-time correctness guarantees" requested while remaining maintainable and extensible.
