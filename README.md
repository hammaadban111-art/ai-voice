# Dictate

A floating voice-dictation bubble for Android. Tap it over any text field —
WhatsApp, Messages, Gmail, Chrome, Notes, ChatGPT, whatever — speak, and the
transcript is typed in at your cursor. Transcription runs on Google's Gemini
API using **your own API key**; there's no backend, no account, and no
subscription.

## Download

Grab the latest build from **[Releases → Dictate-latest.apk](../../releases/latest)**.
Every push to this repo rebuilds and republishes that release, so it's
always up to date.

On your phone: open the Releases page in a browser, download the APK, and
open it. Android will ask you to allow installs from that source the first
time — allow it, then install.

This is a **debug-signed** build meant for personal sideloading, not a Play
Store release.

## Setup

1. Install the APK and open **Dictate**.
2. Follow the onboarding flow: grant microphone access, turn on the
   Dictate **Accessibility Service**, allow **display over other apps**,
   then enable the bubble and try a test dictation.
3. Open **Settings → Gemini** and paste your own Gemini API key (see below).

### Getting a Gemini API key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey) and create
   an API key (a free-tier key works — the app is usable for free within
   your project's Gemini quota).
2. Copy the key.
3. In Dictate, go to **Settings → Gemini**, paste the key, tap **Save**,
   then **Test connection** to confirm it works.

The key is encrypted at rest with **Android Keystore**-backed
`EncryptedSharedPreferences` (AES-256-GCM) and is never written to disk in
plaintext, logged, or committed anywhere. Nothing in this repository
contains a real API key — you always bring your own.

## Using it

- **Tap** the bubble to start dictating; tap **Done** on the pill that
  appears to transcribe and insert the text, or **Cancel** to discard it.
- **Long-press** the bubble for push-to-talk: release to finish.
- **Drag** the bubble anywhere; it snaps to the nearest screen edge.
- The bubble only appears over editable text fields, and never over
  password/PIN/OTP fields.
- Snooze or fully disable the bubble from **Settings → Bubble**.

## How it works

```
floating bubble (WindowManager overlay) ──► AudioRecord (16 kHz mono PCM)
        │                                          │
        │                                          ▼
        │                         Gemini Live "gemini-3.5-transcribe-live"
        │                         over WebSocket (BidiGenerateContent)
        │                                          │
        │                     (falls back to REST "gemini-3.5-transcribe"
        │                      if the live session drops after audio was
        │                      already captured, so nothing is lost)
        ▼                                          │
AccessibilityService  ◄───────────────────────────┘
  detects the focused field, inserts the transcript at the cursor
  (falls back to clipboard + paste if direct insertion isn't supported)
```

| Piece | File |
| --- | --- |
| Floating bubble + recording pill, state machine | `app/src/main/java/com/dictate/app/overlay/` |
| Gemini WebSocket + REST clients | `app/src/main/java/com/dictate/app/gemini/` |
| Microphone capture | `app/src/main/java/com/dictate/app/audio/AudioCapture.kt` |
| Field detection + text insertion | `app/src/main/java/com/dictate/app/accessibility/` |
| Encrypted API key storage | `app/src/main/java/com/dictate/app/data/security/SecureKeyStore.kt` |
| Settings (DataStore) | `app/src/main/java/com/dictate/app/data/settings/SettingsRepository.kt` |
| Onboarding / Home / Settings UI | `app/src/main/java/com/dictate/app/ui/` |

## Permissions

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | Capture your voice during an active dictation only. |
| `SYSTEM_ALERT_WINDOW` | Draw the floating bubble over other apps. |
| `BIND_ACCESSIBILITY_SERVICE` | Detect the focused text field and insert the transcript into it. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keep a dictation alive reliably while recording. |
| `POST_NOTIFICATIONS` | Show the (minimum-priority) foreground-service notification Android requires. |
| `INTERNET` | Send captured audio to the Gemini API for transcription. |
| `VIBRATE` | Tasteful haptic feedback on tap/long-press/done. |

## Privacy

- Microphone audio is only captured during an active dictation, streamed
  straight to Gemini for transcription, and never written to disk.
- The accessibility service reads only the single focused field needed to
  detect an editable cursor and insert text — it never reads or transmits
  the rest of the screen's contents.
- Password, PIN, OTP, and other sensitive fields are excluded from
  dictation entirely.
- Transcript history is off by default; if you turn it on, it's stored
  locally on-device only and can be deleted at any time from
  **Settings → Privacy**.
- No analytics, no ads, no accounts, no backend server — the only network
  call this app makes is to `generativelanguage.googleapis.com` with your
  own API key.

## Building it yourself

```bash
git clone https://github.com/hammaadban111-art/ai-voice.git
cd ai-voice
./gradlew clean test lint assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Requirements: JDK 17+, Android SDK (compileSdk 34, build-tools 34.0.0).
Minimum supported OS is Android 13 (API 33).

### Tech stack

Kotlin, Jetpack Compose, Material 3, coroutines, DataStore, OkHttp
(WebSocket + REST for Gemini), and `androidx.security:security-crypto` for
Keystore-backed encryption. No native code, no third-party analytics SDKs.

## Notes for OnePlus / OxygenOS

OxygenOS aggressively kills background services on some devices. If the
bubble stops responding after the screen has been off for a while:

1. Settings → Battery → Dictate → set battery optimization to **Don't
   optimize** / **Allow background activity**.
2. Settings → Apps → Dictate → make sure **Autostart** is enabled.
3. Keep the "Dictation bubble is active" notification enabled — dismissing
   it (where OxygenOS allows that) can let the OS reclaim the service.
