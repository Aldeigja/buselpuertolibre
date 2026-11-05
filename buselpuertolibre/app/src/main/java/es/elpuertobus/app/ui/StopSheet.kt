package es.elpuertobus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.elpuertobus.app.R
import es.elpuertobus.app.map.StopSheetUiState
import es.elpuertobus.app.schedule.DepartureBoard
import es.elpuertobus.app.schedule.PrintedTimetableLine
import es.elpuertobus.app.schedule.ServiceProfile
import es.elpuertobus.app.schedule.StopSchedule
import es.elpuertobus.app.schedule.UpcomingTrip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopSheet(
    sheet: StopSheetUiState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var tab by rememberSaveable(sheet.stopId) { mutableIntStateOf(0) }
    val overview = if (!sheet.hasPrintedTimetable) 0 else tab
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                val profileName = profileLabel(sheet.profile)
                val season = stringResource(R.string.season_title)
                Text(text = sheet.stopName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.profile_and_season, profileName, season),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sheet.hasPrintedTimetable) {
                TabRow(selectedTabIndex = overview) {
                    Tab(
                        selected = overview == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(R.string.tab_overview)) },
                    )
                    Tab(
                        selected = overview == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(R.string.tab_timetable)) },
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (overview == 0) {
                    OverviewPage(sheet)
                } else {
                    TimetablePage(sheet.timetable)
                }
                if (sheet.holidayAttribution.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = sheet.holidayAttribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewPage(sheet: StopSheetUiState) {
    if (!sheet.hasPrintedTimetable) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.upstream_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (sheet.trips.isEmpty()) {
        Text(
            text = stringResource(R.string.no_more_today),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    sheet.trips.forEach { trip ->
        TripRow(trip)
        HorizontalDivider()
    }
}

@Composable
private fun TripRow(trip: UpcomingTrip) {
    val timeColor =
        if (trip.isPast) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(line = trip.line, colour = trip.lineColor)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(text = trip.destination, style = MaterialTheme.typography.titleSmall)
            Text(
                text = anchorLabel(trip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (DepartureBoard.showLeftAgo(trip)) {
                Text(
                    text = stringResource(R.string.left_ago, trip.elapsedMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = StopSchedule.formatClock(trip.scheduledSeconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = timeColor,
        )
    }
}

@Composable
private fun TimetablePage(lines: List<PrintedTimetableLine>) {
    if (lines.isEmpty()) {
        Text(
            text = stringResource(R.string.no_more_today),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    lines.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(line = line.line, colour = line.lineColor)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.line_label, line.line),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = line.destination,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            line.lineClosedToday -> {
                Text(
                    text = stringResource(R.string.line_closed_today, line.line),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            line.times.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_more_today),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                line.times.forEach { seconds ->
                    Text(
                        text = StopSchedule.formatClock(seconds),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LineBadge(line: Int, colour: String) {
    val fill = runCatching { Color(android.graphics.Color.parseColor(colour)) }.getOrDefault(Color(0xFF2563EB))
    val ink = if (fill.luminance() > 0.62f) Color.Black else Color.White
    BoxBadge(fill = fill, ink = ink, label = line.toString())
}

@Composable
private fun BoxBadge(fill: Color, ink: Color, label: String) {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = ink, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun anchorLabel(trip: UpcomingTrip): String =
    when {
        trip.downstream -> stringResource(R.string.towards_downstream, trip.upstreamStopName, trip.stopsFromAnchor)
        trip.stopsFromAnchor == 0 -> stringResource(R.string.at_timepoint, trip.upstreamStopName)
        trip.stopsFromAnchor == 1 -> stringResource(R.string.from_upstream_single, trip.upstreamStopName)
        else -> stringResource(R.string.from_upstream, trip.upstreamStopName, trip.stopsFromAnchor)
    }

@Composable
private fun profileLabel(profile: ServiceProfile): String =
    when (profile) {
        ServiceProfile.LABORABLES -> stringResource(R.string.profile_laborables)
        ServiceProfile.SABADOS -> stringResource(R.string.profile_sabados)
        ServiceProfile.FESTIVOS -> stringResource(R.string.profile_festivos)
    }

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
