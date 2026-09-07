import Combine
import Foundation

/// The whole dictation loop: tap → microphone → Gemini → text.
///
/// The Android build runs this inside a foreground service so the bubble keeps
/// working across apps. iOS has no such thing, so the same state machine lives
/// in whichever process is on screen — the keyboard extension while you type,
/// the container app for the try-it screen.
@MainActor
final class DictationEngine: ObservableObject {

    enum State: Equatable {
        case idle
        case listening(level: Float)
        case transcribing
        case error(String)

        var isBusy: Bool {
            switch self {
            case .listening, .transcribing: return true
            case .idle, .error: return false
            }
        }
    }

    @Published private(set) var state: State = .idle
    /// Interim words from the live model, shown greyed out under the key.
    @Published private(set) var interimText: String = ""

    private let recorder = AudioRecorder()
    private let settingsStore: SettingsStore
    private var live: GeminiLiveSession?
    private var contextProvider: @MainActor () -> DictationContext
    private var startedAt = Date()

    /// Called with the finished transcript. The keyboard inserts it at the
    /// cursor; the app's try-it screen just shows it.
    var onTranscript: (@MainActor (String, TranscriptEntry) -> Void)?
    /// Called when a dictation fails, after the state has already been set.
    var onFailure: (@MainActor (DictationError) -> Void)?

    init(
        settingsStore: SettingsStore = .shared,
        context: @escaping @MainActor () -> DictationContext = { .none }
    ) {
        self.settingsStore = settingsStore
        self.contextProvider = context

        recorder.onLevel = { [weak self] level in
            Task { @MainActor in
                guard let self, case .listening = self.state else { return }
                self.state = .listening(level: level)
            }
        }
        recorder.onChunk = { [weak self] chunk in
            Task { @MainActor in self?.live?.send(pcm: chunk) }
        }
    }

    var isRecording: Bool { recorder.isRecording }

    func setContextProvider(_ provider: @escaping @MainActor () -> DictationContext) {
        contextProvider = provider
    }

    // -- the loop ----------------------------------------------------------

    /// Tap (or press, in push-to-talk) — starts capture.
    func beginDictation() {
        guard case .idle = state else { return }
        guard let apiKey = ApiKeyStore.load(), !apiKey.isEmpty else {
            fail(.noApiKey)
            return
        }
        guard AudioRecorder.hasMicPermission() else {
            fail(.micPermission)
            return
        }

        let settings = settingsStore.settings
        let context = contextProvider()
        interimText = ""
        startedAt = Date()

        do {
            try recorder.start()
        } catch let error as DictationError {
            fail(error)
            return
        } catch {
            fail(.micUnavailable)
            return
        }

        state = .listening(level: 0)
        Haptics.tap()

        guard settings.model == .live else { return }

        // Live mode opens the socket while the user is still drawing breath, so
        // the first chunk has somewhere to go.
        live = GeminiLiveSession(apiKey: apiKey, settings: settings, context: context) { [weak self] event in
            Task { @MainActor in self?.handle(event) }
        }
        live?.connect()
    }

    /// Tap again (or release) — stops capture and settles the transcript.
    func finishDictation() {
        guard case .listening = state else { return }

        let pcm = recorder.stop()
        let settings = settingsStore.settings
        let context = contextProvider()
        Haptics.tap()

        guard pcm.count / 2 >= AudioRecorder.minimumUsefulSamples else {
            live?.cancel()
            live = nil
            fail(.tooShort)
            return
        }

        state = .transcribing

        if settings.model == .live, let live {
            // The socket has all the audio already; ask it to flush. If it dies
            // before the final turn, `handle` retries the clip on the batch model.
            pendingFallbackAudio = pcm
            live.finish()
            return
        }

        transcribeBatch(pcm: pcm, settings: settings, context: context, viaFallback: false)
    }

    /// Swipe away / lost focus — throws the audio out without transcribing.
    func cancelDictation() {
        recorder.cancel()
        live?.cancel()
        live = nil
        pendingFallbackAudio = nil
        interimText = ""
        state = .idle
    }

    // -- transcription -----------------------------------------------------

    private var pendingFallbackAudio: Data?

    private func handle(_ event: GeminiLiveSession.Event) {
        switch event {
        case .connected, .closed:
            break
        case .interim(let text):
            interimText = text
        case .final(let text):
            live = nil
            pendingFallbackAudio = nil
            deliver(text: text, model: TranscriptionModel.live.modelName, viaFallback: false)
        case .failed(let error):
            live = nil
            // A socket that dropped mid-sentence still has the audio on this
            // side, so retry it once on the batch model rather than losing what
            // the user just said.
            if error.isRecoverableByFallback, let pcm = pendingFallbackAudio {
                pendingFallbackAudio = nil
                transcribeBatch(
                    pcm: pcm,
                    settings: settingsStore.settings,
                    context: contextProvider(),
                    viaFallback: true
                )
                return
            }
            pendingFallbackAudio = nil
            if recorder.isRecording { recorder.cancel() }
            fail(error)
        }
    }

    private func transcribeBatch(pcm: Data, settings: AppSettings, context: DictationContext, viaFallback: Bool) {
        guard let apiKey = ApiKeyStore.load(), !apiKey.isEmpty else {
            fail(.noApiKey)
            return
        }
        state = .transcribing

        Task { [weak self] in
            let client = GeminiClient(apiKey: apiKey)
            do {
                let result = try await client.transcribe(
                    pcm: pcm,
                    settings: settings,
                    context: context,
                    viaFallback: viaFallback
                )
                await MainActor.run {
                    self?.deliver(text: result.text, model: result.model, viaFallback: result.viaFallback)
                }
            } catch let error as DictationError {
                await MainActor.run { self?.fail(error) }
            } catch {
                await MainActor.run { self?.fail(.unknown("Batch transcription failed")) }
            }
        }
    }

    private func deliver(text: String, model: String, viaFallback: Bool) {
        interimText = ""
        let cleaned = GeminiClient.clean(text)
        guard !cleaned.isEmpty else {
            fail(.noSpeech)
            return
        }

        let entry = TranscriptEntry(
            text: cleaned,
            createdAt: Date(),
            appLabel: contextProvider().appLabel,
            model: model,
            viaFallback: viaFallback,
            durationMs: Int(Date().timeIntervalSince(startedAt) * 1000)
        )
        if settingsStore.settings.historyEnabled {
            TranscriptStore.shared.add(entry)
        }

        state = .idle
        Haptics.success()
        onTranscript?(cleaned, entry)
    }

    private func fail(_ error: DictationError) {
        interimText = ""
        state = .error(error.message)
        Haptics.error()
        onFailure?(error)

        // Clear the message after a beat so the key goes back to being a key.
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            await MainActor.run {
                guard let self, case .error = self.state else { return }
                self.state = .idle
            }
        }
    }
}
