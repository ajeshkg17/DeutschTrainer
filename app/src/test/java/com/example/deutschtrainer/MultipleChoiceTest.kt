package com.example.deutschtrainer

import org.junit.Assert.*
import org.junit.Test

class MultipleChoiceTest {
    private val options = listOf("Ich bin müde.", "Ich ist müde.", "Ich sind müde.", "Ich bist müde.")
    private val question = MultipleChoice(options, 0, "Use bin with ich.")

    @Test fun correctChoiceScoresTenAndKeepsExplanation() {
        val result = question.evaluate(0)
        assertEquals(10, result.score)
        assertEquals(options[0], result.correctedTranslation)
        assertTrue(result.shortFeedback.contains("Use bin with ich."))
        assertTrue(result.mistakes.isEmpty())
    }

    @Test fun wrongChoiceScoresZeroAndShowsSelectedAnswer() {
        val result = question.evaluate(2)
        assertEquals(0, result.score)
        assertEquals(options[0], result.correctedTranslation)
        assertTrue(result.mistakes.single().contains(options[2]))
        assertTrue(result.shortFeedback.contains("correct answer is A"))
    }

    @Test fun correctAnswerCanBeInAnyPosition() {
        for (index in 0..3) {
            val mcq = MultipleChoice(options, index, "Explanation")
            assertEquals(10, mcq.evaluate(index).score)
            assertEquals(options[index], mcq.evaluate((index + 1) % 4).correctedTranslation)
        }
    }

    @Test fun missingSelectionAndOutOfRangeKeyAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { question.evaluate(-1) }
        assertThrows(IllegalArgumentException::class.java) { question.evaluate(4) }
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options, -1, "Explanation") }
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options, 4, "Explanation") }
    }

    @Test fun incompleteOrBlankAnswersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options.take(3), 0, "Explanation") }
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options + "Extra", 0, "Explanation") }
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options.dropLast(1) + " ", 0, "Explanation") }
        assertThrows(IllegalArgumentException::class.java) { MultipleChoice(options, 0, " ") }
    }

    @Test fun duplicateAnswersAreRejectedEvenWithDifferentSpacingOrCase() {
        assertThrows(IllegalArgumentException::class.java) {
            MultipleChoice(listOf(options[0], " ICH  BIN MÜDE. ", options[2], options[3]), 0, "Explanation")
        }
    }
}
