package com.example.deutschtrainer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Indigo = Color(0xFF465BD8)
private val IndigoDark = Color(0xFF3547B8)
private val IndigoLight = Color(0xFFE9ECFF)
private val Mint = Color(0xFF1F9D6A)
private val MintLight = Color(0xFFE6F7F0)
private val Amber = Color(0xFFDF922F)
private val AmberLight = Color(0xFFFFF2DD)
private val Coral = Color(0xFFD55762)
private val CoralLight = Color(0xFFFFE9EB)
private val Ink = Color(0xFF151A27)
private val Muted = Color(0xFF667085)
private val AppBg = Color(0xFFF7F8FC)
private val AppLine = Color(0xFFE5E8F0)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoLight,
    onPrimaryContainer = IndigoDark,
    secondary = Mint,
    secondaryContainer = MintLight,
    tertiary = Amber,
    tertiaryContainer = AmberLight,
    error = Coral,
    errorContainer = CoralLight,
    background = AppBg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F3FA),
    onSurfaceVariant = Muted,
    outlineVariant = AppLine
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8190FF),
    onPrimary = Color(0xFF0F173D),
    primaryContainer = Color(0xFF232D5F),
    onPrimaryContainer = Color(0xFFE5E8FF),
    secondary = Color(0xFF56D5A1),
    secondaryContainer = Color(0xFF153C31),
    tertiary = Color(0xFFF1B35F),
    tertiaryContainer = Color(0xFF412F18),
    error = Color(0xFFFF8993),
    errorContainer = Color(0xFF43232A),
    background = Color(0xFF0C1018),
    onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF141A26),
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF1B2231),
    onSurfaceVariant = Color(0xFFA3ADBF),
    outlineVariant = Color(0xFF273144)
)

private enum class AppScreen { PROFILE, HOME, PRACTICE, QUESTION, RESULT, GENERATE, PROGRESS, MORE, HISTORY, SETTINGS }
private enum class ThemeChoice { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeutschTrainerRoot() }
    }
}

@Composable
private fun DeutschTrainerRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    var themeChoice by rememberSaveable {
        mutableStateOf(
            when (prefs.getString("theme", "system")) {
                "light" -> ThemeChoice.LIGHT
                "dark" -> ThemeChoice.DARK
                else -> ThemeChoice.SYSTEM
            }
        )
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        DeutschTrainerApp(
            themeChoice = themeChoice,
            onThemeChoice = {
                themeChoice = it
                prefs.edit().putString("theme", it.name.lowercase()).apply()
            }
        )
    }
}

