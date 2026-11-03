package com.flow.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableReconstructorTest {
    @Test
    fun reconstructsSubjectsFromDayColumnsAndTimeRows() {
        val lines = listOf(
            line("Mon", 100, 20), line("Tue", 220, 20),
            line("9:00", 20, 100), line("10:00", 20, 200),
            line("Math", 100, 100), line("Biology", 220, 200)
        )

        val entries = TimetableReconstructor.reconstruct(lines)

        assertEquals(
            listOf(
                TimetableEntry("Monday", "9:00", "Math"),
                TimetableEntry("Tuesday", "10:00", "Biology")
            ),
            entries
        )
    }

    @Test
    fun reconstructsTransposedGridWithDaysOnRows() {
        val lines = listOf(
            line("Mon", 20, 100), line("Tue", 20, 200),
            line("9:00", 100, 20), line("10:00", 200, 20),
            line("Math", 100, 100), line("Biology", 200, 200)
        )

        val entries = TimetableReconstructor.reconstruct(lines)

        assertEquals(
            listOf(
                TimetableEntry("Monday", "9:00", "Math"),
                TimetableEntry("Tuesday", "10:00", "Biology")
            ),
            entries
        )
    }

    @Test
    fun keepsSingleTimeSlotTimetableInDayColumns() {
        val lines = listOf(
            line("Mon", 100, 20), line("Tue", 220, 20),
            line("9:00", 20, 100),
            line("Math", 100, 100), line("Biology", 220, 100)
        )

        val entries = TimetableReconstructor.reconstruct(lines)

        assertEquals(
            listOf(
                TimetableEntry("Monday", "9:00", "Math"),
                TimetableEntry("Tuesday", "9:00", "Biology")
            ),
            entries
        )
    }

    @Test
    fun returnsNoEntriesWithoutBothHeaders() {
        assertTrue(TimetableReconstructor.reconstruct(listOf(line("Math", 1, 1))).isEmpty())
    }

    @Test
    fun ignoresTextOutsideTheDetectedGridBoundaries() {
        val lines = listOf(
            line("Mon", 100, 20), line("Tue", 220, 20),
            line("9:00", 20, 100), line("10:00", 20, 200),
            line("Math", 100, 100), line("Biology", 220, 200),
            line("Footer text", 900, 600)
        )

        assertEquals(2, TimetableReconstructor.reconstruct(lines).size)
    }

    private fun line(text: String, x: Int, y: Int) =
        OcrLine(text, x.toFloat(), y.toFloat(), (x + 50).toFloat(), (y + 20).toFloat())
}
