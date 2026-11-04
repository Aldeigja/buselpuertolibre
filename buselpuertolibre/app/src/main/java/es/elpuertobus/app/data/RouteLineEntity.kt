package es.elpuertobus.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "route_lines",
    primaryKeys = ["line", "direction"],
)
data class RouteLineEntity(
    @ColumnInfo(name = "line") val line: Int,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "geometry_type") val geometryType: String,
    @ColumnInfo(name = "coordinates_json") val coordinatesJson: String,
    @ColumnInfo(name = "colour") val colour: String?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "osm_relation_id") val osmRelationId: Long?,
)
