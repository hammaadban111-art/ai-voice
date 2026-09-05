package com.dictate.app.core

/**
 * One place that defines what "required setup" means, used by both
 * onboarding (to gate progression) and Home (to show live status), so
 * they can never disagree about whether the app is ready to dictate.
 * Every field is expected to be re-derived from live system/settings
 * state by the caller on each check — this class only aggregates them.
 */
data class SetupStatus(
    val microphoneGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val overlayGranted: Boolean,
    val apiKeyPresent: Boolean,
    val bubbleFeatureEnabled: Boolean,
) {
    /** Everything dictation actually needs. Test dictation is deliberately not part of this. */
    val requiredComplete: Boolean
        get() = microphoneGranted && accessibilityEnabled && overlayGranted && apiKeyPresent && bubbleFeatureEnabled
}
