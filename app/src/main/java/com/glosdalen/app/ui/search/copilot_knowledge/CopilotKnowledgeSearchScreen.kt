@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.copilot_knowledge

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.glosdalen.app.ui.components.SplitButton
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.glosdalen.app.ui.search.components.*

@Composable
fun CopilotKnowledgeSearchScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CopilotKnowledgeViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    // Recheck authentication status when screen is resumed
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckAuthenticationStatus()
    }
    
    // Show intro dialog if needed
    if (uiState.showIntroDialog) {
        CopilotIntroDialog(
            featureName = "General Knowledge",
            description = "General Knowledge uses AI to help you create flashcards for any topic you want to study.",
            features = listOf(
                "Ask questions about any topic (history, science, geography, etc.)",
                "Get comprehensive answers and study flashcards",
                "Provide context to customize difficulty level and focus",
                "Configure AI model, instructions, and settings"
            ),
            disclaimer = "AI-generated content may contain errors. Always verify important facts and information.",
            onDismiss = viewModel::dismissIntroDialog,
            onNavigateToSettings = onNavigateToSettings
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SearchTopAppBar(
            title = "General Knowledge",
            onOpenDrawer = onOpenDrawer,
            onNavigateToSettings = onNavigateToSettings
        )
        
        // Info Card
        if (!uiState.isAuthenticated) {
            CopilotAuthRequiredCard(onNavigateToSettings = onNavigateToSettings)
        }
        
        // Query Section
        CopilotQueryCard(
            query = uiState.query,
            onQueryChange = viewModel::updateQuery,
            queryLabel = "Enter your question",
            queryPlaceholder = "E.g., \"What is the capital of France?\" or \"Explain photosynthesis\"",
            isLoading = uiState.isLoading,
            onSend = viewModel::sendQuery,
            contextQuery = uiState.contextQuery,
            onContextQueryChange = viewModel::updateContextQuery,
            contextPlaceholder = "E.g., \"For a biology exam\" or \"Explain like I'm 10 years old\"",
            isContextExpanded = uiState.isContextExpanded,
            onToggleContextExpanded = viewModel::toggleContextExpanded
        )
        
        // Send Button with Model Selection
        if (uiState.query.isNotEmpty() && !uiState.isLoading && uiState.error == null) {
            CopilotSendButton(
                isRequery = uiState.response.isNotEmpty(),
                availableModels = uiState.availableModels,
                selectedModelId = uiState.selectedModelId,
                onSend = viewModel::sendQuery,
                onSelectModel = viewModel::selectModel
            )
        }
        
        // Loading Indicator with Cancel Button
        if (uiState.isLoading) {
            CopilotLoadingCard(onCancel = viewModel::cancelQuery)
        }
        
        // Error Display
        uiState.error?.let { error ->
            CopilotErrorCard(
                error = error,
                availableModels = uiState.availableModels,
                selectedModelId = uiState.selectedModelId,
                onRetry = viewModel::sendQuery,
                onSelectModel = viewModel::selectModel
            )
        }
        
        // Response Display
        uiState.parsedResponse?.let { parsed ->
            // Direct Answer Section
            if (parsed.directAnswer.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Answer",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(parsed.directAnswer))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy answer",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = parsed.directAnswer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // Flashcards Section
            if (parsed.cards.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Proposed Flashcards (${parsed.cards.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Show suggested deck if available
                        if (parsed.suggestedDeck.isNotBlank()) {
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
                                            text = parsed.suggestedDeck,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        
                        parsed.cards.forEachIndexed { index, card ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Card ${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // Front Side
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Front:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = card.frontSide,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Back Side
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Back:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = card.backSide,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Note (if provided)
                                    if (card.note.isNotBlank()) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Note:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            SelectionContainer {
                                                Text(
                                                    text = card.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Create Card Button for this specific card
                                    CreateCardButton(
                                        selectedCardDirection = uiState.selectedCardDirection,
                                        isCreatingCard = uiState.isCreatingCard,
                                        isAnkiDroidAvailable = uiState.isAnkiDroidAvailable,
                                        hasCardBeenCreated = index in uiState.createdCardIndices,
                                        onCreateCard = { viewModel.createAnkiCard(index) },
                                        onCardDirectionChange = viewModel::updateCardDirection
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Additional Info Section (expandable)
            if (parsed.additionalInfo.isNotBlank()) {
                CopilotAdditionalInfoCard(
                    additionalInfo = parsed.additionalInfo,
                    isExpanded = uiState.isAdditionalInfoExpanded,
                    onToggleExpanded = viewModel::toggleAdditionalInfo
                )
            }
            
            // Copy All and Clear buttons
            CopilotResponseActionsRow(
                clipboardText = {
                    buildCopilotClipboardText(
                        directAnswer = parsed.directAnswer,
                        cards = parsed.cards,
                        additionalInfo = parsed.additionalInfo,
                        front = { it.frontSide },
                        back = { it.backSide },
                        note = { it.note }
                    )
                },
                onClear = viewModel::clearResponse
            )
        }
    }
}

@Composable
private fun CreateCardButton(
    selectedCardDirection: KnowledgeCardDirection,
    isCreatingCard: Boolean,
    isAnkiDroidAvailable: Boolean,
    hasCardBeenCreated: Boolean,
    onCreateCard: () -> Unit,
    onCardDirectionChange: (KnowledgeCardDirection) -> Unit
) {
    val cardDirectionText = when (selectedCardDirection) {
        KnowledgeCardDirection.FRONT_TO_BACK -> "Front → Back"
        KnowledgeCardDirection.BACK_TO_FRONT -> "Back → Front"
        KnowledgeCardDirection.BOTH_DIRECTIONS -> "Both Directions"
        KnowledgeCardDirection.VIA_INTENT -> "Via Intent"
    }
    
    Column {
        SplitButton(
            mainButtonContent = {
                if (isCreatingCard) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("Creating...")
                    }
                } else if (hasCardBeenCreated) {
                    Text("Card Created ✓")
                } else {
                    Text("Add to Anki ($cardDirectionText)")
                }
            },
            dropdownItems = KnowledgeCardDirection.values().toList(),
            selectedItem = selectedCardDirection,
            enabled = !isCreatingCard && isAnkiDroidAvailable && !hasCardBeenCreated,
            onMainClick = onCreateCard,
            onItemSelect = onCardDirectionChange,
            itemLabel = { direction ->
                when (direction) {
                    KnowledgeCardDirection.FRONT_TO_BACK -> "Front → Back"
                    KnowledgeCardDirection.BACK_TO_FRONT -> "Back → Front"
                    KnowledgeCardDirection.BOTH_DIRECTIONS -> "Both Directions"
                    KnowledgeCardDirection.VIA_INTENT -> "Via Intent"
                }
            },
            dropdownButtonContentDescription = "Card direction options"
        )
        
        if (!isAnkiDroidAvailable) {
            Text(
                text = "⚠️ AnkiDroid not available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
