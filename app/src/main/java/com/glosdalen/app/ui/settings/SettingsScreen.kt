@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
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
import com.glosdalen.app.BuildConfig
import com.glosdalen.app.backend.anki.AnkiImplementationType
import com.glosdalen.app.backend.anki.CardDirection
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.backend.deepl.SearchContext
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import com.glosdalen.app.ui.anki.AnkiSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState("")
    val currentNativeLanguage by viewModel.currentNativeLanguage.collectAsState(Language.GERMAN)
    val currentForeignLanguage by viewModel.currentForeignLanguage.collectAsState(Language.ENGLISH)
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
            
            // DeepL API Configuration Section
            DeepLSettingsSection(
                uiState = uiState,
                onApiKeyChange = viewModel::updateApiKey,
                onValidateApiKey = viewModel::validateAndSaveApiKey,
                onModelTypeChange = viewModel::updateDeepLModelType,
                onEnableMultipleFormalitiesChange = viewModel::updateEnableMultipleFormalities
            )
            
            // Language Configuration Section
            LanguageConfigurationSection(
                selectedLanguage = uiState.nativeLanguage,
                onLanguageSelected = viewModel::updateNativeLanguage
            )
            
            // AnkiDroid Integration
            AnkiSettingsSection(
                nativeLanguage = currentNativeLanguage,
                foreignLanguage = currentForeignLanguage,
                templateResolver = viewModel.templateResolver
            )
            
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

@Composable
fun LanguageConfigurationSection(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
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
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    modifier = Modifier.fillMaxWidth()
                )
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
            Language.values().sortedBy { it.displayName }.forEach { language ->
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
