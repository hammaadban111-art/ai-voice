import SwiftUI

/// The mic key — the iOS shape of the Android floating bubble.
///
/// Same states and same colours: blue idle, red recording (scaling with the mic
/// level), amber while transcribing, grey on error. Tap to start and stop, or
/// hold to talk when push-to-talk is on.
struct MicKeyView: View {

    let state: DictationEngine.State
    let pushToTalk: Bool
    var size: CGFloat = 56
    var opacity: Double = 1

    let onTap: () -> Void
    var onPressStart: () -> Void = {}
    var onPressEnd: () -> Void = {}

    @State private var pressing = false

    private var level: Float {
        if case .listening(let level) = state { return level }
        return 0
    }

    private var scale: CGFloat {
        // Matches the Android bubble: up to 35% larger at full volume.
        1 + CGFloat(min(max(level, 0), 1)) * 0.35
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(Theme.tint(for: state))
                .frame(width: size, height: size)
                .scaleEffect(scale)
                .animation(.easeOut(duration: 0.08), value: level)
                .shadow(radius: 6, y: 2)

            switch state {
            case .transcribing:
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
            case .listening:
                Image(systemName: "stop.fill")
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundStyle(.white)
            case .idle, .error:
                Image(systemName: "mic.fill")
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white)
            }
        }
        .opacity(opacity)
        .contentShape(Circle())
        .accessibilityLabel("Dictation microphone")
        .accessibilityHint(pushToTalk ? "Hold to talk" : "Tap to record, tap again to insert")
        .gesture(gesture)
    }

    /// One gesture covers both modes, because a `DragGesture` and a tap gesture
    /// on the same view fight over the touch: hold-to-talk starts on the press
    /// and ends on the release, tap-to-toggle fires once on release.
    private var gesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard pushToTalk, !pressing else { return }
                pressing = true
                onPressStart()
            }
            .onEnded { value in
                if pushToTalk {
                    pressing = false
                    onPressEnd()
                } else if hypot(value.translation.width, value.translation.height) < 20 {
                    // Same touch slop the Android bubble uses to tell a tap from
                    // a drag, so a finger that slides off does nothing.
                    onTap()
                }
            }
    }
}

#Preview {
    VStack(spacing: 30) {
        MicKeyView(state: .idle, pushToTalk: false, onTap: {})
        MicKeyView(state: .listening(level: 0.6), pushToTalk: false, onTap: {})
        MicKeyView(state: .transcribing, pushToTalk: false, onTap: {})
        MicKeyView(state: .error("Failed"), pushToTalk: false, onTap: {})
    }
    .padding()
}
