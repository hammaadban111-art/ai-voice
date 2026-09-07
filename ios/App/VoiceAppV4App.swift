import SwiftUI

@main
struct VoiceAppV4App: App {

    @StateObject private var settings = SettingsStore.shared
    @StateObject private var history = TranscriptStore.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(history)
                .onChange(of: scenePhase) { _, phase in
                    // The keyboard extension writes to the same App Group while
                    // the app is backgrounded, so re-read on every return.
                    if phase == .active {
                        settings.reload()
                        history.reload()
                    }
                }
        }
    }
}
