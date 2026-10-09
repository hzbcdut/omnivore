package app.omnivore.omnivore.feature.reader

import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.core.database.entities.SavedItemAndSavedItemLabelCrossRef
import app.omnivore.omnivore.core.database.entities.SavedItemLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Applies [labels] to [savedItemID] entirely on-device: new label names get a row
 * of their own, then the item's label cross-refs are swapped out for the new set.
 */
suspend fun setSavedItemLabels(
    dataService: DataService,
    savedItemID: String,
    labels: List<SavedItemLabel>
): Boolean {
    withContext(Dispatchers.IO) {
        // Figure out which of the labels are new
        val existingNamedLabels = dataService.db.savedItemLabelDao()
            .namedLabels(labels.map { it.name })
        val existingNames = existingNamedLabels.map { it.name }
        val newNamedLabels = labels.filter { !existingNames.contains(it.name) }

        dataService.db.savedItemLabelDao().insertAll(newNamedLabels.map {
            it.copy(
                savedItemLabelId = it.savedItemLabelId.ifEmpty { UUID.randomUUID().toString() },
                serverSyncStatus = 0
            )
        })

        val allNamedLabels = dataService.db.savedItemLabelDao()
            .namedLabels(labels.map { it.name })
        val crossRefs = allNamedLabels.map {
            SavedItemAndSavedItemLabelCrossRef(
                savedItemLabelId = it.savedItemLabelId,
                savedItemId = savedItemID
            )
        }
        dataService.db.savedItemAndSavedItemLabelCrossRefDao().deleteRefsBySavedItemId(savedItemID)
        dataService.db.savedItemAndSavedItemLabelCrossRefDao().insertAll(crossRefs)
    }
    return true
}
