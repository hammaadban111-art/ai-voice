import SwiftUI

/// Every switch the Android build has, in the same order and with the same copy.
struct SettingsView: View {

    @EnvironmentObject private var settings: SettingsStore
    @State private var showKeySheet = false

    private var s: AppSettings { settings.settings }

    var body: some View {
        List {
            Section("Gemini") {
                Button {
                    showKeySheet = true
                } label: {
                    HStack {
                        Text("Gemini API key")
                        Spacer()
                        Text(ApiKeyStore.hasKey ? "Stored" : "Needed")
                            .foregroundStyle(ApiKeyStore.hasKey ? Theme.statusOK : Theme.statusPending)
                    }
                }

                Picker("Model", selection: binding(\.model)) {
                    ForEach(TranscriptionModel.allCases) { model in
                        Text(model.title).tag(model)
                    }
                }
                Text(s.model.detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("Dictation") {
                Picker("Style", selection: binding(\.style)) {
                    ForEach(DictationStyle.allCases) { style in
                        Text(style.title).tag(style)
                    }
                }
                Text(s.style.detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Toggle("Contextual dictation", isOn: binding(\.contextualDictation))
                Text("Matches the tone and language of the text already around your cursor.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                NavigationLink {
                    LanguagesView()
                } label: {
                    LabeledContent("Language", value: Language.summary(for: s.languageCodes))
                }

                NavigationLink {
                    VocabularyView()
                } label: {
                    LabeledContent(
                        "Custom vocabulary",
                        value: s.customVocabulary.isEmpty ? "None" : "\(s.customVocabulary.count) terms"
                    )
                }
            }

            Section("Mic key") {
                Toggle("Show the mic key", isOn: binding(\.bubbleEnabled))
                Text("Turn the mic key off to type on the keyboard without dictating.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Toggle("Hold to talk", isOn: binding(\.pushToTalk))
                Text("Tap it to record, or hold it to talk and release when done.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Picker("Position", selection: binding(\.bubbleOnRight)) {
                    Text("Left").tag(false)
                    Text("Right").tag(true)
                }
                .pickerStyle(.segmented)

                VStack(alignment: .leading) {
                    LabeledContent("Size", value: "\(Int(s.bubbleSizeDp)) pt")
                    Slider(value: binding(\.bubbleSizeDp), in: 40...84, step: 2)
                }

                VStack(alignment: .leading) {
                    LabeledContent("Opacity", value: "\(Int(s.bubbleOpacity * 100))%")
                    Slider(value: binding(\.bubbleOpacity), in: 0.3...1, step: 0.05)
                }
            }

            Section("History") {
                Toggle("Keep transcript history", isOn: binding(\.historyEnabled))
                NavigationLink("History") { HistoryView() }
            }

            Section("Troubleshooting") {
                Toggle("Show diagnostics", isOn: binding(\.diagnosticsEnabled))
                Text("Device and service status, useful when something misbehaves.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                NavigationLink("Diagnostics") { DiagnosticsView() }
            }

            Section {
                LabeledContent("Version", value: Self.version)
            }
        }
        .navigationTitle("Settings")
        .sheet(isPresented: $showKeySheet) { ApiKeyView { } }
    }

    private func binding<T>(_ path: WritableKeyPath<AppSettings, T>) -> Binding<T> {
        Binding(
            get: { settings.settings[keyPath: path] },
            set: { value in settings.update { $0[keyPath: path] = value } }
        )
    }

    static var version: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "4.1.3"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }
}

#Preview {
    NavigationStack { SettingsView() }
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
}
