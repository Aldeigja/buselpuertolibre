package es.elpuertobus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.elpuertobus.app.R
import es.elpuertobus.app.map.LineSwatch
import es.elpuertobus.app.map.MapViewModel
import es.elpuertobus.app.map.OfflineMapView

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.ready) {
            OfflineMapView(
                stopsGeoJson = state.stopsGeoJson,
                routesGeoJson = state.routesGeoJson,
                focusedLine = state.focusedLine,
                onStopSelected = viewModel::onStopSelected,
                onLineSelected = viewModel::onLineFocused,
                modifier = Modifier.fillMaxSize(),
            )
            LineFocusRow(
                swatches = state.lineSwatches,
                focusedLine = state.focusedLine,
                onSelect = viewModel::onLineFocused,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
            )
            state.sheet?.let { sheet ->
                StopSheet(sheet = sheet, onDismiss = viewModel::dismissSheet)
            }
            Text(
                text = stringResource(R.string.osm_attribution),
                style = MaterialTheme.typography.labelSmall,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
            )
        } else if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun LineFocusRow(
    swatches: List<LineSwatch>,
    focusedLine: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        swatches.forEach { swatch ->
            val label = stringResource(R.string.line_label, swatch.line)
            val fill = Color(android.graphics.Color.parseColor(swatch.colour))
            val ink = if (fill.luminance() > 0.62f) Color.Black else Color.White
            Box(
                modifier =
                    Modifier
                        .size(if (focusedLine == swatch.line) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .clickable { onSelect(swatch.line) }
                        .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = swatch.line.toString(),
                    color = ink,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
