import SwiftUI

/// Saved transcripts, with the same actions as the Android list: copy, delete,
/// clear everything.
struct HistoryView: View {

    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var history: TranscriptStore
    @State private var confirmClear = false

    var body: some View {
        List {
            if !settings.settings.historyEnabled {
                Section {
                    Text("History is off. New transcriptions will not be saved.")
                        .foregroundStyle(.secondary)
                }
            }

            if history.entries.isEmpty {
                Section { Text("Nothing saved yet.").foregroundStyle(.secondary) }
            } else {
                ForEach(history.entries) { entry in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(entry.text)
                        HStack(spacing: 6) {
                            Text(entry.createdAt, format: .dateTime.day().month().hour().minute())
                            if let app = entry.appLabel, !app.isEmpty {
                                Text("· \(app)")
                            }
                            if entry.viaFallback {
                                Text("· fallback model")
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            history.delete(entry)
                        } label: {
                            Label("Delete transcript", systemImage: "trash")
                        }
                    }
                    .contextMenu {
                        Button {
                            UIPasteboard.general.string = entry.text
                        } label: {
                            Label("Copy", systemImage: "doc.on.doc")
                        }
                        Button(role: .destructive) {
                            history.delete(entry)
                        } label: {
                            Label("Delete transcript", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .navigationTitle("History")
        .toolbar {
            if !history.entries.isEmpty {
                Button("Clear history", role: .destructive) { confirmClear = true }
            }
        }
        .confirmationDialog("Clear history", isPresented: $confirmClear) {
            Button("Clear history", role: .destructive) { history.clear() }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This deletes every saved transcript on this device.")
        }
        .onAppear { history.reload() }
    }
}

#Preview {
    NavigationStack { HistoryView() }
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
        .environmentObject(TranscriptStore(url: nil))
}
