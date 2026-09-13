package com.example.deutschtrainer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray


data class UiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val selectedProfileId: Long? = null,
    val quizSets: List<QuizSetEntity> = emptyList(),
    val selectedQuizSetId: Long? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val attempts: List<AttemptWithQuestion> = emptyList(),
    val selectedQuestionId: Long? = null,
    val evaluation: Evaluation? = null,
    val explanation: String? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val selectedModel: AiModel = AiModels.default,
    val geminiKeyConfigured: Boolean = false,
    val deepSeekKeyConfigured: Boolean = false
)

class QuizViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).quizDao()
    private val keyStore = ApiKeyStore(app)
    private val prefs = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val ai = GeminiService(keyStore)

    private val local = MutableStateFlow(
        UiState(
            selectedModel = keyStore.selectedModel(),
            geminiKeyConfigured = keyStore.hasGeminiKey(),
            deepSeekKeyConfigured = keyStore.hasDeepSeekKey()
        )
    )

    private val profileId = MutableStateFlow<Long?>(null)
    private val profileAttempts = profileId.filterNotNull().flatMapLatest { dao.attempts(it) }
    private val profileQuizSets = profileId.filterNotNull().flatMapLatest { dao.quizSets(it) }

    val state: StateFlow<UiState> = combine(
        local,
        dao.profiles(),
        dao.questions(),
        profileAttempts.onStart { emit(emptyList()) },
        profileQuizSets.onStart { emit(emptyList()) }
    ) { s, profiles, allQuestions, attempts, quizSets ->
        val selectedProfile = s.selectedProfileId ?: profiles.firstOrNull()?.id
        if (selectedProfile != null && profileId.value != selectedProfile) profileId.value = selectedProfile

        val persistedQuizId = selectedProfile?.let {
            prefs.getLong(selectedQuizKey(it), -1L).takeIf { value -> value > 0L }
        }
        val selectedQuizId = when {
            s.selectedQuizSetId != null && quizSets.any { it.id == s.selectedQuizSetId } -> s.selectedQuizSetId
            persistedQuizId != null && quizSets.any { it.id == persistedQuizId } -> persistedQuizId
            else -> quizSets.firstOrNull()?.id
        }

        val selectedQuiz = quizSets.firstOrNull { it.id == selectedQuizId }
        val ids = parseQuestionIds(selectedQuiz?.questionIdsJson)
        val byId = allQuestions.associateBy { it.id }
        val quizQuestions = ids.mapNotNull { byId[it] }
        val persistedQuestionId = selectedQuizId?.let {
            prefs.getLong(selectedQuestionKey(it), -1L).takeIf { value -> value > 0L }
        }
        val selectedQuestion = s.selectedQuestionId
            ?.takeIf { id -> quizQuestions.any { it.id == id } }
            ?: persistedQuestionId?.takeIf { id -> quizQuestions.any { it.id == id } }
            ?: quizQuestions.firstOrNull()?.id

        s.copy(
            profiles = profiles,
            selectedProfileId = selectedProfile,
            quizSets = quizSets,
            selectedQuizSetId = selectedQuizId,
            questions = quizQuestions,
            attempts = attempts,
            selectedQuestionId = selectedQuestion
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            val profiles = dao.profiles().first()
            val selectedId = if (profiles.isEmpty()) {
                dao.insertProfile(ProfileEntity(name = "Default"))
            } else {
                val saved = prefs.getLong("selected_profile_id", -1L)
                profiles.firstOrNull { it.id == saved }?.id ?: profiles.first().id
            }

            if (dao.questions().first().isEmpty()) {
                starterQuestions().forEach { dao.insertQuestion(it) }
            }

            ensureQuizLibrary(selectedId, includeAllExisting = true)
            prefs.edit().putLong("selected_profile_id", selectedId).apply()
            profileId.value = selectedId
            local.update {
                it.copy(
                    selectedProfileId = selectedId,
                    selectedQuizSetId = null,
                    selectedQuestionId = null
                )
            }
        }
    }

    fun addProfile(name: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val id = dao.insertProfile(ProfileEntity(name = cleaned))
            ensureQuizLibrary(id, includeAllExisting = false)
            prefs.edit().putLong("selected_profile_id", id).apply()
            profileId.value = id
            local.update {
                it.copy(
                    selectedProfileId = id,
                    selectedQuizSetId = null,
                    selectedQuestionId = null,
                    evaluation = null,
                    explanation = null,
                    message = "Profile '$cleaned' created"
                )
            }
        }
    }

    fun selectProfile(id: Long) {
        prefs.edit().putLong("selected_profile_id", id).apply()
        profileId.value = id
        local.update {
            it.copy(
                selectedProfileId = id,
                selectedQuizSetId = null,
                selectedQuestionId = null,
                evaluation = null,
                explanation = null,
                message = null
            )
        }
        viewModelScope.launch { ensureQuizLibrary(id, includeAllExisting = true) }
    }

    fun selectQuizSet(id: Long) {
        val pid = state.value.selectedProfileId ?: return
        prefs.edit().putLong(selectedQuizKey(pid), id).apply()
        local.update {
            it.copy(
                selectedQuizSetId = id,
                selectedQuestionId = null,
                evaluation = null,
                explanation = null,
                message = null
            )
        }
    }

    fun deleteQuizSet(id: Long) {
        val snapshot = state.value
        val pid = snapshot.selectedProfileId
        viewModelScope.launch {
            dao.deleteQuizSet(id)
            if (pid != null && snapshot.selectedQuizSetId == id) {
                prefs.edit().remove(selectedQuizKey(pid)).apply()
            }
            prefs.edit().remove(selectedQuestionKey(id)).apply()
            local.update {
                it.copy(
                    selectedQuizSetId = if (it.selectedQuizSetId == id) null else it.selectedQuizSetId,
                    selectedQuestionId = null,
                    evaluation = null,
                    explanation = null,
                    message = "Quiz removed from this profile. Graded history was kept."
                )
            }
        }
    }

    fun createMistakesQuiz() {
        val snapshot = state.value
        val pid = snapshot.selectedProfileId ?: return
        val latestPerQuestion = snapshot.attempts.distinctBy { it.attempt.questionId }
        val weak = latestPerQuestion.filter { it.attempt.score < 8 }.take(20)
        val ids = weak.map { it.attempt.questionId }.distinct()
        if (ids.isEmpty()) {
            local.update { it.copy(message = "No current low-score answers to review. Answers below 8/10 will appear here.") }
            return
        }

        val grammar = weak.flatMap { item ->
            runCatching {
                val array = JSONArray(item.attempt.grammarTopicsJson)
                List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }.distinct().take(3)
        val focus = grammar.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Recent weak areas"

        viewModelScope.launch {
            val setId = dao.insertQuizSet(
                QuizSetEntity(
                    profileId = pid,
                    title = "Mistakes review (${ids.size})",
                    level = "Mixed",
                    topic = "Adaptive review",
                    focus = focus,
                    questionIdsJson = JSONArray(ids).toString()
                )
            )
            prefs.edit()
                .putLong(selectedQuizKey(pid), setId)
                .putLong(selectedQuestionKey(setId), ids.first())
                .apply()
            local.update {
                it.copy(
                    selectedQuizSetId = setId,
                    selectedQuestionId = ids.first(),
                    evaluation = null,
                    explanation = null,
                    message = "Created a review quiz from ${ids.size} recent weak answers"
                )
            }
        }
    }

    fun selectQuestion(id: Long) {
        state.value.selectedQuizSetId?.let { quizId ->
            prefs.edit().putLong(selectedQuestionKey(quizId), id).apply()
        }
        local.update { it.copy(selectedQuestionId = id, evaluation = null, explanation = null, message = null) }
    }

    fun nextQuestion() {
        val session = state.value.questions
        if (session.isEmpty()) return
        val index = session.indexOfFirst { it.id == state.value.selectedQuestionId }
        val next = if (index < 0 || index >= session.lastIndex) 0 else index + 1
        selectQuestion(session[next].id)
    }

    fun previousQuestion() {
        val session = state.value.questions
        if (session.isEmpty()) return
        val index = session.indexOfFirst { it.id == state.value.selectedQuestionId }
        val previous = if (index <= 0) session.lastIndex else index - 1
        selectQuestion(session[previous].id)
    }

    fun selectModel(model: AiModel) {
        keyStore.selectModel(model)
        local.update { it.copy(selectedModel = model, message = null) }
    }

    fun saveGeminiKey(key: String) {
        if (key.isBlank()) return
        keyStore.saveGeminiKey(key)
        local.update { it.copy(geminiKeyConfigured = true, message = "Gemini API key saved on this device.") }
    }

    fun saveDeepSeekKey(key: String) {
        if (key.isBlank()) return
        keyStore.saveDeepSeekKey(key)
        local.update { it.copy(deepSeekKeyConfigured = true, message = "DeepSeek API key saved on this device.") }
    }

    fun clearGeminiKey() {
        keyStore.clearGeminiKey()
        local.update { it.copy(geminiKeyConfigured = false, message = "Gemini API key removed from this device.") }
    }

    fun clearDeepSeekKey() {
        keyStore.clearDeepSeekKey()
        local.update { it.copy(deepSeekKeyConfigured = false, message = "DeepSeek API key removed from this device.") }
    }

    fun grade(answer: String) {
        val snapshot = state.value
        val question = snapshot.questions.firstOrNull { it.id == snapshot.selectedQuestionId } ?: return
        val pid = snapshot.selectedProfileId ?: return
        if (answer.isBlank()) return

        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null, explanation = null) }
            try {
                val evaluation = ai.evaluate(question.english, answer.trim(), question.level, snapshot.selectedModel)
                dao.insertAttempt(
                    AttemptEntity(
                        questionId = question.id,
                        profileId = pid,
                        quizSetId = snapshot.selectedQuizSetId,
                        answer = answer.trim(),
                        score = evaluation.score,
                        correctedTranslation = evaluation.correctedTranslation,
                        shortFeedback = evaluation.shortFeedback,
                        mistakesJson = JSONArray(evaluation.mistakes).toString(),
                        improveJson = JSONArray(evaluation.howToImprove).toString(),
                        grammarTopicsJson = JSONArray(evaluation.grammarTopics).toString()
                    )
                )
                local.update { it.copy(loading = false, evaluation = evaluation) }
            } catch (e: Exception) {
                local.update { it.copy(loading = false, message = e.message ?: "Grading failed") }
            }
        }
    }

    fun generate(
        count: Int,
        level: String,
        topic: String,
        focus: String,
        title: String = ""
    ) {
        val snapshot = state.value
        val model = snapshot.selectedModel
        val pid = snapshot.selectedProfileId ?: return
        val safeTopic = topic.trim().ifBlank { "General German" }
        val safeFocus = focus.trim().ifBlank { "Mixed grammar" }
        val safeTitle = title.trim().ifBlank { safeTopic }

        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null) }
            try {
                val generated = ai.generateQuestions(count, level, safeTopic, safeFocus, model)
                val ids = mutableListOf<Long>()
                for (question in generated) {
                    ids += dao.insertQuestion(
                        QuestionEntity(
                            english = question.english,
                            level = question.level,
                            topic = question.topic,
                            focus = question.focus
                        )
                    )
                }

                val setId = dao.insertQuizSet(
                    QuizSetEntity(
                        profileId = pid,
                        title = safeTitle,
                        level = level,
                        topic = safeTopic,
                        focus = safeFocus,
                        questionIdsJson = JSONArray(ids).toString()
                    )
                )
                val editor = prefs.edit().putLong(selectedQuizKey(pid), setId)
                ids.firstOrNull()?.let { editor.putLong(selectedQuestionKey(setId), it) }
                editor.apply()
                local.update {
                    it.copy(
                        loading = false,
                        selectedQuizSetId = setId,
                        selectedQuestionId = ids.firstOrNull(),
                        evaluation = null,
                        explanation = null,
                        message = "Saved '$safeTitle' as a new quiz with ${ids.size} questions"
                    )
                }
            } catch (e: Exception) {
                local.update { it.copy(loading = false, message = e.message ?: "Generation failed") }
            }
        }
    }

    fun explainLast(answer: String) {
        val snapshot = state.value
        val question = snapshot.questions.firstOrNull { it.id == snapshot.selectedQuestionId } ?: return
        val evaluation = snapshot.evaluation ?: return
        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null) }
            try {
                val text = ai.explain(
                    question.english,
                    answer.trim(),
                    evaluation.score,
                    evaluation.correctedTranslation,
                    evaluation.shortFeedback,
                    snapshot.selectedModel
                )
                local.update { it.copy(loading = false, explanation = text) }
            } catch (e: Exception) {
                local.update { it.copy(loading = false, message = e.message ?: "Explanation failed") }
            }
        }
    }

    private suspend fun ensureQuizLibrary(profileId: Long, includeAllExisting: Boolean) {
        if (dao.quizSetCount(profileId) > 0) return
        val all = dao.questions().first().sortedWith(compareBy<QuestionEntity> { it.createdAt }.thenBy { it.id })
        if (all.isEmpty()) return

        val batches = if (includeAllExisting) {
            splitQuestionBatches(all)
        } else {
            listOf(all.take(10))
        }

        var newestSetId: Long? = null
        var newestFirstQuestionId: Long? = null

        batches.filter { it.isNotEmpty() }.forEachIndexed { index, batch ->
            val level = batch.map { it.level }.distinct().singleOrNull() ?: "Mixed"
            val topic = batch.map { it.topic }.distinct().singleOrNull() ?: "Mixed topics"
            val focus = batch.map { it.focus }.distinct().singleOrNull() ?: "Mixed grammar"
            val title = when {
                index == 0 && batch.size == 10 && level == "B1" -> "Starter B1"
                topic != "Mixed topics" && focus != "Mixed grammar" -> "$topic · $focus"
                topic != "Mixed topics" -> topic
                else -> "Recovered quiz ${index + 1}"
            }

            val setId = dao.insertQuizSet(
                QuizSetEntity(
                    profileId = profileId,
                    title = title,
                    level = level,
                    topic = topic,
                    focus = focus,
                    questionIdsJson = JSONArray(batch.map { it.id }).toString(),
                    createdAt = batch.maxOf { it.createdAt }
                )
            )
            prefs.edit().putLong(selectedQuestionKey(setId), batch.first().id).apply()
            newestSetId = setId
            newestFirstQuestionId = batch.first().id
        }

        if (newestSetId != null) {
            prefs.edit()
                .putLong(selectedQuizKey(profileId), newestSetId!!)
                .putLong(selectedQuestionKey(newestSetId!!), newestFirstQuestionId!!)
                .apply()
        }
    }

    private fun splitQuestionBatches(questions: List<QuestionEntity>): List<List<QuestionEntity>> {
        if (questions.isEmpty()) return emptyList()
        val batches = mutableListOf<MutableList<QuestionEntity>>()
        var current = mutableListOf<QuestionEntity>()
        questions.forEach { question ->
            val previous = current.lastOrNull()
            if (previous != null && question.createdAt - previous.createdAt > 15_000L) {
                batches += current
                current = mutableListOf()
            }
            current += question
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private fun selectedQuizKey(profileId: Long) = "selected_quiz_set_$profileId"
    private fun selectedQuestionKey(quizId: Long) = "selected_question_$quizId"

    private fun parseQuestionIds(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index -> array.optLong(index) }.filter { it > 0L }
        }.getOrDefault(emptyList())
    }

    private fun starterQuestions(): List<QuestionEntity> = listOf(
        QuestionEntity(
            english = "Although I was tired, I finished the work because it was important.",
            level = "B1",
            topic = "Everyday life",
            focus = "Subordinate clauses"
        ),
        QuestionEntity(
            english = "If I had more time, I would learn another language.",
            level = "B1",
            topic = "Everyday life",
            focus = "Konjunktiv II"
        ),
        QuestionEntity(
            english = "Could you please tell me when the next train leaves?",
            level = "B1",
            topic = "Travel",
            focus = "Indirect questions"
        ),
        QuestionEntity(
            english = "I have been living in Germany since 2019.",
            level = "B1",
            topic = "Everyday life",
            focus = "Time expressions"
        ),
        QuestionEntity(
            english = "After I finished the meeting, I sent my colleague an email.",
            level = "B1",
            topic = "Work",
            focus = "Word order"
        ),
        QuestionEntity(
            english = "We can solve the problem by discussing it together.",
            level = "B1",
            topic = "Work",
            focus = "indem / dadurch dass"
        ),
        QuestionEntity(
            english = "Despite the bad weather, we decided to go for a walk.",
            level = "B1",
            topic = "Everyday life",
            focus = "Prepositions"
        ),
        QuestionEntity(
            english = "I would have called you if I had known that you were at home.",
            level = "B1",
            topic = "Conversation",
            focus = "Konjunktiv II Vergangenheit"
        ),
        QuestionEntity(
            english = "The documents must be submitted by Friday.",
            level = "B1",
            topic = "Work",
            focus = "Passive with modal verb"
        ),
        QuestionEntity(
            english = "She explained the process so clearly that everyone understood it.",
            level = "B1",
            topic = "Work",
            focus = "Subordinate clauses"
        )
    )
}
