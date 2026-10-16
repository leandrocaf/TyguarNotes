package com.lcaf.tyguarnotes.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.data.repository.SettingsRepository
import com.lcaf.tyguarnotes.data.repository.UnlockStep
import com.lcaf.tyguarnotes.ui.components.PinPadScreen
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.documents.DocumentsScreen
import com.lcaf.tyguarnotes.ui.gallery.GalleryScreen
import com.lcaf.tyguarnotes.ui.gallery.GalleryViewerScreen
import com.lcaf.tyguarnotes.ui.login.LoginScreen
import com.lcaf.tyguarnotes.ui.notes.ListNotesScreen
import com.lcaf.tyguarnotes.ui.notes.NoteScreen
import com.lcaf.tyguarnotes.ui.notes.ViewImageScreen
import com.lcaf.tyguarnotes.ui.settings.SettingsScreen
import com.lcaf.tyguarnotes.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val NOTES_LIST = "notes_list"
    const val NOTE_DETAIL = "note_detail/{noteId}?parentId={parentId}&type={type}"
    const val SETTINGS = "settings"
    const val VIEW_IMAGE = "view_image"
    const val GALLERY = "gallery"
    const val GALLERY_VIEWER = "gallery_viewer/{startIndex}"
    const val DOCUMENTS = "documents"

    fun galleryViewer(startIndex: Int) = "gallery_viewer/$startIndex"

    const val NEW_NOTE_ID = "new"
    fun noteDetail(noteId: String, parentId: String = "", type: String = Note.TYPE_TEXT) =
        "note_detail/${noteId.ifEmpty { NEW_NOTE_ID }}?parentId=$parentId&type=$type"
}

