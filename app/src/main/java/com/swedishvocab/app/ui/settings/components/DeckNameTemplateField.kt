package com.swedishvocab.app.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.swedishvocab.app.data.model.Language
import com.swedishvocab.app.data.model.SearchContext
import com.swedishvocab.app.domain.template.DeckNameTemplateResolver

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun DeckNameTemplateField(
    value: String,
    onValueChange: (String) -> Unit,
    nativeLanguage: Language,
    foreignLanguage: Language,
    templateResolver: DeckNameTemplateResolver,
    modifier: Modifier = Modifier
) {
    var showTemplateHelp by remember { mutableStateOf(false) }
    
    // Use TextFieldValue for cursor position support, but minimize external interference
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    
    // Track if we're currently typing to avoid interference
    var isUserTyping by remember { mutableStateOf(false) }
    
    // Only sync external changes when user is not actively typing
    LaunchedEffect(value) {
        if (!isUserTyping && textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    
    // Separate state for preview to avoid affecting text input
    var resolvedDeckName by remember { mutableStateOf("") }
    
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
    
    // Update preview automatically with debouncing
    LaunchedEffect(textFieldValue.text) {
        snapshotFlow { textFieldValue.text }
            .debounce(300)
            .collectLatest { debouncedValue ->
                val newPreview = templateResolver.resolveDeckName(
                    templateString = debouncedValue,
                    searchContext = searchContext
                )
                resolvedDeckName = newPreview
            }
    }
    
    // Function to insert template (simplified - appends to end for now)
    fun insertTemplate(template: String) {
        val currentSelection = textFieldValue.selection
        val currentText = textFieldValue.text
        
        // Insert template at cursor position
        val beforeCursor = currentText.substring(0, currentSelection.start)
        val afterCursor = currentText.substring(currentSelection.end)
        val newText = beforeCursor + template + afterCursor
        
        // Set cursor position after the inserted template
        val newCursorPosition = currentSelection.start + template.length
        val newTextFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPosition)
        )
        
        textFieldValue = newTextFieldValue
        onValueChange(newText)
    }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                isUserTyping = true
                textFieldValue = newValue
                onValueChange(newValue.text)
                // Reset typing flag after a short delay
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500) 
                    isUserTyping = false
                }
            },
            label = { Text("Deck Name Template") },
            placeholder = { Text("e.g., Vocabulary::{foreign_local}") },
            supportingText = {
                Text(
                    text = "Preview: $resolvedDeckName",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                TextButton(
                    onClick = { showTemplateHelp = !showTemplateHelp }
                ) {
                    Text(if (showTemplateHelp) "Hide Help" else "Show Help")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (showTemplateHelp) {
            TemplateHelpCard(
                templateResolver = templateResolver,
                nativeLanguage = nativeLanguage,
                foreignLanguage = foreignLanguage,
                onTemplateClick = ::insertTemplate,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun TemplateHelpCard(
    templateResolver: DeckNameTemplateResolver,
    nativeLanguage: Language,
    foreignLanguage: Language,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleContext = remember(nativeLanguage, foreignLanguage) {
        SearchContext(
            nativeLanguage = nativeLanguage,
            foreignLanguage = foreignLanguage,
            sourceLanguage = nativeLanguage,
            targetLanguage = foreignLanguage,
            context = null
        )
    }
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Available Templates",
                style = MaterialTheme.typography.titleSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val templates = templateResolver.getAvailableTemplates()
            
            templates.forEach { templateInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTemplateClick(templateInfo.template) }
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
                            style = MaterialTheme.typography.labelSmall,
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Example combinations
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Example Combinations:",
                style = MaterialTheme.typography.labelMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val examples = listOf(
                "Vocabulary::{foreign_local}",
                "{year}::{foreign_native}",
                "{foreign_english}::{month}"
            )
            
            examples.forEach { example ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTemplateClick(example) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = try {
                            templateResolver.resolveDeckName(example, sampleContext)
                        } catch (e: Exception) {
                            "Invalid"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
