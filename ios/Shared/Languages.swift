import Foundation

/// The dictation languages offered, matching the Android build's list.
///
/// More than one may be selected; Gemini is told which languages to expect and
/// picks per utterance, which is what makes code-switching (Urdu with English
/// technical words, say) come out right.
struct Language: Identifiable, Hashable {
    let code: String
    let name: String

    var id: String { code }

    static let defaultCode = "en-US"

    static let all: [Language] = [
        Language(code: "en-US", name: "English (United States)"),
        Language(code: "en-GB", name: "English (United Kingdom)"),
        Language(code: "ur-PK", name: "Urdu"),
        Language(code: "hi-IN", name: "Hindi"),
        Language(code: "ar-EG", name: "Arabic"),
        Language(code: "zh-CN", name: "Chinese (Simplified)"),
        Language(code: "nl-NL", name: "Dutch"),
        Language(code: "fr-FR", name: "French"),
        Language(code: "de-DE", name: "German"),
        Language(code: "id-ID", name: "Indonesian"),
        Language(code: "it-IT", name: "Italian"),
        Language(code: "ja-JP", name: "Japanese"),
        Language(code: "ko-KR", name: "Korean"),
        Language(code: "pl-PL", name: "Polish"),
        Language(code: "pt-BR", name: "Portuguese (Brazil)"),
        Language(code: "ru-RU", name: "Russian"),
        Language(code: "es-ES", name: "Spanish (Spain)"),
        Language(code: "es-419", name: "Spanish (Latin America)"),
        Language(code: "sv-SE", name: "Swedish"),
        Language(code: "tr-TR", name: "Turkish"),
        Language(code: "vi-VN", name: "Vietnamese"),
    ]

    static func name(for code: String) -> String {
        all.first { $0.code == code }?.name ?? code
    }

    /// "English (United States)" or "Urdu, English (United States) and 1 more".
    static func summary(for codes: [String]) -> String {
        let names = codes.map(name(for:))
        switch names.count {
        case 0: return "Detect automatically"
        case 1: return names[0]
        case 2: return "\(names[0]) and \(names[1])"
        default: return "\(names[0]), \(names[1]) and \(names.count - 2) more"
        }
    }
}
