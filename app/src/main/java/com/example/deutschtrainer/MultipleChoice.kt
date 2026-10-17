package com.example.deutschtrainer

/** A generated answer key is checked once, then used locally without another AI call. */
data class MultipleChoice(
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
) {
    init {
        require(options.size == 4 && options.all { it.isNotBlank() }) { "Each MCQ needs four non-empty answers." }
        require(options.map { it.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) }.distinct().size == 4) {
            "MCQ answers must be different."
        }
        require(correctIndex in options.indices) { "The MCQ answer key is missing or invalid." }
        require(explanation.isNotBlank()) { "Each MCQ needs an explanation." }
    }

    fun evaluate(selectedIndex: Int): Evaluation {
        require(selectedIndex in options.indices) { "Choose one answer first." }
        val correct = selectedIndex == correctIndex
        return Evaluation(
            score = if (correct) 10 else 0,
            correctedTranslation = options[correctIndex],
            shortFeedback = (if (correct) "Correct!" else "Not quite. The correct answer is ${'A' + correctIndex}.") + "\n\n" + explanation,
            mistakes = if (correct) emptyList() else listOf("You chose: ${options[selectedIndex]}"),
            howToImprove = emptyList(),
            grammarTopics = emptyList()
        )
    }
}
