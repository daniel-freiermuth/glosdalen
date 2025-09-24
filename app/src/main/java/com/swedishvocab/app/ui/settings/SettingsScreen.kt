@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.swedishvocab.app.data.model.Language

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState("")
    val currentNativeLanguage by viewModel.currentNativeLanguage.collectAsState(Language.GERMAN)
    val currentForeignLanguage by viewModel.currentForeignLanguage.collectAsState(Language.SWEDISH)
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState(true)
    
    // Initialize with current settings
    LaunchedEffect(currentApiKey, currentNativeLanguage, currentForeignLanguage) {
        viewModel.initializeFromCurrentSettings(currentApiKey, currentNativeLanguage, currentForeignLanguage)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            
            // Language Configuration
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Language Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Native Language
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Native Language:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LanguageDropdown(
                            selectedLanguage = uiState.nativeLanguage,
                            onLanguageSelected = viewModel::updateNativeLanguage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Foreign Language
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Foreign Language:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LanguageDropdown(
                            selectedLanguage = uiState.foreignLanguage,
                            onLanguageSelected = viewModel::updateForeignLanguage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Save Button
            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.apiKeyValidated
            ) {
                Text(if (isFirstLaunch) "Get Started" else "Save Settings")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLanguage.displayName,
            onValueChange = { },
            label = { Text("Language") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Language.values().forEach { language ->
                DropdownMenuItem(
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    text = { Text(language.displayName) }
                )
            }
        }
    }
}
