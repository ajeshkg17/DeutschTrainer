package com.example.deutschtrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Screen { PRACTICE, GENERATE, PROGRESS, HISTORY, SETTINGS }

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{DeutschTrainerApp()}}}}

@Composable fun DeutschTrainerApp(vm:QuizViewModel=viewModel()){
 val s by vm.state.collectAsState();var screen by remember{mutableStateOf(Screen.PRACTICE)}
 Scaffold(bottomBar={NavigationBar{listOf(Screen.PRACTICE to "Practice",Screen.GENERATE to "Generate",Screen.PROGRESS to "Progress",Screen.HISTORY to "History",Screen.SETTINGS to "Settings").forEach{(x,t)->NavigationBarItem(selected=screen==x,onClick={screen=x},icon={Text(t.take(1))},label={Text(t)})}}}){pad->
  Box(Modifier.padding(pad).padding(16.dp)){when(screen){Screen.PRACTICE->Practice(s,vm);Screen.GENERATE->Generate(s,vm);Screen.PROGRESS->Progress(s);Screen.HISTORY->History(s);Screen.SETTINGS->Settings(s,vm)}}
 }
}

@Composable fun Practice(s:UiState,vm:QuizViewModel){var answer by remember(s.selectedQuestionId){mutableStateOf("")};val q=s.questions.firstOrNull{it.id==s.selectedQuestionId}
 LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Deutsch Trainer",style=MaterialTheme.typography.headlineMedium);Text("Translate English → German")};if(q==null){item{Card{Column(Modifier.padding(16.dp)){Text("No questions yet.");Text("Open Generate to create your first practice set.")}}}}else{item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("${q.level} • ${q.topic}");Text(q.english,style=MaterialTheme.typography.titleLarge);OutlinedTextField(answer,{answer=it},label={Text("Your German translation")},modifier=Modifier.fillMaxWidth(),minLines=3);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(vm::previousQuestion){Text("Back")};Button(onClick={vm.grade(answer)},enabled=answer.isNotBlank()&&!s.loading){Text(if(s.loading)"Checking…" else "Check")};OutlinedButton(vm::nextQuestion){Text("Next")}}}}};s.evaluation?.let{e->item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Score ${e.score}/10",style=MaterialTheme.typography.headlineSmall);Text(e.shortFeedback);Text("Correction: ${e.correctedTranslation}");if(e.mistakes.isNotEmpty())Text("Mistakes: ${e.mistakes.joinToString(" • ")}");Button(onClick={vm.explainLast(answer)},enabled=!s.loading){Text("Explain in detail")};s.explanation?.let{Text(it)}}}}}};s.message?.let{item{Text(it)}}}
}

@Composable fun Generate(s:UiState,vm:QuizViewModel){var level by remember{mutableStateOf("B1")};var topic by remember{mutableStateOf("Everyday life")};var focus by remember{mutableStateOf("mixed grammar")};var count by remember{mutableStateOf("10")};Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Generate questions",style=MaterialTheme.typography.headlineMedium);OutlinedTextField(level,{level=it},label={Text("CEFR level")});OutlinedTextField(topic,{topic=it},label={Text("Topic")});OutlinedTextField(focus,{focus=it},label={Text("Grammar focus")});OutlinedTextField(count,{count=it},label={Text("Number")});Button(onClick={vm.generate(count.toIntOrNull()?.coerceIn(1,30)?:10,level,topic,focus)},enabled=!s.loading){Text(if(s.loading)"Generating…" else "Generate & save")};s.message?.let{Text(it)}}}

@Composable fun Progress(s:UiState){val avg=if(s.attempts.isEmpty())0.0 else s.attempts.map{it.attempt.score}.average();Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Progress",style=MaterialTheme.typography.headlineMedium);Card{Column(Modifier.padding(16.dp)){Text("Attempts: ${s.attempts.size}");Text("Average score: ${"%.1f".format(avg)}/10")}}}}

@Composable fun History(s:UiState){LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("History",style=MaterialTheme.typography.headlineMedium)};items(s.attempts){x->Card{Column(Modifier.padding(12.dp)){Text(x.question.english);Text(x.attempt.answer);Text("${x.attempt.score}/10 • ${x.attempt.shortFeedback}")}}}}}

@Composable fun Settings(s:UiState,vm:QuizViewModel){var name by remember{mutableStateOf("")};var g by remember{mutableStateOf("")};var d by remember{mutableStateOf("")};LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Settings",style=MaterialTheme.typography.headlineMedium)};item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Profiles",style=MaterialTheme.typography.titleMedium);s.profiles.forEach{p->FilterChip(selected=p.id==s.selectedProfileId,onClick={vm.selectProfile(p.id)},label={Text(p.name)})};OutlinedTextField(name,{name=it},label={Text("New profile name")});Button(onClick={vm.addProfile(name);name=""},enabled=name.isNotBlank()){Text("Add profile")}}}};item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("AI model",style=MaterialTheme.typography.titleMedium);AiModels.all.forEach{m->FilterChip(selected=m.id==s.selectedModel.id,onClick={vm.selectModel(m)},label={Text(m.label)})}}}};item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(g,{g=it},label={Text("Gemini API key")},modifier=Modifier.fillMaxWidth());Button(onClick={vm.saveGeminiKey(g);g=""},enabled=g.isNotBlank()){Text("Save Gemini key")};if(s.geminiKeyConfigured)Text("Gemini key configured ✓");OutlinedTextField(d,{d=it},label={Text("DeepSeek API key")},modifier=Modifier.fillMaxWidth());Button(onClick={vm.saveDeepSeekKey(d);d=""},enabled=d.isNotBlank()){Text("Save DeepSeek key")};if(s.deepSeekKeyConfigured)Text("DeepSeek key configured ✓")}}}}}
}
