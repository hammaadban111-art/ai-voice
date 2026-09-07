import SwiftUI

/// Multi-select language list.
///
/// More than one can be on at once: Gemini is told which to expect and picks
/// per utterance, which is what keeps Urdu-with-English-technical-words intact
/// instead of forcing it into one script.
struct LanguagesView: View {

    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        List {
            Section {
                ForEach(Language.all) { language in
                    Button {
                        toggle(language.code)
                    } label: {
                        HStack {
                            Text(language.name)
                                .foregroundStyle(.primary)
                            Spacer()
                            if settings.settings.languageCodes.contains(language.code) {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Theme.idle)
                            }
                        }
                    }
                }
            } footer: {
                Text("Pick every language you dictate in. Hindi and Urdu share most of their vocabulary and "
                     + "differ mainly in script, so turning on only the one you want makes short phrases reliable.")
            }
        }
        .navigationTitle("Language")
    }

    private func toggle(_ code: String) {
        settings.update { s in
            if let index = s.languageCodes.firstIndex(of: code) {
                // Never end up with an empty list: something has to be expected.
                if s.languageCodes.count > 1 { s.languageCodes.remove(at: index) }
            } else {
                s.languageCodes.append(code)
            }
        }
    }
}

#Preview {
    NavigationStack { LanguagesView() }
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
}
