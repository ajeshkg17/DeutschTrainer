package com.example.deutschtrainer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val V2Indigo = Color(0xFF465BD8)
private val V2IndigoDark = Color(0xFF3547B8)
private val V2Mint = Color(0xFF1F9D6A)
private val V2Amber = Color(0xFFDF922F)
private val V2Coral = Color(0xFFD55762)

private val V2LightScheme = lightColorScheme(
    primary = V2Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9ECFF),
    onPrimaryContainer = V2IndigoDark,
    secondary = V2Mint,
    secondaryContainer = Color(0xFFE6F7F0),
    tertiary = V2Amber,
    tertiaryContainer = Color(0xFFFFF2DD),
    error = V2Coral,
    errorContainer = Color(0xFFFFE9EB),
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F3FA),
    onSurfaceVariant = Color(0xFF667085),
    outlineVariant = Color(0xFFE5E8F0)
)

private val V2DarkScheme = darkColorScheme(
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
    surface = Color(0xFF141A26),
    surfaceVariant = Color(0xFF1B2231),
    onSurfaceVariant = Color(0xFFA3ADBF),
    outlineVariant = Color(0xFF273144)
)

private enum class V2Screen { PROFILE, HOME, QUIZZES, QUESTION, RESULT, CREATE, PROGRESS, MORE, HISTORY, SETTINGS }
private enum class V2Theme { SYSTEM, LIGHT, DARK }

class TrainerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V2Root() }
    }
}

@Composable
private fun V2Root() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    var theme by rememberSaveable {
        mutableStateOf(
            when (prefs.getString("theme", "system")) {
                "light" -> V2Theme.LIGHT
                "dark" -> V2Theme.DARK
                else -> V2Theme.SYSTEM
            }
        )
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = when (theme) {
        V2Theme.SYSTEM -> systemDark
        V2Theme.LIGHT -> false
        V2Theme.DARK -> true
    }

    MaterialTheme(colorScheme = if (useDark) V2DarkScheme else V2LightScheme) {
        V2App(
            theme = theme,
            onTheme = {
                theme = it
                prefs.edit().putString("theme", it.name.lowercase()).apply()
            }
        )
    }
}

