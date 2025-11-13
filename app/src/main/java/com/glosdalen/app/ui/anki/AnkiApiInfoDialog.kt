package com.glosdalen.app.ui.anki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun AnkiApiInfoDialog(
    onDismiss: () -> Unit,
    onRemindLater: () -> Unit,
    onOpenAnkiSettings: () -> Unit // Now represents opening system App Settings
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
    title = { Text("Enable AnkiDroid API Access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Granting AnkiDroid API access lets Glosdalen create cards directly without switching apps.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Change integration method anytime: Settings › Anki.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Steps:\n1) Open Android App Settings\n2) Navigate to Permissions › Additional Permissions\n3) Grant access to the AnkiDroid database\n4) Return here. This message should be gone.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenAnkiSettings) {
                Text("Open Android App Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onRemindLater) { Text("Remind Me Later") }
        }
    )
}
