package es.elpuertobus.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.elpuertobus.app.schedule.DepartureSnapshot
import es.elpuertobus.app.schedule.StopSchedule
import es.elpuertobus.app.schedule.StopScheduleInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class TransitSnapshotOpensTest {
    @Test
    fun bundledSnapshotOpensAndServesAPrintedClock() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = TransitDatabase.open(context)
            try {
                val dao = database.transitDao()
                val stops = dao.locatedStops()
                assertTrue(stops.isNotEmpty())
                val holidays = dao.holidayDates().map(LocalDate::parse).toSet()
                assertTrue(holidays.isNotEmpty())
                val rules = dao.lineServiceRules().associate { it.line to it.rulesJson }
                val terminal = stops.first { it.line == 3 && it.stopName == "Terminal Marítima" }
                val departures = dao.departuresForStop(terminal.stopId)
                assertTrue(departures.isNotEmpty())
                val zone = ZoneId.of("Europe/Madrid")
                val result =
                    StopSchedule.build(
                        StopScheduleInput(
                            stopId = terminal.stopId,
                            line = terminal.line,
                            stopName = terminal.stopName,
                            departures =
                                departures.map { row ->
                                    DepartureSnapshot(
                                        line = row.line,
                                        serviceDay = row.serviceDay,
                                        secondsSinceMidnight = row.secondsSinceMidnight,
                                        boundStopId = row.boundStopId,
                                    )
                                },
                            holidayDates = holidays,
                            lineRulesJson = rules,
                            zoneId = zone,
                            now = ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, zone),
                        ),
                    )
                assertTrue(result.fullTimetable.contains(7 * 3600 + 20 * 60))
                assertEquals("07:20", StopSchedule.formatClock(7 * 3600 + 20 * 60))
            } finally {
                database.close()
            }
        }
    }
}
