window.NativeCallbacks={
 aiResult(id,ok,payload){setLoading(false);let cb=pendingAi[id];delete pendingAi[id];if(cb)cb(ok,payload);else if(!ok)showError(payload)},
 speechResult(id,ok,payload){if(!ok)return showError(payload);let e=document.getElementById('answer');if(e){e.value=payload;saveDraft()}},
 backupSaved(ok,msg){ok?toast(msg):showError(msg)},
 importBackup(text){importBackup(text)},
 importFailed(msg){showError(msg)}
};
applyTheme();renderProfiles();document.getElementById('bottom').style.display='none';
