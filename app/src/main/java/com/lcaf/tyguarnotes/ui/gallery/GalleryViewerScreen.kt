package com.lcaf.tyguarnotes.ui.gallery

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.components.SecureAlertDialog
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryViewerScreen(
    images: List<GalleryItem>,
    startIndex: Int,
    onBack: () -> Unit,
    onDelete: ((GalleryItem) -> Unit)? = null,
    onRotate: ((GalleryItem, ByteArray) -> Unit)? = null
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val repository = remember { Repositories.gallery }
    val userEmail = remember { Repositories.auth.currentUserEmail }
    val cachedBytes = remember { mutableStateMapOf<String, ByteArray>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rotationAngles = remember { mutableStateMapOf<String, Int>() }

    if (showDeleteConfirm) {
        SecureAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_image_question)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val currentItem = images.getOrNull(pagerState.currentPage)
                    if (currentItem != null && onDelete != null) {
                        onDelete(currentItem)
                        onBack()
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${images.size}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        // Save rotations on exit
                        if (onRotate != null) {
                            rotationAngles.forEach { (imagePath, angle) ->
                                if (angle % 360 != 0) {
                                    val item = images.firstOrNull { it.imagePath == imagePath }
                                    val bytes = cachedBytes[imagePath]
                                    if (item != null && bytes != null) {
                                        scope.launch {
                                            val rotatedBytes = withContext(Dispatchers.Default) {
                                                rotateBytes(bytes, angle)
                                            }
                                            onRotate(item, rotatedBytes)
                                        }
                                    }
                                }
                            }
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (onRotate != null) {
                    IconButton(onClick = {
                        val currentItem = images.getOrNull(pagerState.currentPage) ?: return@IconButton
                        val currentAngle = rotationAngles[currentItem.imagePath] ?: 0
                        rotationAngles[currentItem.imagePath] = (currentAngle + 90) % 360
                    }) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) { page ->
            val item = images[page]
            var imageBytes by remember { mutableStateOf(cachedBytes[item.imagePath]) }
            var isLoading by remember { mutableStateOf(imageBytes == null) }

            LaunchedEffect(item.imagePath) {
                if (imageBytes == null) {
                    isLoading = true
                    val result = userEmail?.let {
                        withContext(Dispatchers.IO) { repository.downloadImage(it, item.imagePath) }
                    }
                    result?.onSuccess { bytes ->
                        cachedBytes[item.imagePath] = bytes
                        imageBytes = bytes
                    }
                    isLoading = false
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val bytes = imageBytes
                if (bytes != null) {
                    val angle = rotationAngles[item.imagePath] ?: 0
                    ZoomableImage(bytes = bytes, contentDescription = item.name, rotationDegrees = angle.toFloat())
                } else if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(bytes: ByteArray, contentDescription: String, rotationDegrees: Float = 0f) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset = Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y
            )
        } else {
            offset = Offset.Zero
        }
    }

    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                            }
                        }
                    )
                }
                .transformable(
                    state = transformableState,
                    canPan = { scale > 1f }
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = rotationDegrees
                }
        )
    }
}

private fun rotateBytes(bytes: ByteArray, degrees: Int): ByteArray {
    if (degrees % 360 == 0) return bytes
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    val outputStream = ByteArrayOutputStream()
    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
    return outputStream.toByteArray()
}
