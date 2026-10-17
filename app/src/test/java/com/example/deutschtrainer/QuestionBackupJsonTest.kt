package com.example.deutschtrainer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuestionBackupJsonTest {
    @Test fun mcqRoundTripPreservesChoicesKeyAndExplanationWhileRemappingId() {
        val original = QuestionEntity(id = 42, english = "I am tired.", level = "A2", topic = "Daily life",
            focus = "sein", createdAt = 123L,
            choicesJson = JSONArray(listOf("Ich bin müde.", "Ich ist müde.", "Ich sind müde.", "Ich bist müde.")).toString(),
            correctChoiceIndex = 0, answerExplanation = "Use bin with ich.")
        val restored = QuestionBackupJson.read(JSONObject(QuestionBackupJson.write(original).toString()))
        assertEquals(original.copy(id = 0), restored)
        assertEquals(10, restored.multipleChoice().evaluate(0).score)
        assertEquals(0, restored.multipleChoice().evaluate(1).score)
    }

    @Test fun oldBackupRemainsATranslationQuestion() {
        val original = QuestionEntity(english = "I am tired.", level = "A2", topic = "Daily life", focus = "sein", createdAt = 123L)
        val oldJson = QuestionBackupJson.write(original).apply {
            remove("choicesJson"); remove("correctChoiceIndex"); remove("answerExplanation")
        }
        assertEquals(original, QuestionBackupJson.read(oldJson))
    }

    @Test fun backupWithMissingOrInvalidKeyIsRejected() {
        val json = QuestionBackupJson.write(QuestionEntity(english = "Test", level = "A2", topic = "Test", focus = "Test"))
            .put("choicesJson", JSONArray(listOf("A", "B", "C", "D")).toString())
            .put("answerExplanation", "Explanation")
        assertThrows(IllegalArgumentException::class.java) { QuestionBackupJson.read(json) }
        json.put("correctChoiceIndex", 9)
        assertThrows(IllegalArgumentException::class.java) { QuestionBackupJson.read(json) }
    }
}
