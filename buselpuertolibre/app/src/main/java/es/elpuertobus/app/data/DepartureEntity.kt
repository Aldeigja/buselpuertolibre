package es.elpuertobus.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "departures",
    foreignKeys = [
        ForeignKey(
            entity = ItineraryStopEntity::class,
            parentColumns = ["stop_id"],
            childColumns = ["bound_stop_id"],
        ),
    ],
    indices = [
        Index(
            value = ["bound_stop_id", "seconds_since_midnight"],
            name = "idx_departures_stop",
        ),
    ],
)
data class DepartureEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long?,
    @ColumnInfo(name = "line") val line: Int,
    @ColumnInfo(name = "service_day") val serviceDay: String,
    @ColumnInfo(name = "column_label") val columnLabel: String,
    @ColumnInfo(name = "seconds_since_midnight") val secondsSinceMidnight: Int,
    @ColumnInfo(name = "bound_stop_id") val boundStopId: String,
)
