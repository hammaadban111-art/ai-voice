import SwiftUI

/// The app's main screen: status at a glance, a place to try dictation, and the
/// way in to settings, history and diagnostics.
///
/// Android puts the same information behind the notification and the bubble's
/// long-press menu; on iOS the container app is the only place it can live.
@MainActor
struct HomeView: View {

    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var history: TranscriptStore
    @Environment(\.scenePhase) private var scenePhase

    @State private var showTryIt = false
    @State private var showKeySheet = false
    @State private var micGranted = AudioRecorder.hasMicPermission()

    var body: some View {
        NavigationStack {
            List {
                statusSection
                dictationSection
                if !history.entries.isEmpty { recentSection }
                settingsSection
            }
            .navigationTitle("Voice App V4")
            .sheet(isPresented: $showTryIt) { TryItView() }
            .sheet(isPresented: $showKeySheet) { ApiKeyView { } }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active {
                    micGranted = AudioRecorder.hasMicPermission()
                    history.reload()
                }
            }
        }
    }

    // -- sections ----------------------------------------------------------

    private var statusSection: some View {
        Section {
            if !ApiKeyStore.hasKey {
                banner(
                    title: "Gemini API key needed",
                    detail: "Add your key in Settings to start dictating.",
                    action: "Paste your key"
                ) { showKeySheet = true }
            } else if !Diagnostics.keyboardInstalled {
                banner(
                    title: "Keyboard not added yet",
                    detail: "Add Voice App V4 in Settings › General › Keyboard › Keyboards.",
                    action: "Open settings"
                ) { openSettings() }
            } else if !Diagnostics.keyboardHasFullAccess {
                banner(
                    title: "Allow Full Access is off",
                    detail: "Without it the keyboard cannot use the microphone or reach Gemini.",
                    action: "Open settings"
                ) { openSettings() }
            } else if !micGranted {
                banner(
                    title: "Microphone permission",
                    detail: "Microphone permission is needed before you can dictate.",
                    action: "Allow microphone"
                ) {
                    Task { micGranted = await AudioRecorder.requestMicPermission() }
                }
            } else if settings.settings.isSnoozed {
                banner(
                    title: "Dictation is paused",
                    detail: settings.settings.snoozeDescription.map { "\($0). The mic key stays hidden until you resume." }
                        ?? "The mic key stays hidden until you resume.",
                    action: "Resume"
                ) { settings.resume() }
            } else {
                HStack(spacing: 10) {
                    Circle().fill(Theme.statusOK).frame(width: 10, height: 10)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Dictation is ready").font(.headline)
                        Text("Tap a text field in any app, switch to the Voice App V4 keyboard, and talk.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private var dictationSection: some View {
        Section("Dictation") {
            Button {
                showTryIt = true
            } label: {
                Label("Try dictation here", systemImage: "mic.circle.fill")
            }
            .disabled(!ApiKeyStore.hasKey)

            if settings.settings.isSnoozed {
                Button("Resume dictation") { settings.resume() }
            } else {
                Menu {
                    Button("15 minutes") { settings.snooze(minutes: 15) }
                    Button("30 minutes") { settings.snooze(minutes: 30) }
                    Button("1 hour") { settings.snooze(minutes: 60) }
                    Button("Until restart") { settings.snooze(minutes: nil) }
                } label: {
                    Label("Pause temporarily", systemImage: "pause.circle")
                }
            }
        }
    }

    private var recentSection: some View {
        Section("Recent") {
            ForEach(history.entries.prefix(3)) { entry in
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.text).lineLimit(2)
                    Text(entry.createdAt, style: .relative)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            NavigationLink("See all") { HistoryView() }
        }
    }

    private var settingsSection: some View {
        Section {
            NavigationLink { SettingsView() } label: {
                Label("Settings", systemImage: "gearshape")
            }
            NavigationLink { HistoryView() } label: {
                Label("History", systemImage: "clock.arrow.circlepath")
            }
            NavigationLink { DiagnosticsView() } label: {
                Label("Diagnostics", systemImage: "stethoscope")
            }
            NavigationLink { HowItWorksView() } label: {
                Label("How it works", systemImage: "questionmark.circle")
            }
        }
    }

    private func banner(
        title: String,
        detail: String,
        action: String,
        perform: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.headline)
            Text(detail).font(.subheadline).foregroundStyle(.secondary)
            Button(action, action: perform).buttonStyle(.borderedProminent)
        }
        .padding(.vertical, 6)
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

/// The Android build's "How it works" copy, adapted to the keyboard.
struct HowItWorksView: View {
    var body: some View {
        List {
            Section {
                Text("Tap a text field in any app and switch to the Voice App V4 keyboard. "
                     + "Tap the mic to record, or hold it to talk and release when done. "
                     + "Tap Done and the cleaned-up text lands at your cursor.")
            }
            Section("Privacy") {
                Text("The keyboard reads only the text immediately around your cursor, and only to match "
                     + "the tone of what you are writing. Nothing else about the screen is uploaded. "
                     + "Only the audio you dictate is sent to Google Gemini for transcription.")
                Text("Your API key is stored in the device keychain and never leaves this phone.")
                Text("The keyboard stays away from password and PIN fields.")
            }
        }
        .navigationTitle("How it works")
    }
}

#Preview {
    HomeView()
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
        .environmentObject(TranscriptStore(url: nil))
}
