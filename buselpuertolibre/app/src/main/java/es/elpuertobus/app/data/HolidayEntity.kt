package es.elpuertobus.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey @ColumnInfo(name = "date_iso") val dateIso: String,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "service_profile", defaultValue = "festivos")
    val serviceProfile: String,
)
