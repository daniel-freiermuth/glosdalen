# Glosordalen

A Kotlin Android app for German-Swedish vocabulary lookup with direct Anki card creation.

## Features

- 🔄 **Bidirectional Translation**: German ↔ Swedish word lookup using DeepL API
- 📚 **Anki Integration**: Create cards directly in AnkiDroid with one tap
- ⚙️ **Flexible Card Types**: Choose between unidirectional or bidirectional cards
- 🔑 **User API Key**: Secure, user-provided DeepL API key with validation
- 🎯 **Simple UX**: Search → Translate → Create card workflow
- 📱 **Material Design 3**: Modern, clean interface following Android guidelines

## Prerequisites

- Android Studio Arctic Fox (2020.3.1) or later
- Android SDK API 24+ (Android 7.0)
- DeepL API key (free tier available at [deepl.com/pro-api](https://www.deepl.com/pro-api))
- AnkiDroid app installed on device (for card creation)

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Repository pattern
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp
- **State Management**: StateFlow + Compose State
- **Persistence**: DataStore (Preferences)
- **API**: DeepL REST API

## Project Structure

```
app/src/main/java/com/swedishvocab/app/
├── data/
│   ├── datasource/          # Data source interfaces and implementations
│   ├── model/              # Data models, enums, and error classes
│   ├── network/            # API service definitions
│   └── repository/         # Repository implementations
├── domain/
│   └── preferences/        # User preferences management
├── anki/                   # AnkiDroid integration
├── di/                     # Dependency injection modules
├── ui/
│   ├── search/            # Search screen and ViewModel
│   ├── settings/          # Settings screen and ViewModel
│   └── theme/             # Material Design theme
├── MainActivity.kt         # Main activity and navigation
└── SwedishVocabApplication.kt
```

## Build Instructions

### 1. Clone and Setup

```bash
git clone <repository-url>
cd anki-vocab-app
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the `anki-vocab-app` folder
4. Click "OK"

### 3. Sync Project

Android Studio will automatically:
- Download required dependencies
- Configure Gradle
- Set up the project

### 4. Build the App

```bash
# From terminal in project root
./gradlew assembleDebug

# Or use Android Studio:
# Build -> Make Project (Ctrl+F9)
```

### 5. Run on Device/Emulator

```bash
# Install on connected device
./gradlew installDebug

# Or use Android Studio:
# Run -> Run 'app' (Shift+F10)
```

## Usage

### First Launch Setup

1. **API Key Configuration**:
   - Enter your DeepL API key
   - App validates the key with a test translation
   - Key is stored securely in DataStore

2. **Anki Configuration**:
   - Set default deck name (e.g., "German::Swedish")
   - Choose default card type (Unidirectional/Bidirectional)

### Daily Usage

1. **Search**: Enter German or Swedish word
2. **Toggle Languages**: Use swap button to change direction
3. **View Translation**: See DeepL translation result
4. **Create Card**: Choose card type and create in AnkiDroid
5. **Settings**: Access via gear icon to modify preferences

## Configuration

### DeepL API Setup

1. Visit [deepl.com/pro-api](https://www.deepl.com/pro-api)
2. Sign up for free tier (500,000 chars/month)
3. Copy your API key
4. Enter in app settings on first launch

### AnkiDroid Setup

1. Install [AnkiDroid](https://play.google.com/store/apps/details?id=com.ichi2.anki) from Play Store
2. Create or sync your Anki collection
3. App will automatically detect AnkiDroid installation

## Card Types

### Unidirectional Cards
- Single card: German → Swedish
- Good for recognition practice

### Bidirectional Cards  
- Two cards: German → Swedish + Swedish → German
- Better for active recall and production

## Error Handling

The app gracefully handles:
- **Network Issues**: Offline detection with retry options
- **Invalid API Key**: Clear error messages and validation
- **API Limits**: Rate limiting with retry-after headers
- **AnkiDroid Missing**: Installation prompts and fallback options

## Development

### Architecture Principles

- **Strong Typing**: Sealed classes for errors, enums for constants
- **Separation of Concerns**: Clean architecture layers
- **Dependency Injection**: Testable, modular components
- **Reactive UI**: StateFlow for predictable state management
- **Error First**: Comprehensive error handling throughout

### Key Design Decisions

1. **Online-Only**: No local caching to keep it simple
2. **User API Keys**: No server costs, user controls usage
3. **AnkiDroid Intents**: Direct integration without complex APIs
4. **Material Design 3**: Modern, accessible interface
5. **Compose-First**: Modern Android UI toolkit

## Troubleshooting

### Build Issues

**Gradle Sync Failed**:
```bash
./gradlew clean
./gradlew build
```

**Dependency Issues**:
- Ensure internet connection for dependency downloads
- Check `gradle.properties` for proxy settings if needed

### Runtime Issues

**API Key Invalid**:
- Verify key is correct in DeepL dashboard
- Check API usage limits
- Ensure free tier key uses `api-free.deepl.com`

**AnkiDroid Not Working**:
- Verify AnkiDroid is installed and updated
- Check that AnkiDroid has been opened at least once
- Verify deck names don't contain invalid characters

**Network Errors**:
- Check internet connectivity
- Verify DeepL API status
- Try VPN if regional restrictions apply

## Future Enhancements

Potential extensions (not implemented):
- Multiple data sources (dict.cc, Svenska Ordboken)
- Offline caching and history
- Pronunciation audio
- Example sentences
- Batch card creation
- Statistics and progress tracking

## Contributing

1. Fork the repository
2. Create a feature branch
3. Follow existing code style and architecture
4. Add tests for new functionality
5. Submit a pull request

## License

[Add your license here]

---

**Note**: This app requires active internet connection and valid DeepL API key to function. AnkiDroid must be installed for card creation features.
