package com.hammaad.voiceappv4.data

enum class TranscriptionMode { SMART, VERBATIM }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val bubbleEnabled: Boolean = false,
    val mode: TranscriptionMode = TranscriptionMode.SMART,
    val languageCode: String = "",
    val customVocabulary: List<String> = emptyList(),
    val bubbleSizeDp: Int = 58,
    val bubbleOpacity: Float = 0.96f,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val haptics: Boolean = true,
    val snoozeMinutes: Int = 30,
    val snoozedUntil: Long = 0L,
    val excludedPackages: Set<String> = emptySet(),
    val historyEnabled: Boolean = false,
    val diagnosticsEnabled: Boolean = true,
)

data class TranscriptEntry(val text: String, val timestamp: Long, val appPackage: String)
