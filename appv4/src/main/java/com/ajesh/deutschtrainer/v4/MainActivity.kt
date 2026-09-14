package com.ajesh.deutschtrainer.v4

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("html_v4_secure", MODE_PRIVATE) }
    private var pendingBackup: String? = null
    private var pendingSpeechId: String? = null
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setSupportZoom(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            }
            addJavascriptInterface(NativeBridge(), "DeutschNative")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.GERMANY
    }

    override fun onDestroy() {
        executor.shutdownNow()
        tts?.stop()
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API; retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_EXPORT -> {
                val uri = data?.data ?: return
                val text = pendingBackup ?: return
                runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Could not open the selected file")
                }.onSuccess {
                    callJs("window.NativeCallbacks.backupSaved(true, 'Backup saved successfully');")
                }.onFailure { e ->
                    callJs("window.NativeCallbacks.backupSaved(false, ${q(e.message ?: "Backup failed")});")
                }
                pendingBackup = null
            }
            REQ_IMPORT -> {
                val uri = data?.data ?: return
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the selected file")
                }.onSuccess { text ->
                    callJs("window.NativeCallbacks.importBackup(${q(text)});")
                }.onFailure { e ->
                    callJs("window.NativeCallbacks.importFailed(${q(e.message ?: "Restore failed")});")
                }
            }
            REQ_SPEECH -> {
                val id = pendingSpeechId ?: "speech"
                val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                callJs("window.NativeCallbacks.speechResult(${q(id)}, true, ${q(spoken)});")
                pendingSpeechId = null
            }
        }
    }

    inner class NativeBridge {
        @JavascriptInterface fun getAppMarker(): String = "DeutschTrainer V4 · HTML-FIRST"
        @JavascriptInterface fun getGeminiKey(): String = prefs.getString("gemini_key", "").orEmpty()
        @JavascriptInterface fun getDeepSeekKey(): String = prefs.getString("deepseek_key", "").orEmpty()
        @JavascriptInterface fun getSelectedModel(): String = prefs.getString("model", "gemini-3.8-flash").orEmpty()

        @JavascriptInterface fun saveGeminiKey(value: String) { prefs.edit().putString("gemini_key", value.trim()).apply() }
        @JavascriptInterface fun saveDeepSeekKey(value: String) { prefs.edit().putString("deepseek_key", value.trim()).apply() }
        @JavascriptInterface fun saveSelectedModel(value: String) { prefs.edit().putString("model", value).apply() }
        @JavascriptInterface fun clearGeminiKey() { prefs.edit().remove("gemini_key").apply() }
        @JavascriptInterface fun clearDeepSeekKey() { prefs.edit().remove("deepseek_key").apply() }

        @JavascriptInterface
        fun aiRequest(requestJson: String) {
            executor.execute {
                val request = runCatching { JSONObject(requestJson) }.getOrElse {
                    sendAiResult("unknown", false, "Invalid AI request")
                    return@execute
                }
                val id = request.optString("id", "request")
                runCatching {
                    val provider = request.optString("provider")
                    val model = request.optString("model")
                    val prompt = request.getString("prompt")
                    val jsonMode = request.optBoolean("jsonMode", false)
                    when (provider) {
                        "DEEPSEEK" -> deepSeek(model, prompt, jsonMode)
                        else -> gemini(model, prompt, jsonMode)
                    }
                }.onSuccess { text -> sendAiResult(id, true, text) }
                    .onFailure { e -> sendAiResult(id, false, friendlyError(e)) }
            }
        }

        @JavascriptInterface
        fun exportBackup(json: String, suggestedName: String) {
            pendingBackup = json
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, suggestedName.ifBlank { "deutschtrainer-profile-backup.json" })
                }
                startActivityForResult(intent, REQ_EXPORT)
            }
        }

        @JavascriptInterface
        fun importBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_IMPORT)
            }
        }

        @JavascriptInterface
        fun startSpeech(requestId: String) {
            pendingSpeechId = requestId
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your German translation")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                try {
                    startActivityForResult(intent, REQ_SPEECH)
                } catch (_: ActivityNotFoundException) {
                    val id = pendingSpeechId ?: requestId
                    callJs("window.NativeCallbacks.speechResult(${q(id)}, false, 'Speech recognition is unavailable on this phone');")
                    pendingSpeechId = null
                }
            }
        }

        @JavascriptInterface
        fun speakGerman(text: String) {
            runOnUiThread { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deutsch_v4") }
        }
    }

    private fun gemini(model: String, prompt: String, jsonMode: Boolean): String {
        val key = prefs.getString("gemini_key", "").orEmpty()
        check(key.isNotBlank()) { "Add your Gemini API key in Settings before continuing." }
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        )
        if (jsonMode) body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        return post(
            URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"),
            body,
            mapOf("Content-Type" to "application/json")
        ) { root ->
            root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                ?: error("Gemini returned no text")
        }
    }

    private fun deepSeek(model: String, prompt: String, jsonMode: Boolean): String {
        val key = prefs.getString("deepseek_key", "").orEmpty()
        check(key.isNotBlank()) { "Add your DeepSeek API key in Settings before continuing." }
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0.3)
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        return post(
            URL("https://api.deepseek.com/chat/completions"),
            body,
            mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $key")
        ) { root ->
            root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: error("DeepSeek returned no text")
        }
    }

    private fun post(url: URL, body: JSONObject, headers: Map<String, String>, parse: (JSONObject) -> String): String {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 75_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            c.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("AI request failed ($code): ${text.take(600)}")
            return parse(JSONObject(text))
        } finally { c.disconnect() }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("API key", true) || raw.contains("401") || raw.contains("403") -> "The AI service rejected the API key. Open Settings and check the key for the selected model."
            raw.contains("429") || raw.contains("quota", true) || raw.contains("rate", true) -> "The AI service is rate-limited or the quota has been reached. Wait a little and try again."
            raw.contains("timeout", true) || raw.contains("timed out", true) -> "The AI service took too long to respond. Check your connection and try again."
            raw.contains("Unable to resolve host", true) || raw.contains("network", true) || raw.contains("connect", true) -> "The app could not reach the AI service. Check your internet connection and try again."
            raw.contains("500") || raw.contains("502") || raw.contains("503") -> "The AI service is temporarily unavailable. Your learning data is safe; try again shortly."
            else -> "The AI service could not complete the request. Your learning data is safe.\n\n${raw.take(260)}"
        }
    }

    private fun sendAiResult(id: String, ok: Boolean, payload: String) {
        callJs("window.NativeCallbacks.aiResult(${q(id)}, $ok, ${q(payload)});")
    }

    private fun callJs(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun q(value: String): String = JSONObject.quote(value)

    companion object {
        private const val REQ_EXPORT = 4101
        private const val REQ_IMPORT = 4102
        private const val REQ_SPEECH = 4103
    }
}
