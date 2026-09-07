import Foundation

/// Decides exactly what characters go into the field.
///
/// Ported from the Android `TextInjector.spacedInsertion`: add the space a
/// human would have typed, and no more. Kept separate from the keyboard so the
/// rule can be reasoned about (and tested) without a text field.
enum TextInsertion {

    /// - Parameter before: the text immediately preceding the cursor.
    /// - Returns: `text`, prefixed with a space when one belongs there.
    static func spaced(_ text: String, after before: String?) -> String {
        guard let before, let last = before.last else { return text }
        if last.isWhitespace { return text }
        if let first = text.first, ".,!?;:)]}'\"".contains(first) { return text }
        return " " + text
    }

    /// Whether this field is one dictation should stay out of.
    ///
    /// Android checks the accessibility node's password flag; a keyboard
    /// extension gets the same signal from the text input traits, and iOS also
    /// hides secure fields from custom keyboards outright.
    static func isSensitive(keyboardType: UIKeyboardTypeLike, isSecure: Bool) -> Bool {
        if isSecure { return true }
        switch keyboardType {
        case .numberPad, .phonePad, .asciiCapableNumberPad, .decimalPad:
            // PIN and card-number pads: nothing dictated belongs here.
            return true
        default:
            return false
        }
    }

    /// A tiny stand-in for `UIKeyboardType` so this file stays testable without
    /// UIKit and usable from both targets.
    enum UIKeyboardTypeLike {
        case `default`
        case numberPad
        case phonePad
        case decimalPad
        case asciiCapableNumberPad
        case emailAddress
        case url
        case other
    }
}
