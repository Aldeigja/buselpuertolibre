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
        val byLine = linkedMapOf<Int, MutableList<List<DoubleArray>>>()
        val colours = HashMap<Int, String>()
        for (line in lines.sortedWith(compareBy({ it.line }, { it.direction }))) {
            colours.putIfAbsent(line.line, line.colour ?: "#2563eb")
            byLine.getOrPut(line.line) { mutableListOf() }.addAll(readParts(line))
        }
        val routes =
            byLine.map { (line, parts) ->
                RoutePath(line, colours.getValue(line), parts)
            }
        val features = JSONArray()
        for (stripe in RouteStripes.build(routes)) {
            val coordinates = JSONArray()
            for (point in stripe.points) {
                coordinates.put(JSONArray(listOf(point[0], point[1])))
            }
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put(
                        "geometry",
                        JSONObject()
                            .put("type", "LineString")
                            .put("coordinates", coordinates),
                    )
                    .put(
                        "properties",
                        JSONObject()
                            .put("line", stripe.line)
                            .put("colour", stripe.colour)
                            .put("offset", stripe.offsetPx),
                    ),
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }

    private fun readParts(line: RouteLineEntity): List<List<DoubleArray>> {
        val root = JSONArray(line.coordinatesJson)
        if (line.geometryType == "LineString") {
            return listOf(readRing(root))
        }
        return List(root.length()) { index -> readRing(root.getJSONArray(index)) }
    }

    private fun readRing(ring: JSONArray): List<DoubleArray> =
        List(ring.length()) { index ->
            val point = ring.getJSONArray(index)
            doubleArrayOf(point.getDouble(0), point.getDouble(1))
        }
}
