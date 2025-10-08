# Glosdalen

A Kotlin Android app for multi-language vocabulary lookup with seamless AnkiDroid integration. Support for 30+ languages including German, Swedish, English, French, Spanish, Italian, and many more.

## Features

- **Multi-Language Support**: High-quality translation for 30+ languages
- **Seamless AnkiDroid Integration**: 
  - **Direct database access** (default): Fast, seamless integration with full deck management
  - **Intent-based fallback**: Works even without database permissions
- ️ **Flexible Card Types**: Choose between unidirectional or bidirectional cards
-  **Simple UX**: Search → Translate → Create card workflow

## Usage

### First Launch Setup

1. **API Key Configuration**:
   - Enter your translation service API key (currently DeepL)
   - App validates the key with a test translation
   - Key is stored securely in DataStore

2. **Anki Configuration**:
   - Set default deck name (e.g., "German::Swedish")

### Daily Usage

1. **Search**: Enter a word in any supported language
2. **Toggle Languages**: Use swap button to change translation direction
3. **View Translation**: See DeepL translation result
4. **Create Card**: Choose card type and create in AnkiDroid
5. **Settings**: Access via gear icon to modify preferences

## Configuration

### Translation Service API Setup

**DeepL (Currently Supported):**
1. Visit [deepl.com/pro-api](https://www.deepl.com/pro-api)
2. Sign up for free tier (500,000 chars/month)
3. Copy your API key
4. Enter in app settings on first launch

### AnkiDroid Setup

1. Install AnkiDroid
2. Create or sync your Anki collection
3. Optionally grant Glosdalen access permission to the AnkiDroid database
4. App will automatically detect AnkiDroid installation

## Language Support

Glosdalen currently supports all language pairs available through DeepL, including:

- **Germanic**: German, English, Dutch, Danish, Swedish, Norwegian
- **Romance**: French, Spanish, Italian, Portuguese, Romanian
- **Slavic**: Polish, Czech, Slovak, Bulgarian, Slovenian
- **Other**: Japanese, Chinese, Korean, Arabic, Turkish, Greek, Hungarian, Finnish, Estonian, Latvian, Lithuanian

Perfect for any language learning combination you need!

## Future Enhancements

Potential extensions (not implemented):
- Multiple data sources (OpenAI, Github Copilot, dict.cc, Svenska Ordboken)
- Offline caching and history
- Batch card creation

## License

This project is licensed under the GNU General Public License v3.0 or later - see the [LICENSE](LICENSE) file for details.
