package xyz.hisname.fireflyiii.repository.models.piggy

import androidx.room.Entity
import androidx.room.Fts4
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Fts4(contentEntity = PiggyData::class)
@Entity(tableName = "piggyFts")
data class PiggyFts(
        val piggyId: Long,
        val name: String
)