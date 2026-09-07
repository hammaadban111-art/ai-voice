# Signing and installing the iOS app

CI builds an **unsigned `.ipa`** on every push. Unsigned is deliberate: the
signature depends on whose phone the app is going on, and every tool below takes
a plain `.ipa` and signs it with your own identity.

## First, the thing that catches everyone out

An `.ipa` cannot be installed by opening it. Sending it to a phone over WhatsApp,
AirDrop, email or a cable and tapping it does nothing — iOS has no installer for
a loose `.ipa`, and Files will just sit there. It only becomes an app after a
signing tool puts a signature on it, on the phone it is going to run on.

If the goal is to get dictation onto someone else's iPhone without any of that,
send them the web app instead: <https://hammaadban111-art.github.io/ai-voice/> —
Safari, Share, Add to Home Screen, done.

## Getting the .ipa

Tap this on the phone itself:

**https://github.com/hammaadban111-art/ai-voice/releases/download/latest/VoiceAppV4-iPhone-unsigned.ipa**

It is a plain file on a stable URL — no login, no zip to unpack, and it works in
a phone browser. Every push rebuilds it and replaces the file at that same link,
so it is always the newest build.

(The same run also leaves the build under **Actions → Build iOS app →
Artifacts**, but that copy is a zip behind a GitHub login, which is awkward on a
phone. Use the link above.)

Nothing else needs a Mac from here on, unless the tool you pick does.

## Which signer

ESign, which used to be the obvious answer, was discontinued in April 2025. The
current field:

| Tool | Cost | Computer needed | App lifetime | Good for |
| --- | --- | --- | --- | --- |
| **SideStore** | free | setup only, then untethered | 7 days, auto-refreshes on-device over Wi-Fi | The best free answer for a phone that is not yours to babysit |
| **AltStore Classic** | free | yes, every 7 days | 7 days | Most documentation, safest reputation, easiest first time |
| **Sideloadly** | free | yes (Windows or Mac) | 7 days | One-shot installs, least setup |
| **Feather** | free | no | depends on the certificate you feed it | The open-source successor to ESign |
| **KSign** | free | no | depends on certificate | Other ESign replacement |
| **TrollStore** | free | no | **permanent, no certificate at all** | Only works on iOS 17.0 and below — the CoreTrust bug it uses was patched in 17.0.1 |
| **Apple Developer Program** | $99/yr | Mac once | 1 year, or TestFlight | The only route with no maintenance |

Two things worth knowing before choosing:

- **7 days is an Apple limit, not a tool limit.** Any free Apple ID signature
  expires after a week. SideStore is the only free option that renews it without
  a computer in the room.
- **Paid "signing services"** that sell certificates (Signulous and friends) get
  their certificates revoked by Apple in batches, taking every app installed with
  them down at once. Cheaper than $99/yr right up until the morning it stops
  working.

## The free route that survives being handed to someone else

**SideStore**, because it refreshes itself:

1. On a computer once: install SideStore's pairing tool, generate a pairing file
   for the phone, and put SideStore on it (their docs walk through the current
   steps — the mechanism changes often enough that copying it here would go
   stale).
2. On the phone: open SideStore, sign in with an Apple ID, tap **+**, pick
   `VoiceAppV4-iPhone-unsigned.ipa`.
3. Enable **background refresh** in SideStore so it re-signs before the seven
   days run out. As long as the phone sees Wi-Fi about once a week, the app
   keeps working and nobody has to do anything.

Three-app limit: a free Apple ID can hold three sideloaded apps at a time, and
this app plus its keyboard extension counts as one.

## The route with no maintenance

Pay Apple $99/year, then **TestFlight**:

1. Add the app in App Store Connect, upload a signed build from Xcode.
2. Invite by email or share a public TestFlight link.
3. She installs TestFlight once, taps the link, and gets the app. Builds last
   90 days, and updates arrive as normal app updates.

This is the only configuration where nothing expires under her, and it is what
to do if the app turns out to be something she uses daily.

## After installing, either way

1. Open **Voice App V4**, paste a Gemini API key.
2. Allow the microphone.
3. Settings › General › Keyboard › Keyboards › **Add New Keyboard** › Voice App V4.
4. Tap it again → **Allow Full Access**. Without it the keyboard has no
   microphone and no network, which is the whole app.

## If you would rather not sign anything

[`../web`](../web) is the same app as a web page: open the link in Safari, Add
to Home Screen, and it never expires because there is nothing to sign. It
transcribes identically; the only thing it cannot do is type into another app,
so it ends with Copy and paste.
