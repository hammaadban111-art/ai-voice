import SwiftUI

/// The try-it screen: a real text field and the same mic key the keyboard uses,
/// so setup can be finished without leaving the app.
@MainActor
struct TryItView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: SettingsStore
    @StateObject private var engine = DictationEngine()

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                TextEditor(text: $text)
                    .focused($focused)
                    .scrollContentBackground(.hidden)
                    .padding(12)
                    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    .frame(minHeight: 160)
                    .overlay(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("Your words land here.")
                                .foregroundStyle(.tertiary)
                                .padding(20)
                                .allowsHitTesting(false)
                        }
                    }

                statusLine

                MicKeyView(
                    state: engine.state,
                    pushToTalk: settings.settings.pushToTalk,
                    size: CGFloat(settings.settings.bubbleSizeDp),
                    opacity: settings.settings.bubbleOpacity,
                    onTap: toggle,
                    onPressStart: { engine.beginDictation() },
                    onPressEnd: { engine.finishDictation() }
                )
                .padding(.bottom, 8)

                Spacer(minLength: 0)
            }
            .padding(24)
            .navigationTitle("Try dictation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear {
                // Hand the engine the same context the keyboard gets: what is
                // already in the field, so the transcript matches its tone.
                engine.setContextProvider { DictationContext(appLabel: "Voice App V4", textBeforeCursor: text) }
                engine.onTranscript = { transcript, _ in
                    text += TextInsertion.spaced(transcript, after: text)
                }
                settings.reload()
            }
            .onDisappear { engine.cancelDictation() }
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        switch engine.state {
        case .idle:
            Text(settings.settings.pushToTalk ? "Hold the mic and talk." : "Tap the mic and talk.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        case .listening:
            VStack(spacing: 4) {
                Text("Recording…").font(.subheadline).foregroundStyle(Theme.recording)
                if !engine.interimText.isEmpty {
                    Text(engine.interimText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }
        case .transcribing:
            Text("Turning your words into text.")
                .font(.subheadline)
                .foregroundStyle(Theme.busy)
        case .error(let message):
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Theme.recording)
                .multilineTextAlignment(.center)
        }
    }

    private func toggle() {
        if engine.isRecording {
            engine.finishDictation()
        } else {
            engine.beginDictation()
        }
    }
}

#Preview {
    TryItView()
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
}
