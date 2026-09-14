package com.ajesh.deutschtrainer.v4

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class DriveUser(
    val email: String,
    val name: String
)

internal data class RemoteSnapshot(
    val fileId: String,
    val dbJson: String,
    val payloadHash: String,
    val updatedAt: Long,
    val modifiedTime: String?
)

internal class DriveRest(private val accessToken: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()

    fun userInfo(): DriveUser {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val body = execute(request)
        val obj = JSONObject(body)
        return DriveUser(
            email = obj.optString("email"),
            name = obj.optString("name")
        )
    }

    fun loadSnapshot(): RemoteSnapshot? {
        val query = URLEncoder.encode("name = '$FILE_NAME'", StandardCharsets.UTF_8.name())
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime,size)&pageSize=10"
        val listRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()
        val files = JSONObject(execute(listRequest)).optJSONArray("files") ?: return null
        if (files.length() == 0) return null

        var bestId: String? = null
        var bestModified: String? = null
        for (i in 0 until files.length()) {
            val item = files.optJSONObject(i) ?: continue
            val modified = item.optString("modifiedTime")
            if (bestId == null || modified > (bestModified ?: "")) {
                bestId = item.optString("id")
                bestModified = modified
            }
        }
        val fileId = bestId?.takeIf { it.isNotBlank() } ?: return null
        val getRequest = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val root = JSONObject(execute(getRequest))
        require(root.optString("format") == FORMAT) { "The Drive file is not a DeutschTrainer cloud snapshot." }
        val payload = root.optString("payload")
        require(payload.isNotBlank()) { "The Drive snapshot is empty." }
        val hash = root.optString("payloadHash").ifBlank { sha256(payload) }
        return RemoteSnapshot(
            fileId = fileId,
            dbJson = payload,
            payloadHash = hash,
            updatedAt = root.optLong("updatedAt", 0L),
            modifiedTime = bestModified
        )
    }

    fun uploadSnapshot(localDbJson: String, existingFileId: String? = null): RemoteSnapshot {
        val hash = sha256(localDbJson)
        val now = System.currentTimeMillis()
        val wrapper = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", 1)
            .put("updatedAt", now)
            .put("payloadHash", hash)
            .put("payload", localDbJson)
            .toString()

        val fileId = existingFileId ?: createFile()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $accessToken")
            .patch(wrapper.toRequestBody(JSON_MEDIA))
            .build()
        execute(request)

        return RemoteSnapshot(
            fileId = fileId,
            dbJson = localDbJson,
            payloadHash = hash,
            updatedAt = now,
            modifiedTime = null
        )
    }

    private fun createFile(): String {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id,name,modifiedTime")
            .header("Authorization", "Bearer $accessToken")
            .post(metadata.toRequestBody(JSON_MEDIA))
            .build()
        return JSONObject(execute(request)).optString("id")
            .takeIf { it.isNotBlank() }
            ?: error("Google Drive did not return a file id.")
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Google Drive request failed (${response.code}): ${text.take(600)}")
            }
            return text
        }
    }

    companion object {
        const val FILE_NAME = "deutschtrainer-v4-cloud.json"
        const val FORMAT = "deutschtrainer-v4-cloud"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
