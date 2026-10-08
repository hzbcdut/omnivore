package app.omnivore.omnivore.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity
@TypeConverters(StringListTypeConverter::class)
data class HighlightChange(
    @PrimaryKey val highlightId: String,
    val savedItemId: String,

    val type: String,
    var annotation: String?,
    val createdAt: String?,
    val createdByMe: Boolean = true,
    val markedForDeletion: Boolean = false,
    var patch: String?,
    var prefix: String?,
    var quote: String?,
    var serverSyncStatus: Int = ServerSyncStatus.IS_SYNCED.rawValue,
    val html: String?,
    var shortId: String,
    val suffix: String?,
    val updatedAt: String?,
    val color: String?,
    val highlightPositionPercent: Double?,
    val highlightPositionAnchorIndex: Int?,
    val overlappingIDs: List<String>?
)

class StringListTypeConverter {
    @TypeConverter
    fun listToString(data: List<String>?): String? {
        data?.let {
            return Gson().toJson(data)
        }
        return null
    }

    @TypeConverter
    fun stringToList(jsonString: String?): List<String>? {
        return if (jsonString.isNullOrEmpty()) {
            null
        } else {
            val itemType = object : TypeToken<List<String>>() {}.type
            return Gson().fromJson<List<String>>(jsonString, itemType)
        }
    }
}

