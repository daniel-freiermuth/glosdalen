package com.glosdalen.app.ui.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.glosdalen.app.backend.deepl.Language
import kotlinx.serialization.Serializable

// Settings destinations. Each one is its own Nav3 back-stack entry, so it gets its own
// ViewModelStore, saved state, and system back / predictive back handling from NavDisplay.
@Serializable data object SettingsRoute : NavKey
@Serializable data object DeepLSettingsRoute : NavKey
@Serializable data object AnkiSettingsRoute : NavKey
@Serializable data object CopilotSettingsRoute : NavKey
@Serializable data object LanguageInstructionsRoute : NavKey
@Serializable data class LanguageInstructionsDetailRoute(val language: Language) : NavKey
@Serializable data object ElevenLabsSettingsRoute : NavKey

/**
 * Registers every Settings destination. Navigation callbacks only act while [key] is the top
 * entry, so a repeated tap during a transition cannot push or pop twice.
 */
fun EntryProviderScope<NavKey>.settingsEntries(backStack: NavBackStack<NavKey>) {
    fun navigateBack(key: NavKey) {
        if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }
    fun navigate(from: NavKey, to: NavKey) {
        if (backStack.lastOrNull() == from) backStack.add(to)
    }

    entry<SettingsRoute> { key ->
        SettingsScreen(
            onNavigateBack = { navigateBack(key) },
            onNavigateToDeepL = { navigate(key, DeepLSettingsRoute) },
            onNavigateToAnki = { navigate(key, AnkiSettingsRoute) },
            onNavigateToCopilot = { navigate(key, CopilotSettingsRoute) },
            onNavigateToElevenLabs = { navigate(key, ElevenLabsSettingsRoute) }
        )
    }
    entry<DeepLSettingsRoute> { key ->
        DeepLSettingsScreen(onNavigateBack = { navigateBack(key) })
    }
    entry<AnkiSettingsRoute> { key ->
        AnkiSettingsScreen(onNavigateBack = { navigateBack(key) })
    }
    entry<CopilotSettingsRoute> { key ->
        CopilotSettingsScreen(
            onNavigateBack = { navigateBack(key) },
            onNavigateToLanguageInstructions = { navigate(key, LanguageInstructionsRoute) }
        )
    }
    entry<LanguageInstructionsRoute> { key ->
        LanguageInstructionsScreen(
            onNavigateBack = { navigateBack(key) },
            onLanguageSelect = { language -> navigate(key, LanguageInstructionsDetailRoute(language)) }
        )
    }
    entry<LanguageInstructionsDetailRoute> { key ->
        LanguageInstructionsDetailScreen(
            language = key.language,
            onNavigateBack = { navigateBack(key) }
        )
    }
    entry<ElevenLabsSettingsRoute> { key ->
        ElevenLabsSettingsScreen(onNavigateBack = { navigateBack(key) })
    }
}
