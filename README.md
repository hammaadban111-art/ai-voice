# AI Voice

Offline voice dictation for Android. Tap a floating mic button on top of any app,
speak, and the transcript is typed into whatever text field you were using.

Everything runs on the phone: [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
compiled with the NDK, the GGML weights bundled inside the APK. No cloud API, no
account, no network permission.

## How it works

```
overlay tap ──► AudioRecord (16 kHz mono) ──► whisper.cpp (JNI, arm64)
                                                      │
                              AccessibilityService ◄──┘  ACTION_SET_TEXT
                                                          (clipboard + paste fallback)
```

| Piece | File |
| --- | --- |
| JNI bridge to whisper.cpp | `app/src/main/cpp/whisper_jni.cpp` |
| Native build wiring | `app/src/main/cpp/CMakeLists.txt` |
| Native context lifecycle | `app/src/main/java/com/aivoice/flow/whisper/WhisperEngine.kt` |
| Model unpacking from APK assets | `app/src/main/java/com/aivoice/flow/whisper/ModelStore.kt` |
| Microphone capture | `app/src/main/java/com/aivoice/flow/audio/AudioRecorder.kt` |
| Floating button | `app/src/main/java/com/aivoice/flow/service/OverlayBubble.kt` |
| Dictation loop (foreground service) | `app/src/main/java/com/aivoice/flow/service/DictationService.kt` |
| Text insertion | `app/src/main/java/com/aivoice/flow/service/TextInjector.kt` |
| Setup / permissions screen | `app/src/main/java/com/aivoice/flow/ui/MainActivity.kt` |

## Using it

1. Install the APK and open **AI Voice**.
2. The bundled speech model unpacks itself on first launch (~181 MiB, one time).
3. Grant **Microphone** and **Display over other apps**.
4. Turn on the **AI Voice** accessibility service. Android does not allow an app to
   grant this itself, so the button opens Settings for you. Without it the app still
   works — transcripts land on the clipboard instead of being typed.
5. Tap **Start floating mic**, switch to any app, tap the bubble, speak, tap again.

The bubble is draggable, and a long press cycles the dictation language.

### Language

`small` multilingual weights (`ggml-small-q5_1`, 5-bit quantised) — chosen over
`base` because Hindi and Urdu accuracy falls off sharply on the smaller model.

Auto-detect is the default. Hindi and Urdu share most of their vocabulary and
differ mainly in script, so whisper's detector flips between them on short
utterances; pin the language in the app (or long-press the bubble) when dictating
in either.

## Building

Requires JDK 17, the Android SDK, and NDK `27.2.12479018`.

```bash
git clone --recursive https://github.com/hammaadban111-art/ai-voice
cd ai-voice
./gradlew :app:assembleRelease
```

The `downloadWhisperModel` Gradle task fetches the weights into
`app/src/main/assets/models/` on the first build (they are too large for git) and
the APK is then fully self-contained. CI does the same in
`.github/workflows/build-apk.yml`, which publishes the APK as a release asset.

Build notes:

- arm64 only, `-march=armv8.2-a+fp16+dotprod`. The fp16 and dot-product
  instructions are what make the quantised matmuls fast enough to feel
  interactive; they exist on every Cortex-A75-or-later phone.
- The native library is linked with `-Wl,-z,max-page-size=16384` for the 16 KiB
  page devices Android 15 introduced.
- Model assets are stored uncompressed (`noCompress "bin"`) so the first-run copy
  out of the APK is a byte copy rather than a 190 MB inflate.
- Release builds are signed with the checked-in `keystore/aivoice-sideload.jks`.
  It is intentionally not a secret: a stable signature is what lets one release
  install as an update over the previous one, which a per-build debug key
  cannot do. It proves nothing about authorship — swap in a keystore from CI
  secrets before distributing the app anywhere that matters.
- `versionCode` comes from `GITHUB_RUN_NUMBER`, so each published release
  outranks the last.

## Licence

whisper.cpp is vendored as a submodule under its own MIT licence.
