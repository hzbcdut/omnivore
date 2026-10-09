package app.omnivore.omnivore.feature.editinfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.omnivore.omnivore.R
import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.utils.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class EditInfoState {
  DEFAULT,
  UPDATING,
  ERROR,
  UPDATED
}

@HiltViewModel
class EditInfoViewModel @Inject constructor(
  private val dataService: DataService,
  private val resourceProvider: ResourceProvider
) : ViewModel() {
  val state = MutableLiveData(EditInfoState.DEFAULT)

  var isLoading by mutableStateOf(false)
    private set

  var message by mutableStateOf<String?>(null)
    private set

  fun editInfo(itemId: String, title: String, author: String?, description: String?) {
    viewModelScope.launch {
      isLoading = true
      state.postValue(EditInfoState.UPDATING)

      val updated = withContext(Dispatchers.IO) {
        val savedItem = dataService.db.savedItemDao().findById(itemId) ?: return@withContext false
        val updatedSavedItem =
          savedItem.copy(title = title, author = author, descriptionText = description)
        dataService.db.savedItemDao().update(updatedSavedItem)
        true
      }

      isLoading = false

      if (updated) {
        message = resourceProvider.getString(R.string.edit_info_sheet_success)
        state.postValue(EditInfoState.UPDATED)
      } else {
        message = resourceProvider.getString(R.string.edit_info_sheet_error)
        state.postValue(EditInfoState.ERROR)
      }
    }
  }

  fun resetState() {
    state.postValue(EditInfoState.DEFAULT)
  }
}
