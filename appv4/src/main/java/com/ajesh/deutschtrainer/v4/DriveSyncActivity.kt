package com.ajesh.deutschtrainer.v4

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.util.concurrent.Executors

class DriveSyncActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private var pendingAction: String = "sync"
    private var pendingLocalDb: String? = null

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val result = authorizationClient.getAuthorizationResultFromIntent(activityResult.data)
            handleAuthorized(result)
        } catch (e: ApiException) {
            sendError("Google Drive authorization was not completed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setSupportZoom(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            }
            addJavascriptInterface(DriveBridge(), "DriveNative")
            loadUrl("file:///android_asset/drive-sync.html")
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    inner class DriveBridge {
        @JavascriptInterface
        fun getStatus(): String = statusJson().toString()

        @JavascriptInterface
        fun connect(localDbJson: String) {
            requestAuthorization("sync", localDbJson, selectAccount = true)
        }

        @JavascriptInterface
        fun syncNow(localDbJson: String) {
            requestAuthorization("sync", localDbJson, selectAccount = false)
        }

        @JavascriptInterface
        fun restoreFromDrive() {
            requestAuthorization("restore", null, selectAccount = false)
        }

        @JavascriptInterface
        fun keepLocal(localDbJson: String) {
            requestAuthorization("force_upload", localDbJson, selectAccount = false)
        }

        @JavascriptInterface
        fun useDriveCopy() {
            requestAuthorization("force_restore", null, selectAccount = false)
        }

        @JavascriptInterface
        fun setAutoSync(enabled: Boolean) {
            prefs.edit().putBoolean("auto_sync", enabled).apply()
            sendStatus()
        }

        @JavascriptInterface
        fun disconnect() {
            prefs.edit()
                .remove("connected")
                .remove("email")
                .remove("name")
                .remove("last_hash")
                .remove("last_sync_at")
                .remove("last_remote_updated_at")
                .apply()
            sendStatus()
        }

        @JavascriptInterface
        fun close() {
            runOnUiThread { finish() }
        }
    }

    private fun requestAuthorization(action: String, localDbJson: String?, selectAccount: Boolean) {
        pendingAction = action
        pendingLocalDb = localDbJson
        runOnUiThread {
            val scopes = listOf(
                Scope(Scopes.DRIVE_APPFOLDER),
                Scope("openid"),
                Scope("email"),
                Scope("profile")
            )
            val builder = AuthorizationRequest.builder().setRequestedScopes(scopes)
            if (selectAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            authorizationClient.authorize(builder.build())
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent == null) {
                            sendError("Google Drive needs permission, but Android did not provide a consent screen.")
                        } else {
                            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        }
                    } else {
                        handleAuthorized(result)
                    }
                }
                .addOnFailureListener { error ->
                    sendError(friendlyDriveError(error))
                }
        }
    }

    private fun handleAuthorized(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            sendError("Google Drive authorization succeeded but no access token was returned.")
            return
        }
        executor.execute {
            runCatching {
                val rest = DriveRest(token)
                val user = runCatching { rest.userInfo() }.getOrNull()
                prefs.edit()
                    .putBoolean("connected", true)
                    .putString("email", user?.email.orEmpty())
                    .putString("name", user?.name.orEmpty())
                    .apply()

                when (pendingAction) {
                    "restore", "force_restore" -> restore(rest)
                    "force_upload" -> forceUpload(rest, pendingLocalDb ?: error("The phone data is unavailable."))
                    else -> safeSync(rest, pendingLocalDb ?: error("The phone data is unavailable."))
                }
            }.onFailure { error -> sendError(friendlyDriveError(error)) }
        }
    }

    private fun safeSync(rest: DriveRest, localDbJson: String) {
        val localHash = DriveRest.sha256(localDbJson)
        val remote = rest.loadSnapshot()
        val lastHash = prefs.getString("last_hash", null)

        if (remote == null) {
            val uploaded = rest.uploadSnapshot(localDbJson)
            recordSync(uploaded)
            sendComplete("Your learning data was uploaded to Google Drive.")
            return
        }

        if (remote.payloadHash == localHash) {
            recordSync(remote)
            sendComplete("Everything is already up to date.")
            return
        }

        if (lastHash == null) {
            sendConflict(remote.updatedAt, "Google Drive already has DeutschTrainer data. Choose which copy to keep for this first link.")
            return
        }

        val localChanged = localHash != lastHash
        val remoteChanged = remote.payloadHash != lastHash
        when {
            localChanged && !remoteChanged -> {
                val uploaded = rest.uploadSnapshot(localDbJson, remote.fileId)
                recordSync(uploaded)
                sendComplete("Phone changes were synced to Google Drive.")
            }
            !localChanged && remoteChanged -> applyRemote(remote, "Newer Google Drive data was restored to this phone.")
            !localChanged && !remoteChanged -> {
                recordSync(remote)
                sendComplete("Everything is already up to date.")
            }
            else -> sendConflict(remote.updatedAt, "Both this phone and Google Drive changed since the last sync. DeutschTrainer will not overwrite either copy automatically.")
        }
    }

    private fun forceUpload(rest: DriveRest, localDbJson: String) {
        val remote = rest.loadSnapshot()
        val uploaded = rest.uploadSnapshot(localDbJson, remote?.fileId)
        recordSync(uploaded)
        sendComplete("This phone's learning data is now the Google Drive copy.")
    }

    private fun restore(rest: DriveRest) {
        val remote = rest.loadSnapshot() ?: error("No DeutschTrainer cloud data was found in this Google Drive account.")
        applyRemote(remote, "Google Drive data was restored to this phone.")
    }

    private fun applyRemote(remote: RemoteSnapshot, message: String) {
        recordSync(remote)
        prefs.edit().putBoolean("restore_applied", true).apply()
        callJs("window.DriveCallbacks.restoreData(${JSONObject.quote(remote.dbJson)}, ${JSONObject.quote(message)});")
    }

    private fun recordSync(remote: RemoteSnapshot) {
        prefs.edit()
            .putBoolean("connected", true)
            .putString("last_hash", remote.payloadHash)
            .putLong("last_sync_at", System.currentTimeMillis())
            .putLong("last_remote_updated_at", remote.updatedAt)
            .apply()
    }

    private fun statusJson(): JSONObject = JSONObject()
        .put("connected", prefs.getBoolean("connected", false))
        .put("email", prefs.getString("email", "").orEmpty())
        .put("name", prefs.getString("name", "").orEmpty())
        .put("lastSyncAt", prefs.getLong("last_sync_at", 0L))
        .put("lastRemoteUpdatedAt", prefs.getLong("last_remote_updated_at", 0L))
        .put("autoSync", prefs.getBoolean("auto_sync", true))
        .put("scope", Scopes.DRIVE_APPFOLDER)

    private fun sendStatus() {
        callJs("window.DriveCallbacks.status(${statusJson()});")
    }

    private fun sendComplete(message: String) {
        callJs("window.DriveCallbacks.syncComplete(${JSONObject.quote(message)}, ${statusJson()});")
    }

    private fun sendConflict(remoteUpdatedAt: Long, message: String) {
        callJs("window.DriveCallbacks.conflict($remoteUpdatedAt, ${JSONObject.quote(message)});")
    }

    private fun sendError(message: String) {
        callJs("window.DriveCallbacks.error(${JSONObject.quote(message)});")
    }

    private fun callJs(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun friendlyDriveError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("401") || raw.contains("403") || raw.contains("permission", true) ->
                "Google Drive permission was denied or expired. Connect Drive again and allow DeutschTrainer to manage its private app data."
            raw.contains("429") || raw.contains("rate", true) ->
                "Google Drive is temporarily rate-limited. Wait a moment and try again."
            raw.contains("Unable to resolve host", true) || raw.contains("network", true) || raw.contains("connect", true) ->
                "DeutschTrainer could not reach Google Drive. Check your internet connection and try again."
            else -> "Google Drive sync failed. Your phone data has not been deleted.\n\n${raw.take(320)}"
        }
    }

    companion object {
        const val PREFS = "drive_sync_v4"
    }
}
