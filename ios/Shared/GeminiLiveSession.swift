import Foundation

/// Streaming transcription over the interactions socket.
///
/// This is what makes words appear while you are still talking. Audio goes up
/// in 100 ms chunks as it is captured; interim transcripts come back and are
/// shown greyed out, and the final one is what gets inserted.
///
/// The endpoint, model id and field names follow the shipped Android build:
/// `wss://.../v1beta/interactions`, `gemini-3.5-transcribe-live`, and
/// `inputAudioTranscription` / `interimInputTranscription` on the way back.
final class GeminiLiveSession {

    enum Event {
        case connected
        case interim(String)
        case final(String)
        case closed
        case failed(DictationError)
    }

    private let apiKey: String
    private let settings: AppSettings
    private let context: DictationContext
    private let session: URLSession
    private var socket: URLSessionWebSocketTask?

    private var interim = ""
    private var finalText = ""
    private var finished = false
    private let onEvent: (Event) -> Void

    init(
        apiKey: String,
        settings: AppSettings,
        context: DictationContext,
        session: URLSession = .shared,
        onEvent: @escaping (Event) -> Void
    ) {
        self.apiKey = apiKey
        self.settings = settings
        self.context = context
        self.session = session
        self.onEvent = onEvent
    }

    // -- lifecycle ---------------------------------------------------------

    func connect() {
        var components = URLComponents(string: "wss://\(GeminiClient.host)/v1beta/interactions")!
        components.queryItems = [URLQueryItem(name: "key", value: apiKey)]

        var request = URLRequest(url: components.url!)
        request.timeoutInterval = 20
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")

        let task = session.webSocketTask(with: request)
        socket = task
        task.resume()
        receive()
        sendSetup()
        onEvent(.connected)
    }

    /// Feeds one chunk of 16 kHz mono PCM into the session.
    func send(pcm: Data) {
        guard let socket, !finished else { return }
        let payload: [String: Any] = [
            "realtimeInput": [
                "audio": [
                    "mimeType": "audio/pcm;rate=16000",
                    "data": pcm.base64EncodedString(),
                ],
            ],
        ]
        socket.send(.string(Self.encode(payload))) { [weak self] error in
            if error != nil { self?.fail(.liveConnectFailed("send")) }
        }
    }

    /// Tells the server the user stopped talking and to flush the last words.
    func finish() {
        guard let socket, !finished else { return }
        let payload: [String: Any] = ["realtimeInput": ["audioStreamEnd": true]]
        socket.send(.string(Self.encode(payload))) { _ in }
    }

    func cancel() {
        finished = true
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
    }

    /// Whatever has been transcribed so far, final text plus any interim tail.
    var currentText: String {
        GeminiClient.clean(finalText.isEmpty ? interim : finalText)
    }

    // -- wire --------------------------------------------------------------

    private func sendSetup() {
        guard let socket else { return }
        var setup: [String: Any] = [
            "model": "models/\(TranscriptionModel.live.modelName)",
            "systemInstruction": [
                "parts": [["text": PromptBuilder.systemInstruction(settings: settings, context: context)]],
            ],
            // Ask for the transcript of what goes in, not for a spoken reply.
            "inputAudioTranscription": [:],
            "generationConfig": ["temperature": 0],
        ]
        if !settings.languageCodes.isEmpty {
            setup["transcriptionConfig"] = ["languageCodes": settings.languageCodes]
        }
        // The app decides when a turn starts and ends — the user's tap is a far
        // better endpointer than silence detection over a noisy bus ride.
        setup["realtimeInputConfig"] = ["automaticActivityDetection": ["disabled": true]]

        socket.send(.string(Self.encode(["setup": setup]))) { [weak self] error in
            if error != nil { self?.fail(.liveConnectFailed("setup")) }
        }
    }

    private func receive() {
        socket?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .failure:
                self.fail(.liveConnectFailed("socket"))
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handle(Data(text.utf8))
                case .data(let data):
                    self.handle(data)
                @unknown default:
                    break
                }
                if !self.finished { self.receive() }
            }
        }
    }

    private func handle(_ data: Data) {
        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }

        if let error = json["error"] as? [String: Any] {
            let code = (error["code"] as? Int) ?? 0
            let message = (error["message"] as? String) ?? ""
            fail(code == 429 ? .quota
                 : (code == 401 || code == 403) ? .invalidApiKey
                 : .httpStatus(code, message))
            return
        }

        // Interim words: shown live, replaced by the final transcript.
        if let text = Self.transcript(in: json, key: "interimInputTranscription"), !text.isEmpty {
            interim = text
            onEvent(.interim(GeminiClient.clean(text)))
        }

        // The finished turn. Both spellings appear in the wire format depending
        // on which side produced it, so accept either.
        for key in ["inputTranscription", "inputAudioTranscription", "outputText"] {
            if let text = Self.transcript(in: json, key: key), !text.isEmpty {
                finalText += text
            }
        }

        let server = json["serverContent"] as? [String: Any]
        let complete = (server?["turnComplete"] as? Bool) ?? (json["turnComplete"] as? Bool) ?? false
        let generationComplete = (server?["generationComplete"] as? Bool) ?? false
        if complete || generationComplete {
            finished = true
            let text = GeminiClient.clean(finalText.isEmpty ? interim : finalText)
            onEvent(text.isEmpty ? .failed(.noSpeech) : .final(text))
            socket?.cancel(with: .normalClosure, reason: nil)
            socket = nil
            onEvent(.closed)
        }
    }

    /// Digs the transcript text out of whichever envelope carried it.
    private static func transcript(in json: [String: Any], key: String) -> String? {
        func text(from any: Any?) -> String? {
            if let string = any as? String { return string }
            if let dict = any as? [String: Any] {
                if let text = dict["text"] as? String { return text }
                if let parts = dict["parts"] as? [[String: Any]] {
                    return parts.compactMap { $0["text"] as? String }.joined()
                }
            }
            return nil
        }
        if let direct = text(from: json[key]) { return direct }
        if let server = json["serverContent"] as? [String: Any], let nested = text(from: server[key]) {
            return nested
        }
        return nil
    }

    private func fail(_ error: DictationError) {
        guard !finished else { return }
        finished = true
        socket?.cancel(with: .abnormalClosure, reason: nil)
        socket = nil
        onEvent(.failed(error))
    }

    private static func encode(_ payload: [String: Any]) -> String {
        guard
            let data = try? JSONSerialization.data(withJSONObject: payload),
            let string = String(data: data, encoding: .utf8)
        else { return "{}" }
        return string
    }
}
