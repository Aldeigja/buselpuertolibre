package es.elpuertobus.app.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DepartureBoardTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val rules =
        mapOf(
            6 to """{"saturday":false,"sunday_festivo":false}""",
            7 to """{"sunday_festivo":false}""",
        )

    @Test
    fun unscheduledStopInterleavesUpstreamClocksByTime() {
        val now = ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone)
        val result =
            DepartureBoard.build(
                input(
                    selected = "L1_outbound_002_99",
                    stops =
                        listOf(
                            stop("L1_outbound_001_10", 1, 1, "Centro Comercial El Paseo"),
                            stop("L1_outbound_002_99", 1, 2, "Madrileños"),
                            stop("L1_outbound_003_11", 1, 3, "Escultor Antonio Acuña"),
                            stop("L4_outbound_001_20", 4, 1, "San Bartolomé"),
                            stop("L4_outbound_002_21", 4, 2, "Other"),
                            stop("L4_outbound_003_99", 4, 3, "Madrileños"),
                            stop("L4_outbound_004_22", 4, 4, "Valdés"),
                            stop("L5_outbound_001_77", 5, 1, "Madrileños"),
                        ),
                    departures =
                        listOf(
                            DepartureSnapshot(1, "laborables", clock(7, 20), "L1_outbound_001_10"),
                            DepartureSnapshot(1, "laborables", clock(8, 0), "L1_outbound_001_10"),
                            DepartureSnapshot(4, "laborables", clock(7, 10), "L4_outbound_001_20"),
                            DepartureSnapshot(4, "laborables", clock(7, 50), "L4_outbound_001_20"),
                            DepartureSnapshot(5, "laborables", clock(7, 15), "L5_outbound_001_77"),
                        ),
                    now = now,
                ),
            )
        assertEquals("Madrileños", result.stopName)
        assertFalse(result.hasPrintedTimetable)
        assertTrue(result.timetable.isEmpty())
        assertEquals(listOf(4, 1, 4, 1), result.trips.map { it.line })
        assertEquals(listOf(clock(7, 10), clock(7, 20), clock(7, 50), clock(8, 0)), result.trips.map { it.scheduledSeconds })
        val line1 = result.trips.first { it.line == 1 }
        assertEquals("Centro Comercial El Paseo", line1.upstreamStopName)
        assertEquals(1, line1.stopsFromAnchor)
        assertFalse(line1.downstream)
        assertEquals("Escultor Antonio Acuña", line1.destination)
        assertTrue(line1.isPast)
        assertEquals(10, line1.elapsedMinutes)
        assertTrue(DepartureBoard.showLeftAgo(line1))
        val line4 = result.trips.first { it.line == 4 }
        assertEquals(2, line4.stopsFromAnchor)
        assertEquals("San Bartolomé", line4.upstreamStopName)
        assertEquals("Valdés", line4.destination)
    }

    @Test
    fun stopBeforeTheFirstTimepointUsesTheNextPrintedStop() {
        val result =
            DepartureBoard.build(
                input(
                    selected = "L3_outbound_001_5",
                    stops =
                        listOf(
                            stop("L3_outbound_001_5", 3, 1, "Before"),
                            stop("L3_outbound_002_6", 3, 2, "Terminal Marítima"),
                            stop("L3_outbound_003_7", 3, 3, "Valdelagrana"),
                        ),
                    departures = listOf(DepartureSnapshot(3, "laborables", clock(7, 20), "L3_outbound_002_6")),
                    now = ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, zone),
                ),
            )
        val trip = result.trips.single()
        assertTrue(trip.downstream)
        assertEquals(1, trip.stopsFromAnchor)
        assertEquals("Terminal Marítima", trip.upstreamStopName)
        assertEquals("Valdelagrana", trip.destination)
    }

    @Test
    fun printedStopUsesItsOwnClockAndShowsTheTimetable() {
        val result =
            DepartureBoard.build(
                input(
                    selected = "L3_outbound_002_6",
                    stops =
                        listOf(
                            stop("L3_outbound_001_5", 3, 1, "Before"),
                            stop("L3_outbound_002_6", 3, 2, "Terminal Marítima"),
                            stop("L3_outbound_003_7", 3, 3, "Valdelagrana"),
                        ),
                    departures =
                        listOf(
                            DepartureSnapshot(3, "laborables", clock(7, 20), "L3_outbound_002_6"),
                            DepartureSnapshot(3, "laborables", clock(8, 0), "L3_outbound_002_6"),
                            DepartureSnapshot(3, "sabados", clock(9, 0), "L3_outbound_002_6"),
                        ),
                    now = ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone),
                ),
            )
        assertTrue(result.hasPrintedTimetable)
        assertTrue(result.trips.all { it.stopsFromAnchor == 0 && !it.downstream })
        assertEquals("Terminal Marítima", result.trips.first().upstreamStopName)
        assertEquals(listOf(clock(7, 20), clock(8, 0)), result.timetable.single().times)
        assertEquals(listOf(clock(8, 0)), result.trips.map { it.scheduledSeconds })
        assertFalse(result.trips.single().isPast)
    }

    @Test
    fun ownClockHidesAfterFiveMinutesAndUpstreamKeepsTwenty() {
        val stops =
            listOf(
                stop("L4_outbound_001_16", 4, 1, "Plaza de España - Santa Lucía"),
                stop("L4_outbound_002_40", 4, 2, "San Bartolomé"),
                stop("L1_outbound_001_10", 1, 1, "Centro"),
                stop("L1_outbound_002_99", 1, 2, "Madrileños"),
            )
        val departures =
            listOf(
                DepartureSnapshot(4, "laborables", clock(11, 35), "L4_outbound_001_16"),
                DepartureSnapshot(4, "laborables", clock(11, 55), "L4_outbound_001_16"),
                DepartureSnapshot(1, "laborables", clock(11, 35), "L1_outbound_001_10"),
            )
        val atFiveMinutes =
            DepartureBoard.build(
                input(
                    selected = "L4_outbound_001_16",
                    stops = stops,
                    departures = departures,
                    now = ZonedDateTime.of(2026, 11, 5, 11, 40, 0, 0, zone),
                ),
            )
        assertEquals(listOf(clock(11, 35), clock(11, 55)), atFiveMinutes.trips.map { it.scheduledSeconds })
        assertTrue(atFiveMinutes.trips.first().isPast)

        val atFifteenMinutes =
            DepartureBoard.build(
                input(
                    selected = "L4_outbound_001_16",
                    stops = stops,
                    departures = departures,
                    now = ZonedDateTime.of(2026, 11, 5, 11, 50, 0, 0, zone),
                ),
            )
        assertEquals(listOf(clock(11, 55)), atFifteenMinutes.trips.map { it.scheduledSeconds })
        assertEquals(listOf(clock(11, 35), clock(11, 55)), atFifteenMinutes.timetable.single().times)

        val upstream =
            DepartureBoard.build(
                input(
                    selected = "L1_outbound_002_99",
                    stops = stops,
                    departures = departures,
                    now = ZonedDateTime.of(2026, 11, 5, 11, 50, 0, 0, zone),
                ),
            )
        assertEquals(listOf(clock(11, 35)), upstream.trips.map { it.scheduledSeconds })
        assertEquals(15, upstream.trips.single().elapsedMinutes)
    }

    @Test
    fun clocksOlderThanTwentyMinutesAreLeftOut() {
        val result =
            DepartureBoard.build(
                input(
                    selected = "L1_outbound_002_99",
                    stops =
                        listOf(
                            stop("L1_outbound_001_10", 1, 1, "Centro"),
                            stop("L1_outbound_002_99", 1, 2, "Madrileños"),
                        ),
                    departures =
                        listOf(
                            DepartureSnapshot(1, "laborables", clock(7, 0), "L1_outbound_001_10"),
                            DepartureSnapshot(1, "laborables", clock(7, 15), "L1_outbound_001_10"),
                        ),
                    now = ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone),
                ),
            )
        assertEquals(listOf(clock(7, 15)), result.trips.map { it.scheduledSeconds })
    }

    @Test
    fun aClosedLineContributesNoTrips() {
        val result =
            DepartureBoard.build(
                input(
                    selected = "L6_outbound_002_99",
                    stops =
                        listOf(
                            stop("L6_outbound_001_10", 6, 1, "Plaza de Toros"),
                            stop("L6_outbound_002_99", 6, 2, "Hospital"),
                        ),
                    departures = listOf(DepartureSnapshot(6, "sabados", clock(9, 0), "L6_outbound_001_10")),
                    now = ZonedDateTime.of(2026, 11, 7, 8, 0, 0, 0, zone),
                ),
            )
        assertTrue(result.trips.isEmpty())
    }

    @Test
    fun aDetourIsNotTheUpstreamStopOfTheRoute() {
        val result =
            DepartureBoard.build(
                input(
                    selected = "L6_outbound_002_30",
                    stops =
                        listOf(
                            stop("L6_detour_11125177414", 6, 0, "Cantarranas"),
                            stop("L6_outbound_001_10", 6, 1, "Plaza de Toros"),
                            stop("L6_outbound_002_30", 6, 2, "Hospital"),
                        ),
                    departures =
                        listOf(
                            DepartureSnapshot(6, "laborables", clock(7, 30), "L6_detour_11125177414"),
                            DepartureSnapshot(6, "laborables", clock(7, 40), "L6_outbound_001_10"),
                        ),
                    now = ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, zone),
                ),
            )
        val trip = result.trips.single()
        assertEquals("Plaza de Toros", trip.upstreamStopName)
        assertEquals(1, trip.stopsFromAnchor)
        assertEquals(clock(7, 40), trip.scheduledSeconds)
    }

    @Test
    fun line3ApproximateColumnsStayLabeled() {
        val now = ZonedDateTime.of(2026, 11, 4, 11, 45, 0, 0, zone)
        val stops =
            listOf(
                stop("L3_outbound_001_10", 3, 1, "Plaza del Polvorista"),
                stop("L3_outbound_002_20", 3, 2, "Av. Eduardo y Felipe Osborne - Goya"),
                stop("L3_outbound_003_30", 3, 3, "Áncora - El Ancla"),
                stop("L3_outbound_004_40", 3, 4, "Fuentebravía"),
                stop("L3_outbound_005_50", 3, 5, "Poblado Naval"),
                stop("L3_outbound_006_60", 3, 6, "Colegio Público de Valdelagrana"),
            )
        val departures =
            listOf(
                DepartureSnapshot(3, "laborables", clock(11, 35), "L3_outbound_001_10", "PZA.POLVORISTA"),
                DepartureSnapshot(3, "laborables", clock(11, 40), "L3_outbound_003_30", "ANCORA"),
                DepartureSnapshot(3, "laborables", clock(15, 10), "L3_outbound_004_40", "FUENTEBRAVIA"),
                DepartureSnapshot(3, "laborables", clock(7, 35), "L3_outbound_006_60", "VALDELAGRANA"),
            )
        val atNaval =
            DepartureBoard.build(
                input(selected = "L3_outbound_005_50", stops = stops, departures = departures, now = now),
            )
        val fuentebravia = atNaval.trips.single()
        assertEquals("Fuentebravía", fuentebravia.upstreamStopName)
        assertTrue(fuentebravia.approximate)

        val atOsborne =
            DepartureBoard.build(
                input(selected = "L3_outbound_002_20", stops = stops, departures = departures, now = now),
            )
        val polvorista = atOsborne.trips.single()
        assertEquals("Plaza del Polvorista", polvorista.upstreamStopName)
        assertFalse(polvorista.approximate)

        assertTrue(timetableAt("L3_outbound_003_30", stops, departures, now).approximate)
        assertTrue(timetableAt("L3_outbound_006_60", stops, departures, now).approximate)
        assertFalse(timetableAt("L3_outbound_001_10", stops, departures, now).approximate)

        val line6 =
            DepartureBoard.build(
                input(
                    selected = "L6_outbound_001_70",
                    stops = listOf(stop("L6_outbound_001_70", 6, 1, "Fuentebravía")),
                    departures =
                        listOf(
                            DepartureSnapshot(6, "laborables", clock(11, 45), "L6_outbound_001_70", "FUENTEBRAVÍA"),
                        ),
                    now = now,
                ),
            )
        assertFalse(line6.trips.single().approximate)
        assertFalse(line6.timetable.single().approximate)
    }

    private fun input(
        selected: String,
        stops: List<ItineraryStopRef>,
        departures: List<DepartureSnapshot>,
        now: ZonedDateTime,
    ): DepartureBoardInput =
        DepartureBoardInput(
            selectedStopId = selected,
            stops = stops,
            departures = departures,
            lineColours = mapOf(1 to "#14611d", 4 to "#ff0000", 5 to "#06e1fe"),
            holidayDates = emptySet<LocalDate>(),
            lineRulesJson = rules,
            zoneId = zone,
            now = now,
        )

    private fun timetableAt(
        selected: String,
        stops: List<ItineraryStopRef>,
        departures: List<DepartureSnapshot>,
        now: ZonedDateTime,
    ): PrintedTimetableLine =
        DepartureBoard.build(
            input(selected = selected, stops = stops, departures = departures, now = now),
        ).timetable.single()

    private fun stop(
        id: String,
        line: Int,
        sequence: Int,
        name: String,
    ): ItineraryStopRef =
        ItineraryStopRef(
            stopId = id,
            line = line,
            columnSide = "left",
            sequence = sequence,
            stopName = name,
        )

    private fun clock(hour: Int, minute: Int): Int = hour * 3600 + minute * 60
}
