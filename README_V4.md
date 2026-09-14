# DeutschTrainer V4 — HTML-first clean rebuild

This is a separate Android module (`appv4`) created from the V4 HTML app. The Android APK uses the same `index.html` as its actual user interface inside a WebView; it does not compile or launch the previous Compose UI.

Key identifiers:
- App name: DeutschTrainer V4
- Application ID: `com.ajesh.deutschtrainer.v4`
- UI source: `appv4/src/main/assets/index.html`
- Build command: `gradle :appv4:assembleDebug`
- GitHub artifact: `DeutschTrainer-V4`

Learning data is stored in the HTML app's persistent local storage and can be exported as a portable profile JSON backup. Gemini/DeepSeek API keys are stored separately in Android-private SharedPreferences and are never included in profile backups.
