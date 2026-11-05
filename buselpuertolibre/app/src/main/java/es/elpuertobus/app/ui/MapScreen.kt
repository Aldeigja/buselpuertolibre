package es.elpuertobus.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val sheetScaffoldState =
        rememberBottomSheetScaffoldState(
            bottomSheetState =
                rememberStandardBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    skipHiddenState = false,
                ),
        )
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(state.sheet?.stopId) {
        if (state.sheet != null) {
            sheetShown = true
            sheetScaffoldState.bottomSheetState.partialExpand()
        } else {
            sheetShown = false
            if (sheetScaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                sheetScaffoldState.bottomSheetState.hide()
            }
        }
    }
    val sheetValue = sheetScaffoldState.bottomSheetState.currentValue
    LaunchedEffect(sheetValue) {
        if (sheetShown && sheetValue == SheetValue.Hidden) {
            sheetShown = false
            viewModel.dismissSheet()
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = 240.dp,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContainerColor = Color(0xFFF8FAFC),
        sheetContentColor = TransitColors.Slate900,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 1.dp,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = TransitColors.Slate200) },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetContent = {
            state.sheet?.let { sheet -> StopSheet(sheet) }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    MapChrome(
                        selectedLocale = selectedLocale,
                        onLocale = { tag ->
                            selectedLocale = tag
                            AppLocale.save(context, tag)
                        },
                        swatches = state.lineSwatches,
                        focusedLine = state.focusedLine,
                        onShowAll = viewModel::onShowAllLines,
                        onSelectLine = viewModel::onLineFocused,
                        sheetClearance = innerPadding.calculateBottomPadding(),
                        onNorth = {
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
                        onMyLocation = ::onMyLocationClick,
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
private fun BoxScope.MapChrome(
    selectedLocale: String,
    onLocale: (String) -> Unit,
    swatches: List<LineSwatch>,
    focusedLine: Int?,
    onShowAll: () -> Unit,
    onSelectLine: (Int) -> Unit,
    sheetClearance: androidx.compose.ui.unit.Dp,
    onNorth: () -> Unit,
    onMyLocation: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageSwitch(selected = selectedLocale, onSelect = onLocale)
            LineFilterBar(
                swatches = swatches,
                focusedLine = focusedLine,
                onShowAll = onShowAll,
                onSelect = onSelectLine,
            )
        }
    }
    val aboveSheet = sheetClearance + 16.dp
    MapControlButton(
        onClick = onNorth,
        description = stringResource(R.string.compass_north),
        icon = Icons.Filled.Explore,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = aboveSheet + 56.dp),
    )
    MapControlButton(
        onClick = onMyLocation,
        description = stringResource(R.string.my_location),
        icon = Icons.Filled.MyLocation,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = aboveSheet),
    )
    Text(
        text = stringResource(R.string.osm_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = TransitColors.Slate400,
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = aboveSheet),
    )
}

@Composable
private fun MapControlButton(
    onClick: () -> Unit,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = TransitColors.Slate600)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSwitch(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val enLabel = stringResource(R.string.language_en)
    val esLabel = stringResource(R.string.language_es)
    val colors =
        SegmentedButtonDefaults.colors(
            activeContainerColor = TransitColors.Slate200,
            activeContentColor = TransitColors.Slate900,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = TransitColors.Slate600,
            activeBorderColor = TransitColors.Slate600,
            inactiveBorderColor = TransitColors.Slate200,
        )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.heightIn(min = 48.dp)) {
            SegmentedButton(
                selected = selected == "en",
                onClick = { onSelect("en") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors,
                modifier = Modifier.semantics { contentDescription = enLabel },
            ) {
                Text(enLabel, fontWeight = if (selected == "en") FontWeight.Bold else FontWeight.Medium)
            }
            SegmentedButton(
                selected = selected == "es",
                onClick = { onSelect("es") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors,
                modifier = Modifier.semantics { contentDescription = esLabel },
            ) {
                Text(esLabel, fontWeight = if (selected == "es") FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineFilterBar(
    swatches: List<LineSwatch>,
    focusedLine: Int?,
    onShowAll: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val allLabel = stringResource(R.string.lines_all)
            FilterChip(
                selected = focusedLine == null,
                onClick = onShowAll,
                label = {
                    Text(
                        allLabel,
                        fontWeight = if (focusedLine == null) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = slateChipColors(),
                border = slateChipBorder(focusedLine == null),
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = allLabel },
            )
        }
        items(swatches.size) { index ->
            val swatch = swatches[index]
            LineFilterChip(
                swatch = swatch,
                selected = focusedLine == swatch.line,
                onClick = { onSelect(swatch.line) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineFilterChip(
    swatch: LineSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.line_label, swatch.line)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { LineBadge(line = swatch.line, colour = swatch.colour, modifier = Modifier.size(28.dp)) },
        colors = slateChipColors(),
        border = slateChipBorder(selected),
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label },
    )
}

@Composable
private fun slateChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = TransitColors.Slate900,
        selectedContainerColor = TransitColors.Slate100,
        selectedLabelColor = TransitColors.Slate900,
    )

@Composable
private fun slateChipBorder(selected: Boolean) =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = TransitColors.Slate200,
        selectedBorderColor = TransitColors.Slate600,
        borderWidth = 1.dp,
        selectedBorderWidth = 1.5.dp,
    )
