package es.elpuertobus.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItineraryStopEntity::class,
        RouteLineEntity::class,
        DepartureEntity::class,
        HolidayEntity::class,
        LineServiceRuleEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TransitDatabase : RoomDatabase() {
    abstract fun transitDao(): TransitDao

    companion object {
        fun open(context: Context): TransitDatabase =
            Room.databaseBuilder(context, TransitDatabase::class.java, "transit.db")
                .createFromAsset("transit.db")
                .build()
    }
}
