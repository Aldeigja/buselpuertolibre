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
import org.maplibre.android.style.layers.CircleLayer
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
    onStopSelected: (String) -> Unit = {},
    onLineSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val onStopSelectedState = rememberUpdatedState(onStopSelected)
    val onLineSelectedState = rememberUpdatedState(onLineSelected)
    val focusedLineState = rememberUpdatedState(focusedLine)
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
                    map.setStyle(Style.Builder().fromJson(localBasemapStyleJson(context))) { style ->
                        applyOverlays(style, stopsGeoJson, routesGeoJson, focusedLineState.value)
                        if (clickListenerAttached.compareAndSet(false, true)) {
                            map.addOnMapClickListener { point ->
                                val screen = map.projection.toScreenLocation(point)
                                val stopId =
                                    map.queryRenderedFeatures(screen, "transit-stops-circle")
                                        .firstOrNull()
                                        ?.getStringProperty("stop_id")
                                if (stopId != null) {
                                    onStopSelectedState.value(stopId)
                                } else {
                                    val reach =
                                        RectF(
                                            screen.x - 28f,
                                            screen.y - 28f,
                                            screen.x + 28f,
                                            screen.y + 28f,
                                        )
                                    val line =
                                        map.queryRenderedFeatures(
                                            reach,
                                            "transit-routes-focus",
                                            "transit-routes-line",
                                        ).firstOrNull()
                                            ?.getNumberProperty("line")
                                            ?.toInt()
                                    if (line != null) {
                                        onLineSelectedState.value(line)
                                    }
                                }
                                true
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
                    applyOverlays(style, stopsGeoJson, routesGeoJson, focusedLine)
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
) {
    if (stopsGeoJson.isBlank() || routesGeoJson.isBlank()) {
        return
    }
    val routesSourceId = "transit-routes"
    val stopsSourceId = "transit-stops"
    listOf(
        "transit-routes-line",
        "transit-routes-focus",
        "transit-stops-circle",
    ).forEach { layerId ->
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
    fun selectedLine(): Expression =
        Expression.eq(Expression.get("line"), Expression.literal(focusedLine ?: -1))
    style.addLayer(
        LineLayer("transit-routes-line", routesSourceId).withProperties(
            PropertyFactory.lineColor(Expression.get("colour")),
            PropertyFactory.lineOffset(Expression.get("offset")),
            PropertyFactory.lineWidth(if (focusedLine == null) 3.0f else 1.5f),
            PropertyFactory.lineOpacity(if (focusedLine == null) 0.9f else 0.25f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
            PropertyFactory.lineSortKey(Expression.get("line")),
        ),
    )
    style.addLayer(
        LineLayer("transit-routes-focus", routesSourceId)
            .withFilter(selectedLine())
            .withProperties(
                PropertyFactory.lineColor(Expression.get("colour")),
                PropertyFactory.lineOffset(Expression.get("offset")),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(1.0f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
    )

    style.addSource(GeoJsonSource(stopsSourceId, stopsGeoJson))
    style.addLayer(
        CircleLayer("transit-stops-circle", stopsSourceId).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor("#dc2626"),
            PropertyFactory.circleStrokeWidth(4f),
            PropertyFactory.circleStrokeColor("#ffffff"),
        ),
    )
}
