@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.copilot_chat

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.ui.search.components.CopilotSearchScreenContent
import com.glosdalen.app.ui.search.components.LanguageDirectionToggle

@Composable
fun CopilotChatSearchScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CopilotChatViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    val nativeLanguage by viewModel.nativeLanguage.collectAsState(Language.GERMAN)
    val foreignLanguage by viewModel.foreignLanguage.collectAsState(Language.SWEDISH)
    
    // Memoize target language calculation to prevent unnecessary recompositions
    val targetLanguage = remember(uiState.sourceLanguage, nativeLanguage, foreignLanguage) {
        when (uiState.sourceLanguage) {
            nativeLanguage -> foreignLanguage
            foreignLanguage -> nativeLanguage
            else -> foreignLanguage
        }
    }
    
    // Refresh language state when languages change (e.g., returning from settings)
    LaunchedEffect(nativeLanguage, foreignLanguage) {
        viewModel.refreshLanguageState()
    }
    
    CopilotSearchScreenContent(
        uiState = uiState,
        actions = viewModel,
        title = "Copilot Language",
        introDescription = "Copilot Language uses AI to help you create flashcards with natural language queries for language learning.",
        introFeatures = listOf(
            "Ask for translations, definitions, examples, and more",
            "Provide additional context to customize responses",
            "Configure AI model, instructions, and settings"
        ),
        introDisclaimer = "AI-generated content may contain errors. Always verify important translations and information.",
        queryHeader = {
            LanguageDirectionToggle(
                nativeLanguage = nativeLanguage,
                foreignLanguage = foreignLanguage,
                isNativeSource = uiState.sourceLanguage == nativeLanguage,
                toggleContentDescription = "Change direction",
                onToggleDirection = { viewModel.updateSourceLanguage(targetLanguage) },
                onForeignLanguageSelect = viewModel::updateForeignLanguage
            )
        },
        queryLabel = "Enter ${uiState.sourceLanguage.displayName} text",
        queryPlaceholder = "Type your question here...",
        contextPlaceholder = "E.g., \"Technical documentation\" or \"Casual conversation\"",
        cardDirections = CopilotCardDirection.entries,
        cardDirectionButtonLabel = { direction ->
            when (direction) {
                CopilotCardDirection.FRONT_TO_BACK -> "Front → Back"
                CopilotCardDirection.BOTH_DIRECTIONS -> "Both Directions"
                CopilotCardDirection.VIA_INTENT -> "Send to AnkiDroid"
            }
        },
        cardDirectionItemLabel = { direction ->
            when (direction) {
                CopilotCardDirection.FRONT_TO_BACK -> "Front → Back"
                CopilotCardDirection.BOTH_DIRECTIONS -> "Both Directions"
                CopilotCardDirection.VIA_INTENT -> "Via Intent"
            }
        },
        onOpenDrawer = onOpenDrawer,
        onNavigateToSettings = onNavigateToSettings,
        answerActions = { parsed ->
            if (uiState.isTtsConfigured) {
                TtsButton(
                    isPlaying = uiState.isTtsPlaying,
                    onClick = {
                        if (uiState.isTtsPlaying) {
                            viewModel.stopTts()
                        } else {
                            viewModel.speakText(parsed.directAnswer, parsed.directAnswerLanguageCode)
                        }
                    },
                    idleTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        frontActions = { card ->
            if (uiState.isTtsConfigured) {
                CardSideTtsButton(
                    isPlaying = uiState.isTtsPlaying,
                    onStop = viewModel::stopTts,
                    onSpeak = { viewModel.speakText(card.frontSide, card.frontLanguageCode) }
                )
            }
        },
        backActions = { card ->
            if (uiState.isTtsConfigured) {
                CardSideTtsButton(
                    isPlaying = uiState.isTtsPlaying,
                    onStop = viewModel::stopTts,
                    onSpeak = { viewModel.speakText(card.backSide, card.backLanguageCode) }
                )
            }
        }
    )
}

/** Small "Listen"/"Stop" button next to a flashcard side label. */
@Composable
private fun CardSideTtsButton(
    isPlaying: Boolean,
    onStop: () -> Unit,
    onSpeak: () -> Unit
) {
    TtsButton(
        isPlaying = isPlaying,
        onClick = {
            if (isPlaying) {
                onStop()
            } else {
                onSpeak()
            }
        },
        idleTint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp),
        iconModifier = Modifier.size(18.dp)
    )
}

/** "Listen"/"Stop" icon button; the icon uses [idleTint] while nothing is playing. */
@Composable
private fun TtsButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    idleTint: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isPlaying) "Stop" else "Listen",
            tint = if (isPlaying) {
                MaterialTheme.colorScheme.primary
            } else {
                idleTint
            },
            modifier = iconModifier
        )
    }
}
