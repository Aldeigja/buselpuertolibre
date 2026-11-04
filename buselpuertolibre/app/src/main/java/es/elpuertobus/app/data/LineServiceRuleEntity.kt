package es.elpuertobus.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "line_service_rules")
data class LineServiceRuleEntity(
    @PrimaryKey @ColumnInfo(name = "line") val line: Int,
    @ColumnInfo(name = "rules_json") val rulesJson: String,
)
