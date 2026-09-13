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

    val state: StateFlow<UiState> = combine(
        local,
        dao.profiles(),
        dao.questions(),
        profileAttempts.onStart { emit(emptyList()) }
    ) { s, profiles, qs, at ->
        val selectedProfile = s.selectedProfileId ?: profiles.firstOrNull()?.id
        if (selectedProfile != null && profileId.value != selectedProfile) profileId.value = selectedProfile
        val selectedQuestion = s.selectedQuestionId ?: qs.firstOrNull()?.id
        s.copy(
            profiles = profiles,
            selectedProfileId = selectedProfile,
            questions = qs,
            attempts = at,
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
            prefs.edit().putLong("selected_profile_id", selectedId).apply()
            profileId.value = selectedId
            local.update { it.copy(selectedProfileId = selectedId) }

            if (dao.questions().first().isEmpty()) {
                starterQuestions().forEach { dao.insertQuestion(it) }
            }
        }
    }

    fun addProfile(name: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val id = dao.insertProfile(ProfileEntity(name = cleaned))
            prefs.edit().putLong("selected_profile_id", id).apply()
            profileId.value = id
            local.update { it.copy(selectedProfileId = id, evaluation = null, explanation = null, message = "Profile '$cleaned' created") }
        }
    }

    fun selectProfile(id: Long) {
        prefs.edit().putLong("selected_profile_id", id).apply()
        profileId.value = id
        local.update { it.copy(selectedProfileId = id, evaluation = null, explanation = null, message = null) }
    }

    fun selectQuestion(id: Long) {
        local.update { it.copy(selectedQuestionId = id, evaluation = null, explanation = null, message = null) }
    }

    fun nextQuestion() {
        val session = state.value.questions.take(10)
        if (session.isEmpty()) return
        val index = session.indexOfFirst { it.id == state.value.selectedQuestionId }
        val next = if (index < 0 || index >= session.lastIndex) 0 else index + 1
        selectQuestion(session[next].id)
    }

    fun previousQuestion() {
        val session = state.value.questions.take(10)
        if (session.isEmpty()) return
        val index = session.indexOfFirst { it.id == state.value.selectedQuestionId }
        val prev = if (index <= 0) session.lastIndex else index - 1
        selectQuestion(session[prev].id)
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
        val s = state.value
        val q = s.questions.firstOrNull { it.id == s.selectedQuestionId } ?: return
        val pid = s.selectedProfileId ?: return
        if (answer.isBlank()) return
        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null, explanation = null) }
            runCatching {
                val ev = ai.evaluate(q.english, answer.trim(), q.level, s.selectedModel)
                dao.insertAttempt(
                    AttemptEntity(
                        questionId = q.id,
                        profileId = pid,
                        answer = answer.trim(),
                        score = ev.score,
                        correctedTranslation = ev.correctedTranslation,
                        shortFeedback = ev.shortFeedback,
                        mistakesJson = JSONArray(ev.mistakes).toString(),
                        improveJson = JSONArray(ev.howToImprove).toString(),
                        grammarTopicsJson = JSONArray(ev.grammarTopics).toString()
                    )
                )
                ev
            }.onSuccess { ev -> local.update { it.copy(loading = false, evaluation = ev) } }
             .onFailure { e -> local.update { it.copy(loading = false, message = e.message ?: "Grading failed") } }
        }
    }

    fun generate(count: Int, level: String, topic: String, focus: String) {
        val model = state.value.selectedModel
        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null) }
            runCatching { ai.generateQuestions(count, level, topic, focus, model) }
                .onSuccess { list ->
                    list.forEach { q -> dao.insertQuestion(QuestionEntity(english = q.english, level = q.level, topic = q.topic, focus = q.focus)) }
                    local.update { it.copy(loading = false, message = "${list.size} questions saved") }
                }
                .onFailure { e -> local.update { it.copy(loading = false, message = e.message ?: "Generation failed") } }
        }
    }

    fun explainLast(answer: String) {
        val s = state.value
        val q = s.questions.firstOrNull { it.id == s.selectedQuestionId } ?: return
        val ev = s.evaluation ?: return
        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null) }
            runCatching { ai.explain(q.english, answer.trim(), ev.score, ev.correctedTranslation, ev.shortFeedback, s.selectedModel) }
                .onSuccess { text -> local.update { it.copy(loading = false, explanation = text) } }
                .onFailure { e -> local.update { it.copy(loading = false, message = e.message ?: "Explanation failed") } }
        }
    }

    private fun starterQuestions(): List<QuestionEntity> = listOf(
        QuestionEntity(english = "Although I was tired, I finished the work because it was important.", level = "B1", topic = "Everyday life", focus = "Subordinate clauses"),
        QuestionEntity(english = "If I had more time, I would learn another language.", level = "B1", topic = "Everyday life", focus = "Konjunktiv II"),
        QuestionEntity(english = "Could you please tell me when the next train leaves?", level = "B1", topic = "Travel", focus = "Indirect questions"),
        QuestionEntity(english = "I have been living in Germany since 2019.", level = "B1", topic = "Everyday life", focus = "Time expressions"),
        QuestionEntity(english = "After I finished the meeting, I sent my colleague an email.", level = "B1", topic = "Work", focus = "Word order"),
        QuestionEntity(english = "We can solve the problem by discussing it together.", level = "B1", topic = "Work", focus = "indem / dadurch dass"),
        QuestionEntity(english = "Despite the bad weather, we decided to go for a walk.", level = "B1", topic = "Everyday life", focus = "Prepositions"),
        QuestionEntity(english = "I would have called you if I had known that you were at home.", level = "B1", topic = "Conversation", focus = "Konjunktiv II Vergangenheit"),
        QuestionEntity(english = "The documents must be submitted by Friday.", level = "B1", topic = "Work", focus = "Passive with modal verb"),
        QuestionEntity(english = "She explained the process so clearly that everyone understood it.", level = "B1", topic = "Work", focus = "Subordinate clauses")
    )
}
