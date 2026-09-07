import SwiftUI

/// What the keyboard actually shows: the mic key, whatever is being heard, and
/// the few keys a dictation keyboard still owes you (globe, delete, space,
/// return) so you are not stranded when a word comes out wrong.
@MainActor
struct KeyboardBarView: View {

    @ObservedObject var engine: DictationEngine
    @ObservedObject var settings: SettingsStore

    var hasFullAccess: Bool
    var needsGlobe: Bool
    var isSensitiveField: Bool
    var notice: String?

    let onGlobe: () -> Void
    let onDelete: () -> Void
    let onSpace: () -> Void
    let onReturn: () -> Void
    let onResume: () -> Void

    init(
        engine: DictationEngine,
        settings: SettingsStore,
        hasFullAccess: Bool,
        needsGlobe: Bool,
        isSensitiveField: Bool,
        notice: String? = nil,
        onGlobe: @escaping () -> Void,
        onDelete: @escaping () -> Void,
        onSpace: @escaping () -> Void,
        onReturn: @escaping () -> Void,
        onResume: @escaping () -> Void
    ) {
        self.engine = engine
        self.settings = settings
        self.hasFullAccess = hasFullAccess
        self.needsGlobe = needsGlobe
        self.isSensitiveField = isSensitiveField
        self.notice = notice
        self.onGlobe = onGlobe
        self.onDelete = onDelete
        self.onSpace = onSpace
        self.onReturn = onReturn
        self.onResume = onResume
    }

    private var s: AppSettings { settings.settings }

    var body: some View {
        VStack(spacing: 12) {
            statusLine
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)

            micRow

            keyRow
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
    }

    // -- the mic -----------------------------------------------------------

    @ViewBuilder
    private var micRow: some View {
        HStack {
            if s.bubbleOnRight { Spacer() }

            if canDictate {
                MicKeyView(
                    state: engine.state,
                    pushToTalk: s.pushToTalk,
                    size: CGFloat(s.bubbleSizeDp),
                    opacity: s.bubbleOpacity,
                    onTap: toggle,
                    onPressStart: { engine.beginDictation() },
                    onPressEnd: { engine.finishDictation() }
                )
            } else {
                blockedButton
            }

            if !s.bubbleOnRight { Spacer() }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity)
    }

    private var canDictate: Bool {
        hasFullAccess && s.bubbleEnabled && !s.isSnoozed && !isSensitiveField
    }

    private var blockedButton: some View {
        Circle()
            .fill(Theme.error.opacity(0.4))
            .frame(width: CGFloat(s.bubbleSizeDp), height: CGFloat(s.bubbleSizeDp))
            .overlay(
                Image(systemName: "mic.slash.fill")
                    .font(.system(size: CGFloat(s.bubbleSizeDp) * 0.4, weight: .semibold))
                    .foregroundStyle(.white)
            )
    }

    // -- what it is doing --------------------------------------------------

    @ViewBuilder
    private var statusLine: some View {
        if let notice {
            label(notice, color: Theme.busy)
        } else if !hasFullAccess {
            VStack(spacing: 4) {
                label("Allow Full Access is off", color: Theme.statusPending, bold: true)
                label("Open Settings › General › Keyboard › Keyboards › Voice App V4 and turn on Allow Full "
                      + "Access. Without it the keyboard cannot use the microphone or reach Gemini.",
                      color: .secondary)
            }
        } else if isSensitiveField {
            label("The keyboard stays away from password and PIN fields.", color: .secondary)
        } else if !ApiKeyStore.hasKey {
            label("Add your Gemini API key in Settings first.", color: Theme.statusPending)
        } else if s.isSnoozed {
            VStack(spacing: 6) {
                label(s.snoozeDescription.map { "Dictation is paused — \($0)." } ?? "Dictation is paused.",
                      color: .secondary)
                Button("Resume", action: onResume)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
            }
        } else if !s.bubbleEnabled {
            label("The mic key is off. Turn it on in Voice App V4.", color: .secondary)
        } else {
            switch engine.state {
            case .idle:
                label(s.pushToTalk ? "Hold the mic and talk." : "Tap the mic and talk.", color: .secondary)
            case .listening:
                VStack(spacing: 4) {
                    label("Recording…", color: Theme.recording, bold: true)
                    if !engine.interimText.isEmpty {
                        Text(engine.interimText)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                    }
                }
            case .transcribing:
                label("Turning your words into text.", color: Theme.busy)
            case .error(let message):
                label(message, color: Theme.recording)
            }
        }
    }

    private func label(_ text: String, color: Color, bold: Bool = false) -> some View {
        Text(text)
            .font(bold ? .footnote.bold() : .footnote)
            .foregroundStyle(color)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
    }

    // -- the rest of the keyboard -----------------------------------------

    private var keyRow: some View {
        HStack(spacing: 8) {
            if needsGlobe {
                key(system: "globe", action: onGlobe)
            }
            key(system: "delete.left", action: onDelete)
            Button(action: onSpace) {
                Text("space")
                    .frame(maxWidth: .infinity, minHeight: 42)
            }
            .buttonStyle(KeyStyle())
            key(system: "return", action: onReturn)
        }
        .padding(.horizontal, 12)
    }

    private func key(system: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: system)
                .frame(width: 46, height: 42)
        }
        .buttonStyle(KeyStyle())
    }

    private func toggle() {
        if engine.isRecording {
            engine.finishDictation()
        } else {
            engine.beginDictation()
        }
    }
}

/// Plain iOS key look, so the row does not fight the system keyboard it replaces.
private struct KeyStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body)
            .foregroundStyle(Color.primary)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.secondarySystemBackground))
                    .opacity(configuration.isPressed ? 0.5 : 1)
            )
    }
}
