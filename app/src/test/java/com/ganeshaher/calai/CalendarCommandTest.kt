package com.ganeshaher.calai

import com.ganeshaher.calai.data.DemoCommandParser
import com.ganeshaher.calai.domain.Action
import com.ganeshaher.calai.domain.formatLocal
import com.ganeshaher.calai.domain.parseApi
import com.ganeshaher.calai.domain.parseLocal
import com.ganeshaher.calai.domain.startOfDay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarCommandTest {
    private val parser = DemoCommandParser()

    @Test
    fun demoCreateExtractsTomorrowAndReminder() = runBlocking {
        val command = parser.parse("Schedule Dentist tomorrow at 4 PM and remind me 30 minutes before")

        assertEquals(Action.CREATE, command.action)
        assertEquals("Dentist", command.title)
        assertEquals(30, command.reminder)
        assertEquals(
            formatLocal(startOfDay(1)).substringBefore(' ') + " 16:00",
            formatLocal(requireNotNull(command.start))
        )
    }

    @Test
    fun demoMoveKeepsSearchTitleSeparateFromNewTime() = runBlocking {
        val command = parser.parse("Move Dentist to tomorrow at 5 PM")
        assertEquals(Action.MOVE, command.action)
        assertEquals("Dentist", command.title)
    }

    @Test
    fun invalidTimesDoNotRollOver() = runBlocking {
        assertEquals(Action.CLARIFY, parser.parse("Schedule Dentist tomorrow at 4:99 PM").action)
        assertNull(parseLocal("2026-02-30 12:00"))
        assertNull(parseApi("2026-11-25"))
    }

    @Test
    fun apiTimestampsRespectOffsetsAndFractionalSeconds() {
        assertEquals(
            parseApi("2026-11-25T10:30:00.100Z"),
            parseApi("2026-11-25T16:00:00.1+05:30")
        )
    }
}
