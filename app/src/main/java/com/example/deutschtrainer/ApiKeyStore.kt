package com.example.deutschtrainer

import android.content.Context

class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_keys", Context.MODE_PRIVATE)
    fun saveGeminiKey(value: String) = prefs.edit().putString(KEY_GEMINI_API, value.trim()).apply()
    fun saveDeepSeekKey(value: String) = prefs.edit().putString(KEY_DEEPSEEK_API, value.trim()).apply()
    fun clearGeminiKey() = prefs.edit().remove(KEY_GEMINI_API).apply()
    fun clearDeepSeekKey() = prefs.edit().remove(KEY_DEEPSEEK_API).apply()
    fun geminiKey(): String = prefs.getString(KEY_GEMINI_API, "").orEmpty()
    fun deepSeekKey(): String = prefs.getString(KEY_DEEPSEEK_API, "").orEmpty()
    fun hasGeminiKey() = geminiKey().isNotBlank()
    fun hasDeepSeekKey() = deepSeekKey().isNotBlank()
    fun selectedModel(): AiModel = AiModels.byId(prefs.getString(KEY_SELECTED_MODEL, null))
    fun selectModel(model: AiModel) = prefs.edit().putString(KEY_SELECTED_MODEL, model.id).apply()
    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_DEEPSEEK_API = "deepseek_api_key"
        private const val KEY_SELECTED_MODEL = "selected_ai_model"
    }
}
