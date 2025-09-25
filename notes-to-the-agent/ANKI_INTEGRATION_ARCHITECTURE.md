# AnkiDroid Integration Architecture

This document describes the new AnkiDroid integration architecture that provides both API-based and intent-based card creation with automatic fallback support.

## Overview

The architecture follows the **Strategy Pattern** with three main components:

1. **AnkiRepository** - Main interface for AnkiDroid operations
2. **AnkiApiRepository** - Preferred implementation using AnkiDroid AddContentApi
3. **AnkiIntentRepository** - Fallback implementation using ACTION_SEND intents

## Architecture Diagram

```
┌─────────────────┐
│   SearchViewModel │
└─────┬───────────┘
      │ injects
      ▼
┌─────────────────┐
│  AnkiRepository │ ◄─── Interface
└─────┬───────────┘
      │ implements
      ▼
┌─────────────────────┐
│ AnkiRepositoryImpl  │ ◄─── Strategy Coordinator
├─────────────────────┤
│ + getBestAvailable()│
│ + createCard()      │
│ + createCards()     │
└─────┬───┬───────────┘
      │   │
      │   └─────────────────┐
      ▼                     ▼
┌──────────────────┐  ┌─────────────────────┐
│AnkiApiRepository │  │AnkiIntentRepository │
├──────────────────┤  ├─────────────────────┤
│+ AddContentApi   │  │+ ACTION_SEND intent │
│+ Permissions     │  │+ Legacy support     │
│+ Batch operations│  │+ Fallback mode      │
└──────────────────┘  └─────────────────────┘
```

## Implementation Selection Logic

The `AnkiRepositoryImpl` automatically selects the best available implementation:

1. **API First**: Tries AnkiDroid AddContentApi
   - Checks if AnkiDroid package is available
   - Verifies API permissions are granted
   - Handles permission requests automatically

2. **Intent Fallback**: Falls back to ACTION_SEND intents
   - When API is unavailable or permission denied
   - Works with all AnkiDroid versions
   - Single card creation only

3. **Graceful Degradation**: Reports unavailable state
   - When AnkiDroid is not installed
   - Provides install intent for Play Store

## Key Features

### API Implementation Benefits
- **Batch Operations**: Can create multiple cards efficiently
- **Direct Database**: No user intervention required
- **Custom Models**: Can create app-specific note types
- **Better UX**: Silent operation, stays in app

### Intent Implementation Benefits  
- **Universal Compatibility**: Works with all AnkiDroid versions
- **No Permissions**: Uses standard Android sharing
- **Reliable Fallback**: Always available when AnkiDroid installed
- **User Control**: User sees and can modify cards

### Smart Features
- **Automatic Selection**: Chooses best available method
- **Permission Handling**: Requests API permissions when needed
- **Error Recovery**: Falls back gracefully on failures
- **Logging**: Comprehensive logging for debugging

## Usage Examples

### Basic Card Creation
```kotlin
val card = AnkiCard(
    fields = mapOf(
        "Front" to "German Word",
        "Back" to "Swedish Translation"
    ),
    deckName = "German::Swedish",
    tags = listOf("vocab", "de", "sv")
)

val result = ankiRepository.createCard(card)
```

### Batch Card Creation
```kotlin
val cards = listOf(
    AnkiCard(fields = mapOf("Front" to "Hund", "Back" to "hund")),
    AnkiCard(fields = mapOf("Front" to "Katze", "Back" to "katt"))
)

val result = ankiRepository.createCards(cards)
```

### Check Availability
```kotlin
if (ankiRepository.isAnkiDroidAvailable()) {
    // AnkiDroid is ready for card creation
    val implementationType = ankiRepository.getImplementationType()
    val supportsBatch = ankiRepository.supportsBatchOperations()
}
```

## Configuration

### Dependencies (app/build.gradle.kts)
```kotlin
dependencies {
    // AnkiDroid API
    implementation("com.github.ankidroid:Anki-Android:api-v1.1.0")
    // ... other dependencies
}
```

### Repository Setup (settings.gradle.kts)
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

### Manifest Permissions (AndroidManifest.xml)
```xml
<!-- Package visibility for AnkiDroid detection on Android 11+ -->
<queries>
    <package android:name="com.ichi2.anki" />
    <intent>
        <action android:name="com.ichi2.anki.api.ADD_NOTE" />
    </intent>
</queries>
```

## Error Handling

The architecture provides comprehensive error handling:

```kotlin
sealed class AnkiError : Exception() {
    object AnkiDroidNotInstalled : AnkiError()
    data class ApiNotAvailable(val reason: String) : AnkiError()
    data class PermissionDenied(val reason: String) : AnkiError()
    data class CardCreationFailed(val reason: String) : AnkiError()
    // ... other error types
}
```

## Testing

### Unit Tests
- Repository interface compliance
- Error handling scenarios
- Implementation selection logic

### Integration Tests  
- AnkiDroid availability detection
- Card creation with real AnkiDroid app
- Permission request flows

## Maintenance Guidelines

### Adding New Features
1. Extend the `AnkiRepository` interface
2. Implement in both API and Intent repositories
3. Update the coordinator logic if needed
4. Add comprehensive tests

### Handling AnkiDroid Updates
- API changes: Update `AnkiApiRepositoryImpl`
- Intent changes: Update `AnkiIntentRepositoryImpl`  
- Fallback ensures continued functionality

### Performance Considerations
- API implementation is preferred for performance
- Intent implementation adds user interaction overhead
- Batch operations only available via API

## Future Enhancements

1. **Caching**: Cache deck/model IDs for better performance
2. **Retry Logic**: Automatic retry with exponential backoff
3. **Analytics**: Track usage patterns and success rates
4. **Custom Models**: App-specific note types with custom fields
5. **Sync Integration**: Coordinate with AnkiDroid sync operations

## Troubleshooting

### Common Issues
1. **Permission Denied**: Ensure user grants API permission
2. **API Unavailable**: Check AnkiDroid version compatibility  
3. **Intent Failures**: Verify AnkiDroid can handle ACTION_SEND
4. **Batch Failures**: Fall back to individual card creation

### Debug Information
- Check logs with tag "AnkiRepositoryImpl"
- Verify implementation type selection
- Monitor permission grant/deny flows
- Test with AnkiDroid API disabled in settings
