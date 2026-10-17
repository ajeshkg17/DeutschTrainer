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

## Speaking trainer

**Voice typing** still uses the phone's speech recognition and allows editing before text grading.
For uninterrupted practice, select a Gemini model and configure its key in Settings, then choose
**Record answer** inside a question. Allow microphone permission, speak at your own pace, and tap
**Stop recording**. Pauses do not end the recording. Recordings are limited to five minutes and
stop when the app goes into the background. Use **Listen**, **Record again**, or **Discard** before
choosing **Check recorded answer**.

That button sends the original audio to Gemini, which transcribes the German and evaluates the
translation plus audible pronunciation and fluency. Feedback includes specific speaking practice
advice. The transcript is shown under **What AI heard**. Its translation score uses the same text
grading rubric as typed answers, in a separate request so speaking pace and accent do not affect it.
Speaking feedback is limited to what the audio permits; these are practice estimates, not certified speaking scores.
DeepSeek remains available for text grading. Recorded-answer assessment requires Gemini.
The transcript and feedback are saved in the existing history and portable backups. Audio stays
in temporary app storage until successfully checked or replaced/discarded. Unfinished recordings
are recovered when the same learner reopens the question, including after rotation or restart;
audio is not included in history or backups. If an AI request fails, the recording remains available
on the question screen for playback and retry.

Temporary AI server failures are retried twice. Recorded-answer errors stay beside the recording
controls so you can retry without losing the audio. Longer audio requests have a two-minute
response timeout. API keys are sent in authentication headers.

Next opens directly to learner selection. **More → Backup & restore** opens the portable backup
tools, and tapping a History entry reveals its saved correction, feedback and practice advice.
Draft answers are isolated by learner as well as question.

Run reliability checks with `gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.

## Question layout and hints

The question stays visible above the scrollable answer area. A full-width microphone/Stop
button sits directly below it; Google voice typing has its own button beside the typing tools.
**Show word hints** requests useful German vocabulary (including articles and relevant verb
forms) and a short grammar tip without revealing the complete answer. Hints use the selected
Gemini or DeepSeek text model and are cached privately on the phone for offline reuse.
Hint errors stay inline and can be retried without interrupting a recording.

## Multiple-choice quizzes

In **Create**, choose **Multiple choice** under **Question type**, then set the topic,
grammar focus, level and number of questions. Generation uses the selected Gemini or
DeepSeek model. Each English sentence has four German translations and one correct answer.
Tap an option, then **Check answer** to see the correct translation and a saved explanation.
Checking works offline after generation: a correct choice scores 10/10 and an incorrect one
0/10. Attempts appear in progress/history and incorrect answers can be used in mistake review.
Choices are remembered separately for each learner and question.

Generated sets are validated before saving and saved in one transaction, so malformed or
incomplete output cannot leave a partial quiz. Validation checks answer count, distinct
non-empty options, the answer-key range and the presence of an explanation; the AI supplies
the linguistic answer key. MCQ choices, answer keys and explanations are included in version 2
portable backups. This app also accepts version 1 translation-only backups. Room migration
4 → 5 adds the MCQ fields while preserving existing questions, quizzes and attempts.

## App icon

The project includes a custom DeutschTrainer launcher icon inspired by an open book and the German black/red/gold palette.

## API keys
Open Settings inside the app and save either a Gemini API key or a DeepSeek API key. Keys are stored in Android app-private SharedPreferences and are never written to profile backup files or committed to the repository.

## Data persistence
Room stores profiles, quiz sets, questions and attempts in `deutsch_trainer.db`. Database migrations preserve data during compatible in-place app updates. The explicit JSON backup provides a separate recovery path for clean installs and device changes.
