package com.example.deutschtrainer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle

class AiErrorDialogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "AI request failed"
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: "The AI service could not complete the request. Please try again."

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Close") { _, _ -> finish() }
            .setPositiveButton("Open app") { _, _ ->
                startActivity(
                    Intent(this, TrainerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"

        fun show(context: Context, operation: String, error: Throwable) {
            val raw = error.message.orEmpty()
            val friendly = when {
                raw.contains("401") || raw.contains("403") || raw.contains("API key", ignoreCase = true) ->
                    "The AI service rejected the API key. Open Settings and check the key for the selected model."
                raw.contains("429") || raw.contains("quota", ignoreCase = true) || raw.contains("rate", ignoreCase = true) ->
                    "The AI service is temporarily rate-limited or the quota has been reached. Wait a little and try again."
                raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
                    "The AI service took too long to respond. Check your internet connection and try again."
                raw.contains("Unable to resolve host", ignoreCase = true) ||
                    raw.contains("network", ignoreCase = true) ||
                    raw.contains("connect", ignoreCase = true) ->
                    "The app could not reach the AI service. Check your internet connection and try again."
                raw.contains("500") || raw.contains("502") || raw.contains("503") || raw.contains("server", ignoreCase = true) ->
                    "The AI service is temporarily unavailable. Your answer and learning data are safe; please try again shortly."
                else ->
                    "The AI service could not complete $operation. Your learning data is safe. Please try again.\n\n${raw.take(280)}"
            }

            context.startActivity(
                Intent(context, AiErrorDialogActivity::class.java).apply {
                    putExtra(EXTRA_TITLE, "AI service problem")
                    putExtra(EXTRA_MESSAGE, friendly)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
