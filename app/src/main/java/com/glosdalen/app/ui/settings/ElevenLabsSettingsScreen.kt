@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.backend.elevenlabs.Voice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevenLabsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ElevenLabsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    BackHandler {
        onNavigateBack()
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("ElevenLabs TTS") },
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
            // Info Card
            InfoCard()
            
            // API Key Section
            ApiKeySection(
                apiKey = uiState.apiKey,
                isValidating = uiState.isValidatingApiKey,
                isValidated = uiState.apiKeyValidated,
                isConfigured = uiState.isConfigured,
                error = uiState.apiKeyError,
                subscriptionTier = uiState.subscriptionTier,
                characterUsage = uiState.characterUsage,
                onApiKeyChange = viewModel::updateApiKey,
                onValidate = viewModel::validateAndSaveApiKey,
                onClear = viewModel::clearApiKey
            )
            
            // Voice Selection Section (only if configured)
            if (uiState.isConfigured) {
                VoiceSelectionSection(
                    voices = uiState.voices,
                    selectedVoiceId = uiState.selectedVoiceId,
                    selectedVoiceName = uiState.selectedVoiceName,
                    isLoading = uiState.isLoadingVoices,
                    error = uiState.voicesError,
                    onVoiceSelect = viewModel::selectVoice,
                    onRefresh = viewModel::refreshVoices
                )
                
                // Model Selection Section
                ModelSelectionSection(
                    models = uiState.models,
                    selectedModelId = uiState.selectedModelId,
                    onModelSelect = viewModel::selectModel
                )
                
                // Test Voice Section
                TestVoiceSection(
                    isTesting = uiState.isTestingVoice,
                    error = uiState.testError,
                    onTest = viewModel::testVoice,
                    onStop = viewModel::stopTestVoice
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Text-to-Speech",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "ElevenLabs provides high-quality text-to-speech in multiple languages. " +
                        "Configure your API key to hear pronunciation of translated words.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Get your free API key at elevenlabs.io",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ApiKeySection(
    apiKey: String,
    isValidating: Boolean,
    isValidated: Boolean,
    isConfigured: Boolean,
    error: String?,
    subscriptionTier: String?,
    characterUsage: String?,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onClear: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "API Configuration",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("ElevenLabs API Key") },
                placeholder = { Text("Enter your API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                            )
                        }
                    }
                },
                isError = error != null,
                supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onValidate,
                    enabled = apiKey.isNotBlank() && !isValidating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Validating...")
                    } else if (isValidated || isConfigured) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Validated")
                    } else {
                        Text("Validate & Save")
                    }
                }
                
                if (isConfigured) {
                    OutlinedButton(
                        onClick = onClear,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
            
            // Show subscription info if available
            if (subscriptionTier != null || characterUsage != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    subscriptionTier?.let {
                        Text(
                            text = "Plan: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    characterUsage?.let {
                        Text(
                            text = "Characters: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSelectionSection(
    voices: List<Voice>,
    selectedVoiceId: String,
    selectedVoiceName: String,
    isLoading: Boolean,
    error: String?,
    onVoiceSelect: (Voice) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.titleMedium
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh voices")
                    }
                }
            }
            
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedVoiceName,
                    onValueChange = { },
                    label = { Text("Select Voice") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = voice.displayName(),
                                        fontWeight = if (voice.voiceId == selectedVoiceId) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                    voice.labels?.get("accent")?.let { accent ->
                                        Text(
                                            text = accent,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onVoiceSelect(voice)
                                expanded = false
                            },
                            leadingIcon = if (voice.voiceId == selectedVoiceId) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionSection(
    models: List<com.glosdalen.app.backend.elevenlabs.ElevenLabsModel>,
    selectedModelId: String,
    onModelSelect: (com.glosdalen.app.backend.elevenlabs.ElevenLabsModel) -> Unit
) {
    if (models.isEmpty()) return
    
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.modelId == selectedModelId }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium
            )
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedModel?.name ?: selectedModelId,
                    onValueChange = { },
                    label = { Text("Select Model") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = model.name,
                                        fontWeight = if (model.modelId == selectedModelId) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                    model.description?.let { desc ->
                                        Text(
                                            text = desc.take(60) + if (desc.length > 60) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onModelSelect(model)
                                expanded = false
                            },
                            leadingIcon = if (model.modelId == selectedModelId) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
            
            Text(
                text = "The multilingual v2 model is recommended for non-English languages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TestVoiceSection(
    isTesting: Boolean,
    error: String?,
    onTest: () -> Unit,
    onStop: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Test Voice",
                style = MaterialTheme.typography.titleMedium
            )
            
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Button(
                onClick = if (isTesting) onStop else onTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Voice")
                }
            }
        }
    }
}
