import Foundation

/// One-shot transcription: the whole utterance goes up as a single request.
///
/// This is the "Fallback model" in Settings — slower to the first word than the
/// live session, but it survives a flaky connection, so it is also what the
/// live path drops back to when the socket dies mid-sentence.
struct GeminiClient {

    static let host = "generativelanguage.googleapis.com"
    static let base = "https://\(GeminiClient.host)/v1beta"

    let apiKey: String
    var session: URLSession = .shared

    struct Success {
        let text: String
        let model: String
        let viaFallback: Bool
    }

    /// - Parameter pcm: raw 16 kHz mono little-endian PCM.
    func transcribe(
        pcm: Data,
        settings: AppSettings,
        context: DictationContext,
        viaFallback: Bool = false
    ) async throws -> Success {
        guard pcm.count / 2 >= AudioRecorder.minimumUsefulSamples else { throw DictationError.tooShort }

        let model = TranscriptionModel.batch.modelName
        var request = URLRequest(url: URL(string: "\(Self.base)/models/\(model):generateContent")!)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")

        let body: [String: Any] = [
            "system_instruction": [
                "parts": [["text": PromptBuilder.systemInstruction(settings: settings, context: context)]],
            ],
            "contents": [[
                "role": "user",
                "parts": [
                    ["text": PromptBuilder.batchUserPrompt],
                    ["inline_data": [
                        "mime_type": "audio/wav",
                        "data": pcm.asWav().base64EncodedString(),
                    ]],
                ],
            ]],
            "generation_config": [
                // Transcription is not a place for creativity.
                "temperature": 0,
                "candidate_count": 1,
            ],
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .cancelled {
            throw DictationError.cancelled
        } catch {
            throw DictationError.network
        }

        guard let http = response as? HTTPURLResponse else { throw DictationError.unknown("Batch transcription failed") }
        guard (200..<300).contains(http.statusCode) else {
            throw Self.error(for: http.statusCode, body: data)
        }

        let text = Self.text(in: data)
        guard !text.isEmpty else { throw DictationError.noSpeech }
        return Success(text: text, model: model, viaFallback: viaFallback)
    }

    /// Cheap round-trip used by Settings' "Test connection".
    func validateKey() async throws -> String {
        var request = URLRequest(url: URL(string: "\(Self.base)/models/\(TranscriptionModel.batch.modelName)")!)
        request.timeoutInterval = 20
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw DictationError.network
        }
        guard let http = response as? HTTPURLResponse else { throw DictationError.network }
        guard (200..<300).contains(http.statusCode) else { throw Self.error(for: http.statusCode, body: data) }

        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        return (json?["name"] as? String) ?? TranscriptionModel.batch.modelName
    }

    // -- response plumbing -------------------------------------------------

    static func error(for status: Int, body: Data) -> DictationError {
        let detail = message(in: body)
        switch status {
        case 400 where detail.contains("API key not valid") || detail.contains("API_KEY_INVALID"),
             401, 403:
            return .invalidApiKey
        case 429:
            return .quota
        default:
            return .httpStatus(status, detail)
        }
    }

    private static func message(in body: Data) -> String {
        guard
            let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
            let error = json["error"] as? [String: Any]
        else { return "" }
        return (error["message"] as? String) ?? (error["status"] as? String) ?? ""
    }

    /// Pulls the transcript out of a `generateContent` response and strips the
    /// wrappers the model sometimes adds anyway.
    static func text(in body: Data) -> String {
        guard
            let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
            let candidates = json["candidates"] as? [[String: Any]]
        else { return "" }

        var collected = ""
        for candidate in candidates.prefix(1) {
            let content = candidate["content"] as? [String: Any]
            for part in (content?["parts"] as? [[String: Any]]) ?? [] {
                if let text = part["text"] as? String { collected += text }
            }
        }
        return clean(collected)
    }

    /// Matches the Android JNI's segment cleaner: trims, and drops the
    /// no-speech markers the models emit for silence.
    static func clean(_ raw: String) -> String {
        var text = raw
        for marker in ["[BLANK_AUDIO]", "(blank audio)", "[SILENCE]", "[MUSIC]", "(music)", "[INAUDIBLE]"] {
            text = text.replacingOccurrences(of: marker, with: "")
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        // A model that decided to quote itself should not put quotes in the field.
        if text.count > 1, text.hasPrefix("\""), text.hasSuffix("\"") {
            text = String(text.dropFirst().dropLast())
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
