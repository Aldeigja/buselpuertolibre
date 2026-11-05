package es.elpuertobus.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import es.elpuertobus.app.locale.LocalLanguageTag
import es.elpuertobus.app.locale.appString
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
    var locationEnabled by remember { mutableStateOf(context.hasLocationFixPermission()) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var selectedLocale by remember { mutableStateOf(AppLocale.currentTag(context)) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) {
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
                        AppLocale.resourcesFor(context, selectedLocale).getString(R.string.location_unavailable),
                    )
                }
            }
        }

    fun onMyLocationClick() {
        if (context.hasLocationFixPermission()) {
            locationEnabled = true
            mapReady.withReady { map, style ->
                MapLocation.setEnabled(map, style, context, enabled = true, moveCamera = true)
            }
            return
        }
        if (permissionDeniedOnce) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    AppLocale.resourcesFor(context, selectedLocale).getString(R.string.location_unavailable),
                )
            }
            return
        }
        permissionLauncher.launch(LocationPermissions)
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
    var sheetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(sheetValue) {
        if (sheetValue != SheetValue.Hidden) {
            sheetWasOpen = true
        } else if (sheetWasOpen) {
            sheetWasOpen = false
            sheetShown = false
            viewModel.dismissSheet()
        }
    }

    CompositionLocalProvider(LocalLanguageTag provides selectedLocale) {
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
            key(selectedLocale) {
                state.sheet?.let { sheet -> StopSheet(sheet) }
            }
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
                key(selectedLocale) {
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
                    sheetClearance =
                        if (state.sheet != null && sheetValue != SheetValue.Hidden) {
                            innerPadding.calculateBottomPadding()
                        } else {
                            0.dp
                        },
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
    TransitTopFilterBar(
        swatches = swatches,
        focusedLine = focusedLine,
        onShowAll = onShowAll,
        onSelectLine = onSelectLine,
        selectedLocale = selectedLocale,
        onLocale = onLocale,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val aboveSheet = sheetClearance + 16.dp + if (sheetClearance == 0.dp) navigationInset else 0.dp
    MapControlButton(
        onClick = onNorth,
        description = appString(R.string.compass_north),
        icon = Icons.Filled.Explore,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = aboveSheet + 48.dp + 8.dp),
    )
    MapControlButton(
        onClick = onMyLocation,
        description = appString(R.string.my_location),
        icon = Icons.Filled.MyLocation,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = aboveSheet),
    )
    Text(
        text = appString(R.string.osm_attribution),
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
private fun TransitTopFilterBar(
    swatches: List<LineSwatch>,
    focusedLine: Int?,
    onShowAll: () -> Unit,
    onSelectLine: (Int) -> Unit,
    selectedLocale: String,
    onLocale: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, TransitColors.Slate200),
    ) {
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    AllLinesControl(selected = focusedLine == null, onClick = onShowAll)
                }
                items(swatches.size) { index ->
                    val swatch = swatches[index]
                    LineFilterControl(
                        swatch = swatch,
                        selected = focusedLine == swatch.line,
                        onClick = { onSelectLine(swatch.line) },
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height(24.dp).width(1.dp),
                color = TransitColors.Slate200,
            )
            LanguageControl(selected = selectedLocale, onSelect = onLocale)
        }
    }
}

@Composable
private fun AllLinesControl(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = appString(R.string.lines_all)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (selected) TransitColors.Slate900 else TransitColors.Slate100)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TransitColors.Slate600,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun LineFilterControl(
    swatch: LineSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = appString(R.string.line_label, swatch.line)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) TransitColors.Slate200 else Color.Transparent)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
    ) {
        LineBadge(line = swatch.line, colour = swatch.colour, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun LanguageControl(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val showingSpanish = selected == "es"
    val label = appString(if (showingSpanish) R.string.language_es else R.string.language_en)
    val nextTag = if (showingSpanish) "en" else "es"
    val nextName = appString(if (showingSpanish) R.string.language_en else R.string.language_es)
    val description = appString(R.string.switch_to_language, nextName)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(TransitColors.Slate900)
                .clickable { onSelect(nextTag) }
                .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

private val LocationPermissions =
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

private fun Context.hasLocationFixPermission(): Boolean =
    LocationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
