package es.elpuertobus.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "itinerary_stops")
data class ItineraryStopEntity(
    @PrimaryKey @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "line") val line: Int,
    @ColumnInfo(name = "column_side") val columnSide: String,
    @ColumnInfo(name = "sequence") val sequence: Int,
    @ColumnInfo(name = "stop_name") val stopName: String,
    @ColumnInfo(name = "lat") val lat: Double?,
    @ColumnInfo(name = "lon") val lon: Double?,
    @ColumnInfo(name = "coord_source") val coordSource: String?,
)
