package es.elpuertobus.app.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class MapReadyHolder {
    @Volatile
    var map: MapLibreMap? = null

    @Volatile
    var style: Style? = null

    fun withReady(block: (MapLibreMap, Style) -> Unit) {
        val readyMap = map ?: return
        val readyStyle = style ?: return
        block(readyMap, readyStyle)
    }
}
