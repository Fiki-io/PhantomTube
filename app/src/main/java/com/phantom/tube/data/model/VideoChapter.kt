package com.phantom.tube.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoChapter(
    val title: String,
    val timeSec: Float,
    val timeFormatted: String
)

object VideoChapterParser {
    private val CHAPTER_REGEX = Regex(
        """(?:^|\n)\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s*[-–—:|.]?\s*(.+)"""
    )

    fun parse(description: String): List<VideoChapter> {
        if (description.isBlank()) return emptyList()
        val chapters = mutableListOf<VideoChapter>()
        val matches = CHAPTER_REGEX.findAll(description)

        for (match in matches) {
            val hoursStr = match.groupValues[1]
            val minutesStr = match.groupValues[2]
            val secondsStr = match.groupValues[3]
            val rawTitle = match.groupValues[4].trim()

            val hours = if (hoursStr.isNotBlank()) hoursStr.toIntOrNull() ?: 0 else 0
            val minutes = minutesStr.toIntOrNull() ?: 0
            val seconds = secondsStr.toIntOrNull() ?: 0

            val totalSec = (hours * 3600 + minutes * 60 + seconds).toFloat()
            val timeFormatted = if (hours > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
            }

            // Bersihkan judul dari sisa karakter format
            val cleanTitle = rawTitle.replace(Regex("""^[•\-\–\—\s|:]+"""), "").trim()
            if (cleanTitle.isNotBlank()) {
                chapters.add(
                    VideoChapter(
                        title = cleanTitle,
                        timeSec = totalSec,
                        timeFormatted = timeFormatted
                    )
                )
            }
        }

        val sorted = chapters.sortedBy { it.timeSec }
        return if (sorted.size >= 2 && (sorted.firstOrNull()?.timeSec ?: 999f) <= 60f) {
            sorted
        } else {
            emptyList()
        }
    }
}

