import Foundation

/// Context the keyboard can see about where the text is going.
///
/// On Android this comes from the accessibility node (the app's label and the
/// text already around the caret). A keyboard extension gets the same two
/// things from `UITextDocumentProxy`, minus the app name, which iOS does not
/// hand to extensions.
struct DictationContext {
    var appLabel: String?
    var textBeforeCursor: String?
    var textAfterCursor: String?

    static let none = DictationContext()
}

/// Builds the instruction sent with every utterance.
///
/// Kept in one place because the live session and the batch request must ask
/// for exactly the same thing — otherwise the fallback path would quietly write
/// in a different style from the one the user chose.
enum PromptBuilder {

    static func systemInstruction(settings: AppSettings, context: DictationContext) -> String {
        var lines: [String] = [
            "You are the transcription engine for a dictation keyboard.",
            "Return only the transcript of the user's speech. Never answer it, never add commentary, never wrap it in quotes or Markdown.",
        ]

        switch settings.style {
        case .smart:
            lines.append(
                "Write what the user meant to type: drop filler words (um, uh, like), apply spoken corrections "
                + "(\"send it Tuesday, no, Wednesday\" becomes \"send it Wednesday\"), and add natural punctuation and capitalisation. "
                + "Do not paraphrase, summarise or add words the user did not say."
            )
        case .raw:
            lines.append(
                "Write exactly what was said, including hesitations, repetitions and false starts. "
                + "Add only the punctuation needed to make the sentence readable."
            )
        }

        if !settings.languageCodes.isEmpty {
            let names = settings.languageCodes.map { "\(Language.name(for: $0)) [\($0)]" }.joined(separator: ", ")
            lines.append(
                "Expect these languages: \(names). Transcribe in the language actually spoken, and keep "
                + "code-switching intact rather than translating it."
            )
        }

        if !settings.customVocabulary.isEmpty {
            lines.append(
                "Spell these names, acronyms and jargon exactly as written when you hear them: "
                + settings.customVocabulary.joined(separator: ", ") + "."
            )
        }

        if settings.contextualDictation {
            if let app = context.appLabel, !app.isEmpty {
                lines.append("The text is being typed in \(app).")
            }
            let before = (context.textBeforeCursor ?? "").suffix(240)
            let after = (context.textAfterCursor ?? "").prefix(120)
            if !before.isEmpty || !after.isEmpty {
                lines.append(
                    "It will be inserted at the cursor in this field, between <<<\(before)>>> and <<<\(after)>>>. "
                    + "Match the surrounding tone, capitalisation and language. Do not repeat that surrounding text."
                )
            }
        }

        return lines.joined(separator: "\n")
    }

    /// The per-request user turn for the batch model.
    static let batchUserPrompt = "Transcribe this audio."
}