@Composable
private fun V2App(
    vm: QuizViewModel = viewModel(),
    theme: V2Theme,
    onTheme: (V2Theme) -> Unit
) {
    val state by vm.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(V2Screen.PROFILE) }
    val answers = remember { mutableStateMapOf<Long, String>() }

    LaunchedEffect(state.evaluation) {
        if (state.evaluation != null && screen == V2Screen.QUESTION) screen = V2Screen.RESULT
    }

    val bottomVisible = screen !in setOf(V2Screen.PROFILE, V2Screen.QUESTION, V2Screen.RESULT)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomVisible) V2BottomBar(screen) { screen = it }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                V2Screen.PROFILE -> V2ProfilePicker(
                    state = state,
                    onSelect = { vm.selectProfile(it); screen = V2Screen.HOME },
                    onAdd = vm::addProfile
                )

                V2Screen.HOME -> V2Home(
                    state = state,
                    onQuizzes = { screen = V2Screen.QUIZZES },
                    onCreate = { screen = V2Screen.CREATE },
                    onHistory = { screen = V2Screen.HISTORY },
                    onProfiles = { screen = V2Screen.PROFILE },
                    onThemeToggle = {
                        onTheme(if (theme == V2Theme.DARK) V2Theme.LIGHT else V2Theme.DARK)
                    },
                    onReviewMistakes = {
                        vm.createMistakesQuiz()
                        screen = V2Screen.QUIZZES
                    }
                )

                V2Screen.QUIZZES -> V2QuizLibrary(
                    state = state,
                    onHome = { screen = V2Screen.HOME },
                    onCreate = { screen = V2Screen.CREATE },
                    onSelect = vm::selectQuizSet,
                    onStart = { quizId ->
                        vm.selectQuizSet(quizId)
                        screen = V2Screen.QUESTION
                    },
                    onReviewMistakes = vm::createMistakesQuiz,
                    onDelete = vm::deleteQuizSet
                )

                V2Screen.QUESTION -> V2QuestionScreen(
                    state = state,
                    answers = answers,
                    onBack = { screen = V2Screen.QUIZZES },
                    onHome = { screen = V2Screen.HOME },
                    onPrevious = vm::previousQuestion,
                    onNext = vm::nextQuestion,
                    onGrade = vm::grade
                )

                V2Screen.RESULT -> V2ResultScreen(
                    state = state,
                    answer = state.selectedQuestionId?.let { answers[it] }.orEmpty(),
                    onEdit = { screen = V2Screen.QUESTION },
                    onNext = { vm.nextQuestion(); screen = V2Screen.QUESTION },
                    onExplain = vm::explainLast
                )

                V2Screen.CREATE -> V2CreateScreen(
                    state = state,
                    vm = vm,
                    onQuizzes = { screen = V2Screen.QUIZZES }
                )

                V2Screen.PROGRESS -> V2ProgressScreen(state)
                V2Screen.MORE -> V2MoreScreen(
                    state = state,
                    onHistory = { screen = V2Screen.HISTORY },
                    onSettings = { screen = V2Screen.SETTINGS }
                )
                V2Screen.HISTORY -> V2HistoryScreen(state) { screen = V2Screen.MORE }
                V2Screen.SETTINGS -> V2SettingsScreen(
                    state = state,
                    vm = vm,
                    theme = theme,
                    onTheme = onTheme,
                    onBack = { screen = V2Screen.MORE },
                    onProfiles = { screen = V2Screen.PROFILE }
                )
            }

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun V2BottomBar(screen: V2Screen, onNavigate: (V2Screen) -> Unit) {
    val items = listOf(
        Triple(V2Screen.HOME, Icons.Outlined.Home, "Home"),
        Triple(V2Screen.QUIZZES, Icons.Outlined.MenuBook, "Quizzes"),
        Triple(V2Screen.CREATE, Icons.Outlined.AddCircleOutline, "Create"),
        Triple(V2Screen.PROGRESS, Icons.Outlined.BarChart, "Progress"),
        Triple(V2Screen.MORE, Icons.Outlined.MoreHoriz, "More")
    )
    val selected = if (screen in setOf(V2Screen.HISTORY, V2Screen.SETTINGS)) V2Screen.MORE else screen
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (target, icon, label) ->
            NavigationBarItem(
                selected = selected == target,
                onClick = { onNavigate(target) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun V2ProfilePicker(state: UiState, onSelect: (Long) -> Unit, onAdd: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF20232B)),
                contentAlignment = Alignment.Center
            ) {
                Text("DE", color = Color(0xFFFFD75A), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Column {
                V2Eyebrow("Personal German coach")
                Text("Deutsch Trainer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Choose a learner. Every profile keeps its own quiz library, history and progress.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))

        state.profiles.forEachIndexed { index, profile ->
            val colors = listOf(Color(0xFF7386FF), Color(0xFFE46AA8), Color(0xFF36A689), Color(0xFFE79A45))
            Card(
                Modifier.fillMaxWidth().clickable { onSelect(profile.id) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (state.selectedProfileId == profile.id) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    V2Avatar(profile.name, colors[index % colors.size])
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.Bold)
                        Text("Open quiz library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedCard(
            Modifier.fillMaxWidth().clickable { dialog = true },
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Add profile", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("New profile") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAdd(name); name = ""; dialog = false },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V2Home(
    state: UiState,
    onQuizzes: () -> Unit,
    onCreate: () -> Unit,
    onHistory: () -> Unit,
    onProfiles: () -> Unit,
    onThemeToggle: () -> Unit,
    onReviewMistakes: () -> Unit
) {
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    val activeQuiz = state.quizSets.firstOrNull { it.id == state.selectedQuizSetId }
    val avg = state.attempts.map { it.attempt.score }.average().takeIf { !it.isNaN() }
    val mistakes = state.attempts.count { it.attempt.score < 8 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(onClick = onProfiles)) { V2Avatar(profile?.name ?: "P") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Welcome back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profile?.name ?: "Learner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                FilledTonalIconButton(onClick = onThemeToggle) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = "Toggle theme")
                }
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3749BC), Color(0xFF6579F5))))
                    .padding(22.dp)
            ) {
                Column {
                    Text("Your quiz library", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.quizSets.size} saved quiz${if (state.quizSets.size == 1) "" else "zes"}. Keep different topics and grammar focuses side by side.",
                        color = Color(0xFFE8EAFF),
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                    )
                    Button(
                        onClick = onQuizzes,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = V2IndigoDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (activeQuiz == null) "Open quizzes" else "Continue ${activeQuiz.title}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                V2Stat("Quizzes", state.quizSets.size.toString(), "saved for this profile", Modifier.weight(1f))
                V2Stat("Average", avg?.let { "%.1f/10".format(it) } ?: "—", "${state.attempts.size} attempts", Modifier.weight(1f))
            }
        }

        item { V2Section("Learner tools") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                V2Action(Icons.Outlined.AddCircleOutline, "Create a new quiz", "Any topic, level and grammar focus", onCreate)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                V2Action(Icons.Outlined.RecordVoiceOver, "Speak your answers", "German speech-to-text is available inside each question", onQuizzes)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                V2Action(Icons.Outlined.Replay, "Review weak answers", if (mistakes == 0) "No low-score answers yet" else "$mistakes answers scored below 8/10", onReviewMistakes)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                V2Action(Icons.Outlined.History, "Review history", "Corrections, scores and grammar feedback", onHistory)
            }
        }
    }
}

