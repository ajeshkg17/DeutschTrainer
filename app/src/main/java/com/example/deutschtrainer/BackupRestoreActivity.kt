package com.example.deutschtrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class BackupRestoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF465BD8),
                    secondary = Color(0xFF1F9D6A),
                    background = Color(0xFFF7F8FC),
                    surface = Color.White
                )
            ) {
                BackupRestoreScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun BackupRestoreScreen(
    vm: QuizViewModel = viewModel(),
    onClose: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ProfileBackupManager(context.applicationContext) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var pendingExportProfileId by remember { mutableStateOf<Long?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val profileId = pendingExportProfileId
        if (uri != null && profileId != null) {
            scope.launch {
                working = true
                status = null
                runCatching { manager.exportProfile(profileId, uri) }
                    .onSuccess { summary ->
                        status = "Backup saved: ${summary.profileName} · ${summary.quizCount} quizzes · ${summary.attemptCount} graded answers"
                    }
                    .onFailure { error ->
                        status = "Backup failed: ${error.message ?: "Unknown error"}"
                    }
                working = false
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                working = true
                status = null
                runCatching { manager.importProfile(uri) }
                    .onSuccess { summary ->
                        vm.selectProfile(summary.profileId)
                        status = "Restored ${summary.profileName}: ${summary.quizCount} quizzes, ${summary.questionCount} questions and ${summary.attemptCount} graded answers"
                    }
                    .onFailure { error ->
                        status = "Restore failed: ${error.message ?: "Unknown error"}"
                    }
                working = false
            }
        }
    }

    val selectedProfile = state.profiles.firstOrNull { it.id == state.selectedProfileId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Backup & restore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Portable profile files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Choose what to back up", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working
                        ) {
                            Text(selectedProfile?.name ?: "Select profile", modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        vm.selectProfile(profile.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text("Save your learning data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                    Text("Export current profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Creates one JSON file containing this profile's quizzes, questions, answers, scores, corrections and progress. Keep it in Google Drive, Files, email or any folder you control.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            val id = state.selectedProfileId ?: return@Button
                            pendingExportProfileId = id
                            scope.launch {
                                val fileName = manager.suggestedFileName(id)
                                createBackup.launch(fileName)
                            }
                        },
                        enabled = state.selectedProfileId != null && !working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("Create backup file", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(38.dp))
                    Text("Import profile backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Restores the backup as a profile in this installation. Existing profiles are kept, so restore will not overwrite another learner.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("Choose backup file", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E4)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = Color(0xFF9A6500))
                    Column {
                        Text("API keys are not exported", fontWeight = FontWeight.Bold)
                        Text(
                            "For security, Gemini and DeepSeek API keys stay only in Android private storage. Add them again after a fresh installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6E5423)
                        )
                    }
                }
            }

            status?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(it, Modifier.padding(15.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to DeutschTrainer")
            }
        }
    }
}
