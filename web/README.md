# Voice App V4 — web build

The version that installs on an iPhone with no Mac, no developer account and no
7-day expiry. It is a web app: open the link, add it to the Home Screen, and it
behaves like an app from then on.

**Live at:** https://hammaadban111-art.github.io/ai-voice/

## Installing on an iPhone

1. Open the link **in Safari** (not Chrome — only Safari can add to the Home
   Screen on iOS).
2. Tap the **Share** button (the square with the arrow).
3. Scroll down, tap **Add to Home Screen**, then **Add**.
4. The mic icon is now on her home screen. It opens full-screen with no browser
   bars, and it never expires.

First launch asks for the Gemini API key once, then the microphone.

## Using it

Tap the mic → talk → tap it again → the cleaned-up text appears → tap **Copy** →
paste into WhatsApp, Notes, Messages, anywhere.

## What it can and cannot do

Same transcription as the phone apps: same Gemini model, same Smart/Raw styles,
same 21 languages, same custom vocabulary, same history.

The one thing it cannot do is type directly into another app. Safari does not
let a web page reach another app's text field — no browser on any phone does.
So the last step is Copy and paste, which is exactly what the Android build
falls back to when its accessibility service is off.

If you want dictation *inside* the keyboard on an iPhone, that needs the native
build in [`../ios`](../ios) and a paid Apple Developer account
([`../docs/INSTALL-ios.md`](../docs/INSTALL-ios.md)). There is no free path to it.

## Notes

- The API key is kept in this browser's local storage on that phone, and is sent
  only to Google's Gemini endpoint. Anyone who unlocks the phone can read it
  from Settings, so use a key you are willing to rotate.
- Audio is captured at 16 kHz mono and wrapped as WAV in the browser, so what
  goes up the wire is byte-for-byte the same shape the iOS and Android builds
  send.
- The service worker caches only the app shell. Transcription always needs a
  connection.
- Works in any modern browser; on iOS it needs Safari 15.4 or newer for the
  microphone to work from the Home Screen icon.
