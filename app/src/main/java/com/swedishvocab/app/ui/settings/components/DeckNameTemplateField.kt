package com.swedishvocab.app.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swedishvocab.app.data.model.Language
import com.swedishvocab.app.data.model.SearchContext
import com.swedishvocab.app.domain.template.DeckNameTemplateResolver

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // Use internal state to prevent cursor jumping
    var internalValue by remember { mutableStateOf(value) }
    
    // Sync internal state with external value when it changes externally
    LaunchedEffect(value) {
        if (internalValue != value) {
            internalValue = value
        }
    }
    
    // Separate state for preview to avoid affecting text input
    var resolvedDeckName by remember { mutableStateOf("") }
    
    // Update preview when template or languages change
    LaunchedEffect(internalValue, nativeLanguage, foreignLanguage) {
        resolvedDeckName = try {
            val sampleContext = SearchContext(
                nativeLanguage = nativeLanguage,
                foreignLanguage = foreignLanguage,
                sourceLanguage = nativeLanguage,
                targetLanguage = foreignLanguage,
                context = null
            )
            templateResolver.resolveDeckName(internalValue, sampleContext)
        } catch (e: Exception) {
            "Invalid template"
        }
    }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                onValueChange(newValue)
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = templateInfo.template,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
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
                        color = MaterialTheme.colorScheme.primary,
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = try {
                            templateResolver.resolveDeckName(example, sampleContext)
                        } catch (e: Exception) {
                            "Invalid"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
