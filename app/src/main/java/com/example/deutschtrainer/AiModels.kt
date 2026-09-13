package com.example.deutschtrainer

enum class AiProvider { GEMINI, DEEPSEEK }

data class AiModel(val id: String, val label: String, val provider: AiProvider)

object AiModels {
    val all = listOf(
        AiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite (fast)", AiProvider.GEMINI),
        AiModel("gemini-3.7-flash", "Gemini 3.7 Flash (higher quality)", AiProvider.GEMINI),
        AiModel("deepseek-flash", "DeepSeek Flash (fast)", AiProvider.DEEPSEEK),
        AiModel("deepseek-v4-pro", "DeepSeek V4 Pro (higher quality)", AiProvider.DEEPSEEK)
    )
    val default = all.first()
    fun byId(id: String?): AiModel = all.firstOrNull { it.id == id } ?: default
}
