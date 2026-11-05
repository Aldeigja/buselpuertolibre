package es.elpuertobus.app.map

import es.elpuertobus.app.schedule.PrintedTimetableLine
import es.elpuertobus.app.schedule.ServiceProfile
import es.elpuertobus.app.schedule.UpcomingTrip

data class StopSheetUiState(
    val stopId: String,
    val stopName: String,
    val profile: ServiceProfile,
    val hasPrintedTimetable: Boolean,
    val trips: List<UpcomingTrip>,
    val timetable: List<PrintedTimetableLine>,
    val holidayAttribution: String,
)
