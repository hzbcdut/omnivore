package app.omnivore.omnivore.feature.library

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.core.data.archiveSavedItem
import app.omnivore.omnivore.core.data.deleteSavedItem
import app.omnivore.omnivore.core.data.model.LibraryQuery
import app.omnivore.omnivore.core.data.repository.LibraryRepository
import app.omnivore.omnivore.core.data.unarchiveSavedItem
import app.omnivore.omnivore.core.database.entities.SavedItemLabel
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import app.omnivore.omnivore.core.database.entities.TypeaheadCardData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dataService: DataService,
    private val repository: LibraryRepository,
    @ApplicationContext private val applicationContext: Context
) : ViewModel(), SavedItemViewModel {

    var isRefreshing = MutableStateFlow(false)
    val typeaheadMode = MutableStateFlow(true)
    val searchTextFlow = MutableStateFlow("")
    val searchItemsFlow = MutableStateFlow<List<TypeaheadCardData>>(emptyList())
    val itemsState = MutableStateFlow<List<SavedItemWithLabelsAndHighlights>>(emptyList())

    override val actionsMenuItemLiveData = MutableLiveData<SavedItemWithLabelsAndHighlights?>(null)

    init {
        searchTextFlow
            .debounce(300)
            .onEach {
                if (it.isBlank()){
                    searchItemsFlow.update { emptyList() }
                } else {
                    performTypeaheadSearch()
                }
            }.launchIn(viewModelScope)
    }

    fun updateSearchText(text: String) {
        typeaheadMode.update { true }
        searchTextFlow.update { text }
    }

    fun performSearch() {
        viewModelScope.launch {
            isRefreshing.update { true }
            itemsState.update { (listOf()) }
            typeaheadMode.update { false }

            loadUsingSearchAPI()
        }
    }

    private fun loadUsingSearchAPI() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val needle = searchTextFlow.value.trim()
                val query = LibraryQuery(
                    folders = listOf("inbox"),
                    allowedArchiveStates = listOf(0, 1),
                    sortKey = "newest",
                    requiredLabels = listOf(),
                    excludedLabels = listOf(),
                    allowedContentReaders = listOf("WEB", "PDF", "EPUB")
                )
                val matches = repository.getSavedItems(query).first().filter {
                    needle.isEmpty() || it.savedItem.title.contains(needle, ignoreCase = true)
                }
                itemsState.update { matches }
                isRefreshing.update { false }
            }
        }
    }

    private suspend fun performTypeaheadSearch() {
        isRefreshing.update { true }

        // Execute the search
        val searchResult = repository.getTypeaheadData(searchTextFlow.value)

        searchItemsFlow.update { searchResult }

        isRefreshing.update { false }
    }

    override fun handleSavedItemAction(itemId: String, action: SavedItemAction) {
        when (action) {
            SavedItemAction.Delete -> {
                viewModelScope.launch {
                    dataService.deleteSavedItem(itemId)
                }
            }

            SavedItemAction.Archive -> {
                viewModelScope.launch {
                    dataService.archiveSavedItem(itemId)
                }
            }

            SavedItemAction.Unarchive -> {
                viewModelScope.launch {
                    dataService.unarchiveSavedItem(itemId)
                }
            }

            SavedItemAction.EditLabels -> {
                // TODO
            }

            SavedItemAction.EditInfo -> {
                // TODO
            }

            SavedItemAction.MarkRead -> TODO()
            SavedItemAction.MarkUnread -> TODO()
        }
        actionsMenuItemLiveData.postValue(null)
    }

    fun updateSavedItemLabels(savedItemID: String, labels: List<SavedItemLabel>) {
        viewModelScope.launch {
            repository.setSavedItemLabels(savedItemID, labels)
        }
    }
}
