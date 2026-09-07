import Foundation

/// Every way a dictation can fail, with the message the user actually sees.
/// The cases mirror the Android build's error type one for one.
enum DictationError: Error, Equatable {
    case noApiKey
    case invalidApiKey
    case quota
    case network
    case liveConnectFailed(String)
    case httpStatus(Int, String)
    case micPermission
    case micUnavailable
    case noSpeech
    case tooShort
    case cancelled
    case unknown(String)

    var message: String {
        switch self {
        case .noApiKey:
            return "Add your Gemini API key in Settings first."
        case .invalidApiKey:
            return "Gemini rejected the API key. Check it in Settings."
        case .quota:
            return "Gemini quota or rate limit reached. Try again shortly."
        case .network:
            return "No internet connection."
        case .liveConnectFailed:
            return "Lost the connection to Gemini."
        case .httpStatus(let code, let detail):
            return detail.isEmpty
                ? "Gemini returned an error (\(code))."
                : "Gemini returned an error (\(code)): \(detail)"
        case .micPermission:
            return "Microphone permission is needed before you can dictate."
        case .micUnavailable:
            return "Microphone unavailable."
        case .noSpeech:
            return "Didn't catch any speech."
        case .tooShort:
            return "That was too short to transcribe."
        case .cancelled:
            return "Cancelled"
        case .unknown(let detail):
            return detail.isEmpty ? "Transcription failed." : detail
        }
    }

    /// Whether falling back to the batch model has any chance of helping.
    var isRecoverableByFallback: Bool {
        switch self {
        case .liveConnectFailed, .network, .unknown:
            return true
        default:
            return false
        }
    }
}
