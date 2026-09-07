# Getting Voice App V4 onto an iPhone

There is no App Store build, so the app has to be signed and installed from a
Mac. Three routes, cheapest first.

## What you need either way

- A Mac with **Xcode 16** or newer.
- An **Apple ID** (free is fine for route 1).
- An iPhone on **iOS 17** or newer, connected by cable the first time.

Open the project:

```bash
git clone https://github.com/hammaadban111-art/ai-voice
open ai-voice/ios/VoiceAppV4.xcodeproj
```

If Xcode complains the project is out of date, or you add files, regenerate it:

```bash
python3 ios/Tools/generate_xcodeproj.py      # no extra tools needed
# or, if you prefer XcodeGen:
brew install xcodegen && cd ios && xcodegen generate
```

## Before the first build: signing

Both targets need a team and unique bundle ids. In Xcode:

1. Select the project, then the **VoiceAppV4** target › **Signing & Capabilities**.
2. Tick **Automatically manage signing** and pick your team.
3. If `com.hammaad.voiceappv4` is taken (it will be, if you are not me), change
   the bundle id to something of your own — for example
   `com.yourname.voiceappv4`. Do the same for the **VoiceKeyboard** target,
   keeping the `.keyboard` suffix: `com.yourname.voiceappv4.keyboard`.
4. Both targets already declare an **App Group** (`group.com.hammaad.voiceappv4`)
   and a matching **Keychain Sharing** group. If you changed the bundle ids,
   change the group in these three places to match:
   - `ios/App/VoiceAppV4.entitlements`
   - `ios/Keyboard/VoiceKeyboard.entitlements`
   - `SettingsStore.appGroup` and `ApiKeyStore.accessGroup` in `ios/Shared/`

   The app and the keyboard must agree, or the keyboard will not see your API
   key or your settings.

## Route 1 — free Apple ID (7 days at a time)

1. Plug the phone in, pick it as the run destination, press ⌘R.
2. On the phone: Settings › General › VPN & Device Management › trust your
   developer certificate.
3. The build expires after **7 days**. Re-run ⌘R from Xcode to renew it.

Free accounts are limited to 3 apps at a time, and each app plus its extension
counts once.

## Route 2 — paid developer account ($99/year)

Same as above, but builds last a year and can be distributed with TestFlight:

```bash
cd ios
xcodebuild -project VoiceAppV4.xcodeproj -scheme VoiceAppV4 \
  -destination 'generic/platform=iOS' -configuration Release archive \
  -archivePath build/VoiceAppV4.xcarchive
xcodebuild -exportArchive -archivePath build/VoiceAppV4.xcarchive \
  -exportOptionsPlist ExportOptions.plist -exportPath build/ipa
```

## Route 3 — AltStore / SideStore

Archive an unsigned `.ipa` on the Mac, then let AltStore re-sign it with your
Apple ID and refresh it in the background over Wi-Fi. Same 7-day limit, but the
renewal is automatic.

## After installing

1. Open the app and paste a Gemini API key from
   [Google AI Studio](https://aistudio.google.com/apikey).
2. Allow the microphone when asked.
3. Settings › General › Keyboard › Keyboards › **Add New Keyboard** ›
   Voice App V4.
4. Tap the newly added keyboard and turn on **Allow Full Access**. iOS shows a
   scary warning here; it is the switch that lets any keyboard use the network,
   and without it there is no microphone and no Gemini, so nothing works.
5. In any app: tap a text field, hold the 🌐 globe key to switch to Voice App V4,
   tap the mic, talk, tap again.

## When something misbehaves

The app's **Diagnostics** screen reports the five things that actually break
dictation: the API key, microphone permission, whether the keyboard is
installed, whether Full Access is on, and Low Power Mode. It also has a **Test
connection** button that does a real round-trip to Gemini and tells you exactly
what came back.

| Symptom | Cause |
| --- | --- |
| Mic key is grey with a slash | Full Access is off, or the field is a password/PIN box |
| "Add your Gemini API key in Settings first." | The keyboard cannot read the keychain — check the App Group and Keychain Sharing ids match in both targets |
| "Lost the connection to Gemini." | The live socket dropped; the clip is retried on the batch model automatically |
| "Gemini rejected the API key." | Key is wrong, revoked, or restricted to other APIs |
| Keyboard never appears in the list | The extension did not get embedded — rebuild, and check the app's Embed Foundation Extensions phase |
