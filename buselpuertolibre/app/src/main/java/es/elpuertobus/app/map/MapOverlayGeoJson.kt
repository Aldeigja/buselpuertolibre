package es.elpuertobus.app.map

import es.elpuertobus.app.data.ItineraryStopEntity
import es.elpuertobus.app.data.RouteLineEntity
import org.json.JSONArray
import org.json.JSONObject

object MapOverlayGeoJson {
    fun stopsFeatureCollection(stops: List<ItineraryStopEntity>): String {
        val features = JSONArray()
        for (stop in stops) {
            val lat = stop.lat ?: continue
            val lon = stop.lon ?: continue
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put(
                        "geometry",
                        JSONObject()
                            .put("type", "Point")
                            .put("coordinates", JSONArray(listOf(lon, lat))),
                    )
                    .put(
                        "properties",
                        JSONObject()
                            .put("stop_id", stop.stopId)
                            .put("line", stop.line),
                    ),
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }

    fun routeLinesFeatureCollection(lines: List<RouteLineEntity>): String {
        val features = JSONArray()
        for (line in lines) {
            val coords = JSONArray(line.coordinatesJson)
            val geometry =
                JSONObject()
                    .put("type", line.geometryType)
                    .put("coordinates", coords)
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", geometry)
                    .put(
                        "properties",
                        JSONObject()
                            .put("line", line.line)
                            .put("direction", line.direction)
                            .put("colour", line.colour ?: "#2563eb"),
                    ),
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }
}
