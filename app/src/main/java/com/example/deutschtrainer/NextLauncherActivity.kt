package com.example.deutschtrainer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class NextLauncherActivity : ComponentActivity() {
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
                NextLauncherScreen(
                    onOpenTrainer = {
                        startActivity(Intent(this, TrainerActivity::class.java))
                    },
                    onBackup = {
                        startActivity(Intent(this, BackupRestoreActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun NextLauncherScreen(
    onOpenTrainer: () -> Unit,
    onBackup: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3"
    }.getOrDefault("3")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = Color(0xFF222A62),
            contentColor = Color.White,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                "DEUTSCHTRAINER NEXT · v$version",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "This is the new build",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "It is installed separately from the older Deutsch Trainer app, so there is no chance of Android opening the previous package by mistake.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF3749BC), Color(0xFF6579F5))),
                    RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "DeutschTrainer Next",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Multiple quizzes · custom topics · German voice input · progress tracking · portable profile backups",
                    color = Color(0xFFE8EAFF)
                )
                Button(
                    onClick = onOpenTrainer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3547B8)),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Open the new trainer", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Backup & restore", fontWeight = FontWeight.Bold)
                        Text(
                            "Save a learner profile to a file or restore one after reinstalling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onBackup,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("Open Backup & Restore", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF1F9D6A))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Package: ${context.packageName}\n\nIf you see this screen, you are definitely running the new APK.",
                    color = Color(0xFF225E49),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
