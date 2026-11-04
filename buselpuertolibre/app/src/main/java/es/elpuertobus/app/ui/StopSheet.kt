package es.elpuertobus.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.elpuertobus.app.R
import es.elpuertobus.app.map.StopSheetUiState
import es.elpuertobus.app.schedule.ServiceProfile
import es.elpuertobus.app.schedule.StopSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopSheet(
    sheet: StopSheetUiState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(text = sheet.stopName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.line_label, sheet.line),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.season_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = profileLabel(sheet.profile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            when {
                sheet.lineClosedToday -> {
                    Text(
                        text = stringResource(R.string.line_closed_today, sheet.line),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                sheet.fullTimetable.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_printed_clock),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.next_departures),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (sheet.nextDepartures.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_more_departures_today),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        sheet.nextDepartures.forEach { seconds ->
                            Text(
                                text = StopSchedule.formatClock(seconds),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.full_timetable),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    sheet.fullTimetable.forEach { seconds ->
                        Text(
                            text = StopSchedule.formatClock(seconds),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
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

@Composable
private fun profileLabel(profile: ServiceProfile): String =
    when (profile) {
        ServiceProfile.LABORABLES -> stringResource(R.string.profile_laborables)
        ServiceProfile.SABADOS -> stringResource(R.string.profile_sabados)
        ServiceProfile.FESTIVOS -> stringResource(R.string.profile_festivos)
    }
