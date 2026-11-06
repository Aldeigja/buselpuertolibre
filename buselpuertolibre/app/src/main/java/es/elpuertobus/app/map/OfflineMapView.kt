package es.elpuertobus.app.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.RectF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val OPENING_LAT = 36.5997
private const val OPENING_LON = -6.2305
private const val OPENING_ZOOM = 13.5

@Composable
fun OfflineMapView(
    stopsGeoJson: String,
    routesGeoJson: String,
    focusedLine: Int?,
    selectedStopId: String?,
    mapReady: MapReadyHolder,
    locationEnabled: Boolean,
    onStopSelected: (String) -> Unit = {},
    onLineSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val onStopSelectedState = rememberUpdatedState(onStopSelected)
    val onLineSelectedState = rememberUpdatedState(onLineSelected)
    val focusedLineState = rememberUpdatedState(focusedLine)
    val selectedStopIdState = rememberUpdatedState(selectedStopId)
    val locationEnabledState = rememberUpdatedState(locationEnabled)
    val clickListenerAttached = remember { AtomicBoolean(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    DisposableEffect(mapView) {
        mapView.onCreate(Bundle())
        onDispose {
            mapView.onDestroy()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.isAttributionEnabled = true
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    map.setStyle(Style.Builder().fromJson(localBasemapStyleJson(context))) { style ->
                        applyOverlays(
                            style,
                            stopsGeoJson,
                            routesGeoJson,
                            focusedLineState.value,
                            selectedStopIdState.value,
                        )
                        mapReady.map = map
                        mapReady.style = style
                        MapLocation.setEnabled(
                            map,
                            style,
                            context,
                            locationEnabledState.value,
                        )
                        if (clickListenerAttached.compareAndSet(false, true)) {
                            map.addOnMapClickListener { point ->
                                val screen = map.projection.toScreenLocation(point)
                                val stopHit =
                                    resolveMarkerTap(
                                        map,
                                        screen,
                                        context.resources.displayMetrics.density,
                                        onStopSelectedState.value,
                                    )
                                if (stopHit) {
                                    return@addOnMapClickListener true
                                }
                                val reach =
                                    RectF(
                                        screen.x - 28f,
                                        screen.y - 28f,
                                        screen.x + 28f,
                                        screen.y + 28f,
                                    )
                                val line =
                                    map.queryRenderedFeatures(reach, "transit-routes-line")
                                        .firstOrNull()
                                        ?.getNumberProperty("line")
                                        ?.toInt()
                                if (line != null) {
                                    onLineSelectedState.value(line)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        map.cameraPosition =
                            CameraPosition.Builder()
                                .target(LatLng(OPENING_LAT, OPENING_LON))
                                .zoom(OPENING_ZOOM)
                                .build()
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                map.getStyle { style ->
                    mapReady.map = map
                    mapReady.style = style
                    applyOverlays(style, stopsGeoJson, routesGeoJson, focusedLine, selectedStopId)
                    MapLocation.setEnabled(map, style, context, locationEnabled)
                }
            }
        },
    )
}

private fun applyOverlays(
    style: Style,
    stopsGeoJson: String,
    routesGeoJson: String,
    focusedLine: Int?,
    selectedStopId: String?,
) {
    if (stopsGeoJson.isBlank() || routesGeoJson.isBlank()) {
        return
    }
    val routesSourceId = "transit-routes"
    val stopsSourceId = "transit-stops"
    (listOf(
        "transit-routes-line",
        "transit-routes-focus",
        "transit-stops-circle",
    ) + STOP_MARKER_LAYER_IDS).forEach { layerId ->
        if (style.getLayer(layerId) != null) {
            style.removeLayer(layerId)
        }
    }
    listOf(routesSourceId, stopsSourceId).forEach { sourceId ->
        if (style.getSource(sourceId) != null) {
            style.removeSource(sourceId)
        }
    }

    style.addSource(GeoJsonSource(routesSourceId, routesGeoJson))
    fun selectedLine(): Expression? =
        focusedLine?.let { line ->
            Expression.eq(Expression.get("line"), Expression.literal(line))
        }
    val routesLayer =
        LineLayer("transit-routes-line", routesSourceId).withProperties(
            PropertyFactory.lineColor(Expression.get("colour")),
            PropertyFactory.lineOffset(Expression.get("offset")),
            PropertyFactory.lineWidth(3.0f),
            PropertyFactory.lineOpacity(0.9f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
            PropertyFactory.lineSortKey(Expression.get("line")),
        )
    selectedLine()?.let { routesLayer.withFilter(it) }
    style.addLayer(routesLayer)

    style.addSource(GeoJsonSource(stopsSourceId, stopsGeoJson))
    setupStopMarkerLayers(
        style,
        legacyStopLayerIds = listOf("transit-stops-circle"),
        topRouteLayerId = "transit-routes-line",
        lineFilter = selectedLine(),
        selectedStopId = selectedStopId,
    )
}
