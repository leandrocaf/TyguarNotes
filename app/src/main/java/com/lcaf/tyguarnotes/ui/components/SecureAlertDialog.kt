package com.lcaf.tyguarnotes.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/** True while the app lock screen is up; content behind it must not be shown. */
val LocalAppLocked = compositionLocalOf { false }

/**
 * [AlertDialog] that is not shown while the app is locked. Dialogs live in their own
 * windows, above the lock screen, so they must leave the composition to stay hidden;
 * they come back unchanged once the user unlocks.
 */
@Composable
fun SecureAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    if (LocalAppLocked.current) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties
    )
}
