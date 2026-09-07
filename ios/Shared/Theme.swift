import SwiftUI

/// The mic key's colours, matching the Android bubble states exactly:
/// blue idle, red recording, amber busy, grey error.
enum Theme {
    static let idle = Color(red: 0x2E / 255, green: 0x6B / 255, blue: 0xE6 / 255)
    static let recording = Color(red: 0xE5 / 255, green: 0x48 / 255, blue: 0x4D / 255)
    static let busy = Color(red: 0xE0 / 255, green: 0x8C / 255, blue: 0x1A / 255)
    static let error = Color(red: 0x6E / 255, green: 0x6E / 255, blue: 0x6E / 255)

    static let statusOK = Color(red: 0x1B / 255, green: 0x8A / 255, blue: 0x5A / 255)
    static let statusPending = Color(red: 0xB3 / 255, green: 0x53 / 255, blue: 0x09 / 255)

    static func tint(for state: DictationEngine.State) -> Color {
        switch state {
        case .idle: return idle
        case .listening: return recording
        case .transcribing: return busy
        case .error: return error
        }
    }
}
