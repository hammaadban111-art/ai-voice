import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// The three taps the dictation loop uses: start/stop, inserted, failed.
/// Matches the Android build's haptics so the two feel the same in the hand.
enum Haptics {

    static func tap() {
        #if canImport(UIKit)
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        #endif
    }

    static func success() {
        #if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        #endif
    }

    static func error() {
        #if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.error)
        #endif
    }
}
