@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.backend.anki.AnkiImplementationType
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.backend.deepl.SearchContext
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import com.glosdalen.app.ui.anki.AnkiSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnkiSettingsSection(
    nativeLanguage: Language,
    foreignLanguage: Language,
    templateResolver: DeckNameTemplateResolver
) {
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
                    
                    DeckNameFieldWithDropdown(
                        selectedDeckName = uiState.selectedDeckName,
                        availableDecks = uiState.availableDecks,
                        isLoadingDecks = uiState.isLoadingDecks,
                        onDeckNameChange = ankiViewModel::selectDeck,
                        onRefreshDecks = ankiViewModel::loadAvailableDecks,
                        nativeLanguage = nativeLanguage,
                        foreignLanguage = foreignLanguage,
                        templateResolver = templateResolver
                    )
                }
            } else {
                // Show permission hint if AnkiDroid is installed but API not available
                if (uiState.isAnkiAvailable && !uiState.isUsingApiImplementation) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enable Advanced Features",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            
                            Text(
                                text = "AnkiDroid is installed but API permissions aren't granted. To enable advanced features like deck selection and templates:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Text(
                                text = "1. Click on the button below for entering the app settings.\n2. Go to additional permissions.\n3. Enable access to the AnkiDroid database.\n4. Restart app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                            
                            val context = LocalContext.current
                            Button(
                                onClick = {
                                    // Open app settings
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open App Settings")
                            }
                        }
                    }
                } else {
                    // Intent mode limitations (when AnkiDroid not installed)
                    Text(
                        text = "Using basic intent integration. Install AnkiDroid for advanced features like deck and note model selection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckNameFieldWithDropdown(
    selectedDeckName: String,
    availableDecks: Map<Long, String>,
    isLoadingDecks: Boolean,
    onDeckNameChange: (String) -> Unit,
    onRefreshDecks: () -> Unit,
    nativeLanguage: Language,
    foreignLanguage: Language,
    templateResolver: DeckNameTemplateResolver
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showTemplateHelp by remember { mutableStateOf(false) }
    
    // Use TextFieldValue for proper cursor management
    var textFieldValue by remember { mutableStateOf(TextFieldValue(selectedDeckName, TextRange(selectedDeckName.length))) }
    
    // Track if we're currently typing to avoid interference
    var isUserTyping by remember { mutableStateOf(false) }
    
    // Only sync external changes when user is not actively typing
    LaunchedEffect(selectedDeckName) {
        if (!isUserTyping && textFieldValue.text != selectedDeckName) {
            textFieldValue = TextFieldValue(selectedDeckName, TextRange(selectedDeckName.length))
        }
    }
    
    // Create search context for template resolution  
    val searchContext = remember(nativeLanguage, foreignLanguage) {
        SearchContext(
            nativeLanguage = nativeLanguage, 
            foreignLanguage = foreignLanguage,
            sourceLanguage = foreignLanguage,
            targetLanguage = nativeLanguage,
            context = null
        )
    }
    
    // Resolve preview
    val resolvedDeckName by remember(textFieldValue.text, searchContext) {
        derivedStateOf {
            try {
                templateResolver.resolveDeckName(textFieldValue.text, searchContext)
            } catch (e: Exception) {
                textFieldValue.text
            }
        }
    }
    
    Column {
        // Main dropdown-style text field with proper cursor management
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                isUserTyping = true
                textFieldValue = newValue
                onDeckNameChange(newValue.text)
                // Reset typing flag after a short delay
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500) 
                    isUserTyping = false
                }
            },
            label = { Text("Deck Name") },
            placeholder = { Text("Type custom name or select existing deck") },
            supportingText = if (textFieldValue.text != resolvedDeckName) {
                { 
                    Text(
                        text = "Preview: $resolvedDeckName",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else null,
            trailingIcon = {
                // Only show dropdown arrow if we have decks available
                if (availableDecks.isNotEmpty()) {
                    IconButton(
                        onClick = { showDropdown = !showDropdown }
                    ) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = if (showDropdown) "Hide available decks" else "Show available decks"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Available decks dropdown
        if (showDropdown && availableDecks.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available Anki Decks (${availableDecks.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (isLoadingDecks) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            TextButton(
                                onClick = onRefreshDecks
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh decks",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Refresh",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(availableDecks.values.toList().sorted()) { deckName ->
                            TextButton(
                                onClick = {
                                    textFieldValue = TextFieldValue(deckName, TextRange(deckName.length))
                                    onDeckNameChange(deckName)
                                    showDropdown = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = deckName,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                )
                            }
                            
                            if (deckName != availableDecks.values.sorted().last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Template help section
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(
            onClick = { showTemplateHelp = !showTemplateHelp },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (showTemplateHelp) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (showTemplateHelp) "Hide template help" else "Show template help"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Template Variables ${if (showTemplateHelp) "(Hide)" else "(Show Help)"}")
        }
        
        if (showTemplateHelp) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Click any variable to insert it at your cursor position:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Show template variables (simplified version)
                    val templates = templateResolver.getAvailableTemplates()
                    val sampleContext = SearchContext(
                        nativeLanguage = nativeLanguage,
                        foreignLanguage = foreignLanguage,
                        sourceLanguage = foreignLanguage,
                        targetLanguage = nativeLanguage,
                        context = null
                    )
                    
                    templates.forEach { templateInfo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    // Insert template at cursor position
                                    val currentSelection = textFieldValue.selection
                                    val currentText = textFieldValue.text
                                    
                                    val beforeCursor = currentText.substring(0, currentSelection.start)
                                    val afterCursor = currentText.substring(currentSelection.end)
                                    val newText = beforeCursor + templateInfo.template + afterCursor
                                    
                                    // Set cursor position after the inserted template
                                    val newCursorPosition = currentSelection.start + templateInfo.template.length
                                    val newTextFieldValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursorPosition)
                                    )
                                    
                                    textFieldValue = newTextFieldValue
                                    onDeckNameChange(newText)
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = templateInfo.template,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = templateInfo.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = try {
                                    templateResolver.resolveDeckName(templateInfo.template, sampleContext)
                                } catch (e: Exception) {
                                    templateInfo.example
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        if (templateInfo != templates.last()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
        
        // Show hint when no decks available
        if (availableDecks.isEmpty() && !isLoadingDecks) {
            Text(
                text = "No decks found. Create decks in AnkiDroid first, then refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
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