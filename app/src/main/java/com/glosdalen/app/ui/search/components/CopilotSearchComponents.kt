@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glosdalen.app.domain.preferences.CopilotPreferences
import com.glosdalen.app.libs.copilot.models.CopilotModel
import com.glosdalen.app.ui.components.SplitButton

/**
 * Building blocks shared by the Copilot-backed search screens
 * (Copilot Language and General Knowledge).
 */

@Composable
fun CopilotAuthRequiredCard(onNavigateToSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Not authenticated",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Authentication Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "Please sign in to GitHub Copilot in Settings to use this feature.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Go to Settings")
            }
        }
    }
}

/**
 * Query card: optional [header], the query field and the expandable context field.
 */
@Composable
fun CopilotQueryCard(
    query: String,
    onQueryChange: (String) -> Unit,
    queryLabel: String,
    queryPlaceholder: String,
    isLoading: Boolean,
    onSend: () -> Unit,
    contextQuery: String,
    onContextQueryChange: (String) -> Unit,
    contextPlaceholder: String,
    isContextExpanded: Boolean,
    onToggleContextExpanded: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            header()
            
            // Query Input
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(queryLabel) },
                placeholder = { Text(queryPlaceholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                enabled = !isLoading,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                focusRequester.requestFocus()
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = onSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { 
                        focusManager.clearFocus()
                        onSend()
                    }
                )
            )
            
            // Context Input Section
            Column {
                // Context Toggle Button
                TextButton(
                    onClick = onToggleContextExpanded,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isContextExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isContextExpanded) "Delete context" else "Show context",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isContextExpanded) "Delete Context" else "Add Context for Better Response",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                // Context Input Field (shown when expanded)
                AnimatedVisibility(
                    visible = isContextExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = contextQuery,
                        onValueChange = onContextQueryChange,
                        label = { Text("Context (optional)") },
                        placeholder = { Text(contextPlaceholder) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        maxLines = 3,
                        supportingText = {
                            Text(
                                text = "Provide context to help get a more relevant response.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
    }
}

/** Dropdown entries for model selection: Auto first, then every available model. */
private fun modelItems(availableModels: List<CopilotModel>): List<String> = buildList {
    add(CopilotPreferences.AUTO_MODEL)
    addAll(availableModels.map { it.id })
}

private fun modelDisplayName(
    modelId: String,
    availableModels: List<CopilotModel>,
    autoLabel: String
): String = if (modelId == CopilotPreferences.AUTO_MODEL) {
    autoLabel
} else {
    availableModels.find { it.id == modelId }?.getDisplayName() ?: modelId
}

/** Send/Re-query split button whose dropdown selects the Copilot model. */
@Composable
fun CopilotSendButton(
    isRequery: Boolean,
    availableModels: List<CopilotModel>,
    selectedModelId: String,
    onSend: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val selectedModelDisplay = modelDisplayName(selectedModelId, availableModels, "Auto")
    
    SplitButton(
        mainButtonContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text((if (isRequery) "Re-query" else "Send") +  " ($selectedModelDisplay)")
        },
        dropdownItems = modelItems(availableModels),
        selectedItem = selectedModelId,
        enabled = true,
        onMainClick = {
            focusManager.clearFocus()
            onSend()
        },
        onItemSelect = onSelectModel,
        itemLabel = { modelId ->
            modelDisplayName(modelId, availableModels, "Auto (Recommended)")
        },
        dropdownButtonContentDescription = "Select AI model",
        itemContent = { modelId ->
            Column {
                Text(
                    text = modelDisplayName(modelId, availableModels, "Auto (Recommended)"),
                    color = if (modelId == selectedModelId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // Show additional info for specific models
                if (modelId != CopilotPreferences.AUTO_MODEL) {
                    availableModels.find { it.id == modelId }?.let { model ->
                        Text(
                            text = "${model.vendor} • ${model.getCostIndicator()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Automatically select best model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun CopilotLoadingCard(onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            
            Text(
                text = "Generating response...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel")
            }
        }
    }
}

/** Error card with a "Try Again" split button whose dropdown selects the Copilot model. */
@Composable
fun CopilotErrorCard(
    error: String,
    availableModels: List<CopilotModel>,
    selectedModelId: String,
    onRetry: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            // Retry button with model selection
            val selectedModelDisplay = modelDisplayName(selectedModelId, availableModels, "Auto")
            
            SplitButton(
                onMainClick = {
                    focusManager.clearFocus()
                    onRetry()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                mainButtonContent = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again ($selectedModelDisplay)")
                },
                dropdownItems = modelItems(availableModels),
                selectedItem = selectedModelId,
                enabled = true,
                onItemSelect = onSelectModel,
                itemLabel = { modelId ->
                    modelDisplayName(modelId, availableModels, "Auto (Recommended)")
                },
                dropdownButtonContentDescription = "Select AI model",
                itemContent = { modelId ->
                    Text(
                        text = modelDisplayName(modelId, availableModels, "Auto (Recommended)"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
            )
        }
    }
}

/** Expandable "Additional Information" card. */
@Composable
fun CopilotAdditionalInfoCard(
    additionalInfo: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Additional Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = additionalInfo,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Plain-text rendering of a Copilot response (answer, flashcards, additional info)
 * used by the "Copy All" action.
 */
fun <C> buildCopilotClipboardText(
    directAnswer: String,
    cards: List<C>,
    additionalInfo: String,
    front: (C) -> String,
    back: (C) -> String,
    note: (C) -> String
): String = buildString {
    if (directAnswer.isNotBlank()) {
        appendLine("ANSWER:")
        appendLine(directAnswer)
        appendLine()
    }
    if (cards.isNotEmpty()) {
        appendLine("FLASHCARDS:")
        cards.forEachIndexed { index, card ->
            appendLine("${index + 1}.")
            appendLine("Front: ${front(card)}")
            appendLine("Back: ${back(card)}")
            val cardNote = note(card)
            if (cardNote.isNotBlank()) {
                appendLine("Note: $cardNote")
            }
            appendLine()
        }
    }
    if (additionalInfo.isNotBlank()) {
        appendLine("ADDITIONAL INFORMATION:")
        appendLine(additionalInfo)
    }
}

/** "Copy All" and "Clear" buttons shown below a Copilot response. */
@Composable
fun CopilotResponseActionsRow(
    clipboardText: () -> String,
    onClear: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(clipboardText()))
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy all",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy All")
        }
        
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear")
        }
    }
}

/**
 * First-run dialog for a Copilot-backed screen.
 *
 * @param featureName shown in the title as "Welcome to [featureName]"
 * @param features bullet points listed under "Features:"
 */
@Composable
fun CopilotIntroDialog(
    featureName: String,
    description: String,
    features: List<String>,
    disclaimer: String,
    onDismiss: (showAgain: Boolean) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss(true) },
        icon = {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Welcome to $featureName")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    features.forEach { feature ->
                        Text(
                            text = "• $feature",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tip: Customize Copilot settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToSettings() }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(false) }
            ) {
                Text("Got it")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss(true) }
            ) {
                Text("Show again next time")
            }
        }
    )
}
