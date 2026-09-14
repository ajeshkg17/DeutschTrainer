# DeutschTrainer V4 — Google Drive sync design

HTML-first prototype: `prototypes/drive-sync-v4.html`.

## Storage model

- The app keeps its normal fast local database in WebView `localStorage` (`deutschtrainer_v4`).
- Google Drive sync uses the private `appDataFolder` scope, not the user's visible Drive files.
- One versioned JSON snapshot is stored as `deutschtrainer-v4-cloud.json`.
- Gemini and DeepSeek API keys are never included in cloud data.

## Sync safety

The cloud wrapper stores a payload hash and timestamp. The phone stores the hash from the last successful sync.

- only phone changed → upload
- only Drive changed → restore automatically
- no change → do nothing
- both changed → show a conflict screen; never overwrite silently

Manual JSON export/import remains available separately.

## Android authorization

Use Google Identity Services `AuthorizationClient` with the narrow non-sensitive scope `https://www.googleapis.com/auth/drive.appdata`, plus OpenID/email/profile only to show which Google account is connected.

The Android OAuth client must match package `com.ajesh.deutschtrainer.v4` and the signing certificate SHA-1.
