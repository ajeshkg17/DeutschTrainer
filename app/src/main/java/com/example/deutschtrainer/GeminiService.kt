package com.example.deutschtrainer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Evaluation(
    val score: Int,
    val correctedTranslation: String,
    val shortFeedback: String,
    val mistakes: List<String>,
    val howToImprove: List<String>,
    val grammarTopics: List<String>
)

data class GeneratedQuestion(
    val english: String,
    val level: String,
    val topic: String,
    val focus: String
)

class GeminiService(private val keyStore: ApiKeyStore) {

    suspend fun evaluate(english: String, answer: String, level: String, model: AiModel): Evaluation {
        val prompt = """
You are a strict but constructive German-language examiner.

English sentence:
$english

Learner's German translation:
$answer

Target CEFR level:
$level

Give an integer score from 1 to 10.
10 = fully correct, idiomatic and natural.
8-9 = essentially correct, only small errors.
6-7 = understandable but noticeable grammar or word-choice problems.
4-5 = several important mistakes.
1-3 = major errors or meaning substantially lost.

Do not punish a valid alternative translation. Judge meaning, grammar, word order, case, tense and naturalness. If the learner used speech-to-text, treat the submitted text exactly like a typed answer and do not speculate about pronunciation.

Return ONLY valid JSON in this exact structure:
{
  "score": 8,
  "corrected_translation": "...",
  "short_feedback": "...",
  "mistakes": ["..."],
  "how_to_improve": ["..."],
  "grammar_topics": ["..."]
}
""".trimIndent()

        val obj = JSONObject(cleanJson(generate(prompt, model)))
        return Evaluation(
            score = obj.optInt("score", 1).coerceIn(1, 10),
            correctedTranslation = obj.optString("corrected_translation"),
            shortFeedback = obj.optString("short_feedback"),
            mistakes = obj.optJSONArray("mistakes")?.toStringList().orEmpty(),
            howToImprove = obj.optJSONArray("how_to_improve")?.toStringList().orEmpty(),
            grammarTopics = obj.optJSONArray("grammar_topics")?.toStringList().orEmpty()
        )
    }

    suspend fun generateQuestions(count: Int, level: String, topic: String, focus: String, model: AiModel): List<GeneratedQuestion> {
        val prompt = """
Create exactly $count English-to-German translation practice questions.

CEFR level: $level
Topic: $topic
Grammar focus: $focus

Learning-design requirements:
- Each item must be an English sentence that the learner will translate into German.
- Keep every sentence clearly connected to the requested topic.
- Keep vocabulary and sentence complexity appropriate for $level.
- If the grammar focus is specific, make most questions genuinely require that grammar point rather than merely mentioning it in metadata.
- If the focus is Mixed grammar, use a balanced variety of useful grammar structures for the level.
- Vary sentence length, tense, clause structure, subject, vocabulary and communicative situation.
- Include a realistic mixture of statements, requests, questions, reasons, conditions and time relationships where suitable.
- Do not produce duplicate or near-duplicate sentences inside the set.
- Do not give German answers, translations, hints or explanations.
- Avoid trick questions and unnecessarily obscure vocabulary.
- Make the set useful for repeated real-world practice rather than a list of tiny variations of one sentence.

Return ONLY valid JSON:
{
  "questions": [
    {
      "english": "...",
      "level": "$level",
      "topic": "$topic",
      "focus": "$focus"
    }
  ]
}
""".trimIndent()
        val arr = JSONObject(cleanJson(generate(prompt, model))).getJSONArray("questions")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    GeneratedQuestion(
                        english = o.getString("english"),
                        level = o.optString("level", level),
                        topic = o.optString("topic", topic),
                        focus = o.optString("focus", focus)
                    )
                )
            }
        }.distinctBy { it.english.trim().lowercase() }.take(count)
    }

    suspend fun explain(
        english: String,
        answer: String,
        score: Int,
        correction: String,
        feedback: String,
        model: AiModel
    ): String {
        val prompt = """
You are a German tutor.

English:
$english

Learner answer:
$answer

Score:
$score/10

Suggested correction:
$correction

Existing feedback:
$feedback

Explain:
1. What was correct.
2. Each important mistake and why.
3. Relevant German grammar rule.
4. A better formulation if useful.
5. Two short German examples using the same grammar point.

Use clear English for explanations and German for examples. Be concise enough for a learner to review on a phone.
""".trimIndent()
        return generate(prompt, model)
    }

    private suspend fun generate(prompt: String, model: AiModel): String = withContext(Dispatchers.IO) {
        when (model.provider) {
            AiProvider.GEMINI -> geminiRequest(prompt, model)
            AiProvider.DEEPSEEK -> deepSeekRequest(prompt, model)
        }
    }

    private fun geminiRequest(prompt: String, model: AiModel): String {
        val key = keyStore.geminiKey()
        check(key.isNotBlank()) { "Add your Gemini API key in Settings before continuing." }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.id}:generateContent?key=$key")
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        )
        return httpPost(url, body, mapOf("Content-Type" to "application/json")) { root ->
            root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.ifBlank { null }
                ?: error("Gemini returned no text")
        }
    }

    private fun deepSeekRequest(prompt: String, model: AiModel): String {
        val key = keyStore.deepSeekKey()
        check(key.isNotBlank()) { "Add your DeepSeek API key in Settings before continuing." }
        val body = JSONObject()
            .put("model", model.id)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0.3)
        return httpPost(
            URL("https://api.deepseek.com/chat/completions"),
            body,
            mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $key")
        ) { root ->
            root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.ifBlank { null }
                ?: error("DeepSeek returned no text")
        }
    }

    private fun httpPost(
        url: URL,
        json: JSONObject,
        headers: Map<String, String>,
        parse: (JSONObject) -> String
    ): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(json.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("AI request failed ($code): ${text.take(500)}")
            return parse(JSONObject(text))
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanJson(text: String): String {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOfFirst { it == '{' || it == '[' }
        val endObj = trimmed.lastIndexOf('}')
        val endArr = trimmed.lastIndexOf(']')
        val end = maxOf(endObj, endArr)
        return if (start >= 0 && end >= start) trimmed.substring(start, end + 1) else trimmed
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
