# Voice App V4 — Android

The shipped build, kept here so it is one click away.

**[VoiceAppV4-4.1.3.apk](VoiceAppV4-4.1.3.apk)** — 2.9 MB, package
`com.hammaad.voiceappv4`, min Android 11.

## Installing

1. Open the link above on the phone and download it (on a computer, click the
   file, then **Download**).
2. Open the downloaded file. Android will ask to allow installs from your
   browser or file manager the first time — allow it, then tap **Install**.
3. Open **Voice App V4** and work through the three setup steps.

Sideloaded on OxygenOS or another Android 13+ ROM, the accessibility toggle is
locked until you unlock restricted settings:

> Settings › Apps › Voice App V4 › ⋮ (top corner) › **Allow restricted settings**.
> On OxygenOS the same option can sit under App info › Advanced.

Then Settings › Accessibility › Installed apps (or Downloaded apps) ›
**Voice App V4 Dictation** › on.

## What it needs

| Permission | Why |
| --- | --- |
| Microphone | Records only while you are dictating |
| Display over other apps | Draws the dictation bubble on top of the app you are typing in |
| Accessibility | Finds the focused text field and inserts your words there |
| Notifications | The ongoing notification that keeps the service alive |

A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)
goes in on first launch. It is encrypted with a device-bound AndroidKeyStore key
and never leaves the phone.

## Using it

Tap any text field and the bubble slides in at the edge. Tap it to record, or
hold it to talk and release when done. Tap **Done** and the cleaned-up text
lands at your cursor. Drag the bubble to move it; long-press for the menu.

## Updating

Installing a newer APK over this one keeps your settings and history, as long as
both are signed with the same key.

The iOS replica of this app is in [`../ios`](../ios).
