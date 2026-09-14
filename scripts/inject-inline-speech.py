from pathlib import Path
import re

p = Path('appv4/src/main/java/com/ajesh/deutschtrainer/v4/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Imports for Android's in-process SpeechRecognizer. Unlike RecognizerIntent UI,
# this keeps the WebView/question visible while listening.
if 'import android.Manifest\n' not in s:
    s = s.replace('import android.app.Activity\n', 'import android.Manifest\nimport android.app.Activity\n', 1)
if 'import android.content.pm.PackageManager\n' not in s:
    s = s.replace('import android.content.Intent\n', 'import android.content.Intent\nimport android.content.pm.PackageManager\n', 1)
if 'import android.speech.RecognitionListener\n' not in s:
    s = s.replace(
        'import android.speech.RecognizerIntent\n',
        'import android.speech.RecognitionListener\nimport android.speech.RecognizerIntent\nimport android.speech.SpeechRecognizer\n',
        1,
    )

field = '    private var pendingSpeechId: String? = null\n'
if 'private var speechRecognizer: SpeechRecognizer?' not in s:
    if field not in s:
        raise SystemExit('Could not find speech field insertion point')
    s = s.replace(field, field + '    private var speechRecognizer: SpeechRecognizer? = null\n', 1)

# Destroy the native recognizer with the Activity.
needle = '        tts?.shutdown()\n        webView.destroy()\n'
if 'speechRecognizer?.destroy()' not in s:
    if needle not in s:
        raise SystemExit('Could not find onDestroy insertion point')
    s = s.replace(needle, '        tts?.shutdown()\n        speechRecognizer?.destroy()\n        speechRecognizer = null\n        webView.destroy()\n', 1)

# Permission callback. The microphone permission is requested only when the user
# taps Speak German for the first time.
activity_result_marker = '    @Deprecated("Deprecated in Android API; retained for broad compatibility")\n    override fun onActivityResult'
permission_block = '''    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {\n        super.onRequestPermissionsResult(requestCode, permissions, grantResults)\n        if (requestCode == REQ_AUDIO_PERMISSION) {\n            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {\n                beginInlineSpeech()\n            } else {\n                val id = pendingSpeechId ?: "speech"\n                sendSpeechResult(id, false, "Microphone permission is needed for voice answers. Your typed text was not changed.")\n                pendingSpeechId = null\n            }\n        }\n    }\n\n'''
if 'override fun onRequestPermissionsResult' not in s:
    if activity_result_marker not in s:
        raise SystemExit('Could not find permission callback insertion point')
    s = s.replace(activity_result_marker, permission_block + activity_result_marker, 1)

# Replace the old full-screen RecognizerIntent launcher with an in-process
# SpeechRecognizer bridge and a stop button.
pattern = re.compile(
    r'''        @JavascriptInterface\n        fun startSpeech\(requestId: String\) \{.*?\n        \}\n\n        @JavascriptInterface\n        fun speakGerman''',
    re.S,
)
replacement = '''        @JavascriptInterface\n        fun startSpeech(requestId: String) {\n            pendingSpeechId = requestId\n            runOnUiThread {\n                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {\n                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO_PERMISSION)\n                } else {\n                    beginInlineSpeech()\n                }\n            }\n        }\n\n        @JavascriptInterface\n        fun stopSpeech() {\n            runOnUiThread {\n                val id = pendingSpeechId\n                if (id != null) sendSpeechState(id, "processing", "")\n                speechRecognizer?.stopListening()\n            }\n        }\n\n        @JavascriptInterface\n        fun speakGerman'''
if not pattern.search(s):
    raise SystemExit('Could not locate old startSpeech block')
s = pattern.sub(replacement, s, count=1)

# Native inline recognizer and callbacks to the HTML prototype UI.
marker = '    private fun gemini(model: String, prompt: String, jsonMode: Boolean): String {\n'
helpers = '''    private fun beginInlineSpeech() {\n        val id = pendingSpeechId ?: return\n        if (!SpeechRecognizer.isRecognitionAvailable(this)) {\n            sendSpeechResult(id, false, "Speech recognition is unavailable on this phone. Your typed text was not changed.")\n            pendingSpeechId = null\n            return\n        }\n\n        speechRecognizer?.destroy()\n        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)\n        speechRecognizer = recognizer\n        recognizer.setRecognitionListener(object : RecognitionListener {\n            override fun onReadyForSpeech(params: Bundle?) { sendSpeechState(id, "listening", "") }\n            override fun onBeginningOfSpeech() { sendSpeechState(id, "listening", "") }\n            override fun onRmsChanged(rmsdB: Float) = Unit\n            override fun onBufferReceived(buffer: ByteArray?) = Unit\n            override fun onEndOfSpeech() { sendSpeechState(id, "processing", "") }\n\n            override fun onError(error: Int) {\n                val message = when (error) {\n                    SpeechRecognizer.ERROR_AUDIO -> "There was a microphone audio problem. Your typed text was not changed."\n                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice answers. Your typed text was not changed."\n                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition could not reach the speech service. Check your connection and try again."\n                    SpeechRecognizer.ERROR_NO_MATCH -> "I could not confidently recognize that speech. Nothing was added to your answer."\n                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is already busy. Wait a moment and try again."\n                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected. Your typed text was not changed."\n                    else -> "Voice recognition stopped unexpectedly. Your typed text was not changed."\n                }\n                sendSpeechResult(id, false, message)\n                pendingSpeechId = null\n                speechRecognizer?.destroy()\n                speechRecognizer = null\n            }\n\n            override fun onResults(results: Bundle?) {\n                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()\n                if (spoken.isBlank()) {\n                    sendSpeechResult(id, false, "No speech was recognized. Your typed text was not changed.")\n                } else {\n                    sendSpeechResult(id, true, spoken)\n                }\n                pendingSpeechId = null\n                speechRecognizer?.destroy()\n                speechRecognizer = null\n            }\n\n            override fun onPartialResults(partialResults: Bundle?) {\n                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()\n                if (partial.isNotBlank()) sendSpeechState(id, "partial", partial)\n            }\n\n            override fun onEvent(eventType: Int, params: Bundle?) = Unit\n        })\n\n        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {\n            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)\n            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")\n            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")\n            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)\n            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)\n        }\n        sendSpeechState(id, "starting", "")\n        recognizer.startListening(intent)\n    }\n\n    private fun sendSpeechState(id: String, state: String, payload: String) {\n        callJs("window.NativeCallbacks.speechState(${q(id)}, ${q(state)}, ${q(payload)});")\n    }\n\n    private fun sendSpeechResult(id: String, ok: Boolean, payload: String) {\n        callJs("window.NativeCallbacks.speechResult(${q(id)}, $ok, ${q(payload)});")\n    }\n\n'''
if 'private fun beginInlineSpeech()' not in s:
    if marker not in s:
        raise SystemExit('Could not find speech helper insertion point')
    s = s.replace(marker, helpers + marker, 1)

# Constant for runtime permission request.
const_marker = '        private const val REQ_SPEECH = 4103\n'
if 'REQ_AUDIO_PERMISSION' not in s.split('companion object', 1)[-1]:
    if const_marker not in s:
        raise SystemExit('Could not find companion constant insertion point')
    s = s.replace(const_marker, const_marker + '        private const val REQ_AUDIO_PERMISSION = 4104\n', 1)

p.write_text(s, encoding='utf-8')
print('Inline non-blocking speech recognition injected into MainActivity.kt')
