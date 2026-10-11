package com.example.deutschtrainer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

/** Recording is deliberately separate from speech recognition: pauses never stop it. */
@Composable
fun RecordedAnswer(
    draftId: String,
    enabled: Boolean,
    loading: Boolean,
    serverError: String? = null,
    onBusy: (Boolean) -> Unit,
    onGrade: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val savedRecording = remember(draftId) { File(context.cacheDir, "speaking-answer-$draftId.m4a") }
    var file by remember(draftId) { mutableStateOf(savedRecording.takeIf { it.exists() && it.length() > 0L }) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentBusy by rememberUpdatedState(onBusy)
    val currentEnabled by rememberUpdatedState(!loading)

    fun stopPlayback() {
        player?.release()
        player = null
        playing = false
    }

    fun stopRecording() {
        if (!recording) return
        val ok = runCatching { recorder?.stop() }.isSuccess
        recorder?.release()
        recorder = null
        recording = false
        currentBusy(false)
        if (!ok || file?.length() == 0L) {
            file?.delete()
            file = null
            error = "Recording was too short or interrupted. Please try again."
        }
    }

    fun startRecording() {
        if (!currentEnabled || recording) return
        stopPlayback()
        file?.delete()
        file = null
        error = null
        val output = runCatching { savedRecording.apply { createNewFile() } }.getOrElse {
            error = "Could not save a recording. Check available storage and try again."
            return
        }
        @Suppress("DEPRECATION")
        val next = MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44100)
            next.setAudioEncodingBitRate(64000)
            next.setOutputFile(output.absolutePath)
            next.setOnErrorListener { _, _, _ ->
                stopRecording()
                error = "Microphone recording was interrupted. Please listen and record again."
            }
            next.prepare()
            next.start()
            recorder = next
            file = output
            recording = true
            startedAt = SystemClock.elapsedRealtime()
            seconds = 0
            currentBusy(true)
        } catch (_: Exception) {
            next.release()
            output.delete()
            error = "Could not record. Check microphone permission and close other apps using the microphone."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else error = "Microphone permission is needed to record. Allow it in Android Settings and try again."
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                stopRecording()
                stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (recording) runCatching { recorder?.stop() }
            recorder?.release()
            player?.release()
            // Keep the stopped draft across rotation, navigation, and failed requests.
            // Success, discard, or re-recording removes it.
            currentBusy(false)
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            if (seconds >= 300) {
                stopRecording()
                error = "Reached the 5-minute limit. Your recording is ready to check."
            }
            delay(250)
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speaking trainer", style = MaterialTheme.typography.titleMedium)
            Text(if (recording) "Take your time. Pauses won't stop the recording."
                else "Record, listen, then get pronunciation and fluency feedback.", style = MaterialTheme.typography.bodySmall)
            if (!enabled) Text("You can record and listen offline. Choose a Gemini model and add its API key in Settings for AI speaking feedback.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                if (recording) stopRecording()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = recording || !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (recording) "Stop recording • %d:%02d".format(seconds / 60, seconds % 60)
                    else if (file == null) "Record answer" else "Record again")
            }
            if (file != null && !recording) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (playing) stopPlayback()
                        else try {
                            stopPlayback()
                            val next = MediaPlayer()
                            player = next
                            next.setDataSource(file!!.absolutePath)
                            next.setOnCompletionListener { stopPlayback() }
                            next.setOnErrorListener { _, _, _ ->
                                stopPlayback()
                                error = "Could not play this recording. Please record again."
                                true
                            }
                            next.prepare()
                            next.start()
                            playing = true
                        } catch (_: Exception) {
                            stopPlayback()
                            error = "Could not play this recording. Please record again."
                        }
                    }, enabled = !loading) { Text(if (playing) "Stop playback" else "Listen") }
                    TextButton(onClick = {
                        stopPlayback()
                        file?.delete()
                        file = null
                    }, enabled = !loading) { Text("Discard") }
                }
                Button(onClick = { stopPlayback(); file?.let(onGrade) }, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "Checking…" else "Check recorded answer")
                }
                Text("Only this button sends your audio to Gemini. Audio is temporary; the transcript and feedback are saved in history.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            serverError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
