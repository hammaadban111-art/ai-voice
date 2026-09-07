import SwiftUI
import UIKit

/// The keyboard extension — the iOS answer to Android's floating bubble.
///
/// Android can draw a bubble over any app and type into it through an
/// accessibility service. iOS allows neither, and the sanctioned way to put
/// text into someone else's text field is a keyboard extension: it appears in
/// every app, it sees the text around the cursor, and `insertText` writes at the
/// caret. So the bubble becomes a mic key, and everything behind it — the
/// engine, the models, the settings — is the same code the app runs.
final class KeyboardViewController: UIInputViewController {

    private let settings = SettingsStore.shared
    private lazy var engine = DictationEngine(settingsStore: settings) { [weak self] in
        self?.currentContext() ?? .none
    }
    private var host: UIHostingController<KeyboardBarView>?

    override func viewDidLoad() {
        super.viewDidLoad()

        engine.onTranscript = { [weak self] text, _ in
            self?.insert(text)
        }

        let bar = KeyboardBarView(
            engine: engine,
            settings: settings,
            hasFullAccess: hasFullAccess,
            needsGlobe: needsInputModeSwitchKey,
            isSensitiveField: isSensitiveField,
            onGlobe: { [weak self] in self?.advanceToNextInputMode() },
            onDelete: { [weak self] in self?.textDocumentProxy.deleteBackward() },
            onSpace: { [weak self] in self?.textDocumentProxy.insertText(" ") },
            onReturn: { [weak self] in self?.textDocumentProxy.insertText("\n") },
            onResume: { [weak self] in self?.settings.resume() }
        )

        let host = UIHostingController(rootView: bar)
        host.view.backgroundColor = .clear
        addChild(host)
        view.addSubview(host.view)
        host.didMove(toParent: self)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            // Tall enough for the mic key and the row under it; iOS gives the
            // extension whatever height it asks for.
            view.heightAnchor.constraint(equalToConstant: 268),
        ])
        self.host = host
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // The extension is suspended rather than killed between appearances, so
        // settings changed in the app since last time have to be picked up here.
        settings.reload()
        Diagnostics.recordKeyboardState(fullAccess: hasFullAccess)
        refreshBar()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // Going away mid-recording means the audio has nowhere to land.
        engine.cancelDictation()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        refreshBar()
    }

    // -- text --------------------------------------------------------------

    private func insert(_ text: String) {
        // The field can turn into a password box while the audio is in flight.
        // Never lose what the user just said: it goes on the clipboard instead,
        // exactly as the Android build does when it has nowhere to type.
        guard !isSensitiveField else {
            UIPasteboard.general.string = text
            host?.rootView.notice = "Copied. Long-press the field and paste."
            return
        }
        let proxy = textDocumentProxy
        proxy.insertText(TextInsertion.spaced(text, after: proxy.documentContextBeforeInput))
        host?.rootView.notice = nil
    }

    private func currentContext() -> DictationContext {
        guard settings.settings.contextualDictation else { return .none }
        return DictationContext(
            // iOS does not tell an extension which app is hosting it, so this
            // stays nil — the surrounding text carries the tone anyway.
            appLabel: nil,
            textBeforeCursor: textDocumentProxy.documentContextBeforeInput,
            textAfterCursor: textDocumentProxy.documentContextAfterInput
        )
    }

    /// Password and PIN fields are off limits, matching the Android bubble.
    private var isSensitiveField: Bool {
        let proxy = textDocumentProxy
        // A proxy that does not report the trait is treated as an ordinary
        // field; iOS hides custom keyboards from real secure fields anyway.
        let secure = proxy.isSecureTextEntry ?? false
        let type: TextInsertion.UIKeyboardTypeLike
        switch proxy.keyboardType {
        case .some(.numberPad): type = .numberPad
        case .some(.phonePad): type = .phonePad
        case .some(.decimalPad): type = .decimalPad
        case .some(.asciiCapableNumberPad): type = .asciiCapableNumberPad
        default: type = .default
        }
        return TextInsertion.isSensitive(keyboardType: type, isSecure: secure)
    }

    private func refreshBar() {
        host?.rootView.hasFullAccess = hasFullAccess
        host?.rootView.isSensitiveField = isSensitiveField
        host?.rootView.needsGlobe = needsInputModeSwitchKey
    }
}
