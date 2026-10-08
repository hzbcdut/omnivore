package app.omnivore.omnivore.feature.library

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.omnivore.omnivore.R
import app.omnivore.omnivore.core.data.LocalHtmlImporter
import app.omnivore.omnivore.core.data.model.LibraryQuery
import app.omnivore.omnivore.core.data.repository.LibraryRepository
import app.omnivore.omnivore.core.database.entities.SavedItemLabel
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import app.omnivore.omnivore.core.datastore.DatastoreRepository
import app.omnivore.omnivore.core.datastore.lastUsedSavedItemFilter
import app.omnivore.omnivore.core.datastore.lastUsedSavedItemSortFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val datastoreRepository: DatastoreRepository,
    private val libraryRepository: LibraryRepository,
    private val localHtmlImporter: LocalHtmlImporter,
    @ApplicationContext private val applicationContext: Context
) : ViewModel(), SavedItemViewModel {

    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    private val folders = MutableStateFlow(listOf("inbox"))

    private val _libraryQuery = MutableStateFlow(
        LibraryQuery(
            folders = folders.value,
            allowedArchiveStates = listOf(0),
            sortKey = "newest",
            requiredLabels = listOf(),
            excludedLabels = listOf(),
            allowedContentReaders = listOf("WEB", "PDF", "EPUB")
        )
    )

    init {
        // Silent rescan of the user's chosen import directory, if one was set.
        viewModelScope.launch {
            localHtmlImporter.rescanPersistedTree()
        }
    }

    val uiState: StateFlow<LibraryUiState> = _libraryQuery.flatMapLatest { query ->
        libraryRepository.getSavedItems(query)
    }.map(LibraryUiState::Success).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = LibraryUiState.Loading
    )

    val appliedFilterState = MutableStateFlow(SavedItemFilter.INBOX)
    val appliedSortFilterLiveData = MutableStateFlow(SavedItemSortFilter.NEWEST)
    val bottomSheetState = MutableStateFlow(LibraryBottomSheetState.HIDDEN)

    val currentItem = mutableStateOf<String?>(null)

    val labelsState = libraryRepository.getSavedItemsLabels().stateIn(
        scope = viewModelScope, started = SharingStarted.Lazily, initialValue = listOf()
    )

    val activeLabels = MutableStateFlow<List<SavedItemLabel>>(listOf())

    override val actionsMenuItemLiveData = MutableLiveData<SavedItemWithLabelsAndHighlights?>(null)

    private fun loadInitialFilterValues() {
        updateSavedItemFilter(appliedFilterState.value)
    }

    /** Import a single local .html / .pdf into the library. */
    fun importHtmlUri(uri: Uri) {
        viewModelScope.launch {
            val item = localHtmlImporter.importUri(uri)
            if (item == null) {
                snackbarMessage = applicationContext.getString(R.string.library_view_model_snackbar_error)
            }
        }
    }

    /** Persist [uri] as the auto-scan directory and import its contents now. */
    fun chooseScanDir(uri: Uri) {
        viewModelScope.launch {
            localHtmlImporter.setScanDir(uri)
            localHtmlImporter.scanTree(uri)
        }
    }

    fun clearSnackbarMessage() {
        snackbarMessage = null
    }

    fun refresh() {
        load()
    }

    fun setBottomSheetState(state: LibraryBottomSheetState) {
        bottomSheetState.value = state
    }

    fun initialLoad() {
        load()
    }

    fun load() {
        loadInitialFilterValues()
    }

    /** Kept as a named entry point: the list's infinite-scroll hook calls this. */
    fun loadUsingSearchAPI() {
        viewModelScope.launch {
            localHtmlImporter.rescanPersistedTree()
        }
    }

    fun updateSavedItemFilter(filter: SavedItemFilter) {
        viewModelScope.launch {
            datastoreRepository.putString(lastUsedSavedItemFilter, filter.rawValue)
            appliedFilterState.value = filter
            handleFilterChanges()
        }
    }

    fun updateSavedItemSortFilter(filter: SavedItemSortFilter) {
        viewModelScope.launch {
            datastoreRepository.putString(lastUsedSavedItemSortFilter, filter.rawValue)
            appliedSortFilterLiveData.value = filter
            handleFilterChanges()
        }
    }

    fun updateAppliedLabels(labels: List<SavedItemLabel>) {
        viewModelScope.launch {
            activeLabels.value = labels
            handleFilterChanges()
        }
    }

    private fun handleFilterChanges() {
        val sortKey = when (appliedSortFilterLiveData.value) {
            SavedItemSortFilter.NEWEST -> "newest"
            SavedItemSortFilter.OLDEST -> "oldest"
            SavedItemSortFilter.RECENTLY_READ -> "recentlyRead"
            SavedItemSortFilter.RECENTLY_PUBLISHED -> "recentlyPublished"
        }

        val allowedArchiveStates = when (appliedFilterState.value) {
            SavedItemFilter.ALL -> listOf(0, 1)
            SavedItemFilter.ARCHIVED -> listOf(1)
            else -> listOf(0)
        }

        val allowedContentReaders = when (appliedFilterState.value) {
            SavedItemFilter.FILES -> listOf("PDF", "EPUB")
            else -> listOf("WEB", "PDF", "EPUB")
        }

        var requiredLabels = when (appliedFilterState.value) {
            SavedItemFilter.NEWSLETTERS -> listOf("Newsletter")
            SavedItemFilter.FEEDS -> listOf("RSS")
            else -> activeLabels.value.map { it.name }
        }

        activeLabels.value.let { it ->
            requiredLabels = requiredLabels + it.map { it.name }
        }

        val excludeLabels = when (appliedFilterState.value) {
            SavedItemFilter.NON_FEED -> listOf("Newsletter", "RSS")
            else -> listOf()
        }

        _libraryQuery.value = LibraryQuery(
            folders = folders.value,
            allowedArchiveStates = allowedArchiveStates,
            sortKey = sortKey,
            requiredLabels = requiredLabels,
            excludedLabels = excludeLabels,
            allowedContentReaders = allowedContentReaders
        )
    }

    override fun handleSavedItemAction(itemId: String, action: SavedItemAction) {
        when (action) {
            SavedItemAction.Delete -> {
                deleteSavedItem(itemId)
            }

            SavedItemAction.Archive -> {
                archiveSavedItem(itemId)
            }

            SavedItemAction.Unarchive -> {
                unarchiveSavedItem(itemId)
            }

            SavedItemAction.EditLabels -> {
                currentItem.value = itemId
                bottomSheetState.value = LibraryBottomSheetState.LABEL
            }

            SavedItemAction.EditInfo -> {
                currentItem.value = itemId
                bottomSheetState.value = LibraryBottomSheetState.EDIT
            }

            SavedItemAction.MarkRead -> {
                viewModelScope.launch {
                    libraryRepository.updateReadingProgress(itemId, 100.0, 0)
                }
            }

            SavedItemAction.MarkUnread -> {
                viewModelScope.launch {
                    libraryRepository.updateReadingProgress(itemId, 0.0, 0)
                }
            }
        }
        actionsMenuItemLiveData.postValue(null)
    }

    fun deleteSavedItem(itemID: String) {
        viewModelScope.launch {
            libraryRepository.deleteSavedItem(itemID)
        }
    }

    fun archiveSavedItem(itemID: String) {
        viewModelScope.launch {
            libraryRepository.archiveSavedItem(itemID)
        }
    }

    fun unarchiveSavedItem(itemID: String) {
        viewModelScope.launch {
            libraryRepository.unarchiveSavedItem(itemID)
        }
    }

    fun updateSavedItemLabels(savedItemId: String, labels: List<SavedItemLabel>) {
        viewModelScope.launch {
            val result = libraryRepository.setSavedItemLabels(
                itemId = savedItemId, labels = labels
            )
            snackbarMessage = if (result) {
                applicationContext.getString(R.string.library_view_model_snackbar_success)
            } else {
                applicationContext.getString(R.string.library_view_model_snackbar_error)
            }
            handleFilterChanges()
        }
    }

    fun createNewSavedItemLabel(labelName: String, hexColorValue: String) {
        viewModelScope.launch {
            libraryRepository.createNewSavedItemLabel(labelName, hexColorValue)
        }
    }

    fun currentSavedItemUnderEdit(): SavedItemWithLabelsAndHighlights? {
        currentItem.value?.let { itemID ->
            return (uiState.value as LibraryUiState.Success).items.first { it.savedItem.savedItemId == itemID }
        }
        return null
    }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Success(
        val items: List<SavedItemWithLabelsAndHighlights>,
    ) : LibraryUiState

    data object Error : LibraryUiState
}

enum class SavedItemAction {
    Delete, Archive, Unarchive, EditLabels, EditInfo, MarkRead, MarkUnread
}
