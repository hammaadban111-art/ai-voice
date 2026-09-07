import SwiftUI

struct RootView: View {
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        if settings.settings.setupComplete {
            HomeView()
        } else {
            OnboardingView()
        }
    }
}

#Preview {
    RootView()
        .environmentObject(SettingsStore(defaults: UserDefaults(suiteName: "preview")!))
        .environmentObject(TranscriptStore(url: nil))
}
