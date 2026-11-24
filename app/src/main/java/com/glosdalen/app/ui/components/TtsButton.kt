package com.glosdalen.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.glosdalen.app.ui.theme.AppIcons

/**
 * Text-to-speech "Listen"/"Stop" icon button.
 *
 * Shows a stop icon tinted with the primary color while [isPlaying], otherwise a speaker icon
 * tinted with [idleTint] and described by [idleContentDescription].
 */
@Composable
fun TtsButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    idleTint: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    idleContentDescription: String = "Listen"
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isPlaying) AppIcons.Stop else AppIcons.VolumeUp,
            contentDescription = if (isPlaying) "Stop" else idleContentDescription,
            tint = if (isPlaying) {
                MaterialTheme.colorScheme.primary
            } else {
                idleTint
            },
            modifier = iconModifier
        )
    }
}
