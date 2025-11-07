@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.copilot_chat

import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.glosdalen.app.ui.components.SplitButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.R
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

import com.glosdalen.app.backend.deepl.Language

@Composable
fun CopilotChatSearchScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CopilotChatViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    val nativeLanguage by viewModel.nativeLanguage.collectAsState(Language.GERMAN)
    val foreignLanguage by viewModel.foreignLanguage.collectAsState(Language.SWEDISH)
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    
    // Memoize target language calculation to prevent unnecessary recompositions
    val targetLanguage = remember(uiState.sourceLanguage, nativeLanguage, foreignLanguage) {
        when (uiState.sourceLanguage) {
            nativeLanguage -> foreignLanguage
            foreignLanguage -> nativeLanguage
            else -> foreignLanguage
        }
    }
    
    // Refresh language state when languages change (e.g., returning from settings)
    LaunchedEffect(nativeLanguage, foreignLanguage) {
        viewModel.refreshLanguageState()
    }
    
    // Show intro dialog if needed
    if (uiState.showIntroDialog) {
        CopilotIntroDialog(
            onDismiss = { showAgain ->
                viewModel.dismissIntroDialog(showAgain)
            },
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
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Menu button for drawer
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Open menu")
                }
                
                // Logo + App Name
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Glosdalen Logo",
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Copilot Chat",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        // Info Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isAuthenticated) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isAuthenticated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Connected to Copilot",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "Ask questions about vocabulary, grammar, or get translations with examples and context.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
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
        
        // Query Section with Language Chooser
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Native Language (left, fixed)
                    Text(
                        text = nativeLanguage.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Direction Arrow (center)
                    IconButton(
                        onClick = {
                            viewModel.updateSourceLanguage(targetLanguage)
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.sourceLanguage == nativeLanguage) {
                                Icons.AutoMirrored.Filled.ArrowForward
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = "Change direction"
                        )
                    }
                    
                    // Foreign Language (right, clickable dropdown)
                    ForeignLanguageDropdown(
                        currentLanguage = foreignLanguage,
                        availableLanguages = Language.values().filter { it != nativeLanguage }.sortedBy { it.displayName },
                        onLanguageSelect = { language ->
                            viewModel.updateForeignLanguage(language)
                        }
                    )
                }
                
                // Query Input
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    label = { Text("Enter ${uiState.sourceLanguage.displayName} text") },
                    placeholder = { Text("Type your question here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !uiState.isLoading,
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.updateQuery("")
                                    focusRequester.requestFocus()
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.sendQuery()
                                }
                            ) {
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
                            viewModel.sendQuery()
                        }
                    )
                )
                
                // Context Input Section
                Column {
                    // Context Toggle Button
                    TextButton(
                        onClick = { viewModel.toggleContextExpanded() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isContextExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (uiState.isContextExpanded) "Delete context" else "Show context",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isContextExpanded) "Delete Context" else "Add Context for Better Response",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    // Context Input Field (shown when expanded)
                    AnimatedVisibility(
                        visible = uiState.isContextExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = uiState.contextQuery,
                            onValueChange = viewModel::updateContextQuery,
                            label = { Text("Context (optional)") },
                            placeholder = { Text("E.g., \"Technical documentation\" or \"Casual conversation\"") },
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
        
        // Send Button with Model Selection
        if (uiState.query.isNotEmpty() && !uiState.isLoading && uiState.error == null) {
            // Prepare model dropdown items
            val modelItems = buildList {
                add(com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL)
                addAll(uiState.availableModels.map { it.id })
            }
            
            // Get display name for selected model
            val selectedModelDisplay = if (uiState.selectedModelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                "Auto"
            } else {
                uiState.availableModels.find { it.id == uiState.selectedModelId }?.getDisplayName() ?: uiState.selectedModelId
            }
            
            SplitButton(
                mainButtonContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text((if (uiState.response.isEmpty()) "Send" else "Re-query") +  " ($selectedModelDisplay)")
                },
                dropdownItems = modelItems,
                selectedItem = uiState.selectedModelId,
                enabled = true,
                onMainClick = {
                    focusManager.clearFocus()
                    viewModel.sendQuery()
                },
                onItemSelect = { modelId ->
                    viewModel.selectModel(modelId)
                },
                itemLabel = { modelId ->
                    if (modelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                        "Auto (Recommended)"
                    } else {
                        uiState.availableModels.find { it.id == modelId }?.getDisplayName() ?: modelId
                    }
                },
                dropdownButtonContentDescription = "Select AI model",
                itemContent = { modelId ->
                    Column {
                        val displayName = if (modelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                            "Auto (Recommended)"
                        } else {
                            uiState.availableModels.find { it.id == modelId }?.getDisplayName() ?: modelId
                        }
                        
                        Text(
                            text = displayName,
                            color = if (modelId == uiState.selectedModelId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        // Show additional info for specific models
                        if (modelId != com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                            uiState.availableModels.find { it.id == modelId }?.let { model ->
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
        
        // Loading Indicator with Cancel Button
        if (uiState.isLoading) {
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
                        onClick = { viewModel.cancelQuery() },
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
        
        // Error Display
        uiState.error?.let { error ->
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
                    val modelItems = buildList {
                        add(com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL)
                        addAll(uiState.availableModels.map { it.id })
                    }
                    
                    val selectedModelDisplay = if (uiState.selectedModelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                        "Auto"
                    } else {
                        uiState.availableModels.find { it.id == uiState.selectedModelId }?.getDisplayName() ?: uiState.selectedModelId
                    }
                    
                    SplitButton(
                        onMainClick = {
                            focusManager.clearFocus()
                            viewModel.sendQuery()
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
                        dropdownItems = modelItems,
                        selectedItem = uiState.selectedModelId,
                        enabled = true,
                        onItemSelect = { modelId ->
                            viewModel.selectModel(modelId)
                        },
                        itemLabel = { modelId ->
                            if (modelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                                "Auto (Recommended)"
                            } else {
                                uiState.availableModels.find { it.id == modelId }?.getDisplayName() ?: modelId
                            }
                        },
                        dropdownButtonContentDescription = "Select AI model",
                        itemContent = { modelId ->
                            val displayName = if (modelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                                "Auto (Recommended)"
                            } else {
                                uiState.availableModels.find { it.id == modelId }?.getDisplayName() ?: modelId
                            }
                            
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                    )
                }
            }
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
                        Text(
                            text = "Proposed Flashcards (${parsed.cards.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
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
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAdditionalInfo() }
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
                                imageVector = if (uiState.isAdditionalInfoExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (uiState.isAdditionalInfoExpanded) "Collapse" else "Expand"
                            )
                        }
                        
                        AnimatedVisibility(visible = uiState.isAdditionalInfoExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = parsed.additionalInfo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Copy All and Clear buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val fullText = buildString {
                            if (parsed.directAnswer.isNotBlank()) {
                                appendLine("ANSWER:")
                                appendLine(parsed.directAnswer)
                                appendLine()
                            }
                            if (parsed.cards.isNotEmpty()) {
                                appendLine("FLASHCARDS:")
                                parsed.cards.forEachIndexed { index, card ->
                                    appendLine("${index + 1}.")
                                    appendLine("Front: ${card.frontSide}")
                                    appendLine("Back: ${card.backSide}")
                                    if (card.note.isNotBlank()) {
                                        appendLine("Note: ${card.note}")
                                    }
                                    appendLine()
                                }
                            }
                            if (parsed.additionalInfo.isNotBlank()) {
                                appendLine("ADDITIONAL INFORMATION:")
                                appendLine(parsed.additionalInfo)
                            }
                        }
                        clipboardManager.setText(AnnotatedString(fullText))
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
                    onClick = { viewModel.clearResponse() },
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
    }
}

@Composable
private fun ForeignLanguageDropdown(
    currentLanguage: Language,
    availableLanguages: List<Language>,
    onLanguageSelect: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentLanguage.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select language",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.displayName,
                            color = if (language == currentLanguage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onLanguageSelect(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CreateCardButton(
    selectedCardDirection: CopilotCardDirection,
    isCreatingCard: Boolean,
    isAnkiDroidAvailable: Boolean,
    hasCardBeenCreated: Boolean,
    onCreateCard: () -> Unit,
    onCardDirectionChange: (CopilotCardDirection) -> Unit
) {
    val cardDirectionText = when (selectedCardDirection) {
        CopilotCardDirection.FRONT_TO_BACK -> "Front → Back"
        CopilotCardDirection.BACK_TO_FRONT -> "Back → Front"
        CopilotCardDirection.BOTH_DIRECTIONS -> "Both Directions"
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
            dropdownItems = CopilotCardDirection.values().toList(),
            selectedItem = selectedCardDirection,
            enabled = !isCreatingCard && isAnkiDroidAvailable && !hasCardBeenCreated,
            onMainClick = onCreateCard,
            onItemSelect = onCardDirectionChange,
            itemLabel = { direction ->
                when (direction) {
                    CopilotCardDirection.FRONT_TO_BACK -> "Front → Back"
                    CopilotCardDirection.BACK_TO_FRONT -> "Back → Front"
                    CopilotCardDirection.BOTH_DIRECTIONS -> "Both Directions"
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

@Composable
private fun CopilotIntroDialog(
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
            Text("Welcome to Copilot Chat")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Copilot Chat uses AI to help you create flashcards with natural language queries.",
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
                    Text(
                        text = "• Ask for translations, definitions, examples, and more",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Provide additional context to customize responses",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Configure AI model, instructions, and settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        text = "AI-generated content may contain errors. Always verify important translations and information.",
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
