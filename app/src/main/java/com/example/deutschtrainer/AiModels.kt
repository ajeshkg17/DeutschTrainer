package com.example.deutschtrainer

enum class AiProvider { GEMINI, DEEPSEEK }

data class AiModel(val id: String, val label: String, val provider: AiProvider)

object AiModels {
    val all = listOf(
        AiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite · fast", AiProvider.GEMINI),
        AiModel("gemini-3.8-flash", "Gemini 3.8 Flash · higher quality", AiProvider.GEMINI),
        AiModel("deepseek-v4-flash", "DeepSeek V4 Flash · fast", AiProvider.DEEPSEEK),
        AiModel("deepseek-v4-pro", "DeepSeek V4 Pro · higher quality", AiProvider.DEEPSEEK)
    )

    val default = all[1]
    fun byId(id: String?): AiModel = all.firstOrNull { it.id == id } ?: default
}
