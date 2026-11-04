package es.elpuertobus.app.map

import es.elpuertobus.app.schedule.ServiceProfile

data class StopSheetUiState(
    val stopId: String,
    val stopName: String,
    val line: Int,
    val profile: ServiceProfile,
    val lineClosedToday: Boolean,
    val nextDepartures: List<Int>,
    val fullTimetable: List<Int>,
    val holidayAttribution: String,
)
