package com.example.data.lyrics

import com.example.domain.model.LyricsLine
import com.example.domain.model.LyricsWord

object LrcParser {

    private val lineTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestampRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>\s*([^<]+)""")

    fun parse(rawLrc: String?, defaultDurationMs: Long = 36_000L): List<LyricsLine> {
        if (rawLrc.isNullOrBlank()) return emptyList()

        val parsed = mutableListOf<LyricsLine>()
        val lines = rawLrc.lines()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val timeMatches = lineTimestampRegex.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            val contentStart = timeMatches.last().range.last + 1
            val bodyWithWordTags = trimmed.substring(contentStart).trim()
            if (bodyWithWordTags.isBlank()) continue

            val words = parseEnhancedWords(bodyWithWordTags)
            val cleanText = if (words.isNotEmpty()) {
                words.joinToString(" ") { it.text }.trim()
            } else {
                bodyWithWordTags.replace(Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>"""), "").trim()
            }

            if (cleanText.isBlank()) continue

            val lang = detectLanguageScript(cleanText)
            for (match in timeMatches) {
                val timeMs = toMillis(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3]
                )
                parsed.add(
                    LyricsLine(
                        timeMs = timeMs,
                        text = cleanText,
                        words = words,
                        languageTag = lang
                    )
                )
            }
        }

        val sorted = parsed.sortedBy { it.timeMs }
        return sorted.mapIndexed { idx, line ->
            if (line.words.isNotEmpty()) {
                line
            } else {
                val nextLineTime = sorted.getOrNull(idx + 1)?.timeMs
                    ?: (line.timeMs + 4000L).coerceAtMost(defaultDurationMs.coerceAtLeast(line.timeMs + 2500L))
                val tokens = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.size <= 1) {
                    line
                } else {
                    val span = (nextLineTime - line.timeMs).coerceAtLeast(900L)
                    val perWord = span / tokens.size
                    val autoWords = tokens.mapIndexed { wIdx, token ->
                        LyricsWord(
                            text = token,
                            startMs = line.timeMs + (wIdx * perWord),
                            endMs = line.timeMs + ((wIdx + 1) * perWord)
                        )
                    }
                    line.copy(words = autoWords)
                }
            }
        }
    }

    private fun parseEnhancedWords(body: String): List<LyricsWord> {
        val matches = wordTimestampRegex.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, match ->
            val startMs = toMillis(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3]
            )
            val nextStart = matches.getOrNull(index + 1)?.let { next ->
                toMillis(next.groupValues[1], next.groupValues[2], next.groupValues[3])
            } ?: (startMs + 650L)
            LyricsWord(
                text = match.groupValues[4].trim(),
                startMs = startMs,
                endMs = nextStart.coerceAtLeast(startMs + 180L)
            )
        }
    }

    private fun toMillis(minStr: String, secStr: String, fracStr: String): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val fracMs = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return (minutes * 60_000L) + (seconds * 1_000L) + fracMs
    }

    private fun detectLanguageScript(text: String): String {
        return when {
            text.any { it in '\u0900'..'\u097F' } -> "hi"
            text.any { it in '\u0A00'..'\u0A7F' } -> "pa"
            else -> "en"
        }
    }

    fun serialize(lines: List<LyricsLine>): String {
        return lines.sortedBy { it.timeMs }.joinToString("\n") { line ->
            val totalMs = line.timeMs.coerceAtLeast(0L)
            val mins = (totalMs / 60_000L)
            val secs = (totalMs % 60_000L) / 1000L
            val centis = (totalMs % 1000L) / 10L
            String.format("[%02d:%02d.%02d]%s", mins, secs, centis, line.text)
        }
    }

    fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var active = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) {
                active = i
            } else {
                break
            }
        }
        return active
    }
}
