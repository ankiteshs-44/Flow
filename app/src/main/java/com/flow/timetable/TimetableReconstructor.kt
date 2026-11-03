package com.flow.timetable

import kotlin.math.abs

data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

data class TimetableEntry(
    val day: String,
    val time: String,
    val subject: String
)

object TimetableReconstructor {
    private val weekdayNames = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    private val weekdayAliases = mapOf(
        "mon" to "Monday", "monday" to "Monday",
        "tue" to "Tuesday", "tues" to "Tuesday", "tuesday" to "Tuesday",
        "wed" to "Wednesday", "wednesday" to "Wednesday",
        "thu" to "Thursday", "thur" to "Thursday", "thurs" to "Thursday", "thursday" to "Thursday",
        "fri" to "Friday", "friday" to "Friday",
        "sat" to "Saturday", "saturday" to "Saturday",
        "sun" to "Sunday", "sunday" to "Sunday"
    )
    private val timePattern = Regex("""(?i)\b(?:[01]?\d|2[0-3])(?::[0-5]\d)?\s*(?:am|pm)?\b""")

    fun reconstruct(lines: List<OcrLine>): List<TimetableEntry> {
        if (lines.isEmpty()) return emptyList()

        val days = lines.mapNotNull { line ->
            weekdayAliases[normalize(line.text)]?.let { it to line }
        }
        val times = lines.mapNotNull { line ->
            timePattern.find(line.text)?.value?.let { normalizeTime(it) to line }
        }
        if (days.isEmpty() || times.isEmpty()) return emptyList()

        val dayRows = clusterByCoordinate(days, useY = true)
        val dayHeaderRow = dayRows.maxByOrNull { it.size }.orEmpty()
        val orderedDays = dayHeaderRow
            .distinctBy { it.first }
            .sortedBy { it.second.centerX }
        val timeRows = clusterByCoordinate(times, useY = true)
        val timesAlongRows = timeRows.size > 1 || dayRows.size == 1
        val orderedTimes = if (timesAlongRows) {
            timeRows.map { cluster ->
                cluster.sortedBy { it.second.centerX }.first()
            }.sortedBy { it.second.centerY }
        } else {
            times.distinctBy { it.first }.sortedBy { it.second.centerX }
        }
        if (orderedDays.isEmpty() || orderedTimes.isEmpty()) return emptyList()

        val knownLabels = (days.map { it.second } + times.map { it.second }).toSet()
        val entries = mutableMapOf<Pair<String, String>, MutableList<String>>()
        lines.filterNot { it in knownLabels }.forEach { subjectLine ->
            val day = if (timesAlongRows) {
                nearestWithinGrid(orderedDays, subjectLine.centerX) { it.second.centerX }
            } else {
                nearestWithinGrid(orderedDays, subjectLine.centerY) { it.second.centerY }
            } ?: return@forEach
            val time = if (timesAlongRows) {
                nearestWithinGrid(orderedTimes, subjectLine.centerY) { it.second.centerY }
            } else {
                nearestWithinGrid(orderedTimes, subjectLine.centerX) { it.second.centerX }
            } ?: return@forEach

            val label = subjectLine.text.trim()
            if (label.isNotEmpty()) entries.getOrPut(day.first to time.first) { mutableListOf() } += label
        }

        return entries.map { (slot, subjects) ->
            TimetableEntry(slot.first, slot.second, subjects.distinct().joinToString(" "))
        }.sortedWith(compareBy({ weekdayNames.indexOf(it.day) }, { timeSortValue(it.time) }))
    }

    private fun clusterByCoordinate(
        items: List<Pair<String, OcrLine>>,
        useY: Boolean
    ): List<List<Pair<String, OcrLine>>> {
        val sorted = items.sortedBy { if (useY) it.second.centerY else it.second.centerX }
        val clusters = mutableListOf<MutableList<Pair<String, OcrLine>>>()
        sorted.forEach { item ->
            val coordinate = if (useY) item.second.centerY else item.second.centerX
            val existing = clusters.lastOrNull()
            val center = existing?.map {
                if (useY) it.second.centerY else it.second.centerX
            }?.average()?.toFloat()
            if (existing == null || center == null ||
                abs(coordinate - center) > item.second.height.coerceAtLeast(12f) * 1.5f
            ) {
                clusters += mutableListOf(item)
            } else {
                existing += item
            }
        }
        return clusters
    }

    private fun <T> nearestWithinGrid(
        items: List<T>,
        coordinate: Float,
        position: (T) -> Float
    ): T? {
        val ordered = items.sortedBy(position)
        if (ordered.isEmpty()) return null
        val first = position(ordered.first())
        val last = position(ordered.last())
        val lowerBound = if (ordered.size == 1) Float.NEGATIVE_INFINITY
        else first - (position(ordered[1]) - first) / 2f
        val upperBound = if (ordered.size == 1) Float.POSITIVE_INFINITY
        else last + (last - position(ordered[ordered.lastIndex - 1])) / 2f
        if (coordinate < lowerBound || coordinate > upperBound) return null
        return ordered.minByOrNull { abs(position(it) - coordinate) }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z]"), "")

    private fun normalizeTime(value: String): String =
        value.lowercase().replace(Regex("\\s+"), "").replace("am", " AM").replace("pm", " PM")
            .trim().uppercase()

    private fun timeSortValue(value: String): Int {
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(AM|PM)?""").matchEntire(value)
            ?: return Int.MAX_VALUE
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val suffix = match.groupValues[3]
        if (suffix == "PM" && hour < 12) hour += 12
        if (suffix == "AM" && hour == 12) hour = 0
        return hour * 60 + minute
    }
}
