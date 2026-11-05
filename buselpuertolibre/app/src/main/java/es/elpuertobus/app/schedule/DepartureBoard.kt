package es.elpuertobus.app.schedule

import java.time.ZoneId
import java.time.ZonedDateTime

data class ItineraryStopRef(
    val stopId: String,
    val line: Int,
    val columnSide: String,
    val sequence: Int,
    val stopName: String,
)

data class DepartureBoardInput(
    val selectedStopId: String,
    val stops: List<ItineraryStopRef>,
    val departures: List<DepartureSnapshot>,
    val lineColours: Map<Int, String>,
    val holidayDates: Set<java.time.LocalDate>,
    val lineRulesJson: Map<Int, String>,
    val zoneId: ZoneId,
    val now: ZonedDateTime,
)

data class UpcomingTrip(
    val line: Int,
    val lineColor: String,
    val destination: String,
    val upstreamStopId: String,
    val upstreamStopName: String,
    val stopsFromAnchor: Int,
    val scheduledSeconds: Int,
    val isPast: Boolean,
    val elapsedMinutes: Int,
    val downstream: Boolean,
)

data class PrintedTimetableLine(
    val line: Int,
    val lineColor: String,
    val destination: String,
    val lineClosedToday: Boolean,
    val times: List<Int>,
)

data class DepartureBoardResult(
    val stopName: String,
    val profile: ServiceProfile,
    val hasPrintedTimetable: Boolean,
    val trips: List<UpcomingTrip>,
    val timetable: List<PrintedTimetableLine>,
)

object DepartureBoard {
    const val LOOKBACK_MINUTES = 20
    const val FEED_LIMIT = 8
    private const val LEFT_AGO_MAX_MINUTES = 30
    private const val FALLBACK_COLOUR = "#2563eb"

    fun build(input: DepartureBoardInput): DepartureBoardResult {
        val selected = input.stops.find { it.stopId == input.selectedStopId }
        val localNow = input.now.withZoneSameInstant(input.zoneId)
        val profile = StopSchedule.serviceProfile(localNow.toLocalDate(), input.holidayDates)
        if (selected == null) {
            return DepartureBoardResult(
                stopName = input.selectedStopId,
                profile = profile,
                hasPrintedTimetable = false,
                trips = emptyList(),
                timetable = emptyList(),
            )
        }
        val printedIds = input.departures.map { it.boundStopId }.toSet()
        val node = physicalNode(selected.stopId)
        val serving =
            input.stops
                .filter { physicalNode(it.stopId) == node }
                .sortedWith(compareBy({ it.line }, { it.columnSide }, { it.sequence }))
        val nowSeconds = localNow.toLocalTime().toSecondOfDay()
        val earliest = nowSeconds - LOOKBACK_MINUTES * 60
        val trips = ArrayList<UpcomingTrip>()
        val timetable = ArrayList<PrintedTimetableLine>()
        for (occurrence in serving) {
            val column = columnSequence(occurrence, input.stops)
            val index = column.indexOfFirst { it.stopId == occurrence.stopId }
            if (index < 0) {
                continue
            }
            val destination = column.last().stopName
            val colour = input.lineColours[occurrence.line] ?: FALLBACK_COLOUR
            val closed = StopSchedule.isLineClosed(occurrence.line, profile, input.lineRulesJson)
            if (occurrence.stopId in printedIds) {
                timetable.add(
                    PrintedTimetableLine(
                        line = occurrence.line,
                        lineColor = colour,
                        destination = destination,
                        lineClosedToday = closed,
                        times =
                            if (closed) {
                                emptyList()
                            } else {
                                timesFor(input.departures, occurrence.stopId, profile)
                            },
                    ),
                )
            }
            if (closed) {
                continue
            }
            val anchor = resolveAnchor(column, index, printedIds) ?: continue
            val anchorStop = column[anchor.index]
            for (seconds in timesFor(input.departures, anchorStop.stopId, profile)) {
                if (seconds < earliest) {
                    continue
                }
                val elapsed = (nowSeconds - seconds) / 60
                trips.add(
                    UpcomingTrip(
                        line = occurrence.line,
                        lineColor = colour,
                        destination = destination,
                        upstreamStopId = anchorStop.stopId,
                        upstreamStopName = anchorStop.stopName,
                        stopsFromAnchor = anchor.stopsAway,
                        scheduledSeconds = seconds,
                        isPast = seconds < nowSeconds,
                        elapsedMinutes = if (seconds < nowSeconds) elapsed else 0,
                        downstream = anchor.downstream,
                    ),
                )
            }
        }
        val shown =
            trips
                .distinctBy { listOf(it.line, it.scheduledSeconds, it.upstreamStopId, it.downstream) }
                .sortedWith(compareBy({ it.scheduledSeconds }, { it.line }, { it.upstreamStopName }))
                .take(FEED_LIMIT)
        return DepartureBoardResult(
            stopName = selected.stopName,
            profile = profile,
            hasPrintedTimetable = serving.any { it.stopId in printedIds },
            trips = shown,
            timetable = timetable.sortedWith(compareBy({ it.line }, { it.destination })),
        )
    }

    fun showLeftAgo(trip: UpcomingTrip): Boolean =
        trip.isPast && trip.elapsedMinutes <= LEFT_AGO_MAX_MINUTES

    private data class Anchor(val index: Int, val stopsAway: Int, val downstream: Boolean)

    private fun resolveAnchor(
        column: List<ItineraryStopRef>,
        index: Int,
        printedIds: Set<String>,
    ): Anchor? {
        if (column[index].stopId in printedIds) {
            return Anchor(index, 0, false)
        }
        for (earlier in index - 1 downTo 0) {
            if (column[earlier].stopId in printedIds) {
                return Anchor(earlier, index - earlier, false)
            }
        }
        for (later in index + 1 until column.size) {
            if (column[later].stopId in printedIds) {
                return Anchor(later, later - index, true)
            }
        }
        return null
    }

    private fun columnSequence(
        occurrence: ItineraryStopRef,
        stops: List<ItineraryStopRef>,
    ): List<ItineraryStopRef> {
        if (occurrence.stopId.contains("_detour_")) {
            return listOf(occurrence)
        }
        return stops
            .filter {
                it.line == occurrence.line &&
                    it.columnSide == occurrence.columnSide &&
                    !it.stopId.contains("_detour_")
            }
            .sortedBy { it.sequence }
    }

    private fun timesFor(
        departures: List<DepartureSnapshot>,
        stopId: String,
        profile: ServiceProfile,
    ): List<Int> =
        departures
            .filter { it.boundStopId == stopId && it.serviceDay == profile.dbValue }
            .map { it.secondsSinceMidnight }
            .distinct()
            .sorted()

    private fun physicalNode(stopId: String): String {
        val tail = stopId.substringAfterLast('_')
        return if (tail.isNotEmpty() && tail.all { it.isDigit() }) tail else stopId
    }
}