@Composable
fun AppNavigation(
    settingsRepository: SettingsRepository,
    sharedText: String? = null,
    sharedImageBytes: ByteArray? = null,
    sharedImageUri: Uri? = null,
    openNoteId: String? = null,
    onOpenNoteConsumed: () -> Unit = {},
    onSharedImageConsumed: () -> Unit = {},
    sharedDocBytes: ByteArray? = null,
    sharedDocName: String? = null,
    sharedDocMimeType: String? = null,
    onSharedDocConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    // Shared state for passing image bytes to ViewImageScreen
    var noteViewImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Shared state for gallery viewer (swipe between images)
    var galleryImages by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }

    // Shared text for creating note from external app
    var pendingSharedText by remember { mutableStateOf(sharedText) }

    // Shared image bytes for adding to gallery from external app
    var pendingImageBytes by remember { mutableStateOf(sharedImageBytes) }
    var pendingImageUri by remember { mutableStateOf(sharedImageUri) }

    // Shared document for adding to documents from external app
    var pendingDocBytes by remember { mutableStateOf(sharedDocBytes) }
    var pendingDocName by remember { mutableStateOf(sharedDocName) }
    var pendingDocMimeType by remember { mutableStateOf(sharedDocMimeType) }

    val settingsViewModel = remember {
        SettingsViewModel(settingsRepository)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.NOTES_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                    if (pendingImageBytes != null) {
                        navController.navigate(Routes.GALLERY)
                    } else if (pendingDocBytes != null) {
                        navController.navigate(Routes.DOCUMENTS)
                    } else if (pendingSharedText != null) {
                        navController.navigate(Routes.noteDetail(""))
                    }
                }
            )
        }

        composable(Routes.NOTES_LIST) {
            // Auto-navigate to new note if shared text is pending
            LaunchedEffect(pendingSharedText) {
                if (pendingSharedText != null) {
                    navController.navigate(Routes.noteDetail(""))
                }
            }

            // A tapped reminder opens its note. A link must not skip the PINs, so every protected
            // folder on the way and the note itself are asked first, outermost first.
            var pendingUnlock by remember { mutableStateOf<PendingNoteUnlock?>(null) }
            LaunchedEffect(openNoteId) {
                val noteId = openNoteId ?: return@LaunchedEffect
                val email = Repositories.auth.currentUserEmail
                if (email != null && Repositories.notes.getNote(email, noteId).isSuccess) {
                    val steps = Repositories.notes.getUnlockSteps(email, noteId)
                    if (steps.isEmpty()) navController.navigate(Routes.noteDetail(noteId))
                    else pendingUnlock = PendingNoteUnlock(noteId, steps)
                }
                onOpenNoteConsumed()
            }

            pendingUnlock?.let { unlock ->
                val step = unlock.steps[unlock.index]
                val wrongPinText = stringResource(R.string.wrong_password)
                var pinError by remember(unlock.index) { mutableStateOf<String?>(null) }
                val action = stringResource(if (step.isFolder) R.string.unlock_folder else R.string.unlock_note)
                PinPadScreen(
                    title = if (step.name.isBlank()) action else "$action - ${step.name}",
                    errorMessage = pinError,
                    onConfirm = { entered ->
                        when {
                            entered != step.pin -> pinError = wrongPinText
                            unlock.index + 1 < unlock.steps.size -> pendingUnlock = unlock.copy(index = unlock.index + 1)
                            else -> {
                                pendingUnlock = null
                                navController.navigate(Routes.noteDetail(unlock.noteId))
                            }
                        }
                    },
                    onBack = { pendingUnlock = null }
                )
                return@composable
            }

            ListNotesScreen(
                onNoteClick = { noteId ->
                    navController.navigate(Routes.noteDetail(noteId))
                },
                onNewNote = { parentId ->
                    navController.navigate(Routes.noteDetail("", parentId))
                },
                onNewChecklist = { parentId ->
                    navController.navigate(Routes.noteDetail("", parentId, Note.TYPE_CHECKLIST))
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onGallery = {
                    navController.navigate(Routes.GALLERY)
                },
                onDocuments = {
                    navController.navigate(Routes.DOCUMENTS)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                settingsRepository = settingsRepository
            )
        }

        composable(
            route = Routes.NOTE_DETAIL,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("type") { type = NavType.StringType; defaultValue = Note.TYPE_TEXT }
            ),
            enterTransition = {
                fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.85f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.85f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            val parentId = backStackEntry.arguments?.getString("parentId") ?: ""
            val noteType = backStackEntry.arguments?.getString("type") ?: Note.TYPE_TEXT
            val effectiveNoteId = if (noteId == Routes.NEW_NOTE_ID) "" else noteId
            val textToShare = pendingSharedText
            NoteScreen(
                noteId = effectiveNoteId,
                parentId = parentId,
                noteType = noteType,
                initialDescription = if (effectiveNoteId.isEmpty()) textToShare else null,
                onNavigateBack = {
                    pendingSharedText = null
                    navController.popBackStack()
                },
                onViewImage = { bytes ->
                    noteViewImageBytes = bytes
                    navController.navigate(Routes.VIEW_IMAGE)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsViewModel
            )
        }

        composable(
            route = Routes.VIEW_IMAGE,
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = tween(300))
            }
        ) {
            val bytes = noteViewImageBytes
            if (bytes != null) {
                ViewImageScreen(
                    imageBytes = bytes,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { navController.popBackStack() },
                onNotes = {
                    navController.navigate(Routes.NOTES_LIST) {
                        popUpTo(Routes.NOTES_LIST) { inclusive = true }
                    }
                },
                onDocuments = {
                    navController.navigate(Routes.DOCUMENTS) {
                        popUpTo(Routes.NOTES_LIST)
                    }
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onViewImage = { images, startIndex ->
                    galleryImages = images
                    navController.navigate(Routes.galleryViewer(startIndex))
                },
                pendingImageBytes = pendingImageBytes,
                pendingImageUri = pendingImageUri,
                onPendingImageConsumed = {
                    pendingImageBytes = null
                    pendingImageUri = null
                    onSharedImageConsumed()
                },
                settingsRepository = settingsRepository
            )
        }

        composable(
            route = Routes.GALLERY_VIEWER,
            arguments = listOf(navArgument("startIndex") { type = NavType.IntType }),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            val galleryRepository = remember { Repositories.gallery }
            val authRepository = remember { Repositories.auth }
            val scope = rememberCoroutineScope()
            GalleryViewerScreen(
                images = galleryImages,
                startIndex = startIndex,
                onBack = { navController.popBackStack() },
                onDelete = { item ->
                    scope.launch {
                        val email = authRepository.currentUserEmail ?: return@launch
                        galleryRepository.deleteItem(email, item)
                    }
                },
                onRotate = { item, rotatedBytes ->
                    scope.launch {
                        val email = authRepository.currentUserEmail ?: return@launch
                        galleryRepository.replaceImage(email, item.imagePath, rotatedBytes)
                    }
                }
            )
        }

        composable(Routes.DOCUMENTS) {
            DocumentsScreen(
                onBack = { navController.popBackStack() },
                onNotes = {
                    navController.navigate(Routes.NOTES_LIST) {
                        popUpTo(Routes.NOTES_LIST) { inclusive = true }
                    }
                },
                onGallery = {
                    navController.navigate(Routes.GALLERY) {
                        popUpTo(Routes.NOTES_LIST)
                    }
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                pendingDocBytes = pendingDocBytes,
                pendingDocName = pendingDocName,
                pendingDocMimeType = pendingDocMimeType,
                onPendingDocConsumed = {
                    pendingDocBytes = null
                    pendingDocName = null
                    pendingDocMimeType = null
                    onSharedDocConsumed()
                },
                settingsRepository = settingsRepository
            )
        }
    }
}

/** A note to open once every PIN on the way to it ([index] is the one being asked) has been entered. */
private data class PendingNoteUnlock(val noteId: String, val steps: List<UnlockStep>, val index: Int = 0)
