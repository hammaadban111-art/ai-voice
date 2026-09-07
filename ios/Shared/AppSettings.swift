import Foundation

/// Everything the app and the keyboard extension both need to agree on.
///
/// Field names and persisted keys are kept identical to the Android build
/// (`voiceappv4_settings`) so the two apps stay a matched pair and a settings
/// export from one reads on the other.
struct AppSettings: Codable, Equatable {

    // -- onboarding --------------------------------------------------------
    var setupComplete: Bool = false

    // -- the mic key (Android calls this the bubble) -----------------------
    var bubbleEnabled: Bool = true
    var bubbleOnRight: Bool = true
    var bubbleOpacity: Double = 1.0
    var bubbleSizeDp: Double = 56
    var bubbleX: Double = -1
    var bubbleY: Double = -1
    var pushToTalk: Bool = false

    // -- transcription -----------------------------------------------------
    var model: TranscriptionModel = .live
    var style: DictationStyle = .smart
    var contextualDictation: Bool = true
    var customVocabulary: [String] = []
    var languageCodes: [String] = [Language.defaultCode]

    // -- history -----------------------------------------------------------
    var historyEnabled: Bool = true

    // -- pause / snooze ----------------------------------------------------
    var snoozeMinutes: Int = 30
    var snoozeUntilMs: Double = 0

    // -- misc --------------------------------------------------------------
    var diagnosticsEnabled: Bool = false

    static let `default` = AppSettings()

    /// True while the user has paused dictation from the notification/menu.
    var isSnoozed: Bool {
        if snoozeUntilMs == Self.snoozeUntilRestart { return true }
        return snoozeUntilMs > Date().timeIntervalSince1970 * 1000
    }

    var snoozeDescription: String? {
        guard isSnoozed else { return nil }
        if snoozeUntilMs == Self.snoozeUntilRestart { return "Until restart" }
        let remaining = snoozeUntilMs / 1000 - Date().timeIntervalSince1970
        let minutes = max(1, Int((remaining / 60).rounded(.up)))
        return "\(minutes) min left"
    }

    /// Sentinel matching the Android "Until restart" snooze option.
    static let snoozeUntilRestart: Double = -1
}

/// Which Gemini transcription model runs the utterance.
enum TranscriptionModel: String, Codable, CaseIterable, Identifiable {
    /// Streaming session — interim words appear while you speak.
    case live
    /// One request per utterance. Slower to first word, sturdier on bad networks.
    case batch

    var id: String { rawValue }

    /// Model ids taken verbatim from the shipped Android build.
    var modelName: String {
        switch self {
        case .live: return "gemini-3.5-transcribe-live"
        case .batch: return "gemini-3.5-transcribe"
        }
    }

    var title: String {
        switch self {
        case .live: return "Live model"
        case .batch: return "Fallback model"
        }
    }

    var detail: String {
        switch self {
        case .live: return "Words appear while you talk. Needs a steady connection."
        case .batch: return "Sends the whole clip at once. Slower, but survives a weak signal."
        }
    }
}

/// How much Gemini is allowed to tidy what you said.
enum DictationStyle: String, Codable, CaseIterable, Identifiable {
    case smart
    case raw

    var id: String { rawValue }

    var title: String {
        switch self {
        case .smart: return "Smart"
        case .raw: return "Raw"
        }
    }

    var detail: String {
        switch self {
        case .smart:
            return "Removes filler words, resolves spoken corrections and adds natural punctuation and capitalisation."
        case .raw:
            return "Writes exactly what you said, including hesitations and false starts."
        }
    }
}
