import SwiftUI

/// "Device and service status, useful when something misbehaves."
@MainActor
struct DiagnosticsView: View {

    @State private var checks: [Diagnostics.Check] = Diagnostics.all()
    @State private var connection: String?
    @State private var testing = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        List {
            Section {
                ForEach(checks) { check in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Image(systemName: check.ok ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                                .foregroundStyle(check.ok ? Theme.statusOK : Theme.statusPending)
                            Text(check.title).font(.headline)
                        }
                        Text(check.detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        if !check.ok, let url = check.settingsURL {
                            Button("Open settings") { UIApplication.shared.open(url) }
                                .font(.footnote)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("Device")
            }

            Section("Connection") {
                Button("Test connection") { test() }
                    .disabled(testing || !ApiKeyStore.hasKey)
                if testing {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Checking your key").foregroundStyle(.secondary)
                    }
                } else if let connection {
                    Text(connection).font(.footnote).foregroundStyle(.secondary)
                }
            }

            Section("Build") {
                LabeledContent("App version", value: SettingsView.version)
                LabeledContent("Live model", value: TranscriptionModel.live.modelName)
                LabeledContent("Fallback model", value: TranscriptionModel.batch.modelName)
                LabeledContent("Endpoint", value: GeminiClient.host)
            }
        }
        .navigationTitle("Diagnostics")
        .refreshable { checks = Diagnostics.all() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { checks = Diagnostics.all() }
        }
    }

    private func test() {
        guard let key = ApiKeyStore.load() else { return }
        testing = true
        connection = nil
        Task {
            do {
                let model = try await GeminiClient(apiKey: key).validateKey()
                await MainActor.run {
                    connection = "Key works. Reached \(model)."
                    testing = false
                }
            } catch let error as DictationError {
                await MainActor.run {
                    connection = error.message
                    testing = false
                }
            } catch {
                await MainActor.run {
                    connection = "Transcription failed."
                    testing = false
                }
            }
        }
    }
}

#Preview {
    NavigationStack { DiagnosticsView() }
}
