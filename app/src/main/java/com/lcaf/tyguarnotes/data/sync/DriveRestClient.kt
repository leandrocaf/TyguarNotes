package com.lcaf.tyguarnotes.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

interface AccessTokenProvider {
    suspend fun token(): String
    suspend fun invalidate(token: String)
}

/** Minimal Drive v3 REST client limited to the appDataFolder space. */
class DriveRestClient(
    private val tokens: AccessTokenProvider,
    private val http: OkHttpClient = OkHttpClient(),
    baseUrl: String = "https://www.googleapis.com/"
) : DriveApi {

    private val api = "${baseUrl}drive/v3/".toHttpUrl()
    private val upload = "${baseUrl}upload/drive/v3/".toHttpUrl()

    override suspend fun getStartPageToken(): String {
        val url = api.newBuilder().addPathSegments("changes/startPageToken").build()
        return getJson(url).getString("startPageToken")
    }

    override suspend fun listFiles(pageToken: String?): FilesPage {
        val url = api.newBuilder().addPathSegment("files")
            .addQueryParameter("spaces", SPACE)
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
            .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .build()
        val json = getJson(url)
        return FilesPage(
            files = json.optJSONArray("files").objects().map { it.toRemoteFile() },
            nextPageToken = json.optStringOrNull("nextPageToken")
        )
    }

    override suspend fun listChanges(pageToken: String): ChangesPage {
        val url = api.newBuilder().addPathSegment("changes")
            .addQueryParameter("pageToken", pageToken)
            .addQueryParameter("spaces", SPACE)
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("fields", "nextPageToken,newStartPageToken,changes(fileId,removed,file($FILE_FIELDS,trashed))")
            .build()
        val json = getJson(url)
        val changes = json.optJSONArray("changes").objects().map { change ->
            val file = change.optJSONObject("file")
            val removed = change.optBoolean("removed") || file?.optBoolean("trashed") == true
            RemoteChange(
                fileId = change.getString("fileId"),
                removed = removed,
                file = if (removed) null else file?.toRemoteFile()
            )
        }
        return ChangesPage(
            changes = changes,
            nextPageToken = json.optStringOrNull("nextPageToken"),
            newStartPageToken = json.optStringOrNull("newStartPageToken")
        )
    }

    override suspend fun getStorageQuota(): StorageQuota {
        val url = api.newBuilder().addPathSegment("about")
            .addQueryParameter("fields", "storageQuota(limit,usage)")
            .build()
        val quota = getJson(url).getJSONObject("storageQuota")
        return StorageQuota(
            usage = quota.optString("usage").toLongOrNull() ?: 0L,
            limit = quota.optStringOrNull("limit")?.toLongOrNull()
        )
    }

    override suspend fun appDataBytes(): Long {
        var total = 0L
        var pageToken: String? = null
        do {
            val url = api.newBuilder().addPathSegment("files")
                .addQueryParameter("spaces", SPACE)
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(quotaBytesUsed)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val json = getJson(url)
            total += json.optJSONArray("files").objects().sumOf { it.optString("quotaBytesUsed").toLongOrNull() ?: 0L }
            pageToken = json.optStringOrNull("nextPageToken")
        } while (pageToken != null)
        return total
    }

    override suspend fun findByType(type: String): List<RemoteFile> {
        val url = api.newBuilder().addPathSegment("files")
            .addQueryParameter("spaces", SPACE)
            .addQueryParameter("q", "appProperties has { key='type' and value='$type' }")
            .addQueryParameter("fields", "files($FILE_FIELDS)")
            .build()
        return getJson(url).optJSONArray("files").objects().map { it.toRemoteFile() }
    }

    override suspend fun download(fileId: String): ByteArray {
        val url = api.newBuilder().addPathSegment("files").addPathSegment(fileId)
            .addQueryParameter("alt", "media")
            .build()
        return execute(fileId) { it.url(url).get() }.use { it.body!!.bytes() }
    }

    override suspend fun create(name: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile {
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(SPACE))
            .put("appProperties", JSONObject(appProperties))
        return send("POST", upload.newBuilder().addPathSegment("files"), metadata, content, mimeType, fileId = name)
    }

    override suspend fun update(fileId: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile {
        val metadata = JSONObject().put("appProperties", JSONObject(appProperties))
        return send("PATCH", upload.newBuilder().addPathSegment("files").addPathSegment(fileId), metadata, content, mimeType, fileId)
    }

    override suspend fun delete(fileId: String) {
        val url = api.newBuilder().addPathSegment("files").addPathSegment(fileId).build()
        try {
            execute(fileId) { it.url(url).delete() }.close()
        } catch (_: DriveNotFoundException) {
        }
    }

    /** Uploads metadata + content: a single multipart request for small files, resumable otherwise. */
    private suspend fun send(
        method: String,
        url: HttpUrl.Builder,
        metadata: JSONObject,
        content: ByteArray,
        mimeType: String,
        fileId: String
    ): RemoteFile {
        url.addQueryParameter("fields", FILE_FIELDS)
        val metadataBody = metadata.toString().toRequestBody(JSON)
        val contentBody = content.toRequestBody(mimeType.toMediaType())

        if (content.size <= MULTIPART_LIMIT) {
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataBody)
                .addPart(contentBody)
                .build()
            val target = url.addQueryParameter("uploadType", "multipart").build()
            return execute(fileId) { it.url(target).method(method, body) }.use { it.json().toRemoteFile() }
        }

        val session = url.addQueryParameter("uploadType", "resumable").build()
        val location = execute(fileId) {
            it.url(session).method(method, metadataBody).header("X-Upload-Content-Type", mimeType)
        }.use { it.header("Location") ?: throw IOException("Drive did not return an upload URL") }
        return execute(fileId) { it.url(location).put(contentBody) }.use { it.json().toRemoteFile() }
    }

    private suspend fun getJson(url: HttpUrl): JSONObject =
        execute(url.toString()) { it.url(url).get() }.use { it.json() }

    /** Runs the request with a bearer token, refreshing it once on 401. */
    private suspend fun execute(fileId: String, build: (Request.Builder) -> Request.Builder): Response =
        withContext(Dispatchers.IO) {
            var token = tokens.token()
            var response = call(token, build)
            if (response.code == 401) {
                response.close()
                tokens.invalidate(token)
                token = tokens.token()
                response = call(token, build)
            }
            when {
                response.isSuccessful -> response
                response.code == 404 -> {
                    response.close()
                    throw DriveNotFoundException(fileId)
                }
                else -> {
                    val message = response.body?.string().orEmpty().take(500)
                    response.close()
                    throw IOException("Drive request failed: HTTP ${response.code} $message")
                }
            }
        }

    private fun call(token: String, build: (Request.Builder) -> Request.Builder): Response =
        http.newCall(build(Request.Builder()).header("Authorization", "Bearer $token").build()).execute()

    private fun Response.json() = JSONObject(body!!.string())

    private fun JSONObject.toRemoteFile(): RemoteFile {
        val props = optJSONObject("appProperties")
        return RemoteFile(
            id = getString("id"),
            name = optString("name"),
            appProperties = props?.keys()?.asSequence()?.associateWith { props.getString(it) }.orEmpty(),
            md5Checksum = optStringOrNull("md5Checksum")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val SPACE = "appDataFolder"
        const val FILE_FIELDS = "id,name,md5Checksum,appProperties"
        const val MULTIPART_LIMIT = 5 * 1024 * 1024
        val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
