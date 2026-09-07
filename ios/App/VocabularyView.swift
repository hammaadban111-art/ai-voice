import SwiftUI

/// "Names, jargon and acronyms Gemini should recognise."
struct VocabularyView: View {

    @EnvironmentObject private var settings: SettingsStore
    @State private var draft = ""

    var body: some View {
        List {
            Section {
                HStack {
                    TextField("Add a name or acronym", text: $draft)
                        .autocorrectionDisabled()
                        .onSubmit(add)
                    Button("Add", action: add)
                        .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } footer: {
                Text("Names, jargon and acronyms Gemini should recognise. Spelling them here is what keeps "
                     + "them from being written phonetically.")
            }

            if settings.settings.customVocabulary.isEmpty {
                Section { Text("Nothing saved yet.").foregroundStyle(.secondary) }
            } else {
                Section("Custom vocabulary terms") {
                    ForEach(settings.settings.customVocabulary, id: \.self) { term in
                        Text(term)
                    }
                    .onDelete { offsets in
                        settings.update { $0.customVocabulary.remove(atOffsets: offsets) }
                    }
                }
            }
        }
        .navigationTitle("Custom vocabulary")
        .toolbar { EditButton() }
    }

    private func add() {
        let term = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return }
        settings.update { s in
            guard !s.customVocabulary.contains(term) else { return }
            s.customVocabulary.append(term)
        }
        draft = ""
    }
}

#Preview {
    NavigationStack { VocabularyView() }
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
}
