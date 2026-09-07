import Combine
import Foundation

/// Settings shared between the app and the keyboard extension.
///
/// The Android build keeps these in a DataStore called `voiceappv4_settings`;
/// the iOS equivalent is the App Group's shared `UserDefaults`, which is the
/// only storage both processes can see. Keys match the Android ones so the two
/// builds can be diffed field by field.
final class SettingsStore: ObservableObject {

    static let appGroup = "group.com.hammaad.voiceappv4"
    static let shared = SettingsStore()

    private enum Key {
        static let setupComplete = "setup_complete"
        static let bubbleEnabled = "bubble_enabled"
        static let bubbleOnRight = "bubble_right"
        static let bubbleOpacity = "bubble_opacity"
        static let bubbleSize = "bubble_size"
        static let bubbleX = "bubble_x"
        static let bubbleY = "bubble_y"
        static let pushToTalk = "push_to_talk"
        static let model = "model"
        static let style = "style"
        static let contextual = "contextual_dictation"
        static let customVocabulary = "custom_vocabulary"
        static let languageCodes = "language_codes"
        static let historyEnabled = "history_enabled"
        static let snoozeMinutes = "snooze_minutes"
        static let snoozeUntil = "snooze_until"
        static let diagnostics = "diagnostics_enabled"
    }

    private let defaults: UserDefaults

    @Published var settings: AppSettings {
        didSet {
            guard settings != oldValue else { return }
            write(settings)
        }
    }

    init(defaults: UserDefaults? = nil) {
        // Falling back to `.standard` keeps previews and unit tests working on a
        // machine with no provisioning profile, where the App Group is missing.
        let store = defaults ?? UserDefaults(suiteName: SettingsStore.appGroup) ?? .standard
        self.defaults = store
        self.settings = SettingsStore.read(from: store)
    }

    /// Re-reads shared storage. The keyboard extension is suspended rather than
    /// terminated between appearances, so it calls this every time it comes up.
    func reload() {
        let fresh = SettingsStore.read(from: defaults)
        if fresh != settings { settings = fresh }
    }

    func update(_ mutate: (inout AppSettings) -> Void) {
        var copy = settings
        mutate(&copy)
        settings = copy
    }

    // -- snooze ------------------------------------------------------------

    func snooze(minutes: Int?) {
        update {
            if let minutes {
                $0.snoozeMinutes = minutes
                $0.snoozeUntilMs = (Date().timeIntervalSince1970 + Double(minutes) * 60) * 1000
            } else {
                $0.snoozeUntilMs = AppSettings.snoozeUntilRestart
            }
        }
    }

    func resume() {
        update { $0.snoozeUntilMs = 0 }
    }

    // -- persistence -------------------------------------------------------

    private static func read(from defaults: UserDefaults) -> AppSettings {
        var s = AppSettings.default
        if defaults.object(forKey: Key.setupComplete) != nil {
            s.setupComplete = defaults.bool(forKey: Key.setupComplete)
        }
        if defaults.object(forKey: Key.bubbleEnabled) != nil {
            s.bubbleEnabled = defaults.bool(forKey: Key.bubbleEnabled)
        }
        if defaults.object(forKey: Key.bubbleOnRight) != nil {
            s.bubbleOnRight = defaults.bool(forKey: Key.bubbleOnRight)
        }
        if defaults.object(forKey: Key.bubbleOpacity) != nil {
            s.bubbleOpacity = defaults.double(forKey: Key.bubbleOpacity)
        }
        if defaults.object(forKey: Key.bubbleSize) != nil {
            s.bubbleSizeDp = defaults.double(forKey: Key.bubbleSize)
        }
        if defaults.object(forKey: Key.bubbleX) != nil {
            s.bubbleX = defaults.double(forKey: Key.bubbleX)
        }
        if defaults.object(forKey: Key.bubbleY) != nil {
            s.bubbleY = defaults.double(forKey: Key.bubbleY)
        }
        if defaults.object(forKey: Key.pushToTalk) != nil {
            s.pushToTalk = defaults.bool(forKey: Key.pushToTalk)
        }
        if let raw = defaults.string(forKey: Key.model), let model = TranscriptionModel(rawValue: raw) {
            s.model = model
        }
        if let raw = defaults.string(forKey: Key.style), let style = DictationStyle(rawValue: raw) {
            s.style = style
        }
        if defaults.object(forKey: Key.contextual) != nil {
            s.contextualDictation = defaults.bool(forKey: Key.contextual)
        }
        if let terms = defaults.stringArray(forKey: Key.customVocabulary) {
            s.customVocabulary = terms
        }
        if let codes = defaults.stringArray(forKey: Key.languageCodes), !codes.isEmpty {
            s.languageCodes = codes
        }
        if defaults.object(forKey: Key.historyEnabled) != nil {
            s.historyEnabled = defaults.bool(forKey: Key.historyEnabled)
        }
        if defaults.object(forKey: Key.snoozeMinutes) != nil {
            s.snoozeMinutes = defaults.integer(forKey: Key.snoozeMinutes)
        }
        if defaults.object(forKey: Key.snoozeUntil) != nil {
            s.snoozeUntilMs = defaults.double(forKey: Key.snoozeUntil)
        }
        if defaults.object(forKey: Key.diagnostics) != nil {
            s.diagnosticsEnabled = defaults.bool(forKey: Key.diagnostics)
        }
        return s
    }

    private func write(_ s: AppSettings) {
        defaults.set(s.setupComplete, forKey: Key.setupComplete)
        defaults.set(s.bubbleEnabled, forKey: Key.bubbleEnabled)
        defaults.set(s.bubbleOnRight, forKey: Key.bubbleOnRight)
        defaults.set(s.bubbleOpacity, forKey: Key.bubbleOpacity)
        defaults.set(s.bubbleSizeDp, forKey: Key.bubbleSize)
        defaults.set(s.bubbleX, forKey: Key.bubbleX)
        defaults.set(s.bubbleY, forKey: Key.bubbleY)
        defaults.set(s.pushToTalk, forKey: Key.pushToTalk)
        defaults.set(s.model.rawValue, forKey: Key.model)
        defaults.set(s.style.rawValue, forKey: Key.style)
        defaults.set(s.contextualDictation, forKey: Key.contextual)
        defaults.set(s.customVocabulary, forKey: Key.customVocabulary)
        defaults.set(s.languageCodes, forKey: Key.languageCodes)
        defaults.set(s.historyEnabled, forKey: Key.historyEnabled)
        defaults.set(s.snoozeMinutes, forKey: Key.snoozeMinutes)
        defaults.set(s.snoozeUntilMs, forKey: Key.snoozeUntil)
        defaults.set(s.diagnosticsEnabled, forKey: Key.diagnostics)
    }
}
