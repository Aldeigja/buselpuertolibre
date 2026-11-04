package es.elpuertobus.app.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StopScheduleTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val lineRules =
        mapOf(
            6 to """{"saturday":false,"sunday_festivo":false}""",
            7 to """{"sunday_festivo":false}""",
        )

    @Test
    fun saturdayHolidayUsesFestivos() {
        val holiday = LocalDate.of(2026, 12, 8)
        val profile = StopSchedule.serviceProfile(holiday, setOf(holiday))
        assertEquals(ServiceProfile.FESTIVOS, profile)
    }

    @Test
    fun line6ClosedOnSaturday() {
        val saturday = LocalDate.of(2026, 11, 7)
        val profile = StopSchedule.serviceProfile(saturday, emptySet())
        assertEquals(ServiceProfile.SABADOS, profile)
        assertTrue(StopSchedule.isLineClosed(6, profile, lineRules))
    }

    @Test
    fun line7ClosedOnSunday() {
        val sunday = LocalDate.of(2026, 11, 8)
        val profile = StopSchedule.serviceProfile(sunday, emptySet())
        assertEquals(ServiceProfile.FESTIVOS, profile)
        assertTrue(StopSchedule.isLineClosed(7, profile, lineRules))
    }

    @Test
    fun nextOmitsPassedTimeButFullKeepsIt() {
        val stopId = "L3_outbound_001"
        val departures =
            listOf(
                DepartureSnapshot(3, "laborables", 7 * 3600 + 20 * 60, stopId),
                DepartureSnapshot(3, "laborables", 8 * 3600, stopId),
            )
        val now = ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone)
        val result =
            StopSchedule.build(
                StopScheduleInput(
                    stopId = stopId,
                    line = 3,
                    stopName = "Terminal Marítima",
                    departures = departures,
                    holidayDates = emptySet(),
                    lineRulesJson = lineRules,
                    zoneId = zone,
                    now = now,
                ),
            )
        assertFalse(result.lineClosedToday)
        assertEquals(listOf(8 * 3600), result.nextDepartures)
        assertEquals(listOf(7 * 3600 + 20 * 60, 8 * 3600), result.fullTimetable)
    }

    @Test
    fun stopWithNoDepartureIsEmpty() {
        val now = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, zone)
        val result =
            StopSchedule.build(
                StopScheduleInput(
                    stopId = "L1_outbound_010",
                    line = 1,
                    stopName = "Between timepoints",
                    departures = emptyList(),
                    holidayDates = emptySet(),
                    lineRulesJson = lineRules,
                    zoneId = zone,
                    now = now,
                ),
            )
        assertTrue(result.fullTimetable.isEmpty())
        assertTrue(result.nextDepartures.isEmpty())
    }
}
