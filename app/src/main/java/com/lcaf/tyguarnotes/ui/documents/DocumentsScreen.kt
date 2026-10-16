package com.lcaf.tyguarnotes.ui.documents

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lcaf.tyguarnotes.ui.components.ClearSelectionOnFolderChange
import com.lcaf.tyguarnotes.ui.components.DeleteSelectionDialog
import com.lcaf.tyguarnotes.ui.components.SecureAlertDialog
import com.lcaf.tyguarnotes.ui.components.SelectionOverlay
import com.lcaf.tyguarnotes.ui.components.SelectionTopAppBar
import com.lcaf.tyguarnotes.ui.components.rememberSelectionState
import com.lcaf.tyguarnotes.ui.util.AppLockGrace
import com.lcaf.tyguarnotes.ui.util.Constants
import com.lcaf.tyguarnotes.ui.sync.SyncIndicator
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.components.AppDrawerContent
import com.lcaf.tyguarnotes.ui.components.ConfirmDialog
import com.lcaf.tyguarnotes.ui.components.DrawerItem
import com.lcaf.tyguarnotes.ui.components.PinPadScreen
import com.lcaf.tyguarnotes.ui.components.ZoomAnimatedItem
import com.lcaf.tyguarnotes.ui.data.DocumentItem
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class SortOrder { TYPE, CREATED, NAME }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    onNotes: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    pendingDocBytes: ByteArray? = null,
    pendingDocName: String? = null,
    pendingDocMimeType: String? = null,
    onPendingDocConsumed: () -> Unit = {},
    settingsRepository: com.lcaf.tyguarnotes.data.repository.SettingsRepository? = null,
    viewModel: DocumentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val selection = rememberSelectionState()
    var moveTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var renameTargetItem by remember { mutableStateOf<DocumentItem?>(null) }
    var pickerLockedFolder by remember { mutableStateOf<DocumentItem?>(null) }
    var pickerParentId by remember { mutableStateOf("") }
    var pickerParentName by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(settingsRepository?.documentsViewGrid ?: true) }
    var sortOrder by remember { mutableStateOf(
        try { SortOrder.valueOf(settingsRepository?.documentsSortOrder ?: "CREATED") } catch (_: Exception) { SortOrder.CREATED }
    ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Preview states
    var previewDocBytes by remember { mutableStateOf<ByteArray?>(null) }
    var previewDocName by remember { mutableStateOf("") }
    var previewDocMimeType by remember { mutableStateOf("") }
    var isExternalShare by remember { mutableStateOf(false) }
    var pendingUploadBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingUploadName by remember { mutableStateOf("") }
    var pendingUploadMimeType by remember { mutableStateOf("") }
    var showFolderPicker by remember { mutableStateOf(false) }

    // Lock states
    var showLockPinPad by remember { mutableStateOf(false) }
    var showLockConfirmPinPad by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var lockTargetFolder by remember { mutableStateOf<DocumentItem?>(null) }
    var showUnlockPinPad by remember { mutableStateOf(false) }
    var unlockTargetFolder by remember { mutableStateOf<DocumentItem?>(null) }
    var showPasswordPrompt by remember { mutableStateOf<DocumentItem?>(null) }

    var showDeleteSelection by remember { mutableStateOf(false) }

    val removingItemIds = remember { mutableStateOf(setOf<String>()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
            val fileName = getFileName(context, it)
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                previewDocBytes = bytes
                previewDocName = fileName
                previewDocMimeType = mimeType
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadItems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(pendingDocBytes) {
        if (pendingDocBytes != null) {
            isExternalShare = true
            previewDocBytes = pendingDocBytes
            previewDocName = pendingDocName ?: "DOC_${System.currentTimeMillis()}"
            previewDocMimeType = pendingDocMimeType ?: "application/octet-stream"
            onPendingDocConsumed()
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

    // Rename dialog
    if (renameTargetItem != null) {
        RenameDialog(
            currentName = renameTargetItem!!.name,
            onConfirm = { newName ->
                viewModel.renameItem(renameTargetItem!!.id, newName)
                renameTargetItem = null
            },
            onDismiss = { renameTargetItem = null }
        )
    }

    // Move to folder dialog
    if (moveTargetIds != null) {
        DocFolderPickerDialog(
            viewModel = viewModel,
            initialParentId = pickerParentId,
            initialParentName = pickerParentName,
            onFolderSelected = { parentId ->
                viewModel.moveItems(moveTargetIds!!, parentId)
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

    // Document preview dialog
    if (previewDocBytes != null) {
        DocumentPreviewDialog(
            name = previewDocName,
            mimeType = previewDocMimeType,
            onConfirm = { finalName ->
                if (isExternalShare) {
                    pendingUploadBytes = previewDocBytes
                    pendingUploadName = finalName
                    pendingUploadMimeType = previewDocMimeType
                    previewDocBytes = null
                    previewDocName = ""
                    previewDocMimeType = ""
                    showFolderPicker = true
                } else {
                    viewModel.addDocument(finalName, previewDocBytes!!, previewDocMimeType)
                    previewDocBytes = null
                    previewDocName = ""
                    previewDocMimeType = ""
                    isExternalShare = false
                }
            },
            onDismiss = {
                previewDocBytes = null
                previewDocName = ""
                previewDocMimeType = ""
                isExternalShare = false
            }
        )
    }

    if (showFolderPicker && pendingUploadBytes != null) {
        DocFolderPickerDialog(
            viewModel = viewModel,
            onFolderSelected = { parentId ->
                viewModel.addDocument(pendingUploadName, pendingUploadBytes!!, pendingUploadMimeType, parentId)
                pendingUploadBytes = null
                pendingUploadName = ""
                pendingUploadMimeType = ""
                showFolderPicker = false
                isExternalShare = false
            },
            onDismiss = {
                pendingUploadBytes = null
                pendingUploadName = ""
                pendingUploadMimeType = ""
                showFolderPicker = false
                isExternalShare = false
            },
            onLockedFolderClick = { folder -> pickerLockedFolder = folder }
        )
    }

    // PinPad: set password step 1
    if (showLockPinPad && lockTargetFolder != null && !showLockConfirmPinPad) {
        PinPadScreen(
            title = stringResource(R.string.lock_folder),
            onConfirm = { enteredPin ->
                firstPin = enteredPin
                showLockConfirmPinPad = true
            },
            onBack = {
                showLockPinPad = false
                lockTargetFolder = null
                firstPin = ""
            }
        )
        return
    }

    // PinPad: confirm password step 2
    if (showLockConfirmPinPad && lockTargetFolder != null) {
        val mismatchText = stringResource(R.string.password_mismatch)
        val lockedToast = stringResource(R.string.folder_locked)
        var confirmError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.confirm_new_password),
            errorMessage = confirmError,
            onConfirm = { enteredPin ->
                if (enteredPin == firstPin) {
                    viewModel.lockFolder(lockTargetFolder!!.id, enteredPin)
                    Toast.makeText(context, lockedToast, Toast.LENGTH_SHORT).show()
                    showLockPinPad = false
                    showLockConfirmPinPad = false
                    lockTargetFolder = null
                    firstPin = ""
                } else {
                    confirmError = mismatchText
                }
            },
            onBack = { showLockConfirmPinPad = false }
        )
        return
    }

    // PinPad: unlock folder from menu
    if (showUnlockPinPad && unlockTargetFolder != null) {
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
                    showUnlockPinPad = false
                    unlockTargetFolder = null
                    pinError = null
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = {
                showUnlockPinPad = false
                unlockTargetFolder = null
            }
        )
        return
    }

    // PinPad: open locked folder
    if (showPasswordPrompt != null) {
        val folder = showPasswordPrompt!!
        val wrongPasswordText = stringResource(R.string.wrong_password)
        var pinError by remember { mutableStateOf<String?>(null) }
        PinPadScreen(
            title = stringResource(R.string.unlock_folder),
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                if (enteredPin == folder.password) {
                    showPasswordPrompt = null
                    pinError = null
                    viewModel.openFolder(folder)
                } else {
                    pinError = wrongPasswordText
                }
            },
            onBack = { showPasswordPrompt = null }
        )
        return
    }

    val selectedItems = uiState.items.filter { it.id in selection }
    if (showDeleteSelection) {
        DeleteSelectionDialog(
            count = selectedItems.size,
            includesFolders = selectedItems.any { it.isFolder },
            onConfirm = {
                removingItemIds.value = removingItemIds.value + selection.keys
                selection.clear()
                showDeleteSelection = false
            },
            onDismiss = { showDeleteSelection = false }
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

    if (showNewFolderDialog) {
        NewFolderDialog(
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    val title = if (uiState.currentFolderName.isNotEmpty()) {
        uiState.currentFolderName
    } else {
        stringResource(R.string.documents)
    }

    val sortedItems = remember(uiState.items, sortOrder) {
        val folders = uiState.items.filter { it.isFolder }
        val files = uiState.items.filter { !it.isFolder }
        val sortedFiles = when (sortOrder) {
            SortOrder.TYPE -> files.sortedBy { it.mimeType }
            SortOrder.CREATED -> files.sortedByDescending { it.createdAt }
            SortOrder.NAME -> files.sortedBy { it.name.lowercase() }
        }
        folders + sortedFiles
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val content: @Composable () -> Unit = {
        Scaffold(
            topBar = {
                if (selection.isActive) {
                    SelectionTopAppBar(
                        selectedCount = selection.count,
                        onClose = { selection.clear() },
                        onSelectAll = { selection.selectAll(uiState.items.map { it.id }) },
                        onMove = { moveTargetIds = selection.keys.toList() },
                        onDelete = { showDeleteSelection = true },
                        moveBlockedReason = stringResource(R.string.selection_move_folders)
                            .takeIf { selectedItems.any { it.isFolder } },
                        deleteBlockedReason = stringResource(R.string.selection_delete_locked)
                            .takeIf { selectedItems.any { it.isLocked } },
                        singleItemActions = { dismiss ->
                            val item = selectedItems.singleOrNull() ?: return@SelectionTopAppBar
                            if (item.isFolder) {
                                if (item.isLocked) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unlock_folder)) },
                                        onClick = {
                                            dismiss()
                                            unlockTargetFolder = item
                                            showUnlockPinPad = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lock_folder)) },
                                        onClick = {
                                            dismiss()
                                            lockTargetFolder = item
                                            showLockPinPad = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                onClick = {
                                    dismiss()
                                    renameTargetItem = item
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                                        text = { Text(stringResource(R.string.sort_by_type)) },
                                        onClick = {
                                            sortOrder = SortOrder.TYPE
                                            settingsRepository?.documentsSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_by_date)) },
                                        onClick = {
                                            sortOrder = SortOrder.CREATED
                                            settingsRepository?.documentsSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_by_name)) },
                                        onClick = {
                                            sortOrder = SortOrder.NAME
                                            settingsRepository?.documentsSortOrder = sortOrder.name
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                            IconButton(onClick = {
                                isGridView = !isGridView
                                settingsRepository?.documentsViewGrid = isGridView
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
                                            viewModel.loadItems()
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
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_document)) },
                            onClick = {
                                showAddMenu = false
                                AppLockGrace.beginExternalTask()
                                filePickerLauncher.launch("*/*")
                            },
                            leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) }
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
                if (uiState.isLoading && uiState.items.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_documents),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val itemComposable: @Composable (DocumentItem) -> Unit = { item ->
                        Box(
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (selection.isActive) {
                                        selection.toggle(item.id)
                                    } else if (item.isFolder) {
                                        if (item.isLocked) showPasswordPrompt = item
                                        else viewModel.openFolder(item)
                                    } else {
                                        isDownloading = true
                                        viewModel.downloadFile(item.filePath) { bytes ->
                                            isDownloading = false
                                            if (bytes != null) {
                                                openDocument(context, item.name, bytes, item.mimeType)
                                            }
                                        }
                                    }
                                },
                                onLongClick = { selection.toggle(item.id) }
                            )
                        ) {
                            if (isGridView) {
                                DocumentItemCard(item = item)
                            } else {
                                DocumentItemRow(item = item)
                            }
                            SelectionOverlay(selectionMode = selection.isActive, selected = item.id in selection)
                        }
                    }

                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedItems, key = { it.id }) { item ->
                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains(item.id),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - item.id
                                        viewModel.deleteItem(item)
                                    }
                                ) {
                                    itemComposable(item)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedItems, key = { it.id }) { item ->
                                ZoomAnimatedItem(
                                    isRemoving = removingItemIds.value.contains(item.id),
                                    onRemoved = {
                                        removingItemIds.value = removingItemIds.value - item.id
                                        viewModel.deleteItem(item)
                                    }
                                ) {
                                    itemComposable(item)
                                }
                            }
                        }
                    }
                }

                if (uiState.isUploading || isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (isInSubfolder) {
        content()
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                AppDrawerContent(
                    selectedItem = DrawerItem.DOCUMENTS,
                    onItemClick = { item ->
                        scope.launch { drawerState.close() }
                        when (item) {
                            DrawerItem.NOTES -> onNotes()
                            DrawerItem.GALLERY -> onGallery()
                            DrawerItem.DOCUMENTS -> { /* Already here */ }
                        }
                    }
                )
            }
        ) {
            content()
        }
    }
}

