# Voice App V4

Dictation that follows you into every app. Tap a mic, talk, and the cleaned-up
text lands at your cursor — in a chat box, a search field, a code editor,
wherever you were typing. Transcription runs on Google Gemini.

Three builds live here:

| | What | Where |
| --- | --- | --- |
| **Android** | The shipped app, ready to install | [`android/VoiceAppV4-4.1.3.apk`](android/VoiceAppV4-4.1.3.apk) |
| **iOS** | A one-to-one replica, source to build in Xcode | [`ios/`](ios/) |
| **Web** | Same app as a web page — the free way onto an iPhone | [`web/`](web/) · [open it](https://hammaadban111-art.github.io/ai-voice/) |

### Which one do I want on an iPhone?

iOS has no free, permanent way to install a native app. Sideloading with a free
Apple ID expires after **7 days** and needs a Mac every time; TestFlight and the
App Store both need the $99/year developer account. So:

| Method | Free | Simple | Expires | Types into other apps |
| --- | --- | --- | --- | --- |
| Sideload (AltStore / Xcode) | ✅ | ❌ a Mac every 7 days | 7 days | ✅ |
| TestFlight | ❌ $99/yr | ✅ one tap | 90 days | ✅ |
| **[Web app](web/)** | ✅ | ✅ tap link → Add to Home Screen | **never** | ❌ copy-paste |

The web build is the one to hand someone who just wants it to work: open the
link in Safari, Share › **Add to Home Screen**, done. It transcribes with the
same model and the same prompts; the only difference is that the text lands in
the app and you tap **Copy**, rather than being typed straight into WhatsApp.

## Android

Download **[VoiceAppV4-4.1.3.apk](android/VoiceAppV4-4.1.3.apk)** (2.9 MB) and
open it on the phone. Install instructions and the permission walkthrough are in
[`android/README.md`](android/README.md).

- Package `com.hammaad.voiceappv4`, version 4.1.3, arm64/arm/x86.
- A floating bubble appears when you focus a text field, an accessibility
  service inserts the transcript at the caret.

## iOS

Android lets an app draw a bubble over other apps and type into them through an
accessibility service. iOS allows neither. The sanctioned way to put text into
someone else's text field is a **custom keyboard extension** — it shows up in
every app, it can see the text around the cursor, and it inserts at the caret.
So the bubble becomes a mic key on the keyboard, and everything behind it is the
same design as the Android build.

```
mic key tap ──► AVAudioEngine (16 kHz mono PCM)
                      │
                      ├─► live socket  wss://…/v1beta/interactions
                      │      gemini-3.5-transcribe-live   (interim words as you speak)
                      │
                      └─► batch POST   …/v1beta/models/gemini-3.5-transcribe:generateContent
                             (fallback when the socket dies)
                                        │
              UITextDocumentProxy ◄──────┘  insertText at the cursor
```

**You do not need a Mac to get a build.** Every push compiles the app on a macOS
runner and publishes an unsigned `.ipa` you can download straight onto the phone:

**[Download VoiceAppV4-unsigned.ipa](https://github.com/hammaadban111-art/ai-voice/releases/download/latest/VoiceAppV4-unsigned.ipa)**

Sign it with SideStore, AltStore, Sideloadly or Feather —
[`docs/SIDELOADING.md`](docs/SIDELOADING.md) compares them and explains why ESign
is no longer one of the options. To build it yourself in Xcode instead, see
[`docs/INSTALL-ios.md`](docs/INSTALL-ios.md).

### Feature parity

| Feature | Android | iOS | Notes |
| --- | --- | --- | --- |
| Gemini live transcription | ✅ | ✅ | Same model id, same socket endpoint |
| Batch fallback model | ✅ | ✅ | Auto-retries a dropped live session |
| Insert at cursor in any app | ✅ accessibility service | ✅ keyboard extension | |
| Mic button over other apps | ✅ floating bubble | ✅ mic key on the keyboard | iOS forbids overlays |
| Tap to record / hold to talk | ✅ | ✅ | |
| Smart vs Raw style | ✅ | ✅ | Identical prompt wording |
| Contextual dictation | ✅ | ✅ | Text around the cursor; iOS cannot see the app's name |
| Custom vocabulary | ✅ | ✅ | |
| Multi-language selection | ✅ | ✅ | Same 21 locales |
| Transcript history | ✅ | ✅ | Shared App Group file |
| Pause / snooze | ✅ | ✅ | |
| Encrypted API key | ✅ AndroidKeyStore | ✅ Keychain, device-bound | |
| Diagnostics screen | ✅ | ✅ | iOS reports Full Access instead of accessibility |
| Skip in chosen apps | ✅ excluded apps | ❌ | A keyboard extension is never told which app is hosting it |
| Start on boot | ✅ | ❌ | iOS has no equivalent; the keyboard is always available instead |

Everything except the last two rows behaves the same on both phones.

### Layout

```
ios/
  Shared/      the whole engine — compiled into both targets
    DictationEngine.swift    tap → mic → Gemini → text, with the fallback path
    AudioRecorder.swift      AVAudioEngine → 16 kHz mono PCM, chunked for live
    GeminiLiveSession.swift  streaming socket, interim + final transcripts
    GeminiClient.swift       batch request, key validation, response cleanup
    PromptBuilder.swift      the instruction both paths send
    SettingsStore.swift      App Group settings, keys mirrored from Android
    ApiKeyStore.swift        Keychain, shared with the extension
    TranscriptStore.swift    history file in the App Group container
    MicKeyView.swift         the mic key itself
  App/         the container app: onboarding, settings, history, diagnostics
  Keyboard/    the keyboard extension
  Tools/       project + icon generators
```

### Setup on the phone

1. Build and install, then open **Voice App V4**.
2. Paste a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).
3. Allow the microphone.
4. Settings › General › Keyboard › Keyboards › **Add New Keyboard** › Voice App V4,
   then tap it again and turn on **Allow Full Access** — without it the keyboard
   cannot use the microphone or reach the network.
5. In any app, tap a text field, switch to the Voice App V4 keyboard with the
   globe key, tap the mic, talk, tap again.

## Privacy

Only the audio you dictate leaves the phone, and only to Google Gemini. The
keyboard reads the text immediately around your cursor to match its tone, and
nothing else about the screen. The API key is stored in the device keychain and
never leaves the device. Password and PIN fields are skipped outright.
