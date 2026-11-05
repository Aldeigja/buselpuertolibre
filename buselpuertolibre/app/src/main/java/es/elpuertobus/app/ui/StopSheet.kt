package es.elpuertobus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import es.elpuertobus.app.locale.appString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.elpuertobus.app.R
import es.elpuertobus.app.map.StopSheetUiState
import es.elpuertobus.app.schedule.DepartureBoard
import es.elpuertobus.app.schedule.PrintedTimetableLine
import es.elpuertobus.app.schedule.ServiceProfile
import es.elpuertobus.app.schedule.StopSchedule
import es.elpuertobus.app.schedule.UpcomingTrip
import es.elpuertobus.app.ui.theme.TransitColors

/**
 * Sheet content for the non-modal scaffold on the map. The header stays put;
 * only the departure list or timetable scrolls.
 *
 * Clock text uses the platform sans (Roboto), which implements the OpenType
 * `tnum` feature, so columns do not shift as digits change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopSheet(sheet: StopSheetUiState) {
    var tab by rememberSaveable(sheet.stopId) { mutableIntStateOf(0) }
    val overview = if (!sheet.hasPrintedTimetable) 0 else tab
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = sheet.stopName,
                style = MaterialTheme.typography.titleLarge,
                color = TransitColors.Slate900,
            )
            Text(
                text = appString(R.string.profile_and_season, profileLabel(sheet.profile), appString(R.string.season_title)),
                style = MaterialTheme.typography.labelMedium,
                color = TransitColors.Slate400,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!sheet.hasPrintedTimetable) {
                Surface(
                    color = TransitColors.Slate100,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 1.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = appString(R.string.upstream_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        if (sheet.hasPrintedTimetable) {
            TabRow(
                selectedTabIndex = overview,
                containerColor = TransitColors.Slate100.copy(alpha = 0f),
                divider = { GroupDivider() },
            ) {
                Tab(
                    selected = overview == 0,
                    onClick = { tab = 0 },
                    text = { Text(appString(R.string.tab_overview), color = TransitColors.Slate600) },
                )
                Tab(
                    selected = overview == 1,
                    onClick = { tab = 1 },
                    text = { Text(appString(R.string.tab_timetable), color = TransitColors.Slate600) },
                )
            }
        }
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = navBottom + 16.dp),
        ) {
            if (overview == 0) {
                if (sheet.trips.isEmpty()) {
                    item {
                        Text(
                            text = appString(R.string.no_more_today),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TransitColors.Slate600,
                        )
                    }
                } else {
                    items(sheet.trips.size) { index ->
                        TripRow(sheet.trips[index])
                        ServiceDivider()
                    }
                }
            } else {
                item { TimetablePage(sheet.timetable) }
            }
            if (sheet.holidayAttribution.isNotBlank()) {
                item {
                    Text(
                        text = sheet.holidayAttribution,
                        style = MaterialTheme.typography.labelMedium,
                        color = TransitColors.Slate400,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 24.dp),
        thickness = 1.dp,
        color = TransitColors.Slate100,
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(thickness = 1.dp, color = TransitColors.Slate200)
}

@Composable
private fun TripRow(trip: UpcomingTrip) {
    val timeColor = if (trip.isPast) TransitColors.Slate400 else TransitColors.Slate900
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(line = trip.line, colour = trip.lineColor)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(
                text = trip.destination,
                style = MaterialTheme.typography.titleSmall,
                color = TransitColors.Slate900,
            )
            Text(
                text = anchorLabel(trip),
                style = MaterialTheme.typography.bodySmall,
                color = TransitColors.Slate400,
            )
            if (trip.approximate) {
                Text(
                    text = appString(R.string.approximate_pass),
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitColors.Slate400,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val status = departureStatus(trip)
            if (status != null) {
                Text(
                    text = status,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Bold,
                        ),
                    color = timeColor,
                    textAlign = TextAlign.End,
                )
            }
            Text(
                text = StopSchedule.formatClock(trip.scheduledSeconds),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = if (status == null) FontWeight.Bold else FontWeight.Normal,
                    ),
                color = if (trip.isPast) timeColor else TransitColors.Slate600,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TimetablePage(lines: List<PrintedTimetableLine>) {
    if (lines.isEmpty()) {
        Text(
            text = appString(R.string.no_more_today),
            style = MaterialTheme.typography.bodyMedium,
            color = TransitColors.Slate600,
        )
        return
    }
    val columns = timetableColumns(LocalDensity.current.fontScale)
    lines.forEachIndexed { index, line ->
        if (index > 0) {
            GroupDivider()
        }
        Row(
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(line = line.line, colour = line.lineColor)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = line.destination,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = TransitColors.Slate900,
                )
                if (line.originName != line.destination) {
                    Text(
                        text = appString(R.string.from_origin, line.originName),
                        style = MaterialTheme.typography.labelMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (schedulesCollide(line, lines)) {
                    Text(
                        text = appString(R.string.schedule_not_distinguished),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (line.stopsAhead == 1) {
                    Text(
                        text = appString(R.string.printed_at_next_stop, line.clockStopName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (line.stopsAhead > 1) {
                    Text(
                        text = appString(R.string.towards_downstream, line.clockStopName, line.stopsAhead),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (line.approximate) {
                    Text(
                        text = appString(R.string.approximate_pass),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitColors.Slate600,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        when {
            line.lineClosedToday -> {
                Text(
                    text = appString(R.string.line_closed_today, line.line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TransitColors.Slate600,
                )
            }
            line.times.isEmpty() -> {
                Text(
                    text = appString(R.string.no_more_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TransitColors.Slate600,
                )
            }
            else -> TimeGrid(times = line.times, columns = columns)
        }
    }
}

@Composable
private fun TimeGrid(
    times: List<Int>,
    columns: Int,
) {
    times.chunked(columns).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { seconds ->
                Text(
                    text = StopSchedule.formatClock(seconds),
                    modifier = Modifier.weight(1f),
                    style = clockStyle(MaterialTheme.typography.bodyLarge, FontWeight.Normal),
                    color = TransitColors.Slate900,
                    textAlign = TextAlign.Start,
                )
            }
            repeat(columns - row.size) {
                Text(text = "", modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun timetableColumns(fontScale: Float): Int =
    when {
        fontScale >= 1.6f -> 2
        fontScale >= 1.3f -> 3
        else -> 4
    }

private fun schedulesCollide(
    line: PrintedTimetableLine,
    lines: List<PrintedTimetableLine>,
): Boolean =
    lines.count {
        it.line == line.line && it.originName == line.originName && it.destination == line.destination
    } > 1

@Composable
private fun departureStatus(trip: UpcomingTrip): String? =
    when {
        !trip.isPast && trip.minutesUntil == 0 -> appString(R.string.due_now)
        !trip.isPast -> appString(R.string.in_minutes, trip.minutesUntil)
        DepartureBoard.showLeftAgo(trip) -> appString(R.string.left_ago, trip.elapsedMinutes)
        else -> null
    }

@Composable
private fun anchorLabel(trip: UpcomingTrip): String =
    when {
        trip.downstream -> appString(R.string.towards_downstream, trip.upstreamStopName, trip.stopsFromAnchor)
        trip.stopsFromAnchor == 0 -> appString(R.string.at_timepoint, trip.upstreamStopName)
        trip.stopsFromAnchor == 1 -> appString(R.string.from_upstream_single, trip.upstreamStopName)
        else -> appString(R.string.from_upstream, trip.upstreamStopName, trip.stopsFromAnchor)
    }

@Composable
private fun profileLabel(profile: ServiceProfile): String =
    when (profile) {
        ServiceProfile.LABORABLES -> appString(R.string.profile_laborables)
        ServiceProfile.SABADOS -> appString(R.string.profile_sabados)
        ServiceProfile.FESTIVOS -> appString(R.string.profile_festivos)
    }

private fun clockStyle(
    base: TextStyle,
    weight: FontWeight,
): TextStyle =
    base.copy(
        fontWeight = weight,
        fontFamily = FontFamily.SansSerif,
        fontFeatureSettings = "tnum",
    )
