package com.glosdalen.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A split button with a main action button and a dropdown menu for options.
 * 
 * @param T The type of items in the dropdown menu
 * @param mainButtonContent The content to display in the main button
 * @param dropdownItems List of items to show in the dropdown menu
 * @param selectedItem The currently selected item
 * @param enabled Whether the button is enabled
 * @param onMainClick Callback when the main button is clicked
 * @param onItemSelect Callback when a dropdown item is selected
 * @param itemLabel Function to get the display label for an item
 * @param modifier Modifier for the button container
 * @param dropdownButtonContentDescription Content description for the dropdown button
 * @param itemContent Optional custom content for dropdown items. If null, uses itemLabel.
 * @param isItemEnabled Optional function to determine if an item is enabled. Defaults to all enabled.
 * @param colors Optional button colors for customizing the appearance
 */
@Composable
fun <T> SplitButton(
    mainButtonContent: @Composable RowScope.() -> Unit,
    dropdownItems: List<T>,
    selectedItem: T,
    enabled: Boolean = true,
    onMainClick: () -> Unit,
    onItemSelect: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    dropdownButtonContentDescription: String = "Options",
    itemContent: (@Composable (T) -> Unit)? = null,
    isItemEnabled: (T) -> Boolean = { true },
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    var showDropdown by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Main action button
        Button(
            onClick = onMainClick,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0),
            colors = colors
        ) {
            mainButtonContent()
        }
        
        // Dropdown button
        Box {
            Button(
                onClick = { showDropdown = true },
                enabled = enabled,
                modifier = Modifier.width(48.dp),
                shape = RoundedCornerShape(topStartPercent = 0, bottomStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50),
                contentPadding = PaddingValues(0.dp),
                colors = colors
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = dropdownButtonContentDescription
                )
            }
            
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                dropdownItems.forEach { item ->
                    val itemEnabled = isItemEnabled(item)
                    DropdownMenuItem(
                        text = {
                            if (itemContent != null) {
                                itemContent(item)
                            } else {
                                Text(
                                    text = itemLabel(item),
                                    color = if (item == selectedItem) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (itemEnabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        },
                        onClick = {
                            if (itemEnabled) {
                                onItemSelect(item)
                                showDropdown = false
                            }
                        },
                        enabled = itemEnabled
                    )
                }
            }
        }
    }
}
