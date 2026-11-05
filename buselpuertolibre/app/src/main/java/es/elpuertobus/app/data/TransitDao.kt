package es.elpuertobus.app.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TransitDao {
    @Query(
        """
        SELECT * FROM itinerary_stops
        WHERE lat IS NOT NULL AND lon IS NOT NULL
        ORDER BY line, column_side, sequence
        """,
    )
    suspend fun locatedStops(): List<ItineraryStopEntity>

    @Query(
        """
        SELECT * FROM itinerary_stops
        ORDER BY line, column_side, sequence
        """,
    )
    suspend fun itineraryStops(): List<ItineraryStopEntity>

    @Query("SELECT * FROM route_lines ORDER BY line, direction")
    suspend fun routeLines(): List<RouteLineEntity>

    @Query("SELECT * FROM itinerary_stops WHERE stop_id = :stopId LIMIT 1")
    suspend fun stopById(stopId: String): ItineraryStopEntity?

    @Query("SELECT * FROM departures WHERE bound_stop_id = :stopId")
    suspend fun departuresForStop(stopId: String): List<DepartureEntity>

    @Query("SELECT * FROM departures")
    suspend fun allDepartures(): List<DepartureEntity>

    @Query("SELECT date_iso FROM holidays")
    suspend fun holidayDates(): List<String>

    @Query("SELECT * FROM line_service_rules")
    suspend fun lineServiceRules(): List<LineServiceRuleEntity>

    @Query("SELECT value FROM meta WHERE key = 'holiday_attribution' LIMIT 1")
    suspend fun holidayAttribution(): String?
}
