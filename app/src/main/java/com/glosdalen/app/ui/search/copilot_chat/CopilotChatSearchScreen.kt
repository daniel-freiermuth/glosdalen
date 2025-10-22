@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.search.copilot_chat

import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.R
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.glosdalen.app.backend.anki.CardDirection
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
                            contentDescription = if (uiState.isContextExpanded) "Hide context" else "Show context",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isContextExpanded) "Hide Context" else "Add Context for Better Response",
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
        
        // Send Button (shown when there's a query but no response/error)
        if (uiState.query.isNotEmpty() && !uiState.isLoading && uiState.response.isEmpty() && uiState.error == null) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.sendQuery()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send \"${uiState.query}\"")
            }
        }
        
        // Loading Indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
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
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parsed.directAnswer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Flashcard Section (if both sides are present)
            if (parsed.frontSide.isNotBlank() && parsed.backSide.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Proposed Flashcard",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
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
                                Text(
                                    text = parsed.frontSide,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
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
                                Text(
                                    text = parsed.backSide,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        // Create Card Button
                        CreateCardButton(
                            selectedCardDirection = uiState.selectedCardDirection,
                            isCreatingCard = uiState.isCreatingCard,
                            isAnkiDroidAvailable = uiState.isAnkiDroidAvailable,
                            hasCardBeenCreated = uiState.hasCardBeenCreated,
                            onCreateCard = viewModel::createAnkiCard,
                            onCardDirectionChange = viewModel::updateCardDirection
                        )
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
                                Text(
                                    text = parsed.additionalInfo,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            
            // Clear button
            OutlinedButton(
                onClick = { viewModel.clearResponse() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Response")
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
    selectedCardDirection: CardDirection,
    isCreatingCard: Boolean,
    isAnkiDroidAvailable: Boolean,
    hasCardBeenCreated: Boolean,
    onCreateCard: () -> Unit,
    onCardDirectionChange: (CardDirection) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    
    val cardDirectionText = when (selectedCardDirection) {
        CardDirection.NATIVE_TO_FOREIGN -> "Native → Foreign"
        CardDirection.FOREIGN_TO_NATIVE -> "Foreign → Native"
        CardDirection.BOTH_DIRECTIONS -> "Both Directions"
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Main create card button
        Button(
            onClick = { onCreateCard() },
            modifier = Modifier.weight(1f),
            enabled = !isCreatingCard && isAnkiDroidAvailable && !hasCardBeenCreated,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        ) {
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
        }
        
        // Dropdown button
        Box {
            Button(
                onClick = { showDropdown = true },
                enabled = !isCreatingCard && !hasCardBeenCreated,
                modifier = Modifier.width(48.dp),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Card direction options"
                )
            }
            
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                CardDirection.values().forEach { direction ->
                    DropdownMenuItem(
                        text = {
                            val text = when (direction) {
                                CardDirection.NATIVE_TO_FOREIGN -> "Native → Foreign"
                                CardDirection.FOREIGN_TO_NATIVE -> "Foreign → Native"
                                CardDirection.BOTH_DIRECTIONS -> "Both Directions"
                            }
                            Text(
                                text = text,
                                color = if (direction == selectedCardDirection) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = {
                            onCardDirectionChange(direction)
                            showDropdown = false
                        }
                    )
                }
            }
        }
    }
    
    if (!isAnkiDroidAvailable) {
        Text(
            text = "⚠️ AnkiDroid not available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
