@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.backend.deepl.Language

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageInstructionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LanguageInstructionsViewModel = hiltViewModel()
) {
    val languageStatuses by viewModel.languageStatuses.collectAsState()
    var selectedLanguage by remember { mutableStateOf<Language?>(null) }
    
    // Handle Android's native back button/gesture
    BackHandler {
        if (selectedLanguage != null) {
            selectedLanguage = null
        } else {
            onNavigateBack()
        }
    }
    
    // Show list or detail based on selection
    if (selectedLanguage == null) {
        LanguageListScreen(
            languageStatuses = languageStatuses,
            onLanguageSelect = { language ->
                viewModel.selectLanguage(language)
                selectedLanguage = language
            },
            onNavigateBack = onNavigateBack
        )
    } else {
        LanguageDetailScreen(
            language = selectedLanguage!!,
            onNavigateBack = { selectedLanguage = null },
            viewModel = viewModel
        )
    }
}

@Composable
private fun LanguageListScreen(
    languageStatuses: Map<Language, Boolean>,
    onLanguageSelect: (Language) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Language Settings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Info card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Per-Language Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Configure language-specific instructions and settings. Select a language below to customize how Copilot responds for that language.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Language list
            items(Language.values().sortedBy { it.displayName }) { language ->
                LanguageListItem(
                    language = language,
                    hasCustomSettings = languageStatuses[language] == true,
                    onClick = { onLanguageSelect(language) }
                )
            }
        }
    }
}

@Composable
private fun LanguageListItem(
    language: Language,
    hasCustomSettings: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkmark indicator
                if (hasCustomSettings) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Has custom settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(20.dp))
                }
                
                Column {
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageDetailScreen(
    language: Language,
    onNavigateBack: () -> Unit,
    viewModel: LanguageInstructionsViewModel
) {
    val currentInstructions by viewModel.currentInstructions.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { 
                Column {
                    Text(language.displayName)
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions editor
            InstructionsEditorSection(
                language = language,
                instructions = currentInstructions,
                onInstructionsChange = viewModel::updateInstructions,
                onLoadDefault = viewModel::loadDefaultInstructions,
                onClear = viewModel::clearInstructions,
                hasDefaultInstructions = viewModel.getDefaultInstructions().isNotBlank()
            )
            
            // Placeholder for future per-language settings
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Additional Settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "More language-specific settings will be available here in the future.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionsEditorSection(
    language: Language,
    instructions: String,
    onInstructionsChange: (String) -> Unit,
    onLoadDefault: () -> Unit,
    onClear: () -> Unit,
    hasDefaultInstructions: Boolean
) {
    var textFieldValue by remember(language) {
        mutableStateOf(TextFieldValue(text = instructions, selection = TextRange(instructions.length)))
    }
    
    // Only update from external changes when switching languages
    LaunchedEffect(language, instructions) {
        if (textFieldValue.text != instructions) {
            textFieldValue = TextFieldValue(text = instructions, selection = TextRange(instructions.length))
        }
    }
    
    var showClearDialog by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Instructions for ${language.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Clear button (only show if there are instructions)
                    if (instructions.isNotBlank()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear instructions",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            Text(
                text = "These instructions will be added to your general Copilot instructions when translating ${language.displayName} words.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onInstructionsChange(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Language-specific instructions") },
                placeholder = { 
                    Text("E.g., \"Always include the article for nouns\" or \"Show plural forms\"") 
                },
                minLines = 5,
                maxLines = 10,
                supportingText = {
                    Text(
                        text = if (instructions.isBlank()) {
                            "No custom instructions set for this language"
                        } else {
                            "${instructions.length} characters"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
            
            // Load default button (only show if we have default instructions)
            if (hasDefaultInstructions) {
                HorizontalDivider()
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedButton(
                        onClick = onLoadDefault,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Default Instructions")
                    }
                    
                    Text(
                        text = "This will replace your current instructions with recommended defaults for ${language.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Instructions?") },
            text = { 
                Text("Are you sure you want to clear the instructions for ${language.displayName}? This action cannot be undone.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
