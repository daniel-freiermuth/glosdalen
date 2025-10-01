# Glosdalen - Project Overview

## 🎯 Project Goal
Create an Android app for German-Swedish vocabulary lookup with direct AnkiDroid card creation, emphasizing "technologies that provide strong compile-time correctness guarantees."

## 📱 Core Features Implemented
- **Bidirectional Translation**: German ↔ Swedish using DeepL API
- **Direct Anki Integration**: Create cards in AnkiDroid via Intents
- **User-Provided API Keys**: No server costs, user controls usage
- **Card Type Options**: Unidirectional or bidirectional cards
- **Modern Material Design 3 UI**
- **Online-Only Architecture**: No local caching for simplicity

## 🏗️ Architecture Decisions

### Technology Stack
- **Kotlin + Jetpack Compose**: Modern Android UI with strong typing
- **MVVM + Repository Pattern**: Clean architecture with clear separation
- **Hilt Dependency Injection**: Using KSP (not KAPT) for better performance
- **Retrofit + OkHttp**: DeepL API integration with robust error handling  
- **DataStore**: Persistent storage for API keys and user preferences
- **AnkiDroid Intent Integration**: Direct card creation without complex APIs

### Key Technical Choices
1. **KSP over KAPT**: Better build performance and Java 21 compatibility
2. **Sealed Classes**: Strong error typing with compile-time guarantees
3. **StateFlow**: Reactive UI with predictable state management
4. **Material Design 3**: Modern, accessible interface
5. **Strong Typing**: Comprehensive use of enums, sealed classes, and Result types

## 🎨 User Experience Design
- **First-Launch Setup**: Guided API key configuration
- **Simple Search Interface**: Clean input with language toggle
- **Immediate Feedback**: Loading states and clear error messages
- **Settings Access**: Gear icon for configuration changes
- **Card Creation Flow**: Choose card type before creating in Anki

## 📂 Project Structure
```
app/src/main/java/com/swedishvocab/app/
├── SwedishVocabApplication.kt          # Hilt application class
├── MainActivity.kt                     # Compose navigation setup
├── data/
│   ├── model/                         # Data classes with strong typing
│   │   ├── VocabularyEntry.kt         # Translation result model
│   │   ├── AnkiCard.kt               # Anki card representation
│   │   └── Errors.kt                 # Sealed class error hierarchy
│   ├── network/                      # API integration
│   │   └── DeepLApiService.kt        # Retrofit service definitions
│   ├── repository/                   # Data access layer
│   │   └── VocabularyRepository.kt   # API calls with error handling
│   └── integration/                  # External app integration
│       └── AnkiIntegration.kt        # AnkiDroid Intent handling
├── ui/                               # Compose UI screens
│   ├── search/                       # Main search functionality
│   │   ├── SearchScreen.kt          # Search UI and translation display
│   │   └── SearchViewModel.kt       # Search business logic
│   ├── settings/                     # Configuration screen
│   │   ├── SettingsScreen.kt        # Settings UI and first-launch
│   │   └── SettingsViewModel.kt     # Settings business logic
│   └── theme/                        # Material Design 3 theming
│       └── Theme.kt                 # Color scheme and typography
└── di/                              # Dependency injection
    └── AppModule.kt                 # Hilt module configuration
```

## 🔧 Build Configuration Success
See `BUILD_CONFIGURATION.md` for detailed build setup and troubleshooting.

## 🚀 Current Status
- ✅ **Complete Implementation**: All 13+ Kotlin files implemented
- ✅ **Successful Build**: APK generated at `app/build/outputs/apk/debug/app-debug.apk`
- ✅ **Modern Dependencies**: Latest AndroidX, Compose 2024.06.00, Java 17 target
- ✅ **Clean Code**: No deprecation warnings, modern API usage
- ✅ **Ready for Testing**: 11MB APK ready for device installation

## 📋 Next Steps for Continuation
1. **Device Testing**: Install APK and test with real DeepL API key
2. **AnkiDroid Testing**: Verify card creation with actual AnkiDroid app
3. **Potential Enhancements**: See `FUTURE_ENHANCEMENTS.md`
4. **Production Readiness**: Consider signing keys and Play Store preparation

## 📁 Notes Organization
This `notes-to-the-agent/` folder contains:
- `PROJECT_OVERVIEW.md` (this file) - High-level project summary
- `BUILD_CONFIGURATION.md` - Critical build setup and troubleshooting
- `TECHNICAL_DECISIONS.md` - Detailed architecture and technology choices
- `FUTURE_ENHANCEMENTS.md` - Potential improvements and extensions

**Important**: These notes are designed to enable project continuation even if conversation history is lost. Always check and update these files when making significant changes.
