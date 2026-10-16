package com.lcaf.tyguarnotes.ui.notes

import android.graphics.BitmapFactory
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.components.ConfirmDialog
import com.lcaf.tyguarnotes.ui.components.LoadingOverlay
import com.lcaf.tyguarnotes.ui.components.ReminderEditorDialog
import com.lcaf.tyguarnotes.ui.components.ReminderRow
import com.lcaf.tyguarnotes.ui.components.SecureAlertDialog
import com.lcaf.tyguarnotes.ui.components.rememberReminderPermissionRequester
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.util.AppLockGrace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    noteId: String,
    parentId: String = "",
    noteType: String = Note.TYPE_TEXT,
    initialDescription: String? = null,
    onNavigateBack: () -> Unit,
    onViewImage: (ByteArray) -> Unit,
    viewModel: NoteDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var clickedUrl by remember { mutableStateOf<String?>(null) }

    val view = androidx.compose.ui.platform.LocalView.current

    val navigateBack = {
        focusManager.clearFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        
        // Save if modified before going back
        viewModel.saveAndGoBack { 
            onNavigateBack()
        }
    }

    BackHandler {
        navigateBack()
    }

    val ensureReminderPermissions = rememberReminderPermissionRequester()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            viewModel.onImageSelected(bitmap)
        }
    }

    val pickImage = {
        AppLockGrace.beginExternalTask()
        imagePickerLauncher.launch("image/*")
    }

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId, parentId, noteType)
        if (initialDescription != null) {
            viewModel.onDescriptionChanged(initialDescription)
        }
    }

    if (showReminderDialog) {
        ReminderEditorDialog(
            initialAt = uiState.reminderAt,
            initialRepeat = uiState.reminderRepeat,
            onConfirm = { at, repeat ->
                viewModel.setReminder(at, repeat)
                showReminderDialog = false
                ensureReminderPermissions()
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            message = stringResource(R.string.delete_note_question),
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteNote { navigateBack() }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (clickedUrl != null) {
        val url = clickedUrl!!
        val copiedMessage = stringResource(R.string.link_copied)
        val openText = stringResource(R.string.open_in_browser)
        val incognitoText = stringResource(R.string.open_incognito)
        val copyText = stringResource(R.string.copy_link)
        val cancelText = stringResource(R.string.cancel)

        com.lcaf.tyguarnotes.ui.components.SecureAlertDialog(
            onDismissRequest = { clickedUrl = null },
            title = { Text(url, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    val options = listOf(
                        Triple(Icons.Default.OpenInBrowser, openText) {
                            val fullUrl = if (url.startsWith("http")) url else "https://$url"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                            clickedUrl = null
                        },
                        Triple(Icons.Default.VisibilityOff, incognitoText) {
                            com.lcaf.tyguarnotes.ui.util.Util.openIncognito(context, url)
                            clickedUrl = null
                        },
                        Triple(Icons.Default.ContentCopy, copyText) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            clickedUrl = null
                        },
                        Triple(Icons.Default.Close, cancelText) {
                            clickedUrl = null
                        }
                    )

                    options.forEach { (icon, label, action) ->
                        val isCancel = label == cancelText
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .then(
                                    if (isCancel) Modifier.border(1.dp, Color.Red, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { action() }
                                .padding(vertical = 12.dp, horizontal = if (isCancel) 12.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon, 
                                contentDescription = null, 
                                tint = if (isCancel) Color.Red else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                label, 
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCancel) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (uiState.isChecklist) R.string.checklist_label else R.string.note_fragment_label))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Delete goes first, away from the screen edge where thumbs land, and save is last.
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                        // Picking a new image also replaces the current one.
                        IconButton(onClick = pickImage) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.select_image))
                        }
                        // Going back saves too; this is the explicit way to do it.
                        IconButton(onClick = { navigateBack() }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(if (uiState.isNewNote) R.string.create_note else R.string.update_note)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                ReminderRow(
                    reminderAt = uiState.reminderAt,
                    repeat = uiState.reminderRepeat,
                    onClick = { showReminderDialog = true },
                    onClear = viewModel::clearReminder
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isChecklist) {
                    ChecklistEditor(
                        items = uiState.checklist,
                        focusItemId = uiState.focusItemId,
                        onItemFocused = viewModel::onChecklistItemFocused,
                        onTextChange = viewModel::onChecklistItemChanged,
                        onToggle = viewModel::toggleChecklistItem,
                        onAddAfter = viewModel::addChecklistItem,
                        onRemove = viewModel::removeChecklistItem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    // Description with inline clickable links
                    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).toArgb()
                    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
                    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                hint = "Description"
                                setHintTextColor(hintColor)
                                setTextColor(textColor)
                                textSize = 16f
                                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                isVerticalScrollBarEnabled = true
                                val border = GradientDrawable().apply {
                                    setStroke(2, outlineColor)
                                    cornerRadius = 12f
                                    setColor(android.graphics.Color.TRANSPARENT)
                                }
                                background = border
                                setPadding(32, 24, 32, 24)
                                setLinkTextColor(linkColor)
                                autoLinkMask = Linkify.WEB_URLS
                                linksClickable = true
                                movementMethod = object : LinkMovementMethod() {
                                    override fun onTouchEvent(
                                        widget: TextView,
                                        buffer: Spannable,
                                        event: MotionEvent
                                    ): Boolean {
                                        if (event.action == MotionEvent.ACTION_UP) {
                                            val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                                            val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                                            val layout = widget.layout
                                            if (layout != null) {
                                                val line = layout.getLineForVertical(y)
                                                val off = layout.getOffsetForHorizontal(line, x.toFloat())
                                                val links = buffer.getSpans(off, off, URLSpan::class.java)
                                                if (links.isNotEmpty()) {
                                                    clickedUrl = links[0].url
                                                    return true
                                                }
                                            }
                                        }
                                        return super.onTouchEvent(widget, buffer, event)
                                    }
                                }
                                doAfterTextChanged { editable ->
                                    viewModel.onDescriptionChanged(editable?.toString() ?: "")
                                }
                            }
                        },
                        update = { editText ->
                            if (editText.text.toString() != uiState.description) {
                                val selection = editText.selectionStart
                                editText.setText(uiState.description)
                                val newSelection = selection.coerceAtMost(uiState.description.length)
                                editText.setSelection(newSelection)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // Image, with a small badge to remove it (adding one is an icon in the top bar)
                val bitmap = uiState.imageBitmap
                if (bitmap != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Note image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { uiState.imageBytes?.let { onViewImage(it) } },
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { viewModel.removeImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_image),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        LoadingOverlay(isLoading = uiState.isLoading)
    }
}
