package es.elpuertobus.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.elpuertobus.app.data.TransitDatabase
import es.elpuertobus.app.schedule.DepartureBoard
import es.elpuertobus.app.schedule.DepartureBoardInput
import es.elpuertobus.app.schedule.DepartureSnapshot
import es.elpuertobus.app.schedule.ItineraryStopRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class LineSwatch(
    val line: Int,
    val colour: String,
)

data class MapUiState(
    val ready: Boolean = false,
    val stopsGeoJson: String = "",
    val routesGeoJson: String = "",
    val lineSwatches: List<LineSwatch> = emptyList(),
    val focusedLine: Int? = null,
    val errorMessage: String? = null,
    val sheet: StopSheetUiState? = null,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var departuresCache: List<DepartureSnapshot>? = null
    private var itineraryCache: List<ItineraryStopRef>? = null
    private var lineColoursCache: Map<Int, String> = emptyMap()
    private var holidayDatesCache: Set<LocalDate>? = null
    private var lineRulesCache: Map<Int, String>? = null
    private var holidayAttributionCache: String = ""

    init {
        viewModelScope.launch {
            runCatching {
                val db = TransitDatabase.open(getApplication())
                try {
                    val dao = db.transitDao()
                    val stops = dao.locatedStops()
                    val routes = dao.routeLines()
                    itineraryCache =
                        dao.itineraryStops().map { stop ->
                            ItineraryStopRef(
                                stopId = stop.stopId,
                                line = stop.line,
                                columnSide = stop.columnSide,
                                sequence = stop.sequence,
                                stopName = stop.stopName,
                            )
                        }
                    lineColoursCache =
                        routes.associate { it.line to (it.colour ?: "#2563eb") }
                    departuresCache =
                        dao.allDepartures().map { row ->
                            DepartureSnapshot(
                                line = row.line,
                                serviceDay = row.serviceDay,
                                secondsSinceMidnight = row.secondsSinceMidnight,
                                boundStopId = row.boundStopId,
                                columnLabel = row.columnLabel,
                            )
                        }
                    holidayDatesCache =
                        dao.holidayDates().map(LocalDate::parse).toSet()
                    lineRulesCache =
                        dao.lineServiceRules().associate { it.line to it.rulesJson }
                    holidayAttributionCache = dao.holidayAttribution().orEmpty()
                    MapUiState(
                        ready = true,
                        stopsGeoJson = MapOverlayGeoJson.stopsFeatureCollection(stops),
                        routesGeoJson = MapOverlayGeoJson.routeLinesFeatureCollection(routes),
                        lineSwatches =
                            routes
                                .distinctBy { it.line }
                                .sortedBy { it.line }
                                .map { LineSwatch(it.line, it.colour ?: "#2563eb") },
                    )
                } finally {
                    db.close()
                }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                _uiState.value =
                    MapUiState(
                        errorMessage = error.message ?: error.toString(),
                    )
            }
        }
    }

    fun onStopSelected(stopId: String) {
        val stops = itineraryCache ?: return
        val departures = departuresCache ?: return
        val board =
            DepartureBoard.build(
                DepartureBoardInput(
                    selectedStopId = stopId,
                    stops = stops,
                    departures = departures,
                    lineColours = lineColoursCache,
                    holidayDates = holidayDatesCache ?: emptySet(),
                    lineRulesJson = lineRulesCache ?: emptyMap(),
                    zoneId = zoneId,
                    now = ZonedDateTime.now(zoneId),
                ),
            )
        _uiState.update {
            it.copy(
                sheet =
                    StopSheetUiState(
                        stopId = stopId,
                        stopName = board.stopName,
                        profile = board.profile,
                        hasPrintedTimetable = board.hasPrintedTimetable,
                        trips = board.trips,
                        timetable = board.timetable,
                        holidayAttribution = holidayAttributionCache,
                    ),
            )
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(sheet = null) }
    }

    fun onShowAllLines() {
        _uiState.update { it.copy(focusedLine = null) }
    }

    fun onLineFocused(line: Int) {
        _uiState.update { state ->
            val next = if (state.focusedLine == line) null else line
            state.copy(focusedLine = next)
        }
    }
}
