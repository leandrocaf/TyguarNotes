package com.lcaf.tyguarnotes.data.sync

/** A file in the Drive appDataFolder. */
data class RemoteFile(
    val id: String,
    val name: String,
    val appProperties: Map<String, String> = emptyMap(),
    val md5Checksum: String? = null
)

data class FilesPage(val files: List<RemoteFile>, val nextPageToken: String?)

data class RemoteChange(val fileId: String, val removed: Boolean, val file: RemoteFile?)

/** Exactly one of [nextPageToken] (more pages) or [newStartPageToken] (caught up) is set. */
data class ChangesPage(
    val changes: List<RemoteChange>,
    val nextPageToken: String?,
    val newStartPageToken: String?
)

/** Storage of the whole Google account (shared with Gmail and Photos). [limit] is null when unlimited. */
data class StorageQuota(val usage: Long, val limit: Long?)

class DriveNotFoundException(fileId: String) : Exception("Drive file not found: $fileId")

/** The user must sign in again to grant Drive access; background sync cannot fix it. */
class DriveAuthorizationRequiredException(cause: Throwable? = null) : Exception("Drive authorization required", cause)

/** Operations the sync engine needs, scoped to the hidden appDataFolder. */
interface DriveApi {
    suspend fun getStartPageToken(): String
    suspend fun listFiles(pageToken: String?): FilesPage
    suspend fun listChanges(pageToken: String): ChangesPage
    suspend fun getStorageQuota(): StorageQuota

    /** Bytes counted against the quota by everything in the appDataFolder. */
    suspend fun appDataBytes(): Long

    /** Files whose appProperties has type = [type]. */
    suspend fun findByType(type: String): List<RemoteFile>
    suspend fun download(fileId: String): ByteArray
    suspend fun create(name: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile
    /** @throws DriveNotFoundException if the file no longer exists. */
    suspend fun update(fileId: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile
    /** Succeeds if the file is already gone. */
    suspend fun delete(fileId: String)
}
