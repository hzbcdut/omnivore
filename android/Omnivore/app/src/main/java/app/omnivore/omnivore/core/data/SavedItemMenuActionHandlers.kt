package app.omnivore.omnivore.core.data

import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun DataService.deleteSavedItem(itemID: String) {
    withContext(Dispatchers.IO) {
        val savedItem = db.savedItemDao().findById(itemID = itemID) ?: return@withContext
        db.savedItemDao().deleteById(itemID)
    }
}

suspend fun DataService.archiveSavedItem(itemID: String) {
    withContext(Dispatchers.IO) {
        val savedItem = db.savedItemDao().findById(itemID = itemID) ?: return@withContext

        savedItem.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        savedItem.isArchived = true
        db.savedItemDao().update(savedItem)
    }
}

suspend fun DataService.unarchiveSavedItem(itemID: String) {
    withContext(Dispatchers.IO) {
        val savedItem = db.savedItemDao().findById(itemID = itemID) ?: return@withContext

        savedItem.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        savedItem.isArchived = false
        db.savedItemDao().update(savedItem)
    }
}
