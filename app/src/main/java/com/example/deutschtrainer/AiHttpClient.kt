package com.example.deutschtrainer

import java.net.HttpURLConnection
import java.net.URL

/** Retry only temporary server failures, never rejected keys or exhausted quotas. */
internal class AiHttpClient(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val pause: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun post(url: URL, body: String, headers: Map<String, String>, readTimeoutMs: Int = 60_000): String {
        for (attempt in 0..2) {
            val connection = connect(url).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = readTimeoutMs
                doOutput = true
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) return text
                if (code in setOf(502, 503, 504) && attempt < 2) {
                    // Disconnect before backing off so retries do not hold a socket open.
                    connection.disconnect()
                    pause(1_000L * (attempt + 1))
                    continue
                }
                var safe = text
                headers.filterKeys { it.equals("Authorization", true) || it.equals("x-goog-api-key", true) }
                    .values.forEach { value ->
                        safe = safe.replace(value, "[redacted]")
                            .replace(value.removePrefix("Bearer "), "[redacted]")
                    }
                val guidance = when (code) {
                    401, 403 -> "The API key was rejected. Check the key in Settings."
                    429 -> "The AI quota or rate limit was reached. Try again later."
                    502, 503, 504 -> "The AI service is busy. Your answer is safe; try again shortly."
                    404 -> "The selected AI model is unavailable. Choose another model in Settings."
                    else -> "AI request failed."
                }
                error("$guidance ($code) ${safe.take(300)}")
            } finally {
                connection.disconnect()
            }
        }
        error("AI request could not complete.")
    }
}
