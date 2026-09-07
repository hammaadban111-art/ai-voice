import SwiftUI

/// Where the Gemini API key is pasted, checked and stored.
@MainActor
struct ApiKeyView: View {

    enum CheckState: Equatable {
        case idle
        case checking
        case ok(String)
        case failed(String)
    }

    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var key: String = ""
    @State private var reveal = false
    @State private var check: CheckState = .idle
    @State private var stored = ApiKeyStore.hasKey

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        if reveal {
                            TextField("AIza…", text: $key)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                        } else {
                            SecureField("AIza…", text: $key)
                        }
                        Button(reveal ? "Hide key" : "Show key") { reveal.toggle() }
                            .font(.footnote)
                            .buttonStyle(.borderless)
                    }
                    Button("Paste") {
                        if let pasted = UIPasteboard.general.string {
                            key = pasted.trimmingCharacters(in: .whitespacesAndNewlines)
                        }
                    }
                } header: {
                    Text("Gemini API key")
                } footer: {
                    Text("Your key is encrypted with a device-bound key and never leaves this phone. "
                         + "Get one from Google AI Studio.")
                }

                Section {
                    Button("Test connection") { test() }
                        .disabled(key.isEmpty || check == .checking)

                    switch check {
                    case .idle:
                        EmptyView()
                    case .checking:
                        HStack(spacing: 8) {
                            ProgressView()
                            Text("Checking your key").foregroundStyle(.secondary)
                        }
                    case .ok(let model):
                        Label("Key works. Reached \(model).", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(Theme.statusOK)
                    case .failed(let message):
                        Label(message, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(Theme.statusPending)
                    }
                }

                if stored {
                    Section {
                        Button("Remove stored key", role: .destructive) {
                            ApiKeyStore.clear()
                            stored = false
                            key = ""
                            check = .idle
                            onSave()
                        }
                    }
                }
            }
            .navigationTitle(stored ? "Replace key" : "Add key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save key") { save() }
                        .disabled(key.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() {
        guard ApiKeyStore.save(key) else {
            check = .failed("Failed to store API key")
            return
        }
        stored = true
        onSave()
        dismiss()
    }

    private func test() {
        let candidate = key.trimmingCharacters(in: .whitespacesAndNewlines)
        check = .checking
        Task {
            do {
                let model = try await GeminiClient(apiKey: candidate).validateKey()
                await MainActor.run { check = .ok(model) }
            } catch let error as DictationError {
                await MainActor.run { check = .failed(error.message) }
            } catch {
                await MainActor.run { check = .failed("API key not valid") }
            }
        }
    }
}

#Preview {
    ApiKeyView { }
}
