package com.lcaf.tyguarnotes.ui.gallery

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.lcaf.tyguarnotes.ui.util.Constants

/**
 * Deletes the device's original copy of an image that was moved into a locked folder.
 * Android requires the user to confirm deleting media owned by other apps, so most
 * of the time this yields a system confirmation to launch.
 */
object OriginalImageDeleter {

    sealed interface Outcome {
        data object Deleted : Outcome
        data class NeedsConsent(val intentSender: IntentSender) : Outcome

        /** The source is not a MediaStore item (e.g. a private file of the sharing app). */
        data object Unsupported : Outcome
    }

    fun request(context: Context, source: Uri): Outcome {
        val media = toMediaStoreUri(context, source)
        if (media == null) {
            Log.w(Constants.TAG, "Original image cannot be deleted: unsupported source ${source.authority}")
            return Outcome.Unsupported
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Outcome.NeedsConsent(MediaStore.createDeleteRequest(context.contentResolver, listOf(media)).intentSender)
        }
        return try {
            if (context.contentResolver.delete(media, null, null) > 0) Outcome.Deleted else Outcome.Unsupported
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                Outcome.NeedsConsent(e.userAction.actionIntent.intentSender)
            } else {
                Log.w(Constants.TAG, "Original image cannot be deleted", e)
                Outcome.Unsupported
            }
        }
    }

    /** Maps the URIs pickers and share sheets hand out to the underlying MediaStore item. */
    private fun toMediaStoreUri(context: Context, uri: Uri): Uri? = when {
        // Photo picker URIs are read-only views and cannot be deleted.
        uri.authority == MediaStore.AUTHORITY && "picker" !in uri.pathSegments -> uri
        uri.authority == MEDIA_DOCUMENTS_AUTHORITY &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            DocumentsContract.isDocumentUri(context, uri) ->
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        // Some gallery apps (e.g. Google Photos) wrap the MediaStore URI inside their own.
        else -> uri.pathSegments
            .firstOrNull { it.startsWith("content://${MediaStore.AUTHORITY}/") }
            ?.let(Uri::parse)
            ?.takeIf { "picker" !in it.pathSegments }
    }

    private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
}
