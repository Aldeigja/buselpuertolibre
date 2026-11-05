package es.elpuertobus.app.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ServiceProfile(val dbValue: String) {
    LABORABLES("laborables"),
    SABADOS("sabados"),
    FESTIVOS("festivos"),
}

data class DepartureSnapshot(
    val line: Int,
    val serviceDay: String,
    val secondsSinceMidnight: Int,
    val boundStopId: String,
    val columnLabel: String = "",
)

data class StopScheduleInput(
    val stopId: String,
    val line: Int,
    val stopName: String,
    val departures: List<DepartureSnapshot>,
    val holidayDates: Set<LocalDate>,
    val lineRulesJson: Map<Int, String>,
    val zoneId: ZoneId,
    val now: java.time.ZonedDateTime,
)

data class StopScheduleResult(
    val profile: ServiceProfile,
    val lineClosedToday: Boolean,
    val nextDepartures: List<Int>,
    val fullTimetable: List<Int>,
)

object StopSchedule {
    fun serviceProfile(localDate: LocalDate, holidayDates: Set<LocalDate>): ServiceProfile {
        if (localDate in holidayDates) {
            return ServiceProfile.FESTIVOS
        }
        return when (localDate.dayOfWeek) {
            java.time.DayOfWeek.SUNDAY -> ServiceProfile.FESTIVOS
            java.time.DayOfWeek.SATURDAY -> ServiceProfile.SABADOS
            else -> ServiceProfile.LABORABLES
        }
    }

    fun isLineClosed(line: Int, profile: ServiceProfile, lineRulesJson: Map<Int, String>): Boolean {
        val raw = lineRulesJson[line] ?: return false
        return when (profile) {
            ServiceProfile.SABADOS -> ruleFlag(raw, "saturday") == false
            ServiceProfile.FESTIVOS -> ruleFlag(raw, "sunday_festivo") == false
            ServiceProfile.LABORABLES -> false
        }
    }

    private fun ruleFlag(rulesJson: String, key: String): Boolean? {
        val pattern = """"$key"\s*:\s*(true|false)""".toRegex()
        val value = pattern.find(rulesJson)?.groupValues?.get(1) ?: return null
        return value == "true"
    }

    fun build(input: StopScheduleInput): StopScheduleResult {
        val localDate = input.now.withZoneSameInstant(input.zoneId).toLocalDate()
        val profile = serviceProfile(localDate, input.holidayDates)
        val closed = isLineClosed(input.line, profile, input.lineRulesJson)
        if (closed) {
            return StopScheduleResult(
                profile = profile,
                lineClosedToday = true,
                nextDepartures = emptyList(),
                fullTimetable = emptyList(),
            )
        }
        val today =
            input.departures
                .filter { it.boundStopId == input.stopId && it.serviceDay == profile.dbValue }
                .map { it.secondsSinceMidnight }
                .distinct()
                .sorted()
        val nowSeconds =
            input.now.withZoneSameInstant(input.zoneId).toLocalTime().toSecondOfDay()
        val next = today.filter { it > nowSeconds }
        return StopScheduleResult(
            profile = profile,
            lineClosedToday = false,
            nextDepartures = next,
            fullTimetable = today,
        )
    }

    fun formatClock(secondsSinceMidnight: Int): String {
        val time = LocalTime.ofSecondOfDay(secondsSinceMidnight.toLong())
        return "%02d:%02d".format(time.hour, time.minute)
    }
}
