# Glosordalen - Android Project

## Project Overview
Kotlin Android app for German-Swedish vocabulary lookup with DeepL API integration and AnkiDroid card creation.

## Tech Stack
- Kotlin + Jetpack Compose UI
- MVVM Architecture with Repository pattern  
- Hilt for dependency injection
- Retrofit for DeepL API calls
- AnkiDroid Intent integration

## Key Features
- Bidirectional German ↔ Swedish translation
- Direct Anki card creation (uni/bidirectional)
- User-provided DeepL API key with validation
- Online-only, no caching

## Development Guidelines
- Use strong typing with sealed classes and Result types
- Implement proper error handling with user-friendly messages
- Follow Material Design 3 guidelines
- Write testable code with dependency injection
