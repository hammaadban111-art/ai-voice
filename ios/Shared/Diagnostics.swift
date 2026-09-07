import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// "Device and service status, useful when something misbehaves."
///
/// The Android screen reports the accessibility service, overlay permission and
/// battery restrictions. The iOS equivalents are the keyboard being installed,
/// Full Access being granted, and Low Power Mode — the three things that
/// actually stop dictation working on a phone.
struct Diagnostics {

    struct Check: Identifiable {
        let id = UUID()
        let title: String
        let detail: String
        let ok: Bool
        /// Settings deep link, when the fix is a toggle somewhere in Settings.
        let settingsURL: URL?
    }

    static let keyboardBundleID = "com.hammaad.voiceappv4.keyboard"

    /// Set by the keyboard extension every time it appears, so the app can
    /// report Full Access without being an extension itself.
    private static let fullAccessKey = "keyboard_full_access"
    private static let keyboardSeenKey = "keyboard_last_seen"

    static func recordKeyboardState(fullAccess: Bool) {
        let defaults = UserDefaults(suiteName: SettingsStore.appGroup)
        defaults?.set(fullAccess, forKey: fullAccessKey)
        defaults?.set(Date().timeIntervalSince1970, forKey: keyboardSeenKey)
    }

    static var keyboardHasFullAccess: Bool {
        UserDefaults(suiteName: SettingsStore.appGroup)?.bool(forKey: fullAccessKey) ?? false
    }

    static var keyboardLastSeen: Date? {
        guard let stamp = UserDefaults(suiteName: SettingsStore.appGroup)?.double(forKey: keyboardSeenKey),
              stamp > 0
        else { return nil }
        return Date(timeIntervalSince1970: stamp)
    }

    /// Whether the user has added the keyboard in Settings › General › Keyboard.
    static var keyboardInstalled: Bool {
        guard let keyboards = UserDefaults.standard.object(forKey: "AppleKeyboards") as? [String] else {
            // The list is unavailable in the simulator; fall back to whether the
            // extension has ever reported in.
            return keyboardLastSeen != nil
        }
        return keyboards.contains { $0.contains(keyboardBundleID) }
    }

    static func all() -> [Check] {
        #if canImport(UIKit)
        let settingsURL = URL(string: UIApplication.openSettingsURLString)
        let lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
        #else
        let settingsURL: URL? = nil
        let lowPower = false
        #endif

        var checks: [Check] = []

        checks.append(Check(
            title: "Gemini API key",
            detail: ApiKeyStore.hasKey ? "Stored on this device." : "Not set. Dictation cannot run without it.",
            ok: ApiKeyStore.hasKey,
            settingsURL: nil
        ))

        checks.append(Check(
            title: "Microphone permission",
            detail: AudioRecorder.hasMicPermission()
                ? "Granted."
                : "Needed before you can dictate.",
            ok: AudioRecorder.hasMicPermission(),
            settingsURL: settingsURL
        ))

        checks.append(Check(
            title: "Keyboard installed",
            detail: keyboardInstalled
                ? "Voice App V4 is in your keyboard list."
                : "Add it in Settings › General › Keyboard › Keyboards.",
            ok: keyboardInstalled,
            settingsURL: settingsURL
        ))

        checks.append(Check(
            title: "Allow Full Access",
            detail: keyboardHasFullAccess
                ? "Granted — the keyboard can reach Gemini and the microphone."
                : "Off, so the keyboard cannot use the microphone or the network.",
            ok: keyboardHasFullAccess,
            settingsURL: settingsURL
        ))

        checks.append(Check(
            title: "Low Power Mode",
            detail: lowPower
                ? "On. iOS may suspend the keyboard's network requests."
                : "Off.",
            ok: !lowPower,
            settingsURL: settingsURL
        ))

        if let seen = keyboardLastSeen {
            let formatter = RelativeDateTimeFormatter()
            formatter.unitsStyle = .full
            checks.append(Check(
                title: "Keyboard last active",
                detail: formatter.localizedString(for: seen, relativeTo: Date()),
                ok: true,
                settingsURL: nil
            ))
        }

        return checks
    }
}
