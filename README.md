# Deutsch Trainer — Android project

DeutschTrainer is a local-first German learning app built with Kotlin, Jetpack Compose and Room. It supports multiple learner profiles, multiple saved quiz sets, AI grading/generation, German speech-to-text answers, correction playback, history and progress tracking.

## Build from GitHub on a phone
The included `.github/workflows/build-apk.yml` workflow builds `app-debug.apk` in GitHub Actions. The artifact is named `DeutschTrainer-debug`.

The workflow now keeps a stable app-specific debug signing key in the GitHub Actions cache and uses a monotonically increasing CI version code. This is important because a fresh GitHub runner would otherwise generate a different Android debug certificate for every APK, which prevents Android from installing a new build over the previous one.

The first APK produced after switching to the stable key may require one final uninstall/reinstall because it intentionally establishes a new permanent signing identity. Builds after that should update over the installed app as long as the signing cache is retained.

## Portable profile backup
Each learner can be exported as a versioned JSON backup containing:
- profile name and creation time
- all quizzes owned by that profile
- questions and quiz metadata
- graded answers, scores and timestamps
- corrections, feedback and grammar tags

API keys are deliberately excluded.

A backup can be restored after an uninstall or on another installation. The restore creates a new profile and remaps database IDs safely instead of overwriting an existing learner.

On Android, long-press the DeutschTrainer launcher icon and choose **Backup** to open the Backup & Restore screen. Android's file picker lets you save the JSON file to Files, Google Drive or another document provider.

Android automatic app-data backup is disabled in this version so an uninstall/reinstall cannot silently restore an older Room database. Portable user-controlled backups are the supported reinstall path.

## AI errors
Grading, quiz-generation and explanation failures now open a readable popup with common guidance for API-key errors, quota/rate limits, network problems, timeouts and temporary server failures. Existing learning data is not changed when an AI request fails.

## App icon
The project includes a custom DeutschTrainer launcher icon inspired by an open book and the German black/red/gold palette.

## API keys
Open Settings inside the app and save either a Gemini API key or a DeepSeek API key. Keys are stored in Android app-private SharedPreferences and are never written to profile backup files or committed to the repository.

## Data persistence
Room stores profiles, quiz sets, questions and attempts in `deutsch_trainer.db`. Database migrations preserve data during compatible in-place app updates. The explicit JSON backup provides a separate recovery path for clean installs and device changes.
