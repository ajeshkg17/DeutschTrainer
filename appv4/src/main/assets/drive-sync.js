const DB_KEY='deutschtrainer_v4';
let driveStatus={connected:false,email:'',name:'',lastSyncAt:0,lastRemoteUpdatedAt:0,autoSync:true};
function readDb(){try{return JSON.parse(localStorage.getItem(DB_KEY)||'null')}catch(e){return null}}
function dbJson(){let db=readDb();if(!db)throw Error('No DeutschTrainer learning data was found on this phone.');return JSON.stringify(db)}
function esc(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}
function fmtTime(ms){if(!ms)return 'Never';let d=new Date(ms);return d.toLocaleString([], {dateStyle:'medium',timeStyle:'short'})}
function countProfiles(){return readDb()?.profiles?.length||0}
function setLoading(v){document.getElementById('loading').classList.toggle('show',!!v)}
function toast(t){let e=document.getElementById('toast');e.textContent=t;e.classList.add('show');setTimeout(()=>e.classList.remove('show'),2500)}
function modal(html){document.getElementById('modalBody').innerHTML=html;document.getElementById('modal').classList.add('show')}
function closeModal(){document.getElementById('modal').classList.remove('show')}
function showError(msg){modal(`<h2>Google Drive problem</h2><div class="muted">${esc(msg)}</div><button class="btn full" style="margin-top:16px" onclick="closeModal()">OK</button>`)}
function applyTheme(){try{let d=readDb();let dark=d?.theme==='dark'||(d?.theme==='system'&&matchMedia('(prefers-color-scheme:dark)').matches);document.body.classList.toggle('dark',!!dark)}catch(e){}}
function render(){let s=driveStatus;let account=s.email||s.name||'Google account';document.getElementById('content').innerHTML=`
<div class="sectionTitle">Google Drive</div>
<div class="card">
 <div class="row"><div class="accountIcon">G</div><div class="grow"><b>${esc(account)}</b><div class="muted">Private DeutschTrainer app data</div></div><span class="status ${s.connected?'':'off'}">${s.connected?'● Connected':'Not connected'}</span></div>
 <div class="kv"><span class="muted">Last successful sync</span><b>${esc(fmtTime(s.lastSyncAt))}</b></div>
 <div class="kv"><span class="muted">Profiles on this phone</span><b>${countProfiles()}</b></div>
 <div class="buttons">${s.connected?`<button class="btn" onclick="syncNow()">Sync now</button><button class="btn out" onclick="disconnectDrive()">Disconnect</button>`:`<button class="btn" style="grid-column:1/-1" onclick="connectDrive()">Connect Google Drive</button>`}</div>
</div>
<div class="sectionTitle">Automatic sync</div>
<div class="card"><div class="row"><div class="grow"><b>Keep learning data synced</b><div class="muted">When connected, DeutschTrainer can sync after learning changes and when the app opens.</div></div><div class="switch ${s.autoSync?'on':''}" onclick="toggleAuto()"><i></i></div></div></div>
<div class="sectionTitle">What is stored</div>
<div class="card"><ul class="privacy"><li>Profiles and quiz libraries</li><li>Questions, drafts and answers</li><li>Scores, corrections and progress</li><li>Learning preferences</li></ul><div class="notice"><b>API keys are never synced.</b><br>Gemini and DeepSeek keys remain in Android private storage on each phone.</div></div>
<div class="sectionTitle">Recovery</div>
<div class="card"><b>New phone or fresh install?</b><div class="muted" style="margin:6px 0 12px">Connect the same Google account and restore the latest DeutschTrainer cloud copy.</div><button class="btn out full" onclick="restoreDrive()" ${s.connected?'':'disabled'}>Restore from Drive</button></div>
<div class="sectionTitle">Sync safety</div>
<div class="card"><b>No silent overwrites</b><div class="muted" style="margin-top:6px">DeutschTrainer compares the last successful sync with both copies. If the phone and Drive changed independently, you choose which copy wins.</div></div>`}
function refreshStatus(){try{driveStatus=JSON.parse(DriveNative.getStatus());render()}catch(e){showError('Could not read Drive sync status: '+e.message)}}
function connectDrive(){setLoading(true);try{DriveNative.connect(dbJson())}catch(e){setLoading(false);showError(e.message)}}
function syncNow(){setLoading(true);try{DriveNative.syncNow(dbJson())}catch(e){setLoading(false);showError(e.message)}}
function restoreDrive(){setLoading(true);DriveNative.restoreFromDrive()}
function disconnectDrive(){DriveNative.disconnect();toast('Disconnected on this phone')}
function toggleAuto(){DriveNative.setAutoSync(!driveStatus.autoSync)}
function keepPhone(){setLoading(true);closeModal();DriveNative.keepLocal(dbJson())}
function useDrive(){setLoading(true);closeModal();DriveNative.useDriveCopy()}
window.DriveCallbacks={
 status(s){driveStatus=s;render()},
 syncComplete(message,s){setLoading(false);driveStatus=s;render();toast(message)},
 restoreData(json,message){setLoading(false);try{let obj=JSON.parse(json);if(!obj||obj.version!==4)throw Error('Cloud data is not a supported V4 database.');localStorage.setItem(DB_KEY,json);toast(message);refreshStatus()}catch(e){showError('Could not apply the Drive copy: '+e.message)}},
 conflict(remoteUpdatedAt,message){setLoading(false);modal(`<h2>Changes found on both copies</h2><div class="muted">${esc(message)}</div><div class="muted tiny" style="margin-top:8px">Drive copy updated: ${esc(fmtTime(remoteUpdatedAt))}</div><button class="choice primary" onclick="keepPhone()">Keep this phone and replace Drive copy</button><button class="choice" onclick="useDrive()">Use Google Drive copy on this phone</button><button class="choice" onclick="closeModal()">Cancel — change nothing</button>`)},
 error(message){setLoading(false);showError(message)}
};
applyTheme();refreshStatus();
