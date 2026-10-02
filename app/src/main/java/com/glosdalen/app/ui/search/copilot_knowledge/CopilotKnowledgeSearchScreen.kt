@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.copilot_knowledge

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.glosdalen.app.ui.search.components.CopilotSearchScreenContent

@Composable
fun CopilotKnowledgeSearchScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CopilotKnowledgeViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    
    CopilotSearchScreenContent(
        uiState = uiState,
        actions = viewModel,
        title = "General Knowledge",
        introDescription = "General Knowledge uses AI to help you create flashcards for any topic you want to study.",
        introFeatures = listOf(
            "Ask questions about any topic (history, science, geography, etc.)",
            "Get comprehensive answers and study flashcards",
            "Provide context to customize difficulty level and focus",
            "Configure AI model, instructions, and settings"
        ),
        introDisclaimer = "AI-generated content may contain errors. Always verify important facts and information.",
        queryLabel = "Enter your question",
        queryPlaceholder = "E.g., \"What is the capital of France?\" or \"Explain photosynthesis\"",
        contextPlaceholder = "E.g., \"For a biology exam\" or \"Explain like I'm 10 years old\"",
        cardDirections = KnowledgeCardDirection.entries,
        cardDirectionButtonLabel = ::cardDirectionLabel,
        cardDirectionItemLabel = ::cardDirectionLabel,
        onOpenDrawer = onOpenDrawer,
        onNavigateToSettings = onNavigateToSettings,
        flashcardsHeader = { parsed ->
            if (parsed.suggestedDeck.isNotBlank()) {
                SuggestedDeckCard(deckName = parsed.suggestedDeck)
            }
        }
    )
}

private fun cardDirectionLabel(direction: KnowledgeCardDirection): String = when (direction) {
    KnowledgeCardDirection.FRONT_TO_BACK -> "Front → Back"
    KnowledgeCardDirection.BACK_TO_FRONT -> "Back → Front"
    KnowledgeCardDirection.BOTH_DIRECTIONS -> "Both Directions"
    KnowledgeCardDirection.VIA_INTENT -> "Via Intent"
}

@Composable
private fun SuggestedDeckCard(deckName: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Suggested Deck",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = deckName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
