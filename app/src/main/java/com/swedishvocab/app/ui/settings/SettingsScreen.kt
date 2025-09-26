@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.settings

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
import com.swedishvocab.app.data.model.CardDirection
import com.swedishvocab.app.data.repository.AnkiImplementationType
import com.swedishvocab.app.domain.preferences.AnkiMethodPreference
import com.swedishvocab.app.ui.anki.AnkiSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState("")
    val currentNativeLanguage by viewModel.currentNativeLanguage.collectAsState(Language.GERMAN)
    val currentDeepLModelType by viewModel.currentDeepLModelType.collectAsState(DeepLModelType.QUALITY_OPTIMIZED)
    val currentEnableMultipleFormalities by viewModel.currentEnableMultipleFormalities.collectAsState(true)
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState(true)
    
    // Initialize with current settings
    LaunchedEffect(currentApiKey, currentNativeLanguage, currentDeepLModelType, currentEnableMultipleFormalities) {
        viewModel.initializeFromCurrentSettings(currentApiKey, currentNativeLanguage, Language.SWEDISH, currentDeepLModelType, currentEnableMultipleFormalities)
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
            
            // AnkiDroid Integration
            AnkiSettingsSection()
            
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

@Composable
private fun AnkiSettingsSection() {
    val ankiViewModel: AnkiSettingsViewModel = hiltViewModel()
    val uiState by ankiViewModel.uiState.collectAsState()

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AnkiDroid Integration",
                style = MaterialTheme.typography.titleMedium
            )

            // Method Selection
            if (uiState.bothMethodsAvailable) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Integration Method:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AnkiMethodDropdown(
                        selectedMethod = uiState.selectedMethod,
                        availableMethods = uiState.availableMethods,
                        onMethodSelected = ankiViewModel::selectMethod,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Show current status when only one method is available
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusIcon = if (uiState.isUsingApiImplementation) 
                        Icons.Default.Check else Icons.Default.Settings
                    val statusColor = if (uiState.isUsingApiImplementation)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                    
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (uiState.isUsingApiImplementation) "API Mode" else "Intent Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (uiState.isUsingApiImplementation)
                                "Full features available"
                            else
                                "Basic card creation only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Deck Selection (only if API available)
            if (uiState.isUsingApiImplementation) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Anki Deck:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.isLoadingDecks) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    
                    DeckNameField(
                        selectedDeckName = uiState.selectedDeckName,
                        availableDecks = uiState.availableDecks,
                        isLoadingDecks = uiState.isLoadingDecks,
                        isEnabled = uiState.isUsingApiImplementation,
                        onDeckNameChange = ankiViewModel::selectDeck,
                        onRefreshDecks = ankiViewModel::loadAvailableDecks
                    )
                }

                // Card Direction
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Card Direction:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    CardDirection.values().forEach { direction ->
                        val displayName = when (direction) {
                            CardDirection.NATIVE_TO_FOREIGN -> "Native → Foreign"
                            CardDirection.FOREIGN_TO_NATIVE -> "Foreign → Native"
                            CardDirection.BOTH_DIRECTIONS -> "Both Directions"
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ankiViewModel.selectDirection(direction) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedDirection == direction,
                                onClick = { ankiViewModel.selectDirection(direction) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                // Intent mode limitations
                Text(
                    text = "Using basic intent integration. Install AnkiDroid API for advanced features like deck and note model selection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnkiMethodDropdown(
    selectedMethod: AnkiMethodPreference,
    availableMethods: List<AnkiImplementationType>,
    onMethodSelected: (AnkiMethodPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = getMethodDisplayName(selectedMethod, availableMethods),
            onValueChange = { },
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // AUTO option
            DropdownMenuItem(
                text = { Text("Automatic (recommended)") },
                onClick = {
                    onMethodSelected(AnkiMethodPreference.AUTO)
                    expanded = false
                }
            )
            
            // API option (if available)
            if (availableMethods.contains(AnkiImplementationType.API)) {
                DropdownMenuItem(
                    text = { Text("AnkiDroid API") },
                    onClick = {
                        onMethodSelected(AnkiMethodPreference.API)
                        expanded = false
                    }
                )
            }
            
            // Intent option (if available)
            if (availableMethods.contains(AnkiImplementationType.INTENT)) {
                DropdownMenuItem(
                    text = { Text("Intent Method") },
                    onClick = {
                        onMethodSelected(AnkiMethodPreference.INTENT)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getMethodDisplayName(
    selectedMethod: AnkiMethodPreference, 
    availableMethods: List<AnkiImplementationType>
): String {
    return when (selectedMethod) {
        AnkiMethodPreference.AUTO -> "Automatic (recommended)"
        AnkiMethodPreference.API -> {
            if (availableMethods.contains(AnkiImplementationType.API)) {
                "AnkiDroid API"
            } else {
                "AnkiDroid API (unavailable)"
            }
        }
        AnkiMethodPreference.INTENT -> {
            if (availableMethods.contains(AnkiImplementationType.INTENT)) {
                "Intent Method"  
            } else {
                "Intent Method (unavailable)"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckNameField(
    selectedDeckName: String,
    availableDecks: Map<Long, String>,
    isLoadingDecks: Boolean,
    isEnabled: Boolean,
    onDeckNameChange: (String) -> Unit,
    onRefreshDecks: () -> Unit
) {
    // Use local state for text editing to prevent cursor jumping
    var localDeckName by remember(selectedDeckName) { mutableStateOf(selectedDeckName) }
    var showSuggestions by remember { mutableStateOf(false) }
    
    // Update local state when external state changes (e.g., from loading available decks)
    LaunchedEffect(selectedDeckName) {
        if (localDeckName != selectedDeckName) {
            localDeckName = selectedDeckName
        }
    }
    
    Column {
        OutlinedTextField(
            value = localDeckName,
            onValueChange = { newValue ->
                localDeckName = newValue
                onDeckNameChange(newValue)
                showSuggestions = newValue.isNotEmpty() && availableDecks.isNotEmpty()
            },
            label = { Text("Deck Name") },
            placeholder = { Text("Enter deck name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            supportingText = if (!isEnabled) {
                { Text(
                    text = "Deck selection requires AnkiDroid API. Cards will be created in the default deck.",
                    style = MaterialTheme.typography.bodySmall
                ) }
            } else if (availableDecks.isNotEmpty()) {
                { Text(
                    text = "Tip: Click refresh to load available decks from AnkiDroid, or type a custom deck name.",
                    style = MaterialTheme.typography.bodySmall
                ) }
            } else {
                { Text(
                    text = "Enter a deck name or click refresh to load available decks from AnkiDroid.",
                    style = MaterialTheme.typography.bodySmall
                ) }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (availableDecks.isNotEmpty() && isEnabled) {
                        IconButton(
                            onClick = { showSuggestions = !showSuggestions }
                        ) {
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                contentDescription = "Show available decks"
                            )
                        }
                    }
                    if (isLoadingDecks) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefreshDecks) {
                            Icon(
                                Icons.Default.Refresh, 
                                contentDescription = "Load available decks from AnkiDroid"
                            )
                        }
                    }
                }
            }
        )
        
        // Show available decks as suggestions
        if (showSuggestions && availableDecks.isNotEmpty() && isEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Available decks (${availableDecks.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    // Scrollable list of all decks
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp) // Limit height to make it scrollable
                    ) {
                        items(availableDecks.values.toList()) { deckName ->
                            TextButton(
                                onClick = {
                                    localDeckName = deckName
                                    onDeckNameChange(deckName)
                                    showSuggestions = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = deckName,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
