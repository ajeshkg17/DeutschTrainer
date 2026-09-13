# Deutsch Trainer — reconstructed Android project

This project was reconstructed from the last available `DeutschTrainer-redesign.apk` and the earlier editable Android source.

Recovered from the APK:
- package `com.example.deutschtrainer`
- Jetpack Compose UI and Room database
- Practice / Generate / Progress / History / Settings screens
- model selection
- Gemini and DeepSeek API-key settings
- Gemini and DeepSeek REST endpoints
- 1–10 grading, corrections, mistakes, improvement advice and detailed explanations
- locally persisted questions and attempt history

Added while reconstructing the missing later work:
- multiple local user profiles on one phone
- separate history/progress per profile
- Back / Next question navigation
- update-safe Room migration from the original database version
- polished Material 3 card/navigation layout
- GitHub Actions workflow that builds `app-debug.apk` in the cloud

## Build from GitHub on a phone
Push this project to a GitHub repository. The included `.github/workflows/build-apk.yml` workflow automatically builds the debug APK on pushes to `main`, or you can run it manually from GitHub Actions > Build Android APK > Run workflow.

The resulting artifact is named `DeutschTrainer-debug` and contains `app-debug.apk`.

## API keys
Open Settings inside the app and save either a Gemini API key or a DeepSeek API key. Keys are stored in Android app-private SharedPreferences and are not committed to the source repository.

## Data persistence
Room stores profiles, questions and attempts in `deutsch_trainer.db`. Normal app updates preserve this database. Version 2 includes a migration from the previous single-profile schema so old local history is assigned to the default profile instead of being destroyed.
