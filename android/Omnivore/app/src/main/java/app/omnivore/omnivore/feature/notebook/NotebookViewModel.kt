package app.omnivore.omnivore.feature.notebook

import androidx.lifecycle.*
import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.core.data.createNoteHighlight
import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import app.omnivore.omnivore.core.database.entities.Highlight
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val dataService: DataService,
) : ViewModel() {
    var highlightUnderEdit: Highlight? = null

    fun getLibraryItemById(savedItemId: String): LiveData<SavedItemWithLabelsAndHighlights> {
        return dataService.db.savedItemDao().getLibraryItemById(savedItemId)
    }

    fun addArticleNote(savedItemId: String, note: String) = viewModelScope.launch(Dispatchers.IO) {
            val savedItem = dataService.db.savedItemDao().getById(savedItemId)
            savedItem?.let { item ->
                val noteHighlight = item.highlights.firstOrNull { it.type == "NOTE" }
                noteHighlight?.let {
                    dataService.db.highlightDao().updateNote(
                        highlightId = noteHighlight.highlightId,
                        note = note,
                        serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
                    )
                } ?: run {
                    dataService.createNoteHighlight(savedItemId, note)
                }
            }
        }

    fun updateHighlightNote(highlightId: String, note: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            dataService.db.highlightDao().updateNote(
                highlightId = highlightId,
                note = note ?: "",
                serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
            )
        }
}