@Composable
private fun V2QuizLibrary(
    state: UiState,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onReviewMistakes: () -> Unit,
    onDelete: (Long) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var levelFilter by rememberSaveable { mutableStateOf("All") }
    var deleteTarget by remember { mutableStateOf<QuizSetEntity?>(null) }

    val filtered = state.quizSets.filter { quiz ->
        val matchesSearch = search.isBlank() || listOf(quiz.title, quiz.topic, quiz.focus, quiz.level)
            .any { it.contains(search, ignoreCase = true) }
        val matchesLevel = levelFilter == "All" || quiz.level.equals(levelFilter, ignoreCase = true)
        matchesSearch && matchesLevel
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { V2Header("Practice", "My quizzes", onHome) }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search topic, focus or title") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item { V2ChoiceChips(listOf("All", "A2", "B1", "B2", "C1"), levelFilter) { levelFilter = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onCreate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("New quiz")
                }
                OutlinedButton(onClick = onReviewMistakes, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Replay, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Mistakes")
                }
            }
        }

        if (filtered.isEmpty()) {
            item { V2Info("No matching quizzes", "Create a new topic or change the search/filter.") }
        } else {
            items(filtered, key = { it.id }) { quiz ->
                val ids = remember(quiz.questionIdsJson) { v2QuizIds(quiz.questionIdsJson) }
                val relevantAttempts = state.attempts.filter { it.attempt.questionId in ids }
                val completed = relevantAttempts.map { it.attempt.questionId }.distinct().size
                val average = relevantAttempts.map { it.attempt.score }.average().takeIf { !it.isNaN() }
                val selected = quiz.id == state.selectedQuizSetId

                Card(
                    Modifier.fillMaxWidth().clickable { onSelect(quiz.id) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    }
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(quiz.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(quiz.topic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                            }
                            V2Badge(quiz.level)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Focus · ${quiz.focus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            V2Badge("${ids.size} questions")
                            V2Badge("$completed done")
                            average?.let { V2Badge("%.1f/10".format(it)) }
                        }
                        if (ids.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { (completed.toFloat() / ids.size.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { onStart(quiz.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text(if (completed > 0) "Continue" else "Start", fontWeight = FontWeight.Bold)
                            }
                            OutlinedIconButton(onClick = { deleteTarget = quiz }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete quiz")
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { quiz ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete '${quiz.title}'?") },
            text = { Text("This removes the quiz from this profile. Your graded history is kept.") },
            confirmButton = {
                TextButton(onClick = { onDelete(quiz.id); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V2CreateScreen(state: UiState, vm: QuizViewModel, onQuizzes: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("B1") }
    var topic by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf("Mixed grammar") }
    var count by rememberSaveable { mutableIntStateOf(10) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { V2Header("AI creator", "New quiz", onQuizzes) }
        item {
            V2Info(
                "Every quiz is saved separately",
                "Generating a new topic never replaces your existing quizzes. It is added to this profile's library."
            )
        }
        item { V2LabeledField("Quiz title (optional)", title, { title = it }, "e.g. Work emails") }
        item { V2LabeledField("Topic", topic, { topic = it }, "Type any topic: dentist, lab, shopping, travel…") }
        item { V2LabeledField("Grammar focus", focus, { focus = it }, "Mixed, word order, cases, Konjunktiv II…") }
        item { Text("CEFR level", fontWeight = FontWeight.Bold) }
        item { V2ChoiceChips(listOf("A2", "B1", "B2", "C1"), level) { level = it } }
        item { Text("Number of questions", fontWeight = FontWeight.Bold) }
        item { V2ChoiceChips(listOf("5", "10", "15", "20"), count.toString()) { count = it.toInt() } }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Designed for learning", fontWeight = FontWeight.Bold)
                            Text("The AI is asked for varied, non-duplicate sentences at the requested level and focus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { vm.generate(count, level, topic, focus, title) },
                enabled = topic.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.loading) "Generating…" else "Generate & save new quiz", fontWeight = FontWeight.Bold)
            }
        }
        state.message?.let { item { V2Info("Status", it) } }
    }
}

@Composable
private fun V2QuestionScreen(
    state: UiState,
    answers: MutableMap<Long, String>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGrade: (String) -> Unit
) {
    val session = state.questions
    val question = session.firstOrNull { it.id == state.selectedQuestionId } ?: session.firstOrNull()
    if (question == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This quiz has no questions.") }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val drafts = remember { context.getSharedPreferences("answer_drafts", Context.MODE_PRIVATE) }
    val profileId = state.selectedProfileId ?: 0L
    val draftKey = "${profileId}_${question.id}"
    val stored = remember(question.id, profileId) { drafts.getString(draftKey, "").orEmpty() }
    val answer = answers[question.id] ?: stored
    val index = session.indexOfFirst { it.id == question.id }.coerceAtLeast(0)
    val quiz = state.quizSets.firstOrNull { it.id == state.selectedQuizSetId }
    var speechError by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(false) }

    fun setAnswer(value: String) {
        answers[question.id] = value
        drafts.edit().putString(draftKey, value).apply()
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                setAnswer(spoken)
                speechError = null
            }
        }
    }

    fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your German translation")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            speechError = "Speech recognition is unavailable on this phone. You can still use the microphone on your keyboard."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to quizzes") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QUESTION ${index + 1} OF ${session.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(quiz?.title ?: question.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalIconButton(onClick = onHome) { Icon(Icons.Outlined.Home, contentDescription = "Home") }
        }

        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { (index + 1) / session.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                V2Badge("English → German")
                Spacer(Modifier.height(14.dp))
                Text(question.english, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Translate naturally. More than one correct answer can be valid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Your translation", modifier = Modifier.padding(top = 18.dp, bottom = 7.dp), fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = answer,
            onValueChange = ::setAnswer,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("Write or speak your German sentence…") },
            shape = RoundedCornerShape(18.dp)
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ä", "ö", "ü", "ß").forEach { character ->
                FilledTonalButton(
                    onClick = { setAnswer(answer + character) },
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                ) { Text(character, fontWeight = FontWeight.Bold) }
            }
            FilledTonalButton(onClick = ::startSpeech) {
                Icon(Icons.Outlined.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Speak German", fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "Speech is transcribed as German (de-DE). You can edit the text before grading.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        speechError?.let { V2InlineNotice(it, error = true) }

        OutlinedButton(
            onClick = { showHint = !showHint },
            modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(if (showHint) "Hide grammar hint" else "Show grammar hint")
        }
        if (showHint) {
            V2InlineNotice("Grammar focus: ${question.focus}. Think about the rule before translating; the hint intentionally does not give the German answer.")
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Back")
            }
            Button(
                onClick = { onGrade(answer) },
                enabled = answer.isNotBlank() && !state.loading,
                modifier = Modifier.weight(1.6f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (state.loading) "Checking…" else "Check answer", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.Check, contentDescription = null)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNext) {
                Text("Skip to next"); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null)
            }
        }
        Text(
            "Draft answers and your last position are remembered when you switch quizzes.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun V2ResultScreen(
    state: UiState,
    answer: String,
    onEdit: () -> Unit,
    onNext: () -> Unit,
    onExplain: (String) -> Unit
) {
    val evaluation = state.evaluation ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.padding(start = 12.dp)) {
                V2Eyebrow("Evaluation")
                Text("Your answer", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(116.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { evaluation.score / 10f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = if (evaluation.score >= 8) MaterialTheme.colorScheme.secondary else if (evaluation.score >= 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(evaluation.score.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("out of 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        evaluation.score >= 9 -> "Excellent!"
                        evaluation.score >= 8 -> "Very good!"
                        evaluation.score >= 6 -> "Good progress"
                        else -> "Keep practicing"
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    evaluation.shortFeedback,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("A STRONG TRANSLATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(evaluation.correctedTranslation, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                V2GermanTtsButton(evaluation.correctedTranslation)

                if (evaluation.mistakes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    evaluation.mistakes.forEach { V2FeedbackBox(it, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error) }
                }
                if (evaluation.howToImprove.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    evaluation.howToImprove.forEach { V2FeedbackBox(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary) }
                }
                if (evaluation.grammarTopics.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    V2ChipRow(evaluation.grammarTopics)
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (state.explanation == null) {
                    OutlinedButton(
                        onClick = { onExplain(answer) },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.loading) "Explaining…" else "Explain in detail")
                    }
                } else {
                    Text("Detailed explanation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(state.explanation, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Edit")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1.5f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Next question", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun V2GermanTtsButton(text: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }
        engine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            engine = null
        }
    }
    LaunchedEffect(ready, engine) {
        if (ready) engine?.language = Locale.GERMANY
    }

    FilledTonalButton(
        onClick = { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deutsch_correction") },
        enabled = ready && text.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Icon(Icons.Outlined.VolumeUp, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Text("Listen to the German correction")
    }
}

@Composable
private fun V2ProgressScreen(state: UiState) {
    val attempts = state.attempts
    val average = attempts.map { it.attempt.score }.average().takeIf { !it.isNaN() }
    val grammar = v2GrammarAverages(attempts)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                V2Eyebrow("Insights")
                Text("Your progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                V2Stat("Average", average?.let { "%.1f/10".format(it) } ?: "—", "overall", Modifier.weight(1f))
                V2Stat("Quizzes", state.quizSets.size.toString(), "saved topics", Modifier.weight(1f))
            }
        }
        item { V2Section("Quiz progress") }
        items(state.quizSets.take(8), key = { it.id }) { quiz ->
            val ids = v2QuizIds(quiz.questionIdsJson)
            val relevant = attempts.filter { it.attempt.questionId in ids }
            val completed = relevant.map { it.attempt.questionId }.distinct().size
            val avg = relevant.map { it.attempt.score }.average().takeIf { !it.isNaN() }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(quiz.title, fontWeight = FontWeight.Bold); Text(quiz.focus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        avg?.let { V2Badge("%.1f/10".format(it)) }
                    }
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = { if (ids.isEmpty()) 0f else completed.toFloat() / ids.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                    )
                    Text("$completed of ${ids.size} questions attempted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        item { V2Section("Grammar mastery") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    if (grammar.isEmpty()) {
                        Text("Complete a few graded answers to see grammar strengths and weak areas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        grammar.sortedByDescending { it.second }.take(8).forEach { (name, score) ->
                            val p = (score / 10.0).coerceIn(0.0, 1.0).toFloat()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text("${(p * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(6.dp).clip(CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2HistoryScreen(state: UiState, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val shown = when (filter) {
        "Mistakes" -> state.attempts.filter { it.attempt.score < 8 }
        "High scores" -> state.attempts.filter { it.attempt.score >= 9 }
        else -> state.attempts
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        V2Header("Review", "History", onBack, topPadding = 18.dp)
        Spacer(Modifier.height(10.dp))
        V2ChoiceChips(listOf("All", "Mistakes", "High scores"), filter) { filter = it }
        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) {
            V2Info("Nothing here yet", "Your graded translations will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown) { item ->
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            V2ScoreBadge(item.attempt.score)
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
}

@Composable
private fun V2MoreScreen(state: UiState, onHistory: () -> Unit, onSettings: () -> Unit) {
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { V2Eyebrow("More"); Text("Review & settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            V2Avatar(profile?.name ?: "P")
        }
        Spacer(Modifier.height(20.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            V2Action(Icons.Outlined.History, "History", "Review answers and corrections", onHistory)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            V2Action(Icons.Outlined.Settings, "Settings", "Profiles, AI model, theme and data", onSettings)
        }
    }
}

@Composable
private fun V2SettingsScreen(
    state: UiState,
    vm: QuizViewModel,
    theme: V2Theme,
    onTheme: (V2Theme) -> Unit,
    onBack: () -> Unit,
    onProfiles: () -> Unit
) {
    var geminiKey by remember { mutableStateOf("") }
    var deepSeekKey by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { V2Header("", "Settings", onBack) }
        item { V2Section("Profile") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    V2Avatar(profile?.name ?: "P")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(profile?.name ?: "Profile", fontWeight = FontWeight.Bold); Text("${state.quizSets.size} saved quizzes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onProfiles) { Text("Switch") }
                }
            }
        }
        item { V2Section("AI model") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Box {
                        OutlinedButton(onClick = { modelExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state.selectedModel.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                            AiModels.all.forEach { model ->
                                DropdownMenuItem(text = { Text(model.label) }, onClick = { vm.selectModel(model); modelExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.selectedModel.provider == AiProvider.GEMINI) {
                        V2SecretField("Gemini API key", geminiKey, { geminiKey = it }, state.geminiKeyConfigured) {
                            vm.saveGeminiKey(geminiKey); geminiKey = ""
                        }
                    } else {
                        V2SecretField("DeepSeek API key", deepSeekKey, { deepSeekKey = it }, state.deepSeekKeyConfigured) {
                            vm.saveDeepSeekKey(deepSeekKey); deepSeekKey = ""
                        }
                    }
                }
            }
        }
        item { V2Section("Appearance") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    V2ChoiceChips(listOf("System", "Light", "Dark"), theme.name.lowercase().replaceFirstChar { it.uppercase() }) {
                        onTheme(V2Theme.valueOf(it.uppercase()))
                    }
                }
            }
        }
        item {
            V2Info(
                "Learning data",
                "Quiz libraries, drafts, progress and graded history are stored locally. Normal APK updates preserve the Room database."
            )
        }
        state.message?.let { item { V2Info("Status", it) } }
    }
}

@Composable
private fun V2SecretField(label: String, value: String, onValue: (String) -> Unit, configured: Boolean, onSave: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = { if (configured) Text("Configured ✓", color = MaterialTheme.colorScheme.secondary) }
    )
    Button(onClick = onSave, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save key") }
}

@Composable
private fun V2Header(eyebrow: String, title: String, onBack: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(Modifier.fillMaxWidth().padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (eyebrow.isNotBlank()) V2Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        FilledTonalIconButton(onClick = onBack) { Icon(Icons.Outlined.Home, contentDescription = "Home") }
    }
}

@Composable
private fun V2Avatar(name: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun V2Stat(label: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun V2Action(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V2Eyebrow(text: String) {
    Text(text.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun V2Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun V2Badge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary, shape = CircleShape) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun V2ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected.equals(option, ignoreCase = true),
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun V2LabeledField(label: String, value: String, onValue: (String) -> Unit, placeholder: String) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun V2Info(title: String, text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun V2InlineNotice(text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun V2FeedbackBox(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, modifier = Modifier.padding(12.dp), color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun V2ChipRow(items: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { V2Badge(it) }
    }
}

@Composable
private fun V2ScoreBadge(score: Int) {
    val bg = if (score >= 8) MaterialTheme.colorScheme.secondaryContainer else if (score >= 6) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (score >= 8) MaterialTheme.colorScheme.secondary else if (score >= 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(score.toString(), color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

private fun v2QuizIds(json: String): List<Long> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { index -> array.optLong(index) }.filter { it > 0L }
}.getOrDefault(emptyList())

private fun v2GrammarAverages(attempts: List<AttemptWithQuestion>): List<Pair<String, Double>> {
    val map = linkedMapOf<String, MutableList<Int>>()
    attempts.forEach { item ->
        runCatching {
            val array = JSONArray(item.attempt.grammarTopicsJson)
            for (index in 0 until array.length()) {
                val topic = array.optString(index).trim()
                if (topic.isNotBlank()) map.getOrPut(topic) { mutableListOf() }.add(item.attempt.score)
            }
        }
    }
    return map.map { it.key to it.value.average() }
}
