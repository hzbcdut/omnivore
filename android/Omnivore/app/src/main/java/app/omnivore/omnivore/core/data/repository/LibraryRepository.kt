package app.omnivore.omnivore.core.data.repository

import android.content.Context
import app.omnivore.omnivore.core.data.model.LibraryQuery
import app.omnivore.omnivore.core.database.entities.SavedItemLabel
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import app.omnivore.omnivore.core.database.entities.TypeaheadCardData
import kotlinx.coroutines.flow.Flow

interface LibraryRepository  {

    fun getSavedItems(query: LibraryQuery): Flow<List<SavedItemWithLabelsAndHighlights>>

    fun getSavedItemsLabels(): Flow<List<SavedItemLabel>>

    suspend fun getLabels(): List<SavedItemLabel>

    suspend fun insertAllLabels(labels: List<SavedItemLabel>)

    suspend fun setSavedItemLabels(itemId: String, labels: List<SavedItemLabel>): Boolean

    suspend fun updateReadingProgress(
        itemId: String,
        readingProgressPercentage: Double,
        readingProgressAnchorIndex: Int
    )

    suspend fun createNewSavedItemLabel(labelName: String, hexColorValue: String)

    suspend fun isSavedItemContentStoredInDB(context: Context, slug: String): Boolean

    suspend fun deleteSavedItem(itemID: String)

    suspend fun archiveSavedItem(itemID: String)

    suspend fun unarchiveSavedItem(itemID: String)

    suspend fun getTypeaheadData(query: String): List<TypeaheadCardData>
}
