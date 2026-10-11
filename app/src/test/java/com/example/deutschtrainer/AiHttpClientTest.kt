package com.example.deutschtrainer

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiHttpClientTest {
    private val url = URL("https://example.invalid/grade")

    private class Reply(url: URL, private val status: Int, private val text: String) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var released = false
        override fun connect() = Unit
        override fun disconnect() { released = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        override fun getErrorStream() = inputStream
    }

    @Test fun temporaryOutageRetriesTheSameAnswerAndReleasesConnections() {
        val replies = listOf(Reply(url, 503, "busy"), Reply(url, 502, "busy"), Reply(url, 200, "{\"score\":9}"))
        var calls = 0
        val pauses = mutableListOf<Long>()
        val client = AiHttpClient({ replies[calls++] }, { pauses.add(it) })
        val body = "{\"answer\":\"Ich möchte üben.\"}"
        assertEquals("{\"score\":9}", client.post(url, body, mapOf("x-goog-api-key" to "test-secret"), 120_000))
        assertEquals(listOf(1000L, 2000L), pauses)
        assertEquals(3, calls)
        replies.forEach {
            assertEquals(body, it.sent.toString("UTF-8"))
            assertEquals("test-secret", it.getRequestProperty("x-goog-api-key"))
            assertEquals(120_000, it.readTimeout)
            assertTrue(it.released)
        }
    }

    @Test fun exhaustedRetriesKeepAnActionableError() {
        val replies = List(3) { Reply(url, 503, "high demand") }
        var calls = 0
        val client = AiHttpClient({ replies[calls++] }, {})
        val result = runCatching { client.post(url, "answer", emptyMap()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("try again"))
        assertEquals(3, calls)
        assertTrue(replies.all { it.released })
    }

    @Test fun rejectedKeyIsNotRetriedOrIncludedInTheError() {
        val reply = Reply(url, 403, "rejected test-secret")
        var calls = 0
        val client = AiHttpClient({ calls++; reply }, { fail("Must not retry rejected keys") })
        val result = runCatching { client.post(url, "answer", mapOf("x-goog-api-key" to "test-secret")) }
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()!!.message!!.contains("test-secret"))
        assertEquals(1, calls)
        assertTrue(reply.released)
    }

    @Test fun quotaFailureDoesNotAutomaticallySpendMoreRequests() {
        val reply = Reply(url, 429, "quota reached")
        var calls = 0
        val client = AiHttpClient({ calls++; reply }, { fail("Must not retry quota failures") })
        val result = runCatching { client.post(url, "answer", emptyMap()) }
        assertTrue(result.exceptionOrNull()!!.message!!.contains("quota"))
        assertEquals(1, calls)
        assertTrue(reply.released)
    }
}
