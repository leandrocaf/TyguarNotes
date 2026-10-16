package com.lcaf.tyguarnotes.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.components.SecureAlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Top-bar action showing the Drive sync state; tapping it opens details and actions. */
@Composable
fun SyncIndicator(
    onSignInAgain: () -> Unit,
    viewModel: SyncIndicatorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    val needsAttention = uiState.state == SyncIndicatorState.AUTH_REQUIRED || uiState.state == SyncIndicatorState.ERROR

    IconButton(onClick = { showDetails = true }) {
        BadgedBox(badge = { if (needsAttention) Badge() }) {
            val description = stringResource(R.string.sync_status)
            if (uiState.state == SyncIndicatorState.SYNCING) {
                SpinningIcon(Icons.Default.Sync, description)
            } else {
                Icon(iconFor(uiState.state), contentDescription = description)
            }
        }
    }

    if (showDetails) {
        SyncDetailsDialog(
            uiState = uiState,
            onSyncNow = {
                showDetails = false
                viewModel.syncNow()
            },
            onSignInAgain = {
                showDetails = false
                onSignInAgain()
            },
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
private fun SyncDetailsDialog(
    uiState: SyncIndicatorUiState,
    onSyncNow: () -> Unit,
    onSignInAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(iconFor(uiState.state), contentDescription = null) },
        title = { Text(stringResource(R.string.sync_status)) },
        text = {
            Column {
                Text(messageFor(uiState))
                uiState.lastSyncedAt?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    val formatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
                    Text(
                        text = stringResource(R.string.sync_last_synced_at, formatted),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.state == SyncIndicatorState.AUTH_REQUIRED) {
                TextButton(onClick = onSignInAgain) { Text(stringResource(R.string.sign_in_again)) }
            } else {
                TextButton(onClick = onSyncNow, enabled = uiState.state != SyncIndicatorState.SYNCING) {
                    Text(stringResource(R.string.sync_now))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}

@Composable
private fun SpinningIcon(icon: ImageVector, contentDescription: String) {
    val transition = rememberInfiniteTransition(label = "sync")
    val angle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "syncRotation"
    )
    Icon(icon, contentDescription = contentDescription, modifier = Modifier.rotate(angle))
}

private fun iconFor(state: SyncIndicatorState): ImageVector = when (state) {
    SyncIndicatorState.SYNCED -> Icons.Default.CloudDone
    SyncIndicatorState.SYNCING -> Icons.Default.Sync
    SyncIndicatorState.PENDING -> Icons.Default.CloudUpload
    SyncIndicatorState.OFFLINE -> Icons.Default.CloudOff
    SyncIndicatorState.ERROR, SyncIndicatorState.AUTH_REQUIRED -> Icons.Default.SyncProblem
}

@Composable
private fun messageFor(uiState: SyncIndicatorUiState): String = when (uiState.state) {
    SyncIndicatorState.SYNCED -> stringResource(R.string.sync_status_synced)
    SyncIndicatorState.SYNCING -> stringResource(R.string.sync_status_syncing)
    SyncIndicatorState.PENDING ->
        if (uiState.pendingCount > 0) {
            pluralStringResource(R.plurals.sync_pending_changes, uiState.pendingCount, uiState.pendingCount)
        } else {
            stringResource(R.string.sync_status_waiting_first)
        }
    SyncIndicatorState.OFFLINE -> stringResource(R.string.sync_status_offline)
    SyncIndicatorState.ERROR -> stringResource(R.string.sync_status_failed)
    SyncIndicatorState.AUTH_REQUIRED -> stringResource(R.string.sync_status_auth_required)
}
