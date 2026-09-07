import SwiftUI

/// "Set up dictation — three steps, then you're dictating anywhere."
///
/// Same shape as the Android setup screen: a numbered list of the things iOS
/// will not let the app grant itself, each with a button that opens the right
/// place, and a try-it step at the end that can be skipped.
@MainActor
struct OnboardingView: View {

    @EnvironmentObject private var settings: SettingsStore
    @State private var showKeySheet = false
    @State private var showTryIt = false
    @State private var micGranted = AudioRecorder.hasMicPermission()
    @State private var hasKey = ApiKeyStore.hasKey
    @Environment(\.scenePhase) private var scenePhase

    private var keyboardReady: Bool { Diagnostics.keyboardInstalled && Diagnostics.keyboardHasFullAccess }
    private var allDone: Bool { hasKey && micGranted && keyboardReady }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    header

                    step(
                        number: 1,
                        title: "Gemini API key",
                        detail: "Your key is encrypted with a device-bound key and never leaves this phone.",
                        done: hasKey,
                        action: hasKey ? "Replace key" : "Paste your key"
                    ) { showKeySheet = true }

                    step(
                        number: 2,
                        title: "Allow microphone",
                        detail: "Needed to record your voice. Only the audio you dictate is sent to Gemini.",
                        done: micGranted,
                        action: "Allow microphone"
                    ) {
                        Task {
                            micGranted = await AudioRecorder.requestMicPermission()
                            if !micGranted { openSettings() }
                        }
                    }

                    step(
                        number: 3,
                        title: "Add the keyboard",
                        detail: "Settings › General › Keyboard › Keyboards › Add New Keyboard › Voice App V4. "
                            + "Then tap it again and turn on Allow Full Access, which is what lets it use the "
                            + "microphone and reach Gemini.",
                        done: keyboardReady,
                        action: "Open settings"
                    ) { openSettings() }

                    tryItCard

                    Button {
                        settings.update { $0.setupComplete = true }
                    } label: {
                        Text(allDone ? "Done" : "Finish setup")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(!hasKey)

                    if !hasKey {
                        Text("Add your Gemini API key to start transcribing.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(24)
            }
            .navigationTitle("Set up dictation")
            .navigationBarTitleDisplayMode(.large)
        }
        .sheet(isPresented: $showKeySheet) {
            ApiKeyView { hasKey = ApiKeyStore.hasKey }
        }
        .sheet(isPresented: $showTryIt) {
            TryItView()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refresh() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Voice App V4")
                .font(.largeTitle.bold())
            Text("Three steps, then you're dictating anywhere.")
                .foregroundStyle(.secondary)
        }
    }

    private var tryItCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Try it")
                .font(.headline)
            Text("Open any messaging app, tap the message box, switch to the Voice App V4 keyboard, "
                 + "say a sentence, then tap Done. You can skip this and do it later.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Try it here instead") { showTryIt = true }
                .buttonStyle(.bordered)
                .disabled(!hasKey || !micGranted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder
    private func step(
        number: Int,
        title: String,
        detail: String,
        done: Bool,
        action: String,
        perform: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Text("\(number).")
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(.secondary)
                Text(title)
                    .font(.headline)
                Spacer()
                Text(done ? "Granted" : "Needed")
                    .font(.subheadline.bold())
                    .foregroundStyle(done ? Theme.statusOK : Theme.statusPending)
            }
            Text(detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button(action, action: perform)
                .buttonStyle(.bordered)
        }
    }

    private func refresh() {
        micGranted = AudioRecorder.hasMicPermission()
        hasKey = ApiKeyStore.hasKey
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

#Preview {
    OnboardingView()
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
}