@Composable
private fun DeutschTrainerApp(
    vm: QuizViewModel = viewModel(),
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit
) {
    val state by vm.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(AppScreen.PROFILE) }
    val answers = remember { mutableStateMapOf<Long, String>() }

    LaunchedEffect(state.evaluation) {
        if (state.evaluation != null && screen == AppScreen.QUESTION) screen = AppScreen.RESULT
    }

    val bottomVisible = screen !in setOf(AppScreen.PROFILE, AppScreen.QUESTION, AppScreen.RESULT)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomVisible) AppBottomBar(screen) { screen = it }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.PROFILE -> ProfilePickerScreen(
                    state = state,
                    onSelect = { vm.selectProfile(it); screen = AppScreen.HOME },
                    onAdd = { vm.addProfile(it) }
                )
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onPractice = { screen = AppScreen.PRACTICE },
                    onGenerate = { screen = AppScreen.GENERATE },
                    onHistory = { screen = AppScreen.HISTORY },
                    onProfiles = { screen = AppScreen.PROFILE },
                    onTheme = { onThemeChoice(if (themeChoice == ThemeChoice.DARK) ThemeChoice.LIGHT else ThemeChoice.DARK) }
                )
                AppScreen.PRACTICE -> PracticeSetupScreen(
                    state = state,
                    onHome = { screen = AppScreen.HOME },
                    onStart = { level, topic, _ ->
                        val pool = state.questions.take(10)
                        val picked = pool.firstOrNull { it.level.equals(level, true) && it.topic.contains(topic, true) }
                            ?: pool.firstOrNull { it.level.equals(level, true) }
                            ?: pool.firstOrNull()
                        if (picked != null) {
                            vm.selectQuestion(picked.id)
                            screen = AppScreen.QUESTION
                        } else screen = AppScreen.GENERATE
                    }
                )
                AppScreen.QUESTION -> QuestionScreen(
                    state = state,
                    answers = answers,
                    onBackToSetup = { screen = AppScreen.PRACTICE },
                    onHome = { screen = AppScreen.HOME },
                    onPrevious = vm::previousQuestion,
                    onNext = vm::nextQuestion,
                    onGrade = vm::grade
                )
                AppScreen.RESULT -> ResultScreen(
                    state = state,
                    answer = state.selectedQuestionId?.let { answers[it] }.orEmpty(),
                    onEdit = { screen = AppScreen.QUESTION },
                    onNext = { vm.nextQuestion(); screen = AppScreen.QUESTION },
                    onExplain = { vm.explainLast(it) }
                )
                AppScreen.GENERATE -> GenerateScreen(state, vm)
                AppScreen.PROGRESS -> ProgressScreen(state)
                AppScreen.MORE -> MoreScreen(state, { screen = AppScreen.HISTORY }, { screen = AppScreen.SETTINGS })
                AppScreen.HISTORY -> HistoryScreen(state) { screen = AppScreen.MORE }
                AppScreen.SETTINGS -> SettingsScreen(
                    state, vm, themeChoice, onThemeChoice,
                    onBack = { screen = AppScreen.MORE },
                    onSwitchProfile = { screen = AppScreen.PROFILE }
                )
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun AppBottomBar(screen: AppScreen, onNavigate: (AppScreen) -> Unit) {
    val items = listOf(
        Triple(AppScreen.HOME, Icons.Outlined.Home, "Home"),
        Triple(AppScreen.PRACTICE, Icons.Outlined.MenuBook, "Practice"),
        Triple(AppScreen.GENERATE, Icons.Outlined.AddCircleOutline, "Create"),
        Triple(AppScreen.PROGRESS, Icons.Outlined.BarChart, "Progress"),
        Triple(AppScreen.MORE, Icons.Outlined.MoreHoriz, "More")
    )
    val selected = if (screen in setOf(AppScreen.HISTORY, AppScreen.SETTINGS)) AppScreen.MORE else screen
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        items.forEach { (target, icon, label) ->
            NavigationBarItem(
                selected = selected == target,
                onClick = { onNavigate(target) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun ProfilePickerScreen(state: UiState, onSelect: (Long) -> Unit, onAdd: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF20232B)), contentAlignment = Alignment.Center) {
                Text("DE", color = Color(0xFFFFD75A), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Column {
                Eyebrow("Personal German coach")
                Text("Deutsch Trainer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Choose a profile. Each learner keeps separate scores, history and progress on this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        if (state.profiles.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else state.profiles.forEachIndexed { index, profile ->
            ProfileCard(profile, index, state.selectedProfileId == profile.id) { onSelect(profile.id) }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedCard(
            Modifier.fillMaxWidth().clickable { showDialog = true },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Add profile", fontWeight = FontWeight.Bold)
                    Text("Create another learner on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Local-first profiles · data survives normal app updates", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("New profile") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAdd(name); name = ""; showDialog = false } }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileCard(profile: ProfileEntity, index: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = listOf(Color(0xFF7386FF), Color(0xFFE46AA8), Color(0xFF36A689), Color(0xFFE79A45))
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(profile.name, colors[index % colors.size])
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold)
                Text(if (selected) "Current profile" else "Tap to continue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(name: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onPractice: () -> Unit,
    onGenerate: () -> Unit,
    onHistory: () -> Unit,
    onProfiles: () -> Unit,
    onTheme: () -> Unit
) {
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    val attempts = state.attempts
    val avg = attempts.map { it.attempt.score }.average().takeIf { !it.isNaN() } ?: 0.0
    val weak = grammarAverages(attempts).minByOrNull { it.second }
    val streak = dayStreak(attempts.map { it.attempt.createdAt })
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(onClick = onProfiles)) { Avatar(profile?.name ?: "Profile") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Good ${dayPart()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profile?.name ?: "Learner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                FilledTonalIconButton(onClick = onTheme) { Icon(Icons.Outlined.DarkMode, contentDescription = "Toggle theme") }
            }
        }
        item { HomeHero(streak, onPractice) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Attempts", attempts.size.toString(), "saved locally", Modifier.weight(1f))
                StatCard("Average", if (attempts.isEmpty()) "—" else "%.1f/10".format(avg), if (attempts.isEmpty()) "start practicing" else "overall score", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Recommended for you") }
        item { RecommendationCard(weak, onPractice) }
        item { SectionTitle("Quick actions") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ActionRow(Icons.Outlined.AddCircleOutline, "Create a new set", "Choose level, topic and grammar", onGenerate)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(Icons.Outlined.History, "Review mistakes", "Look back at corrections and scores", onHistory)
            }
        }
    }
}

@Composable
private fun HomeHero(streak: Int, onPractice: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF3749BC), Color(0xFF6579F5)))).padding(22.dp)
    ) {
        Column {
            Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                Text(if (streak > 0) "🔥 $streak day streak" else "✦ Start today's streak", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Ready for today's German?", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("10 focused sentences. About 8 minutes.", color = Color(0xFFE8EAFF), modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            Button(onClick = onPractice, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = IndigoDark), shape = RoundedCornerShape(16.dp)) {
                Text("Continue practice", fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RecommendationCard(weak: Pair<String, Double>?, onPractice: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Text("🎯", fontSize = 22.sp) }
                Column(Modifier.weight(1f)) {
                    Text(weak?.first ?: "Build a baseline", fontWeight = FontWeight.Bold)
                    Text(if (weak == null) "Complete a few graded answers to unlock personalized recommendations." else "This is currently your lowest-scoring grammar area.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (weak != null) TextButton(onClick = onPractice) { Text("Practice") }
            }
            if (weak != null) {
                Spacer(Modifier.height(14.dp))
                val mastery = (weak.second / 10.0).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(progress = { mastery }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current mastery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(mastery * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PracticeSetupScreen(state: UiState, onHome: () -> Unit, onStart: (String, String, String) -> Unit) {
    var level by rememberSaveable { mutableStateOf("B1") }
    var topic by rememberSaveable { mutableStateOf("Everyday life") }
    var focus by rememberSaveable { mutableStateOf("Mixed") }
    val topics = listOf(
        Triple("☕", "Everyday life", "Home, shopping, plans"),
        Triple("💼", "Work", "Meetings, email, tasks"),
        Triple("✈️", "Travel", "Hotel, train, airport"),
        Triple("🗣️", "Conversation", "Natural spoken German")
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppHeader("Practice", "Build a session", onHome) }
        item { SectionTitle("Level") }
        item { ChoiceChips(listOf("A2", "B1", "B2", "C1"), level) { level = it } }
        item { SectionTitle("Topic") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in topics.chunked(2)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> TopicCard(t, topic == t.second, Modifier.weight(1f)) { topic = t.second } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("Grammar focus") }
        item { ChoiceChips(listOf("Mixed", "Word order", "Cases", "Prepositions", "Konjunktiv II"), focus) { focus = it } }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Session length", fontWeight = FontWeight.Bold); Text("10 newest questions · ~8 min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    SoftBadge("Adaptive")
                }
            }
        }
        item {
            Button(onClick = { onStart(level, topic, focus) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (state.questions.isEmpty()) "Create a practice set" else "Start 10-question session", fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun TopicCard(topic: Triple<String, String, String>, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(15.dp).heightIn(min = 82.dp)) {
            Text(topic.first, fontSize = 22.sp); Spacer(Modifier.height(7.dp)); Text(topic.second, fontWeight = FontWeight.Bold); Text(topic.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestionScreen(
    state: UiState,
    answers: MutableMap<Long, String>,
    onBackToSetup: () -> Unit,
    onHome: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGrade: (String) -> Unit
) {
    val session = state.questions.take(10)
    val q = session.firstOrNull { it.id == state.selectedQuestionId } ?: session.firstOrNull()
    if (q == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No questions yet") }; return }
    val index = session.indexOfFirst { it.id == q.id }.coerceAtLeast(0)
    val answer = answers[q.id].orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBackToSetup) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QUESTION ${index + 1} OF ${session.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text("${q.level} · ${q.topic}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            FilledTonalIconButton(onClick = onHome) { Icon(Icons.Outlined.Home, contentDescription = "Home") }
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(progress = { (index + 1) / session.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                SoftBadge("English → German"); Spacer(Modifier.height(14.dp)); Text(q.english, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Translate naturally. More than one correct answer is possible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Your translation", modifier = Modifier.padding(top = 18.dp, bottom = 7.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = answer, onValueChange = { answers[q.id] = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Write your German sentence here…") }, shape = RoundedCornerShape(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 9.dp)) {
            listOf("ä", "ö", "ü", "ß").forEach { c -> FilledTonalButton(onClick = { answers[q.id] = answer + c }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text(c, fontWeight = FontWeight.Bold) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.ArrowBack, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Back") }
            Button(onClick = { onGrade(answer) }, enabled = answer.isNotBlank() && !state.loading, modifier = Modifier.weight(1.6f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(if (state.loading) "Checking…" else "Check answer", fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.Check, contentDescription = null) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onNext) { Text("Skip to next"); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null) } }
        Text("Your answer is kept while you move between questions.", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultScreen(state: UiState, answer: String, onEdit: () -> Unit, onNext: () -> Unit, onExplain: (String) -> Unit) {
    val ev = state.evaluation ?: run { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.padding(start = 12.dp)) { Eyebrow("Evaluation"); Text("Your answer", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(14.dp))
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(116.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { ev.score / 10f }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp, color = if (ev.score >= 8) MaterialTheme.colorScheme.secondary else if (ev.score >= 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${ev.score}.0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("out of 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(14.dp))
                Text(if (ev.score >= 9) "Excellent!" else if (ev.score >= 8) "Very good!" else if (ev.score >= 6) "Good progress" else "Keep practicing", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(ev.shortFeedback, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("A STRONG TRANSLATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ev.correctedTranslation, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                if (ev.mistakes.isNotEmpty()) { Spacer(Modifier.height(14.dp)); ev.mistakes.forEach { FeedbackBox(it, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error) } }
                if (ev.howToImprove.isNotEmpty()) { Spacer(Modifier.height(8.dp)); ev.howToImprove.forEach { FeedbackBox(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary) } }
                if (ev.grammarTopics.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ChipRow(ev.grammarTopics) }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (state.explanation == null) {
                    OutlinedButton(onClick = { onExplain(answer) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MenuBook, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (state.loading) "Explaining…" else "Explain in detail") }
                } else { Text("Detailed explanation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(state.explanation, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Edit") }
            Button(onClick = onNext, modifier = Modifier.weight(1.5f).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Next question", fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null) }
        }
    }
}

@Composable
private fun FeedbackBox(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(text, modifier = Modifier.padding(12.dp), color = fg, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun GenerateScreen(state: UiState, vm: QuizViewModel) {
    var level by rememberSaveable { mutableStateOf("B1") }
    var topic by rememberSaveable { mutableStateOf("Everyday life and work") }
    var focus by rememberSaveable { mutableStateOf("Subordinate clauses, word order, prepositions") }
    var count by rememberSaveable { mutableIntStateOf(10) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Eyebrow("AI creator"); Text("New practice set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; SoftBadge(state.selectedModel.label.substringBefore(" ·").take(20)) } }
        item { Text("CEFR level", fontWeight = FontWeight.Bold) }
        item { ChoiceChips(listOf("A2", "B1", "B2", "C1"), level) { level = it } }
        item { LabeledField("Topic", topic) { topic = it } }
        item { LabeledField("Grammar focus", focus) { focus = it } }
        item { Text("Number of questions", fontWeight = FontWeight.Bold) }
        item { ChoiceChips(listOf("5", "10", "15", "20"), count.toString()) { count = it.toInt() } }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    Column { Text("Personalized generation", fontWeight = FontWeight.Bold); Text("The set can target your weaker grammar areas while keeping the requested CEFR level.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Button(onClick = { vm.generate(count, level, topic, focus) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (state.loading) "Generating…" else "Generate $count questions", fontWeight = FontWeight.Bold) }
        }
        state.message?.let { message -> item { InfoCard("Status", message) } }
    }
}

@Composable
private fun ProgressScreen(state: UiState) {
    val attempts = state.attempts
    val avg = attempts.map { it.attempt.score }.average().takeIf { !it.isNaN() } ?: 0.0
    val grammar = grammarAverages(attempts)
    val recent = attempts.take(7).reversed()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Eyebrow("Insights"); Text("Your progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; if (attempts.isNotEmpty()) SoftBadge(if (avg >= 8) "Strong" else "Improving") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("Overall average", if (attempts.isEmpty()) "—" else "%.1f".format(avg), "out of 10", Modifier.weight(1f)); StatCard("Total attempts", attempts.size.toString(), "saved answers", Modifier.weight(1f)) } }
        if (attempts.isEmpty()) item { InfoCard("No progress data yet", "Complete a graded translation and your score trends and grammar strengths will appear here.") }
        else {
            item { SectionTitle("Recent scores") }
            item { ScoreBars(recent.map { it.attempt.score }) }
            item { SectionTitle("Grammar mastery") }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp)) {
                        if (grammar.isEmpty()) Text("Grammar tags will appear after more graded answers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        grammar.sortedByDescending { it.second }.take(6).forEach { (name, score) -> MasteryRow(name, score) }
                    }
                }
            }
            grammar.minByOrNull { it.second }?.let { weak -> item { SectionTitle("Needs attention") }; item { InfoCard(weak.first, "Current average ${"%.1f".format(weak.second)}/10") } }
        }
    }
}

@Composable
private fun ScoreBars(scores: List<Int>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
            val data = if (scores.isEmpty()) listOf(0) else scores
            data.forEachIndexed { index, score ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight((score / 10f).coerceAtLeast(0.05f)).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                    Spacer(Modifier.height(6.dp)); Text((index + 1).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MasteryRow(name: String, score: Double) {
    val p = (score / 10.0).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
    }
}

@Composable
private fun MoreScreen(state: UiState, onHistory: () -> Unit, onSettings: () -> Unit) {
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Eyebrow("More"); Text("Review & settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; Avatar(profile?.name ?: "P") }
        Spacer(Modifier.height(20.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            ActionRow(Icons.Outlined.History, "History", "Review answers and corrections", onHistory); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); ActionRow(Icons.Outlined.Settings, "Settings", "Profiles, AI model, theme and data", onSettings)
        }
    }
}

@Composable
private fun HistoryScreen(state: UiState, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val shown = when (filter) { "Mistakes" -> state.attempts.filter { it.attempt.score < 8 }; "High scores" -> state.attempts.filter { it.attempt.score >= 9 }; else -> state.attempts }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        AppHeader("Review", "History", onBack, topPadding = 18.dp); Spacer(Modifier.height(12.dp)); ChoiceChips(listOf("All", "Mistakes", "High scores"), filter) { filter = it }; Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) InfoCard("Nothing here yet", "Your graded translations will appear here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown) { item ->
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        ScoreBadge(item.attempt.score)
                        Column(Modifier.weight(1f)) {
                            Text(item.question.english, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.attempt.answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text("${item.question.level} · ${item.question.focus} · ${fmt.format(Date(item.attempt.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val bg = if (score >= 8) MaterialTheme.colorScheme.secondaryContainer else if (score >= 6) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (score >= 8) MaterialTheme.colorScheme.secondary else if (score >= 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) { Text(score.toString(), color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    vm: QuizViewModel,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    onBack: () -> Unit,
    onSwitchProfile: () -> Unit
) {
    var geminiKey by remember { mutableStateOf("") }
    var deepSeekKey by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AppHeader("", "Settings", onBack) }
        item { SectionTitle("Profiles") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(profile?.name ?: "P"); Column(Modifier.weight(1f)) { Text(profile?.name ?: "Profile", fontWeight = FontWeight.Bold); Text("Current profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = onSwitchProfile) { Text("Switch") }
                }
            }
        }
        item { SectionTitle("AI model") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Provider / model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { modelExpanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp)) { Text(state.selectedModel.label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start); Icon(Icons.Outlined.ExpandMore, contentDescription = null) }
                        DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) { AiModels.all.forEach { m -> DropdownMenuItem(text = { Text(m.label) }, onClick = { vm.selectModel(m); modelExpanded = false }) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.selectedModel.provider == AiProvider.GEMINI) SecretField("Gemini API key", geminiKey, { geminiKey = it }, state.geminiKeyConfigured) { vm.saveGeminiKey(geminiKey); geminiKey = "" }
                    else SecretField("DeepSeek API key", deepSeekKey, { deepSeekKey = it }, state.deepSeekKeyConfigured) { vm.saveDeepSeekKey(deepSeekKey); deepSeekKey = "" }
                    Text("API keys are stored in Android app-private preferences and are not written to the learning database.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item { SectionTitle("Appearance") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) { Text("Theme", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); ChoiceChips(listOf("System", "Light", "Dark"), themeChoice.name.lowercase().replaceFirstChar { it.uppercase() }) { onThemeChoice(ThemeChoice.valueOf(it.uppercase())) } }
            }
        }
        item { SectionTitle("Learning & data") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                SettingsRow("Daily goal", "10 questions per day", "10"); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); SettingsRow("Adaptive practice", "Use recent weak grammar areas", "On"); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); SettingsRow("Auto-save progress", "Preserve history across app updates", "On")
            }
        }
        item { InfoCard("Local-first storage", "Profiles, questions and graded attempts are kept in the Room database on this phone. Normal APK updates preserve this data.") }
        state.message?.let { item { InfoCard("Status", it) } }
    }
}

@Composable
private fun SecretField(label: String, value: String, onValue: (String) -> Unit, configured: Boolean, onSave: () -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), supportingText = { if (configured) Text("Configured ✓", color = MaterialTheme.colorScheme.secondary) })
    Button(onClick = onSave, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp)) { Text("Save key") }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; SoftBadge(value) }
}

@Composable
private fun StatCard(label: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp)); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppHeader(eyebrow: String, title: String, onBack: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(Modifier.fillMaxWidth().padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { if (eyebrow.isNotBlank()) Eyebrow(eyebrow); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        FilledTonalIconButton(onClick = onBack) { Icon(Icons.Outlined.Home, contentDescription = "Home") }
    }
}

@Composable
private fun Eyebrow(text: String) { Text(text.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable
private fun SoftBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary, shape = CircleShape) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> FilterChip(selected = selected.equals(option, true), onClick = { onSelect(option) }, label = { Text(option) }) } }
}

@Composable
private fun ChipRow(items: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { items.forEach { SoftBadge(it) } }
}

@Composable
private fun LabeledField(label: String, value: String, onValue: (String) -> Unit) {
    Column { Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp)); OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) } }
}

private fun grammarAverages(attempts: List<AttemptWithQuestion>): List<Pair<String, Double>> {
    val map = linkedMapOf<String, MutableList<Int>>()
    attempts.forEach { item ->
        runCatching {
            val arr = JSONArray(item.attempt.grammarTopicsJson)
            for (i in 0 until arr.length()) {
                val topic = arr.optString(i).trim()
                if (topic.isNotBlank()) map.getOrPut(topic) { mutableListOf() }.add(item.attempt.score)
            }
        }
    }
    return map.map { it.key to it.value.average() }
}

private fun dayStreak(times: List<Long>): Int {
    if (times.isEmpty()) return 0
    val day = 86_400_000L
    val days = times.map { it / day }.distinct().sortedDescending()
    var expected = System.currentTimeMillis() / day
    if (days.firstOrNull() != expected) expected -= 1
    var streak = 0
    for (d in days) {
        if (d == expected) { streak++; expected-- } else if (d < expected) break
    }
    return streak
}

private fun dayPart(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when { hour < 12 -> "morning"; hour < 18 -> "afternoon"; else -> "evening" }
}
