package es.elpuertobus.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.elpuertobus.app.data.TransitDatabase
import es.elpuertobus.app.schedule.DepartureSnapshot
import es.elpuertobus.app.schedule.StopSchedule
import es.elpuertobus.app.schedule.StopScheduleInput
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
                    departuresCache =
                        dao.allDepartures().map { row ->
                            DepartureSnapshot(
                                line = row.line,
                                serviceDay = row.serviceDay,
                                secondsSinceMidnight = row.secondsSinceMidnight,
                                boundStopId = row.boundStopId,
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
        viewModelScope.launch {
            runCatching {
                val db = TransitDatabase.open(getApplication())
                try {
                    val stop =
                        db.transitDao().stopById(stopId)
                            ?: error("Unknown stop $stopId")
                    val focused = _uiState.value.focusedLine
                    if (focused != null && stop.line != focused) {
                        return@runCatching null
                    }
                    val departures = departuresCache ?: emptyList()
                    val holidays = holidayDatesCache ?: emptySet()
                    val rules = lineRulesCache ?: emptyMap()
                    val attribution = holidayAttributionCache
                    val schedule =
                        StopSchedule.build(
                            StopScheduleInput(
                                stopId = stopId,
                                line = stop.line,
                                stopName = stop.stopName,
                                departures = departures,
                                holidayDates = holidays,
                                lineRulesJson = rules,
                                zoneId = zoneId,
                                now = ZonedDateTime.now(zoneId),
                            ),
                        )
                    StopSheetUiState(
                        stopId = stopId,
                        stopName = stop.stopName,
                        line = stop.line,
                        profile = schedule.profile,
                        lineClosedToday = schedule.lineClosedToday,
                        nextDepartures = schedule.nextDepartures,
                        fullTimetable = schedule.fullTimetable,
                        holidayAttribution = attribution,
                    )
                } finally {
                    db.close()
                }
            }.onSuccess { sheet ->
                if (sheet != null) {
                    _uiState.update { it.copy(sheet = sheet) }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: error.toString())
                }
            }
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
            val sheet = state.sheet?.takeIf { next == null || it.line == next }
            state.copy(focusedLine = next, sheet = sheet)
        }
    }
}
