let activeSpeechRequest=null;

function renderQuestion(){
  let q=question(),list=questions(),qu=quiz();
  if(!q){
    document.getElementById('question').innerHTML=`${header('Practice','No questions','quizzes')}<div class="card empty" style="margin-top:20px"><div class="emptyIcon">?</div><b>This quiz is empty.</b></div>`;
    return;
  }
  let key=profile().id+'_'+q.id,ans=db.drafts[key]||'';
  document.getElementById('question').innerHTML=`<div class="row"><button class="iconbtn" onclick="go('quizzes')">←</button><div class="grow" style="text-align:center"><div class="tiny muted"><b>QUESTION ${db.ui.questionIndex+1} OF ${list.length}</b></div><div><b>${esc(qu?.title||q.topic)}</b></div></div><button class="iconbtn" onclick="go('home')">⌂</button></div><div style="margin-top:13px">${progress((db.ui.questionIndex+1)*100/list.length)}</div><div class="card" style="margin-top:15px">${badge('English → German')}<div class="question">${esc(q.english)}</div><p class="muted small" style="margin-top:8px">Translate naturally. More than one correct answer can be valid.</p></div><div class="field"><label>Your translation</label><textarea id="answer" oninput="saveDraft()" placeholder="Write or speak your German sentence…">${esc(ans)}</textarea></div><div class="tools">${['ä','ö','ü','ß'].map(c=>`<button class="mini" onclick="addChar('${c}')">${c}</button>`).join('')}<button id="speechBtn" class="mini mic" onclick="startSpeech()">🎙 Speak German</button></div><div id="voicePanel" class="voicebar"><div class="row"><span class="voiceDot"></span><div class="grow"><div id="voiceTitle" class="voiceTitle">Listening in German</div><div id="voiceHelp" class="muted small">The question stays visible and your typed text is kept.</div></div><button id="voiceStop" class="mini" onclick="stopSpeech()">Stop</button></div><div id="voicePreview" class="voicePreview"></div></div><div class="notice">Speech is added to the end of what you already typed. Nothing is erased. You can edit the full answer before grading.</div><button class="btn soft full" style="margin-top:10px" onclick="toggleHint()">💡 Show grammar hint</button><div id="hint" style="display:none" class="notice">Grammar focus: ${esc(q.focus)}. Think about the rule before translating; the hint does not reveal the German answer.</div><div class="row" style="margin-top:15px"><button class="btn out grow" onclick="prevQuestion()">← Back</button><button class="btn grow" style="flex:1.6" onclick="gradeAnswer()">Check answer ✓</button></div><button class="btn soft full" style="margin-top:9px" onclick="nextQuestion()">Skip to next →</button><div class="tiny muted" style="text-align:center;margin-top:10px">Draft answers and your last question are remembered automatically.</div>`;
}

function startSpeech(){
  const answer=document.getElementById('answer');
  if(answer){ saveDraft(); answer.blur(); }
  activeSpeechRequest='answer-'+Date.now();
  setVoiceState('starting','');
  try{
    DeutschNative.startSpeech(activeSpeechRequest);
  }catch(e){
    setVoiceState('error','Speech recognition is unavailable in this build.');
  }
}

function stopSpeech(){
  try{DeutschNative.stopSpeech()}catch(e){}
  setVoiceState('processing','Finishing…');
}

function setVoiceState(state,payload){
  const panel=document.getElementById('voicePanel'),title=document.getElementById('voiceTitle'),help=document.getElementById('voiceHelp'),preview=document.getElementById('voicePreview'),btn=document.getElementById('speechBtn'),stop=document.getElementById('voiceStop');
  if(!panel)return;
  panel.className='voicebar show '+state;
  if(btn)btn.classList.toggle('listening',['starting','listening','processing'].includes(state));
  if(preview){
    preview.textContent=payload||'';
    preview.classList.toggle('show',!!payload && ['partial','done','error'].includes(state));
  }
  if(stop)stop.style.display=['starting','listening','partial'].includes(state)?'inline-block':'none';
  if(state==='starting'){title.textContent='Starting microphone…';help.textContent='Your typed answer is already saved.'}
  else if(state==='listening'||state==='partial'){title.textContent='Listening in German';help.textContent='Keep speaking. The question stays visible.'}
  else if(state==='processing'){title.textContent='Processing speech…';help.textContent='Your existing answer will not be changed until the final transcript is ready.'}
  else if(state==='done'){title.textContent='Speech added to your answer';help.textContent='You can continue typing or speak again.';setTimeout(()=>{if(panel)panel.classList.remove('show')},2200)}
  else if(state==='error'){title.textContent='Voice input problem';help.textContent='Your typed answer is still safe.'}
}

function appendSpeechToAnswer(spoken){
  const e=document.getElementById('answer');
  const clean=String(spoken||'').trim();
  if(!e||!clean)return;
  const existing=e.value||'';
  const sep=existing && !/\s$/.test(existing)?' ':'';
  e.value=existing+sep+clean;
  saveDraft();
}

if(window.NativeCallbacks){
  window.NativeCallbacks.speechState=function(id,state,payload){
    if(activeSpeechRequest && id!==activeSpeechRequest)return;
    setVoiceState(state,payload||'');
  };
  window.NativeCallbacks.speechResult=function(id,ok,payload){
    if(activeSpeechRequest && id!==activeSpeechRequest)return;
    if(!ok){setVoiceState('error',payload||'Speech recognition failed.');return;}
    appendSpeechToAnswer(payload);
    setVoiceState('done',payload||'');
    activeSpeechRequest=null;
  };
}
