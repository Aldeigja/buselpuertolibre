package es.elpuertobus.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.elpuertobus.app.R
import es.elpuertobus.app.locale.AppLocale
import es.elpuertobus.app.map.LineSwatch
import es.elpuertobus.app.map.MapLocation
import es.elpuertobus.app.map.MapReadyHolder
import es.elpuertobus.app.map.MapViewModel
import es.elpuertobus.app.map.OfflineMapView
import es.elpuertobus.app.ui.theme.TransitColors
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mapReady = remember { MapReadyHolder() }
    var locationEnabled by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var selectedLocale by remember { mutableStateOf(AppLocale.currentTag(context)) }
    val localeContext = remember(selectedLocale) { AppLocale.wrap(context, selectedLocale) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                permissionDeniedOnce = false
                locationEnabled = true
                mapReady.withReady { map, style ->
                    MapLocation.setEnabled(map, style, context, enabled = true, moveCamera = true)
                }
            } else {
                permissionDeniedOnce = true
                locationEnabled = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        localeContext.getString(R.string.location_unavailable),
                    )
                }
            }
        }

    fun onMyLocationClick() {
        val fineGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            locationEnabled = true
            mapReady.withReady { map, style ->
                MapLocation.setEnabled(map, style, context, enabled = true, moveCamera = true)
            }
            return
        }
        if (permissionDeniedOnce) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    localeContext.getString(R.string.location_unavailable),
                )
            }
            return
        }
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (state.ready) {
                OfflineMapView(
                    stopsGeoJson = state.stopsGeoJson,
                    routesGeoJson = state.routesGeoJson,
                    focusedLine = state.focusedLine,
                    mapReady = mapReady,
                    locationEnabled = locationEnabled,
                    onStopSelected = viewModel::onStopSelected,
                    onLineSelected = viewModel::onLineFocused,
                    modifier = Modifier.fillMaxSize(),
                )
                WithAppLocale(localeContext) {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding(),
                    ) {
                        LanguageBar(
                            selected = selectedLocale,
                            onSelect = { tag ->
                                selectedLocale = tag
                                AppLocale.save(context, tag)
                            },
                        )
                        LineFilterBar(
                            swatches = state.lineSwatches,
                            focusedLine = state.focusedLine,
                            onShowAll = viewModel::onShowAllLines,
                            onSelect = viewModel::onLineFocused,
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            mapReady.withReady { map, _ ->
                                val current = map.cameraPosition
                                map.animateCamera(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder(current)
                                            .bearing(0.0)
                                            .tilt(0.0)
                                            .build(),
                                    ),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 120.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = stringResource(R.string.compass_north),
                        )
                    }
                    FloatingActionButton(
                        onClick = ::onMyLocationClick,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = stringResource(R.string.my_location),
                        )
                    }
                    state.sheet?.let { sheet ->
                        StopSheet(sheet = sheet, onDismiss = viewModel::dismissSheet)
                    }
                    Text(
                        text = stringResource(R.string.osm_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = TransitColors.Slate400,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                    )
                }
            } else if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage ?: "",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WithAppLocale(
    localeContext: Context,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = this
    CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalConfiguration provides localeContext.resources.configuration,
    ) {
        scope.content()
    }
}

@Composable
private fun LanguageBar(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enLabel = stringResource(R.string.language_en)
            val esLabel = stringResource(R.string.language_es)
            FilterChip(
                selected = selected == "en",
                onClick = { onSelect("en") },
                label = { Text(enLabel) },
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .semantics { contentDescription = enLabel },
            )
            FilterChip(
                selected = selected == "es",
                onClick = { onSelect("es") },
                label = { Text(esLabel) },
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .semantics { contentDescription = esLabel },
            )
        }
    }
}

@Composable
private fun LineFilterBar(
    swatches: List<LineSwatch>,
    focusedLine: Int?,
    onShowAll: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allLabel = stringResource(R.string.lines_all)
            FilterChip(
                selected = focusedLine == null,
                onClick = onShowAll,
                label = { Text(allLabel) },
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = 40.dp, minHeight = 48.dp)
                        .semantics { contentDescription = allLabel },
            )
            swatches.forEach { swatch ->
                LineFilterChip(
                    swatch = swatch,
                    selected = focusedLine == swatch.line,
                    onClick = { onSelect(swatch.line) },
                )
            }
        }
    }
}

@Composable
private fun LineFilterChip(
    swatch: LineSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.line_label, swatch.line)
    val fill = Color(android.graphics.Color.parseColor(swatch.colour))
    val ink = if (fill.luminance() > 0.62f) TransitColors.SlateInkOnLightBadge else TransitColors.WhiteInk
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = swatch.line.toString(),
                    color = ink,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                )
            }
        },
        modifier =
            Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 48.dp)
                .semantics { contentDescription = label },
    )
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
