package com.example.deutschtrainer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Portable, versioned backup format for one learner profile.
 *
 * The backup intentionally excludes API keys and Android-private preferences.
 * It contains the selected learner's quizzes, questions, graded attempts,
 * corrections, grammar tags and timestamps so it can be restored after an
 * uninstall or on another device.
 */
class ProfileBackupManager(private val context: Context) {
    private val database = AppDatabase.get(context)
    private val dao = database.quizDao()

    data class ExportSummary(
        val profileName: String,
        val quizCount: Int,
        val questionCount: Int,
        val attemptCount: Int
    )

    data class ImportSummary(
        val profileId: Long,
        val profileName: String,
        val quizCount: Int,
        val questionCount: Int,
        val attemptCount: Int
    )

    suspend fun suggestedFileName(profileId: Long): String {
        val name = dao.profileSnapshot(profileId)?.name ?: "Profile"
        val safe = name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "Profile" }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "DeutschTrainer_${safe}_$date.json"
    }

    suspend fun exportProfile(profileId: Long, uri: Uri): ExportSummary {
        val profile = dao.profileSnapshot(profileId) ?: error("The selected profile no longer exists.")
        val allQuestions = dao.questionsSnapshot()
        val quizSets = dao.quizSetsSnapshot(profileId)
        val attempts = dao.attemptsSnapshot(profileId)

        val referencedIds = linkedSetOf<Long>()
        quizSets.forEach { set -> referencedIds += parseIdArray(set.questionIdsJson) }
        attempts.forEach { referencedIds += it.questionId }
        val questions = allQuestions.filter { it.id in referencedIds }

        val root = JSONObject()
            .put("format", FORMAT_NAME)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("app", "DeutschTrainer")
            .put(
                "profile",
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("createdAt", profile.createdAt)
            )
            .put(
                "questions",
                JSONArray().apply {
                    questions.forEach { q ->
                        put(
                            JSONObject()
                                .put("id", q.id)
                                .put("english", q.english)
                                .put("level", q.level)
                                .put("topic", q.topic)
                                .put("focus", q.focus)
                                .put("createdAt", q.createdAt)
                        )
                    }
                }
            )
            .put(
                "quizSets",
                JSONArray().apply {
                    quizSets.forEach { set ->
                        put(
                            JSONObject()
                                .put("id", set.id)
                                .put("title", set.title)
                                .put("level", set.level)
                                .put("topic", set.topic)
                                .put("focus", set.focus)
                                .put("questionIds", JSONArray(parseIdArray(set.questionIdsJson)))
                                .put("createdAt", set.createdAt)
                        )
                    }
                }
            )
            .put(
                "attempts",
                JSONArray().apply {
                    attempts.forEach { a ->
                        put(
                            JSONObject()
                                .put("id", a.id)
                                .put("questionId", a.questionId)
                                .put("quizSetId", a.quizSetId ?: JSONObject.NULL)
                                .put("answer", a.answer)
                                .put("score", a.score)
                                .put("correctedTranslation", a.correctedTranslation)
                                .put("shortFeedback", a.shortFeedback)
                                .put("mistakesJson", a.mistakesJson)
                                .put("improveJson", a.improveJson)
                                .put("grammarTopicsJson", a.grammarTopicsJson)
                                .put("createdAt", a.createdAt)
                        )
                    }
                }
            )
            .put(
                "privacy",
                JSONObject()
                    .put("apiKeysIncluded", false)
                    .put("note", "API keys are intentionally excluded from backups.")
            )

        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
            writer.write(root.toString(2))
        } ?: error("Android could not open the selected file for writing.")

        return ExportSummary(
            profileName = profile.name,
            quizCount = quizSets.size,
            questionCount = questions.size,
            attemptCount = attempts.size
        )
    }

    suspend fun importProfile(uri: Uri): ImportSummary {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Android could not open that backup file.")
        val root = JSONObject(text)

        require(root.optString("format") == FORMAT_NAME) {
            "This is not a DeutschTrainer profile backup."
        }
        val version = root.optInt("formatVersion", -1)
        require(version in 1..FORMAT_VERSION) {
            "Backup format version $version is not supported by this app."
        }

        val profileObject = root.getJSONObject("profile")
        val originalName = profileObject.optString("name").trim().ifBlank { "Restored profile" }
        val existingNames = dao.profilesSnapshot().map { it.name.lowercase(Locale.ROOT) }.toSet()
        val restoredName = uniqueProfileName(originalName, existingNames)

        val questionObjects = root.optJSONArray("questions") ?: JSONArray()
        val quizObjects = root.optJSONArray("quizSets") ?: JSONArray()
        val attemptObjects = root.optJSONArray("attempts") ?: JSONArray()

        return database.withTransaction {
            val newProfileId = dao.insertProfile(
                ProfileEntity(
                    name = restoredName,
                    createdAt = profileObject.optLong("createdAt", System.currentTimeMillis())
                )
            )

            val questionMap = mutableMapOf<Long, Long>()
            for (i in 0 until questionObjects.length()) {
                val q = questionObjects.getJSONObject(i)
                val oldId = q.getLong("id")
                val newId = dao.insertQuestion(
                    QuestionEntity(
                        english = q.optString("english"),
                        level = q.optString("level", "B1"),
                        topic = q.optString("topic", "Imported"),
                        focus = q.optString("focus", "Mixed"),
                        createdAt = q.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                questionMap[oldId] = newId
            }

            val quizMap = mutableMapOf<Long, Long>()
            for (i in 0 until quizObjects.length()) {
                val set = quizObjects.getJSONObject(i)
                val oldSetId = set.getLong("id")
                val oldQuestionIds = jsonArrayToLongs(set.optJSONArray("questionIds"))
                val newQuestionIds = oldQuestionIds.mapNotNull { questionMap[it] }
                val newSetId = dao.insertQuizSet(
                    QuizSetEntity(
                        profileId = newProfileId,
                        title = set.optString("title", "Imported quiz"),
                        level = set.optString("level", "Mixed"),
                        topic = set.optString("topic", "Imported"),
                        focus = set.optString("focus", "Mixed"),
                        questionIdsJson = JSONArray(newQuestionIds).toString(),
                        createdAt = set.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                quizMap[oldSetId] = newSetId
            }

            var importedAttempts = 0
            for (i in 0 until attemptObjects.length()) {
                val a = attemptObjects.getJSONObject(i)
                val newQuestionId = questionMap[a.optLong("questionId")] ?: continue
                val oldQuizId = if (a.has("quizSetId") && !a.isNull("quizSetId")) a.optLong("quizSetId") else null
                dao.insertAttempt(
                    AttemptEntity(
                        questionId = newQuestionId,
                        profileId = newProfileId,
                        quizSetId = oldQuizId?.let { quizMap[it] },
                        answer = a.optString("answer"),
                        score = a.optInt("score", 1).coerceIn(1, 10),
                        correctedTranslation = a.optString("correctedTranslation"),
                        shortFeedback = a.optString("shortFeedback"),
                        mistakesJson = a.optString("mistakesJson", "[]"),
                        improveJson = a.optString("improveJson", "[]"),
                        grammarTopicsJson = a.optString("grammarTopicsJson", "[]"),
                        createdAt = a.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                importedAttempts++
            }

            ImportSummary(
                profileId = newProfileId,
                profileName = restoredName,
                quizCount = quizMap.size,
                questionCount = questionMap.size,
                attemptCount = importedAttempts
            )
        }
    }

    private fun parseIdArray(json: String): List<Long> = runCatching {
        jsonArrayToLongs(JSONArray(json))
    }.getOrDefault(emptyList())

    private fun jsonArrayToLongs(array: JSONArray?): List<Long> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val id = array.optLong(i, -1L)
                if (id > 0L) add(id)
            }
        }
    }

    private fun uniqueProfileName(original: String, existingLower: Set<String>): String {
        if (original.lowercase(Locale.ROOT) !in existingLower) return original
        var suffix = 2
        while (true) {
            val candidate = "$original (restored $suffix)"
            if (candidate.lowercase(Locale.ROOT) !in existingLower) return candidate
            suffix++
        }
    }

    companion object {
        const val FORMAT_NAME = "deutschtrainer-profile-backup"
        const val FORMAT_VERSION = 1
    }
}
