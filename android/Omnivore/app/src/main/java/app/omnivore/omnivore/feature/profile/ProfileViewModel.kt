package app.omnivore.omnivore.feature.profile

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.core.data.LocalHtmlImporter
import app.omnivore.omnivore.core.datastore.DatastoreRepository
import app.omnivore.omnivore.core.datastore.libraryLastSyncTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataService: DataService,
    private val datastoreRepo: DatastoreRepository,
    private val localHtmlImporter: LocalHtmlImporter
) : ViewModel() {

    var snackbarMessage by mutableStateOf("Resetting data...")

    var isResettingData by mutableStateOf(false)
    var isDataResetCompleted by mutableStateOf(false)

    fun resetDataCache() {
        isResettingData = true

        viewModelScope.launch {
            datastoreRepo.clearValue(libraryLastSyncTimestamp)
            dataService.clearDatabase()
            delay(1000)
            isResettingData = false

            if (!isDataResetCompleted) {
                isDataResetCompleted = true
            }
        }
    }

    /** Persist [uri] as the auto-scan directory and import its contents now. */
    fun chooseScanDir(uri: Uri) {
        viewModelScope.launch {
            localHtmlImporter.setScanDir(uri)
            localHtmlImporter.scanTree(uri)
            snackbarMessage = "Import directory saved"
        }
    }

    fun rescanNow() {
        viewModelScope.launch {
            val imported = localHtmlImporter.rescanPersistedTree()
            snackbarMessage = "Rescanned: $imported item(s)"
        }
    }
}
