package app.omnivore.omnivore.core.data

import app.omnivore.omnivore.core.database.OmnivoreDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DataService @Inject constructor(
    omnivoreDatabase: OmnivoreDatabase
) {
    val db = omnivoreDatabase

    fun clearDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            db.clearAllTables()
        }
    }
}
