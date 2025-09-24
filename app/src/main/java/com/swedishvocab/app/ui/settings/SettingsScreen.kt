@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.swedishvocab.app.data.model.CardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState("")
    val currentDeckName by viewModel.currentDeckName.collectAsState("")
    val currentCardType by viewModel.currentCardType.collectAsState(CardType.UNIDIRECTIONAL)
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState(true)
    
    // Initialize with current settings
    LaunchedEffect(currentApiKey, currentDeckName, currentCardType) {
        viewModel.initializeFromCurrentSettings(currentApiKey, currentDeckName, currentCardType)
    }
    
    // Handle settings saved
    LaunchedEffect(uiState.settingsSaved) {
        if (uiState.settingsSaved) {
            viewModel.clearSettingsSaved()
            if (!isFirstLaunch) {
                onNavigateBack()
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                if (!isFirstLaunch) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFirstLaunch) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Welcome!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Please configure your settings to get started.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // API Key Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DeepL API Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("DeepL API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = uiState.apiKeyError != null,
                        supportingText = {
                            uiState.apiKeyError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        trailingIcon = {
                            if (uiState.apiKeyValidated) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Validated",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    
                    Button(
                        onClick = viewModel::validateAndSaveApiKey,
                        enabled = !uiState.isValidatingApiKey && uiState.apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isValidatingApiKey) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Validating...")
                            }
                        } else {
                            Text("Validate API Key")
                        }
                    }
                    
                    Text(
                        text = "Get your free API key at deepl.com/pro-api",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Anki Configuration
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Anki Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = uiState.deckName,
                        onValueChange = viewModel::updateDeckName,
                        label = { Text("Default Deck Name") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("German::Swedish") }
                    )
                    
                    Text("Default Card Type:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.cardType == CardType.UNIDIRECTIONAL,
                            onClick = { viewModel.updateCardType(CardType.UNIDIRECTIONAL) },
                            label = { Text("Unidirectional") }
                        )
                        FilterChip(
                            selected = uiState.cardType == CardType.BIDIRECTIONAL,
                            onClick = { viewModel.updateCardType(CardType.BIDIRECTIONAL) },
                            label = { Text("Bidirectional") }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Save Button
            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.apiKeyValidated && uiState.deckName.isNotBlank()
            ) {
                Text(if (isFirstLaunch) "Get Started" else "Save Settings")
            }
        }
    }
}
