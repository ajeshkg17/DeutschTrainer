package com.example.deutschtrainer

import org.json.JSONObject

/** Shared by portable backup and restore, including older translation-only backups. */
object QuestionBackupJson {
    fun write(q: QuestionEntity): JSONObject = JSONObject()
        .put("id", q.id)
        .put("english", q.english)
        .put("level", q.level)
        .put("topic", q.topic)
        .put("focus", q.focus)
        .put("createdAt", q.createdAt)
        .put("choicesJson", q.choicesJson)
        .put("correctChoiceIndex", q.correctChoiceIndex)
        .put("answerExplanation", q.answerExplanation)

    fun read(q: JSONObject): QuestionEntity {
        val question = QuestionEntity(
            english = q.optString("english"),
            level = q.optString("level", "B1"),
            topic = q.optString("topic", "Imported"),
            focus = q.optString("focus", "Mixed"),
            createdAt = q.optLong("createdAt", System.currentTimeMillis()),
            choicesJson = q.optString("choicesJson", "[]"),
            correctChoiceIndex = q.optInt("correctChoiceIndex", -1),
            answerExplanation = q.optString("answerExplanation", "")
        )
        if (question.correctChoiceIndex >= 0) question.multipleChoice()
        else require(question.correctChoiceIndex == -1 && org.json.JSONArray(question.choicesJson).length() == 0) {
            "The backup contains an MCQ with a missing answer key."
        }
        return question
    }
}
