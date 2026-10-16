package com.lcaf.tyguarnotes.ui.notes

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lcaf.tyguarnotes.ui.components.ClearSelectionOnFolderChange
import com.lcaf.tyguarnotes.ui.components.DeleteSelectionDialog
import com.lcaf.tyguarnotes.ui.components.ReminderEditorDialog
import com.lcaf.tyguarnotes.ui.components.SecureAlertDialog
import com.lcaf.tyguarnotes.ui.components.SelectionOverlay
import com.lcaf.tyguarnotes.ui.components.SelectionTopAppBar
import com.lcaf.tyguarnotes.ui.components.rememberReminderPermissionRequester
import com.lcaf.tyguarnotes.ui.components.rememberSelectionState
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import com.lcaf.tyguarnotes.ui.sync.SyncIndicator
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.components.AppDrawerContent
import com.lcaf.tyguarnotes.ui.components.ConfirmDialog
import com.lcaf.tyguarnotes.ui.components.DrawerItem
import com.lcaf.tyguarnotes.ui.components.NoteCard
import com.lcaf.tyguarnotes.ui.components.PinPadScreen
import com.lcaf.tyguarnotes.ui.components.ZoomAnimatedItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.NoteFolder
import com.lcaf.tyguarnotes.ui.util.AppLockGrace
import kotlinx.coroutines.launch

