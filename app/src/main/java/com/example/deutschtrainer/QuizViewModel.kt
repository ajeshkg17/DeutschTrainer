package com.example.deutschtrainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

data class UiState(val profiles:List<ProfileEntity> = emptyList(),val selectedProfileId:Long?=null,val questions:List<QuestionEntity> = emptyList(),val attempts:List<AttemptWithQuestion> = emptyList(),val selectedQuestionId:Long?=null,val evaluation:Evaluation?=null,val explanation:String?=null,val loading:Boolean=false,val message:String?=null,val selectedModel:AiModel=AiModels.default,val geminiKeyConfigured:Boolean=false,val deepSeekKeyConfigured:Boolean=false)

class QuizViewModel(app:Application):AndroidViewModel(app){
 private val dao=AppDatabase.get(app).quizDao(); private val keys=ApiKeyStore(app); private val ai=GeminiService(keys)
 private val local=MutableStateFlow(UiState(selectedModel=keys.selectedModel(),geminiKeyConfigured=keys.hasGeminiKey(),deepSeekKeyConfigured=keys.hasDeepSeekKey()))
 private val pid=MutableStateFlow<Long?>(null)
 private val profileAttempts=pid.filterNotNull().flatMapLatest{dao.attempts(it)}
 val state:StateFlow<UiState> = combine(local,dao.profiles(),dao.questions(),profileAttempts.onStart{emit(emptyList())}){s,p,q,a->val sp=s.selectedProfileId?:p.firstOrNull()?.id;if(sp!=null&&pid.value!=sp)pid.value=sp;s.copy(profiles=p,selectedProfileId=sp,questions=q,attempts=a,selectedQuestionId=s.selectedQuestionId?:q.firstOrNull()?.id)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),local.value)
 init{viewModelScope.launch{val p=dao.profiles().first();val id=if(p.isEmpty())dao.insertProfile(ProfileEntity(name="Default")) else p.first().id;pid.value=id;local.update{it.copy(selectedProfileId=id)}}}
 fun addProfile(name:String){if(name.isBlank())return;viewModelScope.launch{val id=dao.insertProfile(ProfileEntity(name=name.trim()));pid.value=id;local.update{it.copy(selectedProfileId=id,message="Profile created")}}}
 fun selectProfile(id:Long){pid.value=id;local.update{it.copy(selectedProfileId=id,evaluation=null,explanation=null)}}
 fun selectQuestion(id:Long){local.update{it.copy(selectedQuestionId=id,evaluation=null,explanation=null,message=null)}}
 fun nextQuestion(){val s=state.value;if(s.questions.isEmpty())return;val i=s.questions.indexOfFirst{it.id==s.selectedQuestionId};selectQuestion(s.questions[if(i<0||i>=s.questions.lastIndex)0 else i+1].id)}
 fun previousQuestion(){val s=state.value;if(s.questions.isEmpty())return;val i=s.questions.indexOfFirst{it.id==s.selectedQuestionId};selectQuestion(s.questions[if(i<=0)s.questions.lastIndex else i-1].id)}
 fun selectModel(m:AiModel){keys.selectModel(m);local.update{it.copy(selectedModel=m)}}
 fun saveGeminiKey(k:String){if(k.isNotBlank()){keys.saveGeminiKey(k);local.update{it.copy(geminiKeyConfigured=true,message="Gemini API key saved on this device.")}}}
 fun saveDeepSeekKey(k:String){if(k.isNotBlank()){keys.saveDeepSeekKey(k);local.update{it.copy(deepSeekKeyConfigured=true,message="DeepSeek API key saved on this device.")}}}
 fun clearGeminiKey(){keys.clearGeminiKey();local.update{it.copy(geminiKeyConfigured=false)}}
 fun clearDeepSeekKey(){keys.clearDeepSeekKey();local.update{it.copy(deepSeekKeyConfigured=false)}}
 fun grade(answer:String){val s=state.value;val q=s.questions.firstOrNull{it.id==s.selectedQuestionId}?:return;val p=s.selectedProfileId?:return;if(answer.isBlank())return;viewModelScope.launch{local.update{it.copy(loading=true,message=null,explanation=null)};runCatching{val e=ai.evaluate(q.english,answer.trim(),q.level,s.selectedModel);dao.insertAttempt(AttemptEntity(questionId=q.id,profileId=p,answer=answer.trim(),score=e.score,correctedTranslation=e.correctedTranslation,shortFeedback=e.shortFeedback,mistakesJson=JSONArray(e.mistakes).toString(),improveJson=JSONArray(e.howToImprove).toString(),grammarTopicsJson=JSONArray(e.grammarTopics).toString()));e}.onSuccess{e->local.update{it.copy(loading=false,evaluation=e)}}.onFailure{e->local.update{it.copy(loading=false,message=e.message?:"Grading failed")}}}}
 fun generate(count:Int,level:String,topic:String,focus:String){val m=state.value.selectedModel;viewModelScope.launch{local.update{it.copy(loading=true,message=null)};runCatching{ai.generateQuestions(count,level,topic,focus,m)}.onSuccess{list->list.forEach{dao.insertQuestion(QuestionEntity(english=it.english,level=it.level,topic=it.topic,focus=it.focus))};local.update{it.copy(loading=false,message="${list.size} questions saved")}}.onFailure{e->local.update{it.copy(loading=false,message=e.message?:"Generation failed")}}}}
 fun explainLast(answer:String){val s=state.value;val q=s.questions.firstOrNull{it.id==s.selectedQuestionId}?:return;val e=s.evaluation?:return;viewModelScope.launch{local.update{it.copy(loading=true)};runCatching{ai.explain(q.english,answer,e.score,e.correctedTranslation,e.shortFeedback,s.selectedModel)}.onSuccess{x->local.update{it.copy(loading=false,explanation=x)}}.onFailure{x->local.update{it.copy(loading=false,message=x.message)}}}}
}
