# Voxta 2.0

Voxta 2.0 is a native Android 13+ contextual voice-dictation app (`com.hammaad.voiceappv4`). It uses Jetpack Compose for onboarding, Home, Settings, and the optional test; an `AccessibilityService` for focused-field detection and cursor-safe insertion; `AudioRecord` for temporary 16 kHz mono PCM; and the current Gemini transcription APIs.

## What is implemented

- Ordered setup: microphone/listening notification, encrypted Gemini API key, overlay and Accessibility access, contextual bubble enablement, then an optional real test.
- Persisted onboarding and settings. A completed setup restarts on Home; Home rechecks the real permission state.
- The Home dashboard reports `TOTAL WORDS` and `DICTATIONS TODAY` from the local transcript ledger.
- Bubble only when the feature is enabled, not snoozed, overlay access is granted, an allowed editable field is focused, and an input-method window is active.
- Password input variations, Android system surfaces, Voxta itself, and user-excluded packages are filtered.
- Draggable bubble with edge snap; tap recording; visible live microphone activity; Cancel and Done; long-press/release push-to-talk.
- Ephemeral `AudioRecord` capture: PCM stays in memory, is never written to disk, and is cleared after use. A 10-minute safety limit prevents unbounded memory growth.
- Gemini Live WebSocket model `gemini-3.5-transcribe-live`, manual activity start/end, raw 16-bit PCM at 16 kHz, interim/final input transcription, Smart/Verbatim, automatic/manual language, and custom vocabulary.
- If Live fails after audio exists, a WAV is uploaded through Gemini's resumable Files API and transcribed by `gemini-3.5-transcribe` through the Interactions API. The temporary remote file is deleted in `finally`; local buffers are cleared.
- Selection-aware text replacement preserves existing text and restores the cursor after insertion. If `ACTION_SET_TEXT` fails, the transcript remains visible with a Paste action (and remains on the clipboard if direct paste is also rejected).
- Optional on-device transcript-only history and local diagnostics. Neither stores audio, API keys, accessibility screen contents, analytics, identifiers, or Firebase data.
- Explicit states for missing/revoked microphone access, invalid key/API rejection, quota/server failures, offline/connection failures, empty audio, and insertion failure.

## Build

Requirements: JDK 17 and an Android SDK containing platform/build-tools 36. This checkout's `local.properties` points to the Homebrew SDK at `/opt/homebrew/share/android-commandlinetools`; change that path if building elsewhere.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Voxta 2.0 debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses Android's debug signing key. For personal long-term use, create and protect a release keystore and configure release signing before building `assembleRelease`.

## Install and setup on OnePlus / OxygenOS

1. Copy the APK to the phone, open it, and permit installation from that file manager/browser when Android asks.
2. Open Voxta. Allow microphone access and the active-dictation notification.
3. Enter your own Gemini API key. The app has no bundled key. The value is encrypted using an AES-GCM key held by Android Keystore and is excluded from backup.
4. Open **Appear over other apps** and allow Voxta.
5. Open **Accessibility**, select Voxta, review the narrow purpose statement, and enable it.
6. Enable the contextual bubble and finish setup. The optional dictation test can be skipped.

If a sideloaded build shows **Restricted setting**, go to **Settings → Apps → App management → Voxta**, open the three-dot menu, choose **Allow restricted settings**, then return to Accessibility and enable the service. OxygenOS labels vary by release. If the service is later stopped, set Voxta's battery use to allow background activity / do not optimize, and confirm overlay and Accessibility access again. Do not disable system security globally.

## Permission and failure recovery

- Bubble never appears: confirm Home says ready, the bubble is enabled/not snoozed, overlay and Accessibility access remain on, the app package is not excluded, and the keyboard is visible in a non-password editable field.
- Microphone revoked/in use: restore microphone permission, close any app currently holding the microphone, then retry.
- Invalid key or quota: replace the key under Settings and use **Test connection**. Google project quota/billing is controlled outside this app.
- Offline/lost connection: captured audio is retained only long enough to attempt the batch fallback. If both routes fail, it is discarded and never queued to disk.
- Insertion failure: keep the original field focused and tap **Paste**. If the target app blocks accessibility paste, the transcript is still on Android's clipboard for manual paste.
- Accessibility stopped after an OxygenOS update/reboot: reopen the system Accessibility page and re-enable Voxta; then recheck battery/background restrictions.

## Manual device acceptance checklist

- [ ] Fresh install opens onboarding and enforces microphone → key → bubble/accessibility order.
- [ ] Finish without running the optional test; force-stop/reopen and confirm Home opens directly.
- [ ] Home shows `TOTAL WORDS` and `DICTATIONS TODAY`; verify both update after a successful dictation and persist after reopening.
- [ ] Home screen/launcher shows no bubble.
- [ ] WhatsApp (or another target app) shows no bubble before focusing a text field.
- [ ] Focusing a normal message field and opening the keyboard shows the bubble.
- [ ] Dragging snaps the bubble to an edge and the location survives hide/show.
- [ ] Tap starts actual recording immediately; notification and animated microphone level are visible.
- [ ] Cancel stops capture and inserts nothing.
- [ ] Done produces Gemini text and inserts it at the current cursor/selection without deleting surrounding text.
- [ ] Smart mode resolves fillers/corrections; Verbatim preserves spoken wording.
- [ ] Long-press starts push-to-talk and release finishes it.
- [ ] Closing the keyboard, leaving the field, going Home, or opening a password/PIN field hides/prevents the bubble.
- [ ] Excluded package, snooze/resume, disable/re-enable, history, and diagnostics behave as configured.
- [ ] Revoke each permission and verify Home/error recovery does not crash.
- [ ] Disconnect network and exhaust/restrict quota to verify clear fallback/error messaging.
- [ ] Force an insertion-hostile field and verify transcript + Paste fallback.

## Verification boundary

Local verification proves compilation, unit-tested protocol/field/insertion/WAV logic, lint completion, APK packaging, and emulator installation/launch when recorded in the handoff. It does **not** prove real OnePlus/OxygenOS background behavior, WhatsApp compatibility, hardware microphone levels, a user's Gemini account/key/quota, live network transcription, or third-party cursor insertion. Those require the manual checklist on the intended phone and are intentionally not claimed from a desktop build.

Current Gemini reference used by the implementation: [Live transcription](https://ai.google.dev/gemini-api/docs/live-api/live-transcribe), [audio transcription](https://ai.google.dev/gemini-api/docs/transcribe), and [Live WebSocket API](https://ai.google.dev/api/live).