private enum class NotesSortOrder { MODIFIED, CREATED, NAME }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListNotesScreen(
    onNoteClick: (String) -> Unit,
    onNewNote: (String) -> Unit,
    onNewChecklist: (String) -> Unit,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onDocuments: () -> Unit,
    onLogout: () -> Unit,
    settingsRepository: com.lcaf.tyguarnotes.data.repository.SettingsRepository,
    viewModel: ListNotesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    val selection = rememberSelectionState()
    var moveTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var showDeleteSelection by remember { mutableStateOf(false) }
    var showSelectionReminder by remember { mutableStateOf(false) }
    val ensureReminderPermissions = rememberReminderPermissionRequester()
    var isGridView by remember { mutableStateOf(settingsRepository.notesViewGrid) }
    var sortOrder by remember { mutableStateOf(
        try { NotesSortOrder.valueOf(settingsRepository.notesSortOrder) } catch (_: Exception) { NotesSortOrder.MODIFIED }
    ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Password dialog states
    var showLockDialog by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showPasswordPrompt by remember { mutableStateOf<Note?>(null) }
    var lockTargetNote by remember { mutableStateOf<Note?>(null) }

    // Folder states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showFolderLockPinPad by remember { mutableStateOf(false) }
    var showFolderLockConfirm by remember { mutableStateOf(false) }
    var folderFirstPin by remember { mutableStateOf("") }
    var lockTargetFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var showFolderUnlockPinPad by remember { mutableStateOf(false) }
    var unlockTargetFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var showFolderPasswordPrompt by remember { mutableStateOf<NoteFolder?>(null) }
    var renameFolderTarget by remember { mutableStateOf<NoteFolder?>(null) }
    var pickerLockedFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var pickerParentId by remember { mutableStateOf("") }
    var pickerParentName by remember { mutableStateOf("") }

    val removingItemIds = remember { mutableStateOf(setOf<String>()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadNotes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // PinPad: unlock folder in picker
    if (pickerLockedFolder != null) {
        val folder = pickerLockedFolder!!
        val wrongText = stringResource(R.string.wrong_password)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = "${stringResource(R.string.unlock_folder)} - ${folder.name}",
            errorMessage = pinError,
            onConfirm = { pin ->
                if (pin == folder.password) {
                    pinError = null
                    pickerParentId = folder.id
                    pickerParentName = folder.name
                    pickerLockedFolder = null
                } else {
                    pinError = wrongText
                }
            },
            onBack = { pickerLockedFolder = null }
        )
        return
    }

    // Rename folder dialog
    if (renameFolderTarget != null) {
        var name by remember { mutableStateOf(renameFolderTarget!!.name) }
        SecureAlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) { viewModel.renameFolder(renameFolderTarget!!.id, name.trim()); renameFolderTarget = null } },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renameFolderTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Move note to folder dialog
    if (moveTargetIds != null) {
        NoteFolderPickerDialog(
            viewModel = viewModel,
            initialParentId = pickerParentId,
            initialParentName = pickerParentName,
            onFolderSelected = { parentId ->
                viewModel.moveNotes(moveTargetIds!!, parentId)
                moveTargetIds = null
                selection.clear()
                pickerParentId = ""
                pickerParentName = ""
            },
            onDismiss = {
                moveTargetIds = null
                pickerParentId = ""
                pickerParentName = ""
            },
            onLockedFolderClick = { folder -> pickerLockedFolder = folder }
        )
    }

    // Folder PinPad: set password step 1
    if (showFolderLockPinPad && lockTargetFolder != null && !showFolderLockConfirm) {
        PinPadScreen(
            title = stringResource(R.string.lock_folder),
            onConfirm = { enteredPin ->
                folderFirstPin = enteredPin
                showFolderLockConfirm = true
            },
            onBack = {
                showFolderLockPinPad = false
                lockTargetFolder = null
                folderFirstPin = ""
            }
        )
        return
    }

    // Folder PinPad: confirm password step 2
    if (showFolderLockConfirm && lockTargetFolder != null) {
        val mismatchText = stringResource(R.string.password_mismatch)
        val lockedToast = stringResource(R.string.folder_locked)
        var confirmError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.confirm_new_password),
            errorMessage = confirmError,
            onConfirm = { enteredPin ->
                if (enteredPin == folderFirstPin) {
                    viewModel.lockFolder(lockTargetFolder!!.id, enteredPin)
                    Toast.makeText(context, lockedToast, Toast.LENGTH_SHORT).show()
                    showFolderLockPinPad = false
                    showFolderLockConfirm = false
                    lockTargetFolder = null
                    folderFirstPin = ""
                } else {
                    confirmError = mismatchText
                }
            },
            onBack = { showFolderLockConfirm = false }
        )
        return
    }

    // Folder PinPad: unlock folder
    if (showFolderUnlockPinPad && unlockTargetFolder != null) {
        val folder = unlockTargetFolder!!
        val wrongPasswordText = stringResource(R.string.wrong_password)
        val unlockedToast = stringResource(R.string.folder_unlocked)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.unlock_folder),
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == folder.password) {
                    viewModel.unlockFolder(folder.id)
                    Toast.makeText(context, unlockedToast, Toast.LENGTH_SHORT).show()
                    showFolderUnlockPinPad = false
                    unlockTargetFolder = null
                    pinError = null
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = {
                showFolderUnlockPinPad = false
                unlockTargetFolder = null
            }
        )
        return
    }

    // Folder PinPad: open locked folder
    if (showFolderPasswordPrompt != null) {
        val folder = showFolderPasswordPrompt!!
        val wrongPasswordText = stringResource(R.string.wrong_password)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.unlock_folder),
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == folder.password) {
                    showFolderPasswordPrompt = null
                    pinError = null
                    viewModel.openFolder(folder)
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = { showFolderPasswordPrompt = null }
        )
        return
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        SecureAlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                            showNewFolderDialog = false
                        }
                    },
                    enabled = folderName.isNotBlank()
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            message = stringResource(R.string.logout_question),
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    // Fullscreen PIN pad: set password (step 1 — enter new pin)
    if (showLockDialog && lockTargetNote != null && !showLockConfirm) {
        PinPadScreen(
            title = stringResource(R.string.lock_note),
            onConfirm = { enteredPin ->
                firstPin = enteredPin
                showLockConfirm = true
            },
            onBack = {
                showLockDialog = false
                lockTargetNote = null
                firstPin = ""
            }
        )
        return
    }

    // Fullscreen PIN pad: confirm password (step 2 — re-enter pin)
    if (showLockConfirm && lockTargetNote != null) {
        val mismatchText = stringResource(R.string.password_mismatch)
        val lockedToast = stringResource(R.string.note_locked)
        var confirmError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.confirm_new_password),
            errorMessage = confirmError,
            onConfirm = { enteredPin ->
                if (enteredPin == firstPin) {
                    viewModel.lockNote(lockTargetNote!!.id, enteredPin)
                    Toast.makeText(context, lockedToast, Toast.LENGTH_SHORT).show()
                    showLockDialog = false
                    showLockConfirm = false
                    lockTargetNote = null
                    firstPin = ""
                } else {
                    confirmError = mismatchText
                }
            },
            onBack = {
                showLockConfirm = false
            }
        )
        return
    }

    // Fullscreen PIN pad to unlock note (from menu)
    if (showUnlockDialog && lockTargetNote != null) {
        val note = lockTargetNote!!
        val wrongPasswordText = stringResource(R.string.wrong_password)
        val unlockedToast = stringResource(R.string.note_unlocked)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.unlock_note),
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == note.password) {
                    viewModel.unlockNote(note.id)
                    Toast.makeText(context, unlockedToast, Toast.LENGTH_SHORT).show()
                    showUnlockDialog = false
                    lockTargetNote = null
                    pinError = null
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = {
                showUnlockDialog = false
                lockTargetNote = null
            }
        )
        return
    }

    // Fullscreen PIN pad to open locked note
    if (showPasswordPrompt != null) {
        val note = showPasswordPrompt!!
        val wrongPasswordText = stringResource(R.string.wrong_password)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.unlock_note),
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == note.password) {
                    showPasswordPrompt = null
                    pinError = null
                    onNoteClick(note.id)
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = {
                showPasswordPrompt = null
            }
        )
        return
    }

    val sortedNotes = remember(uiState.filteredNotes, sortOrder) {
        when (sortOrder) {
            NotesSortOrder.MODIFIED -> uiState.filteredNotes.sortedByDescending { it.lastModification }
            NotesSortOrder.CREATED -> uiState.filteredNotes.sortedByDescending { it.createdAt }
            NotesSortOrder.NAME -> uiState.filteredNotes.sortedBy { it.title.lowercase() }
        }
    }

    val isInSubfolder = uiState.folderStack.isNotEmpty()
    BackHandler(enabled = isInSubfolder) {
        viewModel.navigateBack()
    }
    // Declared last so it takes precedence: back first leaves selection mode.
    BackHandler(enabled = selection.isActive) {
        selection.clear()
    }
    ClearSelectionOnFolderChange(selection, uiState.currentFolderId)

    // Selection keys match removingItemIds: "folder_<id>" for folders, the note id for notes.
    val selectedFolders = uiState.folders.filter { "folder_${it.id}" in selection }
    val selectedNotes = uiState.notes.filter { it.id in selection }
    if (showSelectionReminder) {
        // With one note the dialog starts from its current reminder; with several it starts fresh.
        val only = selectedNotes.singleOrNull()
        val ids = selectedNotes.map { it.id }
        ReminderEditorDialog(
            initialAt = only?.reminderAt,
            initialRepeat = only?.reminderRepeat ?: ReminderRepeat.NONE,
            onConfirm = { at, repeat ->
                viewModel.setReminder(ids, at, repeat)
                showSelectionReminder = false
                selection.clear()
                ensureReminderPermissions()
            },
            onRemove = if (selectedNotes.any { it.hasReminder }) {
                {
                    viewModel.setReminder(ids, null, ReminderRepeat.NONE)
                    showSelectionReminder = false
                    selection.clear()
                }
            } else null,
            onDismiss = { showSelectionReminder = false }
        )
    }

    if (showDeleteSelection) {
        DeleteSelectionDialog(
            count = selection.count,
            includesFolders = selectedFolders.isNotEmpty(),
            onConfirm = {
                removingItemIds.value = removingItemIds.value + selection.keys
                selection.clear()
                showDeleteSelection = false
            },
            onDismiss = { showDeleteSelection = false }
        )
    }
    val title = if (uiState.currentFolderName.isNotEmpty()) {
        uiState.currentFolderName
    } else {
        stringResource(R.string.list_notes_fragment_label)
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawerContent(
                selectedItem = DrawerItem.NOTES,
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    when (item) {
                        DrawerItem.NOTES -> { /* Already here */ }
                        DrawerItem.GALLERY -> onGallery()
                        DrawerItem.DOCUMENTS -> onDocuments()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                if (selection.isActive) {
                    SelectionTopAppBar(
                        selectedCount = selection.count,
                        onClose = { selection.clear() },
                        onSelectAll = {
                            selection.selectAll(uiState.folders.map { "folder_${it.id}" } + sortedNotes.map { it.id })
                        },
                        onMove = { moveTargetIds = selectedNotes.map { it.id } },
                        onDelete = { showDeleteSelection = true },
                        onReminder = { showSelectionReminder = true },
                        reminderBlockedReason = stringResource(R.string.selection_reminder_folders)
                            .takeIf { selectedFolders.isNotEmpty() },
                        moveBlockedReason = stringResource(R.string.selection_move_folders)
                            .takeIf { selectedFolders.isNotEmpty() },
                        deleteBlockedReason = stringResource(R.string.selection_delete_locked)
                            .takeIf { selectedFolders.any { it.isLocked } || selectedNotes.any { it.password.isNotEmpty() } },
                        singleItemActions = { dismiss ->
                            val folder = selectedFolders.singleOrNull()
                            val note = selectedNotes.singleOrNull()
                            if (folder != null) {
                                if (folder.isLocked) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unlock_folder)) },
                                        onClick = {
                                            dismiss()
                                            unlockTargetFolder = folder
                                            showFolderUnlockPinPad = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lock_folder)) },
                                        onClick = {
                                            dismiss()
                                            lockTargetFolder = folder
                                            showFolderLockPinPad = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    onClick = {
                                        dismiss()
                                        renameFolderTarget = folder
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                            } else if (note != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    onClick = {
                                        dismiss()
                                        shareNote(context, note)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (note.password.isEmpty()) R.string.lock_note else R.string.unlock_note))
                                    },
                                    onClick = {
                                        dismiss()
                                        lockTargetNote = note
                                        if (note.password.isEmpty()) showLockDialog = true else showUnlockDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(if (note.password.isEmpty()) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null)
                                    }
                                )
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        navigationIcon = {
                            if (isInSubfolder) {
                                IconButton(onClick = { viewModel.navigateBack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                                }
                            }
                        },
                        actions = {
                            SyncIndicator(
                                onSignInAgain = {
                                    viewModel.logout()
                                    onLogout()
                                }
                            )
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_by_modified)) },
                                        onClick = {
                                            sortOrder = NotesSortOrder.MODIFIED
                                            settingsRepository.notesSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_by_date)) },
                                        onClick = {
                                            sortOrder = NotesSortOrder.CREATED
                                            settingsRepository.notesSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_by_name)) },
                                        onClick = {
                                            sortOrder = NotesSortOrder.NAME
                                            settingsRepository.notesSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                            IconButton(onClick = {
                                isGridView = !isGridView
                                settingsRepository.notesViewGrid = isGridView
                            }) {
                                Icon(
                                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = null
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_refresh)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.loadNotes()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_settings)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSettings()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_logout)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showLogoutDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!selection.isActive) Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_note))
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_note)) },
                            onClick = {
                                showAddMenu = false
                                onNewNote(viewModel.currentFolderId)
                            },
                            leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_checklist)) },
                            onClick = {
                                showAddMenu = false
                                onNewChecklist(viewModel.currentFolderId)
                            },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_folder)) },
                            onClick = {
                                showAddMenu = false
                                showNewFolderDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        label = { Text(stringResource(R.string.search)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )

                    if (isGridView) {
                        // Staggered (Keep-style): each column stacks cards at their own height, no row gaps.
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.folders, key = { "folder_${it.id}" }) { folder ->
                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains("folder_${folder.id}"),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - "folder_${folder.id}"
                                        viewModel.deleteFolder(folder)
                                    }
                                ) {
                                Box {
                                    NoteFolderCard(
                                        folder = folder,
                                        isSelected = "folder_${folder.id}" in selection,
                                        onClick = {
                                            if (selection.isActive) {
                                                selection.toggle("folder_${folder.id}")
                                            } else if (folder.isLocked) {
                                                showFolderPasswordPrompt = folder
                                            } else {
                                                viewModel.openFolder(folder)
                                            }
                                        },
                                        onLongClick = { selection.toggle("folder_${folder.id}") }
                                    )
                                    SelectionOverlay(selectionMode = selection.isActive, selected = "folder_${folder.id}" in selection)
                                }
                                }
                            }
                            items(sortedNotes, key = { it.id }) { note ->

                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains(note.id),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - note.id
                                        viewModel.deleteNote(note.id)
                                    }
                                ) {
                                Box {
                                    NoteCard(
                                        note = note,
                                        isSelected = note.id in selection,
                                        onClick = {
                                            if (selection.isActive) {
                                                selection.toggle(note.id)
                                            } else if (note.password.isNotEmpty()) {
                                                showPasswordPrompt = note
                                            } else {
                                                onNoteClick(note.id)
                                            }
                                        },
                                        onLongClick = { selection.toggle(note.id) }
                                    )

                                    SelectionOverlay(selectionMode = selection.isActive, selected = note.id in selection)
                                }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.folders, key = { "folder_${it.id}" }) { folder ->
                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains("folder_${folder.id}"),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - "folder_${folder.id}"
                                        viewModel.deleteFolder(folder)
                                    }
                                ) {
                                Box {
                                    NoteFolderCard(
                                        folder = folder,
                                        isSelected = "folder_${folder.id}" in selection,
                                        onClick = {
                                            if (selection.isActive) {
                                                selection.toggle("folder_${folder.id}")
                                            } else if (folder.isLocked) {
                                                showFolderPasswordPrompt = folder
                                            } else {
                                                viewModel.openFolder(folder)
                                            }
                                        },
                                        onLongClick = { selection.toggle("folder_${folder.id}") }
                                    )
                                    SelectionOverlay(selectionMode = selection.isActive, selected = "folder_${folder.id}" in selection)
                                }
                                }
                            }
                            items(sortedNotes, key = { it.id }) { note ->

                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains(note.id),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - note.id
                                        viewModel.deleteNote(note.id)
                                    }
                                ) {
                                Box {
                                    NoteCard(
                                        note = note,
                                        isSelected = note.id in selection,
                                        onClick = {
                                            if (selection.isActive) {
                                                selection.toggle(note.id)
                                            } else if (note.password.isNotEmpty()) {
                                                showPasswordPrompt = note
                                            } else {
                                                onNoteClick(note.id)
                                            }
                                        },
                                        onLongClick = { selection.toggle(note.id) }
                                    )

                                    SelectionOverlay(selectionMode = selection.isActive, selected = note.id in selection)
                                }
                                }
                            }
                        }
                    }
                }

                // Discrete loading text
                if (uiState.isRefreshing) {
                    Text(
                        text = stringResource(R.string.updating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

private fun shareNote(context: android.content.Context, note: Note) {
    val text = buildString {
        if (note.title.isNotBlank()) appendLine(note.title)
        if (note.description.isNotBlank()) append(note.description)
        note.checklist.forEach { appendLine("${if (it.checked) "☑" else "☐"} ${it.text}") }
    }.trimEnd()
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    AppLockGrace.beginExternalTask()
    context.startActivity(Intent.createChooser(sendIntent, null))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteFolderCard(
    folder: NoteFolder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (folder.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun NoteFolderPickerDialog(
    viewModel: ListNotesViewModel,
    initialParentId: String = "",
    initialParentName: String = "",
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onLockedFolderClick: (NoteFolder) -> Unit = {}
) {
    var currentParentId by remember(initialParentId) { mutableStateOf(initialParentId) }
    var currentParentName by remember(initialParentName) { mutableStateOf(initialParentName) }
    var folderStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var folders by remember { mutableStateOf<List<NoteFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentParentId) {
        isLoading = true
        folders = viewModel.getAvailableFolders(currentParentId)
        isLoading = false
    }

    val locationText = if (currentParentName.isNotEmpty()) currentParentName else stringResource(R.string.root_folder)

    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_folder)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "📂 $locationText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else if (folders.isEmpty()) {
                    Text(text = stringResource(R.string.no_subfolders), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            if (folder.isLocked) { onLockedFolderClick(folder) }
                            else { folderStack = folderStack + Pair(currentParentId, currentParentName); currentParentId = folder.id; currentParentName = folder.name }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            if (folder.isLocked) { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = folder.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFolderSelected(currentParentId) }) { Text(stringResource(R.string.save_here)) }
        },
        dismissButton = {
            if (folderStack.isNotEmpty()) {
                TextButton(onClick = { val (prevId, prevName) = folderStack.last(); folderStack = folderStack.dropLast(1); currentParentId = prevId; currentParentName = prevName }) { Text(stringResource(R.string.previous)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
