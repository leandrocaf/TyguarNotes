package com.lcaf.tyguarnotes.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveRestClientTest {

    private class FakeTokens : AccessTokenProvider {
        var current = "token-1"
        val invalidated = mutableListOf<String>()
        override suspend fun token() = current
        override suspend fun invalidate(token: String) {
            invalidated += token
            current = "token-2"
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private lateinit var client: DriveRestClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokens()
        client = DriveRestClient(tokens, baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun smallCreateIsMultipartInAppDataFolder() = runTest {
        server.enqueue(json("""{"id":"abc","name":"note_1.json","md5Checksum":"ff","appProperties":{"type":"record"}}"""))

        val file = client.create("note_1.json", mapOf("type" to "record"), "{}".toByteArray(), "application/json")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/upload/drive/v3/files", request.requestUrl!!.encodedPath)
        assertEquals("multipart", request.requestUrl!!.queryParameter("uploadType"))
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/related"))
        assertTrue(request.body.readUtf8().contains("\"parents\":[\"appDataFolder\"]"))
        assertEquals(RemoteFile("abc", "note_1.json", mapOf("type" to "record"), "ff"), file)
    }

    @Test
    fun largeUploadUsesResumableSession() = runTest {
        server.enqueue(MockResponse().setHeader("Location", server.url("/session/1").toString()))
        server.enqueue(json("""{"id":"big","name":"blob"}"""))

        client.update("big", emptyMap(), ByteArray(5 * 1024 * 1024 + 1), "application/octet-stream")

        val start = server.takeRequest()
        assertEquals("PATCH", start.method)
        assertEquals("resumable", start.requestUrl!!.queryParameter("uploadType"))
        assertEquals("application/octet-stream", start.getHeader("X-Upload-Content-Type"))
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/session/1", put.path)
        assertEquals(5L * 1024 * 1024 + 1, put.bodySize)
    }

    @Test
    fun expiredTokenIsRefreshedOnce() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"startPageToken":"42"}"""))

        assertEquals("42", client.getStartPageToken())
        assertEquals(listOf("token-1"), tokens.invalidated)
        server.takeRequest()
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun changesAreParsedIncludingRemovalsAndTrash() = runTest {
        server.enqueue(
            json(
                """{"newStartPageToken":"9","changes":[
                    {"fileId":"a","removed":false,"file":{"id":"a","name":"x","appProperties":{"type":"blob","path":"p"}}},
                    {"fileId":"b","removed":true},
                    {"fileId":"c","removed":false,"file":{"id":"c","name":"y","trashed":true}}
                ]}"""
            )
        )

        val page = client.listChanges("5")

        val request = server.takeRequest()
        assertEquals("appDataFolder", request.requestUrl!!.queryParameter("spaces"))
        assertEquals("5", request.requestUrl!!.queryParameter("pageToken"))
        assertEquals("9", page.newStartPageToken)
        assertNull(page.nextPageToken)
        assertEquals(mapOf("type" to "blob", "path" to "p"), page.changes[0].file!!.appProperties)
        assertEquals(listOf(false, true, true), page.changes.map { it.removed })
    }

    @Test(expected = DriveNotFoundException::class)
    fun updatingMissingFileThrowsNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        client.update("gone", emptyMap(), byteArrayOf(1), "application/octet-stream")
    }

    @Test
    fun deletingMissingFileSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        client.delete("gone")
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun metadataUpdateDoesNotMoveFile() = runTest {
        server.enqueue(json("""{"id":"abc","name":"n"}"""))
        client.update("abc", mapOf("updatedAt" to "1"), "{}".toByteArray(), "application/json")
        val body = server.takeRequest().body.readUtf8()
        val metadata = JSONObject(body.lines().first { it.startsWith("{") })
        assertTrue(!metadata.has("parents"))
        assertEquals("1", metadata.getJSONObject("appProperties").getString("updatedAt"))
    }

    @Test
    fun findByTypeQueriesAppProperties() = runTest {
        server.enqueue(json("""{"files":[{"id":"v","name":"vault","appProperties":{"type":"vault"}}]}"""))

        val files = client.findByType("vault")

        val q = server.takeRequest().requestUrl!!.queryParameter("q")
        assertEquals("appProperties has { key='type' and value='vault' }", q)
        assertEquals(listOf("v"), files.map { it.id })
    }

    @Test
    fun storageQuotaHandlesUnlimitedAccounts() = runTest {
        server.enqueue(json("""{"storageQuota":{"limit":"16106127360","usage":"8800000000"}}"""))
        server.enqueue(json("""{"storageQuota":{"usage":"42"}}"""))

        assertEquals(StorageQuota(usage = 8_800_000_000, limit = 16_106_127_360), client.getStorageQuota())
        assertEquals("/drive/v3/about", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(StorageQuota(usage = 42, limit = null), client.getStorageQuota())
    }

    @Test
    fun appDataBytesSumsAllPages() = runTest {
        server.enqueue(json("""{"nextPageToken":"p2","files":[{"quotaBytesUsed":"100"},{"quotaBytesUsed":"50"}]}"""))
        server.enqueue(json("""{"files":[{"quotaBytesUsed":"7"},{}]}"""))

        assertEquals(157L, client.appDataBytes())
        val first = server.takeRequest().requestUrl!!
        assertEquals("appDataFolder", first.queryParameter("spaces"))
        assertEquals("p2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }
}
