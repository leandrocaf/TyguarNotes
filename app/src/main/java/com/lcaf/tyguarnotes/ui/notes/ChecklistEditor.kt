package com.lcaf.tyguarnotes.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.data.ChecklistItem

/**
 * Google Keep–style checklist: unchecked items first, then a "done" section with the
 * checked ones struck through. Enter adds the next item; backspace on an empty item removes it.
 */
@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    focusItemId: String?,
    onItemFocused: () -> Unit,
    onTextChange: (itemId: String, text: String) -> Unit,
    onToggle: (itemId: String) -> Unit,
    onAddAfter: (itemId: String?) -> Unit,
    onRemove: (itemId: String, focusPrevious: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val unchecked = items.filter { !it.checked }
    val checked = items.filter { it.checked }

    LazyColumn(modifier = modifier) {
        items(unchecked, key = { it.id }) { item ->
            ChecklistRow(
                item = item,
                requestFocus = item.id == focusItemId,
                onFocused = onItemFocused,
                onTextChange = { onTextChange(item.id, it) },
                onToggle = { onToggle(item.id) },
                onEnter = { onAddAfter(item.id) },
                onRemove = { focusPrevious -> onRemove(item.id, focusPrevious) }
            )
        }
        item(key = "add_item") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddAfter(null) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.checklist_add_item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        if (checked.isNotEmpty()) {
            item(key = "done_header") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.checklist_done_count, checked.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )
            }
            items(checked, key = { it.id }) { item ->
                ChecklistRow(
                    item = item,
                    requestFocus = item.id == focusItemId,
                    onFocused = onItemFocused,
                    onTextChange = { onTextChange(item.id, it) },
                    onToggle = { onToggle(item.id) },
                    onEnter = {},
                    onRemove = { onRemove(item.id, false) }
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggle: () -> Unit,
    onEnter: () -> Unit,
    onRemove: (focusPrevious: Boolean) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocused()
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface.let { if (item.checked) it.copy(alpha = 0.5f) else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = item.text,
                onValueChange = { text ->
                    // Soft keyboards deliver Enter as a newline: it means "next item".
                    if ('\n' in text) {
                        onTextChange(text.replace("\n", ""))
                        onEnter()
                    } else {
                        onTextChange(text)
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val removeEmpty = event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace && item.text.isEmpty()
                        if (removeEmpty) onRemove(true)
                        removeEmpty
                    }
            )
            if (item.text.isEmpty() && !isFocused) {
                Text(
                    text = stringResource(R.string.checklist_item_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        if (isFocused) {
            IconButton(onClick = { onRemove(false) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.checklist_remove_item),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
