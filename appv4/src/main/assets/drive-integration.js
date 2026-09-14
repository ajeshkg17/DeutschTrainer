(function(){
  const originalRenderSettings = window.renderSettings;
  window.renderSettings = function(){
    originalRenderSettings();
    const host = document.getElementById('settings');
    if(!host || document.getElementById('driveSyncCard')) return;
    host.insertAdjacentHTML('beforeend', `
      <div class="section-title">Cloud sync</div>
      <div class="card" id="driveSyncCard">
        <div class="row">
          <div class="actionIcon">☁</div>
          <div class="grow">
            <b>Google Drive sync</b>
            <div class="muted small">Keep profiles, quizzes, answers, scores and progress in your private Drive app-data folder.</div>
          </div>
        </div>
        <button class="btn full" style="margin-top:13px" onclick="DeutschNative.openDriveSync()">Open Google Drive sync</button>
        <div class="tiny muted" style="margin-top:9px">API keys are never uploaded.</div>
      </div>`);
  };
})();
