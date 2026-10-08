package app.omnivore.omnivore.core.data.repository.impl

import android.content.Context
import app.omnivore.omnivore.core.data.loadLibraryItemContent
import app.omnivore.omnivore.core.data.model.LibraryQuery
import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import app.omnivore.omnivore.core.data.repository.LibraryRepository
import app.omnivore.omnivore.core.database.dao.SavedItemAndSavedItemLabelCrossRefDao
import app.omnivore.omnivore.core.database.dao.SavedItemDao
import app.omnivore.omnivore.core.database.dao.SavedItemLabelDao
import app.omnivore.omnivore.core.database.entities.SavedItemAndSavedItemLabelCrossRef
import app.omnivore.omnivore.core.database.entities.SavedItemLabel
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import app.omnivore.omnivore.core.database.entities.TypeaheadCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class LibraryRepositoryImpl @Inject constructor(
    private val savedItemDao: SavedItemDao,
    private val savedItemLabelDao: SavedItemLabelDao,
    private val savedItemAndSavedItemLabelCrossRefDao: SavedItemAndSavedItemLabelCrossRefDao
): LibraryRepository {

    override fun getSavedItems(query: LibraryQuery): Flow<List<SavedItemWithLabelsAndHighlights>> =
        savedItemDao.filteredLibraryData(
            folders = query.folders,
            query.allowedArchiveStates,
            query.sortKey,
            hasRequiredLabels = query.requiredLabels.size,
            hasExcludedLabels = query.excludedLabels.size,
            query.requiredLabels,
            query.excludedLabels,
            query.allowedContentReaders
        )

    override fun getSavedItemsLabels(): Flow<List<SavedItemLabel>> = savedItemLabelDao.getSavedItemLabels()

    override suspend fun getLabels(): List<SavedItemLabel> = savedItemLabelDao.allLabels()

    override suspend fun insertAllLabels(labels: List<SavedItemLabel>) {
        savedItemLabelDao.insertAll(labels)
    }

    override suspend fun getTypeaheadData(query: String): List<TypeaheadCardData> {
        return withContext(Dispatchers.IO) {
            savedItemDao.getTypeaheadData(query)
        }
    }

    override suspend fun updateReadingProgress(
        itemId: String,
        readingProgressPercentage: Double,
        readingProgressAnchorIndex: Int
    ) {
        val savedItem = savedItemDao.findById(itemId) ?: return
        val updatedItem = savedItem.copy(
            readingProgress = readingProgressPercentage,
            readingProgressAnchor = readingProgressAnchorIndex,
            serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        )
        savedItemDao.update(updatedItem)
    }

    override suspend fun setSavedItemLabels(
        itemId: String,
        labels: List<SavedItemLabel>
    ): Boolean {
        // Figure out which of the labels are new
        val existingNamedLabels = savedItemLabelDao.namedLabels(labels.map { it.name })
        val existingNames = existingNamedLabels.map { it.name }
        val newNamedLabels = labels.filter { !existingNames.contains(it.name) }

        savedItemLabelDao.insertAll(newNamedLabels.map {
            it.copy(
                savedItemLabelId = it.savedItemLabelId.ifEmpty { UUID.randomUUID().toString() },
                serverSyncStatus = 0
            )
        })

        val allNamedLabels = savedItemLabelDao.namedLabels(labels.map { it.name })
        val crossRefs = allNamedLabels.map {
            SavedItemAndSavedItemLabelCrossRef(
                savedItemLabelId = it.savedItemLabelId,
                savedItemId = itemId
            )
        }
        savedItemAndSavedItemLabelCrossRefDao.deleteRefsBySavedItemId(itemId)
        savedItemAndSavedItemLabelCrossRefDao.insertAll(crossRefs)

        return true
    }

    override suspend fun createNewSavedItemLabel(labelName: String, hexColorValue: String) {
        val savedItemLabel = SavedItemLabel(
            savedItemLabelId = UUID.randomUUID().toString(),
            name = labelName,
            color = hexColorValue,
            createdAt = null,
            labelDescription = null
        )
        savedItemLabelDao.insertAll(listOf(savedItemLabel))
    }

    override suspend fun isSavedItemContentStoredInDB(context: Context, slug: String): Boolean {
        val existingItem = savedItemDao.getSavedItemWithLabelsAndHighlights(slug)
        existingItem?.savedItem?.savedItemId?.let { savedItemId ->
            val htmlContent = loadLibraryItemContent(context, savedItemId)
            return (htmlContent ?: "").length > 10
        }
        return false
    }

    override suspend fun deleteSavedItem(itemID: String) {
        val savedItem = savedItemDao.findById(itemID = itemID) ?: return
        savedItemDao.deleteById(itemID)
    }

    override suspend fun archiveSavedItem(itemID: String) {
        val savedItem = savedItemDao.findById(itemID = itemID) ?: return

        savedItem.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        savedItem.isArchived = true
        savedItemDao.update(savedItem)
    }

    override suspend fun unarchiveSavedItem(itemID: String) {
        val savedItem = savedItemDao.findById(itemID = itemID) ?: return

        savedItem.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        savedItem.isArchived = false
        savedItemDao.update(savedItem)
    }
}
