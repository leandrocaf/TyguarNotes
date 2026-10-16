package com.lcaf.tyguarnotes.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lcaf.tyguarnotes.R

/** Keys of the items selected in a list; selection mode is on while it is not empty. */
class SelectionState(initial: Set<String> = emptySet()) {
    var keys by mutableStateOf(initial)
        private set

    val isActive: Boolean get() = keys.isNotEmpty()
    val count: Int get() = keys.size

    operator fun contains(key: String) = key in keys

    fun toggle(key: String) {
        keys = if (key in keys) keys - key else keys + key
    }

    fun selectAll(all: Collection<String>) {
        keys = all.toSet()
    }

    fun clear() {
        keys = emptySet()
    }

    companion object {
        val Saver = listSaver<SelectionState, String>(
            save = { it.keys.toList() },
            restore = { SelectionState(it.toSet()) }
        )
    }
}

@Composable
fun rememberSelectionState(): SelectionState = rememberSaveable(saver = SelectionState.Saver) { SelectionState() }

/** Clears [selection] when the user opens another folder, but not after a configuration change. */
@Composable
fun ClearSelectionOnFolderChange(selection: SelectionState, folderId: String) {
    var selectionFolder by rememberSaveable { mutableStateOf(folderId) }
    LaunchedEffect(folderId) {
        if (selectionFolder != folderId) {
            selection.clear()
            selectionFolder = folderId
        }
    }
}

/**
 * Top bar shown instead of the screen's own while items are selected.
 * A non-null blocked reason greys the action out and explains why when tapped.
 * [singleItemActions] fills the overflow menu, shown only when one item is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    moveBlockedReason: String? = null,
    deleteBlockedReason: String? = null,
    onReminder: (() -> Unit)? = null,
    reminderBlockedReason: String? = null,
    singleItemActions: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            val spoken = pluralStringResource(R.plurals.items_selected, selectedCount, selectedCount)
            Text(text = selectedCount.toString(), maxLines = 1, modifier = Modifier.semantics { contentDescription = spoken })
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
            if (onReminder != null) {
                SelectionAction(Icons.Default.Alarm, stringResource(R.string.reminder_title), reminderBlockedReason, onReminder)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (singleItemActions != null && selectedCount == 1) {
                        singleItemActions { showMenu = false }
                        HorizontalDivider()
                    }
                    SelectionMenuItem(Icons.Default.DriveFileMove, stringResource(R.string.move_to_folder), moveBlockedReason) {
                        showMenu = false
                        onMove()
                    }
                    // Last and in red: it is the one to avoid tapping by accident.
                    SelectionMenuItem(Icons.Default.Delete, stringResource(R.string.delete), deleteBlockedReason, destructive = true) {
                        showMenu = false
                        onDelete()
                    }
                }
            }
        }
    )
}

@Composable
private fun SelectionAction(icon: ImageVector, description: String, blockedReason: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    IconButton(onClick = {
        if (blockedReason != null) Toast.makeText(context, blockedReason, Toast.LENGTH_LONG).show() else onClick()
    }) {
        val tint = LocalContentColor.current
        Icon(icon, contentDescription = description, tint = if (blockedReason != null) tint.copy(alpha = 0.38f) else tint)
    }
}

/** Selection marker and highlight drawn over an item; place it last inside the item's Box. */
@Composable
fun BoxScope.SelectionOverlay(selectionMode: Boolean, selected: Boolean) {
    if (!selectionMode) return
    if (selected) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        )
    }
    Icon(
        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    )
}

/** Confirmation for deleting the current selection. */
@Composable
fun DeleteSelectionDialog(count: Int, includesFolders: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val question = pluralStringResource(R.plurals.delete_items_question, count, count)
    val message = if (includesFolders) "$question\n\n${stringResource(R.string.delete_folders_warning)}" else question
    ConfirmDialog(
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel)
    )
}

/** Menu entry that, when blocked, looks disabled but still explains why on tap. */
@Composable
private fun SelectionMenuItem(
    icon: ImageVector,
    label: String,
    blockedReason: String?,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val color = when {
        blockedReason != null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = {
            if (blockedReason != null) Toast.makeText(context, blockedReason, Toast.LENGTH_LONG).show() else onClick()
        }
    )
}
