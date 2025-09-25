@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import com.swedishvocab.app.BuildConfig
import com.swedishvocab.app.data.model.DeepLModelType
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
    val currentDeepLModelType by viewModel.currentDeepLModelType.collectAsState(DeepLModelType.QUALITY_OPTIMIZED)
    val currentEnableMultipleFormalities by viewModel.currentEnableMultipleFormalities.collectAsState(true)
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState(true)
    
    // Initialize with current settings
    LaunchedEffect(currentApiKey, currentNativeLanguage, currentForeignLanguage, currentDeepLModelType, currentEnableMultipleFormalities) {
        viewModel.initializeFromCurrentSettings(currentApiKey, currentNativeLanguage, currentForeignLanguage, currentDeepLModelType, currentEnableMultipleFormalities)
    }
    
    // Handle settings saved (only for first launch auto-navigation)
    LaunchedEffect(uiState.settingsSaved) {
        if (uiState.settingsSaved) {
            viewModel.clearSettingsSaved()
            // Only auto-navigate on first launch, manual navigation handles other cases
            if (isFirstLaunch) {
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
                    IconButton(
                        onClick = {
                            // Save settings first, then navigate back
                            viewModel.saveSettings()
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                    
                    // Clickable API key help link
                    val linkText = buildAnnotatedString {
                        append("Get your free API key at ")
                        
                        withLink(
                            LinkAnnotation.Url("https://www.deepl.com/pro-api")
                        ) {
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append("deepl.com/pro-api")
                            }
                        }
                    }
                    
                    Text(
                        text = linkText,
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
                    
                    // DeepL Model Type
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Translation Quality:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ModelTypeDropdown(
                            selectedModelType = uiState.deepLModelType,
                            onModelTypeSelected = viewModel::updateDeepLModelType,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Multiple Formalities Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multiple Translation Suggestions",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Query different formality levels for supported languages (slower but more options)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = uiState.enableMultipleFormalities,
                            onCheckedChange = viewModel::updateEnableMultipleFormalities
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // About section
            AboutSection()
            
            // Auto-save when API key is validated or on first launch when API key is provided
            if (isFirstLaunch && uiState.apiKeyValidated) {
                Button(
                    onClick = viewModel::saveSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Started")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelTypeDropdown(
    selectedModelType: DeepLModelType,
    onModelTypeSelected: (DeepLModelType) -> Unit,
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
            value = selectedModelType.displayName,
            onValueChange = { },
            label = { Text("Quality") },
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
            DeepLModelType.values().forEach { modelType ->
                DropdownMenuItem(
                    onClick = {
                        onModelTypeSelected(modelType)
                        expanded = false
                    },
                    text = { 
                        Column {
                            Text(
                                text = modelType.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = modelType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Version info
                val versionText = remember { "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Git hash
                val buildText = remember { "Build: ${BuildConfig.GIT_HASH}" }
                Text(
                    text = buildText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Build date
                val buildDateText = remember { "Built: ${BuildConfig.BUILD_DATE}" }
                Text(
                    text = buildDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Developed with ♥ by Erik and Daniel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
