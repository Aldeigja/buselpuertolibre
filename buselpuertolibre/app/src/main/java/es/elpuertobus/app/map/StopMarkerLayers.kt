package es.elpuertobus.app.map

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleSortKey
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private val COLOR_MARKER_INK = Color.parseColor("#1E3A8A")
private val COLOR_UNTIMED_CORE = Color.parseColor("#FFFFFF")
private val COLOR_HALO = Color.parseColor("#FFFFFF")

private const val OPACITY_SELECTION_RING = 0.35f
private const val SELECTION_RING_WIDTH = 3.0f
private const val TAP_HALF_EXTENT_DP = 24f

internal const val STOPS_SOURCE_ID = "transit-stops"
private const val LAYER_SELECTION_RING = "stops-selection-ring"
private const val LAYER_HALO_BASE = "stops-halo-base"
private const val LAYER_CORE_MARKER = "stops-core-marker"

internal val STOP_MARKER_LAYER_IDS =
    listOf(LAYER_SELECTION_RING, LAYER_HALO_BASE, LAYER_CORE_MARKER)

private fun hasTimetable(): Expression = coalesce(get("has_timetable"), literal(false))

private fun byState(timed: Float, untimed: Float): Expression =
    switchCase(hasTimetable(), literal(timed), literal(untimed))

private fun zoomRamp(
    at12: Expression,
    at14: Expression,
    at16: Expression,
): Expression =
    interpolate(
        linear(),
        zoom(),
        stop(12.0f, at12),
        stop(14.0f, at14),
        stop(16.0f, at16),
    )

private fun drawOrder(): Expression = switchCase(hasTimetable(), literal(2.0f), literal(1.0f))

/**
 * Replaces the legacy stop circle with the halo, core, and selection ring.
 * Call after the route line layer and the stops source exist.
 */
fun setupStopMarkerLayers(
    style: Style,
    legacyStopLayerIds: List<String>,
    topRouteLayerId: String?,
    lineFilter: Expression?,
    selectedStopId: String?,
) {
    (legacyStopLayerIds + STOP_MARKER_LAYER_IDS).forEach { layerId ->
        if (style.getLayer(layerId) != null) {
            style.removeLayer(layerId)
        }
    }

    val haloRadius =
        zoomRamp(
            byState(3.75f, 3.55f),
            byState(5.20f, 5.50f),
            byState(8.00f, 8.50f),
        )
    val coreRadius =
        zoomRamp(
            byState(3.0f, 1.8f),
            byState(4.2f, 3.0f),
            byState(6.0f, 5.0f),
        )
    val untimedStroke =
        zoomRamp(
            byState(0.0f, 1.0f),
            byState(0.0f, 1.5f),
            byState(0.0f, 2.0f),
        )
    val transparent = color(Color.TRANSPARENT)

    val ring =
        CircleLayer(LAYER_SELECTION_RING, STOPS_SOURCE_ID).withProperties(
            circleRadius(haloRadius),
            circleColor(Color.TRANSPARENT),
            circleStrokeWidth(SELECTION_RING_WIDTH),
            circleStrokeColor(COLOR_MARKER_INK),
            circleStrokeOpacity(OPACITY_SELECTION_RING),
        )
    val halo =
        CircleLayer(LAYER_HALO_BASE, STOPS_SOURCE_ID).withProperties(
            circleRadius(haloRadius),
            circleColor(COLOR_HALO),
            circleOpacity(1f),
            circleSortKey(drawOrder()),
        )
    val core =
        CircleLayer(LAYER_CORE_MARKER, STOPS_SOURCE_ID).withProperties(
            circleRadius(coreRadius),
            circleColor(switchCase(hasTimetable(), color(COLOR_MARKER_INK), color(COLOR_UNTIMED_CORE))),
            circleStrokeWidth(untimedStroke),
            circleStrokeColor(switchCase(hasTimetable(), transparent, color(COLOR_MARKER_INK))),
            circleOpacity(1f),
            circleStrokeOpacity(1f),
            circleSortKey(drawOrder()),
        )

    if (topRouteLayerId != null && style.getLayer(topRouteLayerId) != null) {
        style.addLayerAbove(ring, topRouteLayerId)
    } else {
        style.addLayer(ring)
    }
    style.addLayerAbove(halo, LAYER_SELECTION_RING)
    style.addLayerAbove(core, LAYER_HALO_BASE)
    applyStopMarkerFilters(style, lineFilter, selectedStopId)
}

fun applyStopMarkerFilters(
    style: Style,
    lineFilter: Expression?,
    selectedStopId: String?,
) {
    val base: Expression = lineFilter ?: literal(true)
    style.getLayerAs<CircleLayer>(LAYER_HALO_BASE)?.setFilter(base)
    style.getLayerAs<CircleLayer>(LAYER_CORE_MARKER)?.setFilter(base)
    val ringFilter: Expression =
        if (selectedStopId.isNullOrEmpty()) {
            literal(false)
        } else {
            all(base, eq(get("id"), literal(selectedStopId)))
        }
    style.getLayerAs<CircleLayer>(LAYER_SELECTION_RING)?.setFilter(ringFilter)
}

private fun isTimed(feature: Feature): Boolean =
    runCatching { feature.getBooleanProperty("has_timetable") }.getOrNull() == true

fun resolveMarkerTap(
    map: MapLibreMap,
    tap: PointF,
    density: Float,
    onStopSelected: (String) -> Unit,
): Boolean {
    val half = TAP_HALF_EXTENT_DP * density
    val box = RectF(tap.x - half, tap.y - half, tap.x + half, tap.y + half)
    val features = map.queryRenderedFeatures(box, LAYER_CORE_MARKER, LAYER_HALO_BASE)
    if (features.isEmpty()) {
        return false
    }
    data class Candidate(val id: String, val distPx: Float, val timed: Boolean)
    val candidates =
        features.mapNotNull { feature ->
            val id = feature.getStringProperty("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val point = feature.geometry() as? Point ?: return@mapNotNull null
            val screen = map.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
            Candidate(id, hypot(screen.x - tap.x, screen.y - tap.y), isTimed(feature))
        }
    if (candidates.isEmpty()) {
        return false
    }
    val unique = candidates.groupBy { it.id }.map { (_, hits) -> hits.minBy { it.distPx } }
    val pick =
        unique
            .sortedWith(compareBy<Candidate> { it.distPx }.thenByDescending { it.timed })
            .first()
    onStopSelected(pick.id)
    return true
}
