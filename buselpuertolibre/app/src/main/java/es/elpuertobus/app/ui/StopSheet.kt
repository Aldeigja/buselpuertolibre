package es.elpuertobus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
                    .padding(bottom = 24.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                val profileName = profileLabel(sheet.profile)
                val season = stringResource(R.string.season_title)
                Text(
                    text = sheet.stopName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TransitColors.Slate900,
                )
                Text(
                    text = stringResource(R.string.profile_and_season, profileName, season),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TransitColors.Slate400,
                )
            }
            if (sheet.hasPrintedTimetable) {
                TabRow(selectedTabIndex = overview, divider = { SheetDivider() }) {
                    Tab(
                        selected = overview == 0,
                        onClick = { tab = 0 },
                        text = {
                            Text(
                                stringResource(R.string.tab_overview),
                                color = TransitColors.Slate600,
                            )
                        },
                    )
                    Tab(
                        selected = overview == 1,
                        onClick = { tab = 1 },
                        text = {
                            Text(
                                stringResource(R.string.tab_timetable),
                                color = TransitColors.Slate600,
                            )
                        },
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
                        color = TransitColors.Slate400,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(color = TransitColors.Slate200, thickness = 1.dp)
}

@Composable
private fun OverviewPage(sheet: StopSheetUiState) {
    if (!sheet.hasPrintedTimetable) {
        Surface(
            color = TransitColors.Slate100,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 1.dp,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = stringResource(R.string.upstream_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = TransitColors.Slate600,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (sheet.trips.isEmpty()) {
        Text(
            text = stringResource(R.string.no_more_today),
            style = MaterialTheme.typography.bodyLarge,
            color = TransitColors.Slate600,
        )
        return
    }
    sheet.trips.forEach { trip ->
        TripRow(trip)
        SheetDivider()
    }
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
                    text = stringResource(R.string.approximate_pass),
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitColors.Slate400,
                )
            }
            if (DepartureBoard.showLeftAgo(trip)) {
                Text(
                    text = stringResource(R.string.left_ago, trip.elapsedMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitColors.Slate400,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!trip.isPast) {
                Text(
                    text =
                        if (trip.minutesUntil == 0) {
                            stringResource(R.string.due_now)
                        } else {
                            stringResource(R.string.in_minutes, trip.minutesUntil)
                        },
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
                        fontWeight = if (trip.isPast) FontWeight.Bold else FontWeight.Normal,
                    ),
                color = if (trip.isPast) timeColor else TransitColors.Slate600,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetablePage(lines: List<PrintedTimetableLine>) {
    if (lines.isEmpty()) {
        Text(
            text = stringResource(R.string.no_more_today),
            style = MaterialTheme.typography.bodyLarge,
            color = TransitColors.Slate600,
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
                    color = TransitColors.Slate900,
                )
                Text(
                    text = line.destination,
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitColors.Slate600,
                )
                if (line.approximate) {
                    Text(
                        text = stringResource(R.string.approximate_pass),
                        style = MaterialTheme.typography.bodySmall,
                        color = TransitColors.Slate400,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            line.lineClosedToday -> {
                Text(
                    text = stringResource(R.string.line_closed_today, line.line),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TransitColors.Slate600,
                )
            }
            line.times.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_more_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TransitColors.Slate600,
                )
            }
            else -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4,
                ) {
                    line.times.forEach { seconds ->
                        Text(
                            text = StopSchedule.formatClock(seconds),
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontFeatureSettings = "tnum",
                                ),
                            color = TransitColors.Slate900,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