@Composable
private fun DocumentItemCard(item: DocumentItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (item.isFolder) {
                Box {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (item.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.BottomEnd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Icon(
                    iconForMimeType(item.mimeType),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = item.name,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DocumentItemRow(item: DocumentItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isFolder) {
                Box {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (item.isLocked) {
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
            } else {
                Icon(
                    iconForMimeType(item.mimeType),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.name,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NewFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var folderName by remember { mutableStateOf("") }
    SecureAlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { if (folderName.isNotBlank()) onConfirm(folderName.trim()) },
                enabled = folderName.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun iconForMimeType(mimeType: String) = when {
    mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
    mimeType.contains("text") -> Icons.Default.Description
    else -> Icons.Default.InsertDriveFile
}

private fun openDocument(context: android.content.Context, name: String, bytes: ByteArray, mimeType: String) {
    try {
        val cacheDir = File(context.cacheDir, Constants.SHARED_DOCS_DIR)
        cacheDir.mkdirs()
        val file = File(cacheDir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        AppLockGrace.beginExternalTask()
        context.startActivity(intent)
    } catch (_: Exception) {
        AppLockGrace.cancelExternalTask()
    }
}

@Composable
private fun DocumentPreviewDialog(
    name: String,
    mimeType: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fileName by remember { mutableStateOf(name) }
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    iconForMimeType(mimeType),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.file_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (fileName.isNotBlank()) onConfirm(fileName.trim()) },
                enabled = fileName.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DocFolderPickerDialog(
    viewModel: DocumentsViewModel,
    initialParentId: String = "",
    initialParentName: String = "",
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onLockedFolderClick: (DocumentItem) -> Unit = {}
) {
    var currentParentId by remember(initialParentId) { mutableStateOf(initialParentId) }
    var currentParentName by remember(initialParentName) { mutableStateOf(initialParentName) }
    var folderStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var folders by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentParentId) {
        isLoading = true
        folders = viewModel.getFolders(currentParentId)
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

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                name = it.getString(index)
            }
        }
    }
    return name ?: "DOC_${System.currentTimeMillis()}"
}
