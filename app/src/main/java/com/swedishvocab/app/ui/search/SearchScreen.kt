@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.swedishvocab.app.R
import com.swedishvocab.app.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiKey by viewModel.deepLApiKey.collectAsState("")
    val nativeLanguage by viewModel.nativeLanguage.collectAsState(Language.GERMAN)
    val foreignLanguage by viewModel.foreignLanguage.collectAsState(Language.SWEDISH)
    
    // Focus management for keyboard dismissal
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
    
    // Handle card creation result
    val context = LocalContext.current
    uiState.cardCreationResult?.let { result ->
        LaunchedEffect(result) {
            if (result.isSuccess) {
                val successMessage = if (uiState.ankiImplementationType == "INTENT") {
                    "Opened AnkiDroid - please complete card creation"
                } else {
                    when {
                        uiState.cardsCreatedCount == 1 && uiState.lastCardDirection == CardDirection.BOTH_DIRECTIONS -> 
                            "Bidirectional card created successfully!"
                        uiState.cardsCreatedCount == 1 -> 
                            "Card created successfully!"
                        else -> 
                            "${uiState.cardsCreatedCount} cards created successfully!"
                    }
                }
                Toast.makeText(
                    context, 
                    successMessage, 
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val error = result.exceptionOrNull()
                val errorMessage = when {
                    error?.message?.contains("permission", ignoreCase = true) == true -> 
                        "AnkiDroid permission required. Please grant access in settings."
                    error?.message?.contains("not installed", ignoreCase = true) == true -> 
                        "AnkiDroid is not installed. Please install it from the Play Store."
                    error?.message?.contains("deck", ignoreCase = true) == true -> 
                        "Failed to create deck. Please check AnkiDroid settings."
                    else -> "Failed to create card: ${error?.message ?: "Unknown error"}"
                }
                Toast.makeText(
                    context, 
                    errorMessage, 
                    Toast.LENGTH_LONG
                ).show()
            }
            viewModel.clearCardCreationResult()
        }
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
            Text(
                text = "Glosdalen",
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        // Configuration Warning
        if (apiKey.isBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ Configuration Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Please configure your DeepL API key in Settings to use translation features.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Settings")
                    }
                }
            }
        }
        
        // Search Section
        key("search_section") {
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
                                contentDescription = "Change translation direction"
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
                    
                    // Search Input
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        label = { Text("Enter ${uiState.sourceLanguage.displayName} text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.updateSearchQuery("")
                                        focusRequester.requestFocus()
                                    }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.searchWord()
                                    }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchWord()
                            }
                        )
                    )
                    
                    // Context tip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Translation tip",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "💡 Add context (phrases, sentences) for more precise translations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
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
                                text = if (uiState.isContextExpanded) "Hide Context" else "Add Context for Better Translation",
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
                                placeholder = { Text("E.g., \"The bank of the river\" or \"I need to bank this money\"") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                maxLines = 3,
                                supportingText = {
                                    Text(
                                        text = "Provide context to help DeepL understand which meaning you want.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Search Button (shown when there's a query but no results)
        if (uiState.searchQuery.isNotEmpty() && !uiState.isLoading && uiState.translationResult == null && uiState.error == null) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.searchWord()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search \"${uiState.searchQuery}\"")
            }
        }
        
        // Results Section
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        uiState.error?.let { error ->
            ErrorCard(
                error = error,
                onRetry = viewModel::retrySearch,
                onDismiss = viewModel::clearError
            )
        }
        
        uiState.translationResult?.let { result ->
            TranslationCard(
                vocabularyEntry = result,
                isAnkiDroidAvailable = uiState.isAnkiDroidAvailable,
                isCreatingCard = uiState.isCreatingCard,
                selectedTranslation = uiState.selectedTranslation,
                hasCardBeenCreated = uiState.hasCardBeenCreated,
                selectedCardDirection = uiState.selectedCardDirection,
                ankiImplementationType = uiState.ankiImplementationType,
                onCreateCard = viewModel::createAnkiCard,
                onCardDirectionChange = viewModel::updateCardDirection,
                onTranslationClick = { translationText ->
                    // Set the translation as new search query and reverse language
                    viewModel.updateSearchQuery(translationText)
                    // Use the memoized target language calculation
                    viewModel.updateSourceLanguage(targetLanguage)
                    // Trigger new search
                    viewModel.searchWord()
                },
                onTranslationSelect = viewModel::selectTranslation
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: VocabularyError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (error) {
                    is VocabularyError.NetworkError -> "Network error"
                    is VocabularyError.InvalidApiKey -> "Invalid API key"
                    is VocabularyError.ApiLimitExceeded -> "API limit exceeded"
                    is VocabularyError.ApiError -> "API error"
                    is VocabularyError.UnsupportedLanguagePair -> "Unsupported language pair"
                    is VocabularyError.UnknownError -> "Unknown error"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = when (error) {
                    is VocabularyError.NetworkError -> "Check your internet connection"
                    is VocabularyError.InvalidApiKey -> "Please check your DeepL API key in settings"
                    is VocabularyError.ApiLimitExceeded -> "Try again later"
                    else -> "Please try again"
                },
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun CreateCardButtonWithDropdown(
    selectedCardDirection: CardDirection,
    ankiImplementationType: String,
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
    
    // Proper split button with two distinct clickable areas
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Main create card button (left side)
        Button(
            onClick = { onCreateCard() },
            modifier = Modifier.weight(1f),
            enabled = !isCreatingCard && isAnkiDroidAvailable && !hasCardBeenCreated,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isCreatingCard) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Creating...")
                }
            } else if (hasCardBeenCreated) {
                Text("Card Created ✓")
            } else {
                Text("Create Card ($cardDirectionText)")
            }
        }
        
        // Dropdown button (right side)
        Box {
            Button(
                onClick = { showDropdown = true },
                enabled = !isCreatingCard && isAnkiDroidAvailable && !hasCardBeenCreated,
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.widthIn(min = 48.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Choose card direction",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Dropdown menu
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                CardDirection.values().forEach { direction ->
                    val isEnabled = ankiImplementationType == "API" || direction != CardDirection.BOTH_DIRECTIONS
                    val displayName = when (direction) {
                        CardDirection.NATIVE_TO_FOREIGN -> "Native → Foreign"
                        CardDirection.FOREIGN_TO_NATIVE -> "Foreign → Native"
                        CardDirection.BOTH_DIRECTIONS -> "Both Directions"
                    }
                    val description = when (direction) {
                        CardDirection.NATIVE_TO_FOREIGN -> "German on front, Swedish on back"
                        CardDirection.FOREIGN_TO_NATIVE -> "Swedish on front, German on back"
                        CardDirection.BOTH_DIRECTIONS -> "Create cards in both directions"
                    }
                    
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = displayName,
                                    color = if (direction == selectedCardDirection) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (isEnabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = if (!isEnabled && direction == CardDirection.BOTH_DIRECTIONS) {
                                        "Requires AnkiDroid API"
                                    } else {
                                        description
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            if (isEnabled) {
                                onCardDirectionChange(direction)
                                showDropdown = false
                            }
                        },
                        enabled = isEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationCard(
    vocabularyEntry: VocabularyEntry,
    isAnkiDroidAvailable: Boolean,
    isCreatingCard: Boolean,
    selectedTranslation: String?,
    hasCardBeenCreated: Boolean,
    selectedCardDirection: CardDirection,
    ankiImplementationType: String,
    onCreateCard: () -> Unit,
    onCardDirectionChange: (CardDirection) -> Unit,
    onTranslationClick: (String) -> Unit,
    onTranslationSelect: (String) -> Unit
) {
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Translation",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Translations Section
            val translations = vocabularyEntry.translations
            if (translations.isNotEmpty()) {
                translations.forEachIndexed { index, translation ->
                    val isSelected = selectedTranslation == translation.text
                    val isDefault = selectedTranslation == null && index == 0
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onTranslationSelect(translation.text) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isDefault && selectedTranslation == null -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Translation number
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Translation text
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = translation.text,
                                    style = when {
                                        isSelected -> MaterialTheme.typography.titleMedium
                                        isDefault && selectedTranslation == null -> MaterialTheme.typography.titleMedium
                                        else -> MaterialTheme.typography.bodyLarge
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                if (isSelected) {
                                    Text(
                                        text = "Selected for Anki card",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isDefault && selectedTranslation == null) {
                                    Text(
                                        text = "Will be used for Anki card",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Action buttons
                            Column(horizontalAlignment = Alignment.End) {
                                // Reverse search button
                                TextButton(
                                    onClick = { onTranslationClick(translation.text) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Search this translation",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Search",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Create card button with direction dropdown
            CreateCardButtonWithDropdown(
                selectedCardDirection = selectedCardDirection,
                ankiImplementationType = ankiImplementationType,
                isCreatingCard = isCreatingCard,
                isAnkiDroidAvailable = isAnkiDroidAvailable,
                hasCardBeenCreated = hasCardBeenCreated,
                onCreateCard = onCreateCard,
                onCardDirectionChange = onCardDirectionChange
            )
            
            if (!isAnkiDroidAvailable) {
                Text(
                    text = "AnkiDroid not installed. Please install AnkiDroid to create cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
