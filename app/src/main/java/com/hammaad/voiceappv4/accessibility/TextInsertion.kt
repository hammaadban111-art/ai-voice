package com.hammaad.voiceappv4.accessibility

data class InsertionResult(val text: String, val cursor: Int)

object TextInsertion {
    fun merge(existing: String, start: Int, end: Int, transcript: String): InsertionResult {
        val clean = transcript.trim()
        val leftIndex = start.coerceIn(0, existing.length)
        val rightIndex = end.coerceIn(leftIndex, existing.length)
        val before = existing.substring(0, leftIndex)
        val after = existing.substring(rightIndex)
        val prefix = if (before.isNotEmpty() && !before.last().isWhitespace() && clean.isNotEmpty() && !isPunctuation(clean.first())) " " else ""
        val suffix = if (after.isNotEmpty() && !after.first().isWhitespace() && clean.isNotEmpty() && !isPunctuation(after.first())) " " else ""
        val inserted = prefix + clean + suffix
        return InsertionResult(before + inserted + after, before.length + inserted.length)
    }

    private fun isPunctuation(char: Char) = char in ".,!?;:)]}"
}
