package com.example.deutschtrainer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Evaluation(val score:Int,val correctedTranslation:String,val shortFeedback:String,val mistakes:List<String>,val howToImprove:List<String>,val grammarTopics:List<String>)
data class GeneratedQuestion(val english:String,val level:String,val topic:String,val focus:String)

class GeminiService(private val keyStore: ApiKeyStore) {
    suspend fun evaluate(english:String,answer:String,level:String,model:AiModel):Evaluation {
        val prompt="""You are a strict but constructive German-language examiner.
English sentence: $english
Learner's German translation: $answer
Target CEFR level: $level
Give an integer score from 1 to 10. Do not punish a valid alternative translation.
Return ONLY valid JSON: {"score":8,"corrected_translation":"...","short_feedback":"...","mistakes":["..."],"how_to_improve":["..."],"grammar_topics":["..."]}"""
        val o=JSONObject(cleanJson(generate(prompt,model)))
        return Evaluation(o.optInt("score",1).coerceIn(1,10),o.optString("corrected_translation"),o.optString("short_feedback"),o.optJSONArray("mistakes")?.toStringList().orEmpty(),o.optJSONArray("how_to_improve")?.toStringList().orEmpty(),o.optJSONArray("grammar_topics")?.toStringList().orEmpty())
    }
    suspend fun generateQuestions(count:Int,level:String,topic:String,focus:String,model:AiModel):List<GeneratedQuestion>{
        val prompt="""Create $count English-to-German translation practice questions. CEFR level: $level. Topic: $topic. Grammar focus: $focus. Vary sentence structure; no German answers or hints; no duplicates. Return ONLY valid JSON: {"questions":[{"english":"...","level":"$level","topic":"$topic","focus":"$focus"}]}"""
        val a=JSONObject(cleanJson(generate(prompt,model))).getJSONArray("questions")
        return (0 until a.length()).map { i -> val o=a.getJSONObject(i); GeneratedQuestion(o.getString("english"),o.optString("level",level),o.optString("topic",topic),o.optString("focus",focus)) }
    }
    suspend fun explain(english:String,answer:String,score:Int,correction:String,feedback:String,model:AiModel):String = generate("""You are a German tutor. English: $english Learner answer: $answer Score: $score/10 Suggested correction: $correction Existing feedback: $feedback Explain what was correct, each important mistake and why, relevant grammar, a better formulation, and two short German examples. Use English for explanations and German for examples.""",model)
    private suspend fun generate(prompt:String,model:AiModel):String=withContext(Dispatchers.IO){ when(model.provider){AiProvider.GEMINI->geminiRequest(prompt,model);AiProvider.DEEPSEEK->deepSeekRequest(prompt,model)} }
    private fun geminiRequest(prompt:String,model:AiModel):String{
        val key=keyStore.geminiKey(); check(key.isNotBlank()){ "Add your Gemini API key in Settings before continuing." }
        val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
        return httpPost(URL("https://generativelanguage.googleapis.com/v1beta/models/${model.id}:generateContent?key=$key"),body,mapOf("Content-Type" to "application/json")){r->r.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.ifBlank{null}?:error("Gemini returned no text")}
    }
    private fun deepSeekRequest(prompt:String,model:AiModel):String{
        val key=keyStore.deepSeekKey(); check(key.isNotBlank()){ "Add your DeepSeek API key in Settings before continuing." }
        val body=JSONObject().put("model",model.id).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))).put("temperature",0.3)
        return httpPost(URL("https://api.deepseek.com/chat/completions"),body,mapOf("Content-Type" to "application/json","Authorization" to "Bearer $key")){r->r.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.ifBlank{null}?:error("DeepSeek returned no text")}
    }
    private fun httpPost(url:URL,json:JSONObject,headers:Map<String,String>,parse:(JSONObject)->String):String{
        val c=(url.openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=30000;readTimeout=60000;doOutput=true;headers.forEach{(k,v)->setRequestProperty(k,v)}}
        try{c.outputStream.bufferedWriter().use{it.write(json.toString())};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val text=stream?.bufferedReader()?.use{it.readText()}.orEmpty();if(code !in 200..299) error("AI request failed ($code): ${text.take(500)}");return parse(JSONObject(text))}finally{c.disconnect()}
    }
    private fun cleanJson(text:String):String{val t=text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim();val s=t.indexOfFirst{it=='{'||it=='['};val e=maxOf(t.lastIndexOf('}'),t.lastIndexOf(']'));return if(s>=0&&e>=s)t.substring(s,e+1) else t}
    private fun JSONArray.toStringList():List<String>=(0 until length()).mapNotNull{optString(it).takeIf(String::isNotBlank)}
}
