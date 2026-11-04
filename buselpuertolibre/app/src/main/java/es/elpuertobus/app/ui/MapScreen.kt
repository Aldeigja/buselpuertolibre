package es.elpuertobus.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.elpuertobus.app.R
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
                onStopSelected = viewModel::onStopSelected,
                modifier = Modifier.fillMaxSize(),
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
