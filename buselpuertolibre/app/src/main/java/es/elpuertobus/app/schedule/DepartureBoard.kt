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
    val minutesUntil: Int,
    val downstream: Boolean,
    val approximate: Boolean,
)

data class PrintedTimetableLine(
    val line: Int,
    val lineColor: String,
    val originName: String,
    val destination: String,
    val lineClosedToday: Boolean,
    val times: List<Int>,
    val approximate: Boolean,
    val clockStopName: String = "",
    val stopsAhead: Int = 0,
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
    const val OWN_CLOCK_LOOKBACK_MINUTES = 5
    const val FEED_LIMIT = 8
    private const val LEFT_AGO_MAX_MINUTES = 30
    private const val FALLBACK_COLOUR = "#2563eb"

    /** Line 3 winter columns headed "PASO APROX." in the brochure. */
    private val APPROXIMATE_COLUMNS = setOf("VALDELAGRANA", "ANCORA", "FUENTEBRAVIA")

    /**
     * The only weekday row that prints both an Áncora time and a Fuentebravía time
     * is Áncora 15:00 with Fuentebravía 15:10. Every other Áncora time turns back.
     */
    private const val ANCORA_CONTINUES_AFTER_MINUTES = 10

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
        val trips = ArrayList<UpcomingTrip>()
        val timetable = ArrayList<PrintedTimetableLine>()
        for (occurrence in serving) {
            val column = columnSequence(occurrence, input.stops)
            val index = column.indexOfFirst { it.stopId == occurrence.stopId }
            if (index < 0) {
                continue
            }
            val originName = column.first().stopName
            val destination = column.last().stopName
            val colour = input.lineColours[occurrence.line] ?: FALLBACK_COLOUR
            val closed = StopSchedule.isLineClosed(occurrence.line, profile, input.lineRulesJson)
            val ancoraSplit = line3AncoraSplit(column, index, input.departures, profile)
            if (ancoraSplit != null) {
                if (ancoraSplit.turnTimes.isNotEmpty()) {
                    timetable.add(
                        PrintedTimetableLine(
                            line = occurrence.line,
                            lineColor = colour,
                            originName = ancoraSplit.ancora.stopName,
                            destination = ancoraSplit.terminalName,
                            lineClosedToday = closed,
                            times = if (closed) emptyList() else ancoraSplit.turnTimes,
                            approximate = true,
                        ),
                    )
                }
                val fuente = ancoraSplit.fuente
                if (fuente != null && ancoraSplit.throughTimes.isNotEmpty()) {
                    timetable.add(
                        PrintedTimetableLine(
                            line = occurrence.line,
                            lineColor = colour,
                            originName = originName,
                            destination = destination,
                            lineClosedToday = closed,
                            times = if (closed) emptyList() else ancoraSplit.throughTimes,
                            approximate = true,
                            clockStopName = fuente.stopName,
                            stopsAhead = ancoraSplit.stopsAhead,
                        ),
                    )
                }
            } else if (occurrence.stopId in printedIds) {
                timetable.add(
                    PrintedTimetableLine(
                        line = occurrence.line,
                        lineColor = colour,
                        originName = originName,
                        destination = destination,
                        lineClosedToday = closed,
                        times =
                            if (closed) {
                                emptyList()
                            } else {
                                timesFor(input.departures, occurrence.stopId, profile)
                            },
                        approximate = isApproximate(input.departures, occurrence.stopId),
                    ),
                )
            }
            if (closed) {
                continue
            }
            if (ancoraSplit != null) {
                addAncoraSplitTrips(
                    trips = trips,
                    occurrence = occurrence,
                    colour = colour,
                    split = ancoraSplit,
                    nowSeconds = nowSeconds,
                )
                continue
            }
            val anchor = resolveAnchor(column, index, printedIds) ?: continue
            val anchorStop = column[anchor.index]
            val approximate = isApproximate(input.departures, anchorStop.stopId)
            val earliest = nowSeconds - lookbackMinutes(anchor) * 60
            for (seconds in timesFor(input.departures, anchorStop.stopId, profile)) {
                if (seconds < earliest) {
                    continue
                }
                val elapsed = (nowSeconds - seconds) / 60
                trips.add(
                    tripAt(
                        occurrence = occurrence,
                        colour = colour,
                        destination = destination,
                        anchorStop = anchorStop,
                        stopsAway = anchor.stopsAway,
                        seconds = seconds,
                        nowSeconds = nowSeconds,
                        elapsed = elapsed,
                        downstream = anchor.downstream,
                        approximate = approximate,
                    ),
                )
            }
            val turnBack = line3AncoraTurnBack(column, index, input.departures, profile)
            if (turnBack != null) {
                val earliestTurn = nowSeconds - LOOKBACK_MINUTES * 60
                for (seconds in turnBack.times) {
                    if (seconds < earliestTurn) {
                        continue
                    }
                    val elapsed = (nowSeconds - seconds) / 60
                    trips.add(
                        tripAt(
                            occurrence = occurrence,
                            colour = colour,
                            destination = turnBack.destination,
                            anchorStop = turnBack.anchorStop,
                            stopsAway = turnBack.stopsAway,
                            seconds = seconds,
                            nowSeconds = nowSeconds,
                            elapsed = elapsed,
                            downstream = false,
                            approximate = true,
                        ),
                    )
                }
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
            timetable = timetable.sortedWith(compareBy({ it.line }, { it.originName }, { it.destination })),
        )
    }

    fun showLeftAgo(trip: UpcomingTrip): Boolean =
        trip.isPast && trip.elapsedMinutes <= LEFT_AGO_MAX_MINUTES

    private data class Anchor(val index: Int, val stopsAway: Int, val downstream: Boolean)

    private data class AncoraTurnBack(
        val anchorStop: ItineraryStopRef,
        val stopsAway: Int,
        val times: List<Int>,
        val destination: String,
    )

    private fun tripAt(
        occurrence: ItineraryStopRef,
        colour: String,
        destination: String,
        anchorStop: ItineraryStopRef,
        stopsAway: Int,
        seconds: Int,
        nowSeconds: Int,
        elapsed: Int,
        downstream: Boolean,
        approximate: Boolean,
    ): UpcomingTrip =
        UpcomingTrip(
            line = occurrence.line,
            lineColor = colour,
            destination = destination,
            upstreamStopId = anchorStop.stopId,
            upstreamStopName = anchorStop.stopName,
            stopsFromAnchor = stopsAway,
            scheduledSeconds = seconds,
            isPast = seconds < nowSeconds,
            elapsedMinutes = if (seconds < nowSeconds) elapsed else 0,
            minutesUntil = if (seconds >= nowSeconds) (seconds - nowSeconds) / 60 else 0,
            downstream = downstream,
            approximate = approximate,
        )

    private data class AncoraSplit(
        val ancora: ItineraryStopRef,
        val fuente: ItineraryStopRef?,
        val stopsAhead: Int,
        val terminalName: String,
        val throughDestination: String,
        val turnTimes: List<Int>,
        val continuedTimes: List<Int>,
        val throughTimes: List<Int>,
    )

    /**
     * Weekday buses reach Áncora and turn back until the last turning pass. Later
     * trips still pass Áncora and are printed at Fuentebravía, one stop ahead.
     * Festivo trips all turn. The Fuentebravía minute is not copied onto Áncora.
     */
    private fun line3AncoraSplit(
        column: List<ItineraryStopRef>,
        index: Int,
        departures: List<DepartureSnapshot>,
        profile: ServiceProfile,
    ): AncoraSplit? {
        if (column.getOrNull(index)?.line != 3) {
            return null
        }
        val ancora =
            column.indexOfFirst { stop ->
                departures.any { it.boundStopId == stop.stopId && it.columnLabel == "ANCORA" }
            }
        if (ancora != index) {
            return null
        }
        val fuente =
            column.indexOfFirst { stop ->
                departures.any { it.boundStopId == stop.stopId && it.columnLabel == "FUENTEBRAVIA" }
            }
        val searchFrom = if (fuente >= 0) fuente + 1 else ancora + 1
        val returnAncla =
            (searchFrom until column.size).firstOrNull {
                column[it].stopName.contains("Ancla")
            } ?: return null
        val terminal =
            (returnAncla until column.size).firstOrNull {
                column[it].stopName.startsWith("Terminal")
            } ?: return null
        val ancoraStop = column[ancora]
        val continued = continuedAncoraSeconds(departures, ancoraStop.stopId, profile)
        val ancoraTimes = timesFor(departures, ancoraStop.stopId, profile)
        val turnTimes = ancoraTimes.filter { it !in continued }
        val fuenteStop = if (fuente >= 0) column[fuente] else null
        val throughTimes =
            if (fuenteStop == null) {
                emptyList()
            } else {
                timesFor(departures, fuenteStop.stopId, profile)
            }
        if (turnTimes.isEmpty() && throughTimes.isEmpty()) {
            return null
        }
        return AncoraSplit(
            ancora = ancoraStop,
            fuente = fuenteStop,
            stopsAhead = if (fuenteStop == null) 0 else fuente - ancora,
            terminalName = column[terminal].stopName,
            throughDestination = column.last().stopName,
            turnTimes = turnTimes,
            continuedTimes = ancoraTimes.filter { it in continued },
            throughTimes = throughTimes,
        )
    }

    private fun addAncoraSplitTrips(
        trips: MutableList<UpcomingTrip>,
        occurrence: ItineraryStopRef,
        colour: String,
        split: AncoraSplit,
        nowSeconds: Int,
    ) {
        fun addOwn(seconds: Int, destination: String) {
            val earliest = nowSeconds - OWN_CLOCK_LOOKBACK_MINUTES * 60
            if (seconds < earliest) {
                return
            }
            val elapsed = (nowSeconds - seconds) / 60
            trips.add(
                tripAt(
                    occurrence = occurrence,
                    colour = colour,
                    destination = destination,
                    anchorStop = split.ancora,
                    stopsAway = 0,
                    seconds = seconds,
                    nowSeconds = nowSeconds,
                    elapsed = elapsed,
                    downstream = false,
                    approximate = true,
                ),
            )
        }
        for (seconds in split.turnTimes) {
            addOwn(seconds, split.terminalName)
        }
        for (seconds in split.continuedTimes) {
            addOwn(seconds, split.throughDestination)
        }
        val fuente = split.fuente ?: return
        val paired = split.continuedTimes.map { it + ANCORA_CONTINUES_AFTER_MINUTES * 60 }.toSet()
        val earliest = nowSeconds - LOOKBACK_MINUTES * 60
        for (seconds in split.throughTimes) {
            if (seconds in paired || seconds < earliest) {
                continue
            }
            val elapsed = (nowSeconds - seconds) / 60
            trips.add(
                tripAt(
                    occurrence = occurrence,
                    colour = colour,
                    destination = split.throughDestination,
                    anchorStop = fuente,
                    stopsAway = split.stopsAhead,
                    seconds = seconds,
                    nowSeconds = nowSeconds,
                    elapsed = elapsed,
                    downstream = true,
                    approximate = true,
                ),
            )
        }
    }

    /**
     * Before Fuentebravía, Line 3 reaches Áncora and comes back through the citybound
     * stops. Those stops keep the Fuentebravía times for the later through trips.
     */
    private fun line3AncoraTurnBack(
        column: List<ItineraryStopRef>,
        index: Int,
        departures: List<DepartureSnapshot>,
        profile: ServiceProfile,
    ): AncoraTurnBack? {
        if (column.getOrNull(index)?.line != 3) {
            return null
        }
        val ancora =
            column.indexOfFirst { stop ->
                departures.any { it.boundStopId == stop.stopId && it.columnLabel == "ANCORA" }
            }
        if (ancora < 0) {
            return null
        }
        val fuente =
            column.indexOfFirst { stop ->
                departures.any { it.boundStopId == stop.stopId && it.columnLabel == "FUENTEBRAVIA" }
            }
        val searchFrom = if (fuente >= 0) fuente + 1 else ancora + 1
        val returnAncla =
            (searchFrom until column.size).firstOrNull {
                column[it].stopName.contains("Ancla")
            } ?: return null
        val terminal =
            (returnAncla until column.size).firstOrNull {
                column[it].stopName.startsWith("Terminal")
            } ?: return null
        if (index !in returnAncla until terminal) {
            return null
        }
        val anchorStop = column[ancora]
        val continued = continuedAncoraSeconds(departures, anchorStop.stopId, profile)
        val times = timesFor(departures, anchorStop.stopId, profile).filter { it !in continued }
        if (times.isEmpty()) {
            return null
        }
        return AncoraTurnBack(
            anchorStop = anchorStop,
            stopsAway = index - returnAncla + 1,
            times = times,
            destination = column[terminal].stopName,
        )
    }

    private fun continuedAncoraSeconds(
        departures: List<DepartureSnapshot>,
        ancoraStopId: String,
        profile: ServiceProfile,
    ): Set<Int> {
        val fuente =
            departures
                .filter {
                    it.line == 3 &&
                        it.serviceDay == profile.dbValue &&
                        it.columnLabel == "FUENTEBRAVIA"
                }
                .map { it.secondsSinceMidnight }
                .toSet()
        return timesFor(departures, ancoraStopId, profile)
            .filter { it + ANCORA_CONTINUES_AFTER_MINUTES * 60 in fuente }
            .toSet()
    }

    private fun lookbackMinutes(anchor: Anchor): Int =
        if (anchor.stopsAway == 0 && !anchor.downstream) {
            OWN_CLOCK_LOOKBACK_MINUTES
        } else {
            LOOKBACK_MINUTES
        }

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

    private fun isApproximate(departures: List<DepartureSnapshot>, stopId: String): Boolean =
        departures.any { it.boundStopId == stopId && it.columnLabel in APPROXIMATE_COLUMNS }

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
