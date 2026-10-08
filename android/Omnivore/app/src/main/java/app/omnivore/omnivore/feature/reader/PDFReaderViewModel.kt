package app.omnivore.omnivore.feature.reader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.omnivore.omnivore.core.data.CreateHighlightParams
import app.omnivore.omnivore.core.data.DataService
import app.omnivore.omnivore.core.data.DeleteHighlightParams
import app.omnivore.omnivore.core.data.NanoId
import app.omnivore.omnivore.core.data.MergeHighlightsParams
import app.omnivore.omnivore.core.data.ReadingProgressParams
import app.omnivore.omnivore.core.data.UpdateHighlightParams
import app.omnivore.omnivore.core.data.createWebHighlight
import app.omnivore.omnivore.core.data.deleteHighlightFromJSON
import app.omnivore.omnivore.core.data.getUriForInternalFile
import app.omnivore.omnivore.core.data.mergeWebHighlights
import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import app.omnivore.omnivore.core.data.updateWebHighlight
import app.omnivore.omnivore.core.database.entities.SavedItem
import com.google.gson.Gson
import com.pspdfkit.annotations.Annotation
import com.pspdfkit.annotations.HighlightAnnotation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

data class PDFReaderParams(
  val item: SavedItem,
  val articleContent: ArticleContent,
  val localFileUri: Uri
)

@HiltViewModel
class PDFReaderViewModel @Inject constructor(
  private val dataService: DataService,
  @ApplicationContext private val applicationContext: Context
): ViewModel() {
  var annotationUnderNoteEdit: Annotation? = null
  val pdfReaderParamsLiveData = MutableLiveData<PDFReaderParams?>(null)
  private var currentReadingProgress = 0.0
  private var currentReadingPageIndex = 0

  fun loadItem(slug: String, context: Context) {
    viewModelScope.launch {
      loadItemFromDB(slug, context)
    }
  }

  private suspend fun loadItemFromDB(slug: String, context: Context) {
    withContext(Dispatchers.IO) {
      val persistedItem = dataService.db.savedItemDao().getSavedItemWithLabelsAndHighlights(slug)
      persistedItem?.let { item ->
          Log.d("PDF", " - persistedItem?.let { item -> ${item}")
          Log.d("PDF", " - item.savedItem.localPDF -> ${item.savedItem.localPDF}")

          val localPdf =  getUriForInternalFile(context,"${item.savedItem.savedItemId}.pdf")
          val localFile = localPdf.toFile()

          if (localFile.exists()) {
            val articleContent = ArticleContent(
              title = item.savedItem.title,
              htmlContent = "",
              highlights = item.highlights,
              contentStatus = "SUCCEEDED",
              labelsJSONString = Gson().toJson(item.labels)
            )

            pdfReaderParamsLiveData.postValue(
              PDFReaderParams(
                item.savedItem,
                articleContent,
                  localPdf
              )
            )
        }
      }
    }
  }

  fun reset() {
    pdfReaderParamsLiveData.postValue(null)
  }

  fun syncPageChange(currentPageIndex: Int, totalPages: Int) {
    val rawProgress = ((currentPageIndex + 1).toDouble() / totalPages.toDouble()) * 100
    val percent = minOf(100.0, maxOf(0.0, rawProgress))
    currentReadingProgress = percent
    currentReadingPageIndex = currentPageIndex
    viewModelScope.launch {
      val savedItemId = pdfReaderParamsLiveData.value?.item?.savedItemId ?: return@launch
      withContext(Dispatchers.IO) {
        val savedItem = dataService.db.savedItemDao().findById(savedItemId) ?: return@withContext
        dataService.db.savedItemDao().update(
          savedItem.copy(
            readingProgress = percent,
            readingProgressAnchor = currentPageIndex,
            serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
          )
        )
      }
    }
  }

  fun syncHighlightUpdates(newAnnotation: Annotation, quote: String, overlapIds: List<String>, note: String? = null) {
    val itemID = pdfReaderParamsLiveData.value?.item?.savedItemId ?: return
    val highlightID = UUID.randomUUID().toString()
    val shortId = NanoId.generate(size=14)

    val jsonValues = JSONObject()
      .put("id", highlightID)
      .put("shortId", shortId)
      .put("quote", quote)
      .put("articleId", itemID)

    newAnnotation.customData = JSONObject().put("omnivoreHighlight", jsonValues)

    if (overlapIds.isNotEmpty()) {
      val params = MergeHighlightsParams(
        shortId = shortId,
        id = highlightID,
        quote = quote,
        patch = newAnnotation.toInstantJson(),
        articleId = itemID,
        prefix = null,
        suffix = null,
        overlapHighlightIdList = overlapIds,
        annotation = note
      )

      viewModelScope.launch {
        dataService.mergeWebHighlights(Gson().toJson(params))
      }
    } else {
      val params = CreateHighlightParams(
        type = "HIGHLIGHT",
        shortId = shortId,
        id = highlightID,
        quote = quote,
        patch = newAnnotation.toInstantJson(),
        articleId = itemID,
        annotation = note,
        highlightPositionAnchorIndex = currentReadingPageIndex,
        highlightPositionPercent = currentReadingProgress
      )

      viewModelScope.launch {
        dataService.createWebHighlight(Gson().toJson(params), colorName = null)
      }

      if (note != null) {
        storeUpdatedNoteLocally(newAnnotation, note)
      }
    }
  }

  fun updateHighlightNote(annotation: Annotation, note: String) {
    // Save the updated note locally
    storeUpdatedNoteLocally(annotation, note)

    val highlightID = pluckHighlightID(annotation) ?: return
    val itemID = pdfReaderParamsLiveData.value?.item?.savedItemId ?: return

    viewModelScope.launch {
      val params = UpdateHighlightParams(
        highlightId = highlightID,
        libraryItemId = itemID,
        annotation = note,
        sharedAt = null
      )
      dataService.updateWebHighlight(Gson().toJson(params))
    }
  }

  private fun storeUpdatedNoteLocally(annotation: Annotation, note: String) {
    val omnivoreHighlight = annotation.customData?.get("omnivoreHighlight") as? JSONObject
    omnivoreHighlight?.put("editedNote", note)
    omnivoreHighlight?.let {
      Log.d("pdf", "setting custom data: $omnivoreHighlight")
      annotation.customData = JSONObject().put("omnivoreHighlight", it)
    }
  }

  fun deleteHighlight(annotation: Annotation) {
    val highlightID = pluckHighlightID(annotation) ?: return
    val itemID = pdfReaderParamsLiveData.value?.item?.savedItemId ?: return
    viewModelScope.launch {
      val params = DeleteHighlightParams(highlightId = highlightID, libraryItemId = itemID)
      dataService.deleteHighlightFromJSON(Gson().toJson(params))
    }
  }

  fun overlappingAnnotations(newAnnotation: Annotation, existingAnnotations: List<Annotation>): List<Annotation> {
    val result: MutableList<Annotation> = mutableListOf()

    for (existingAnnotation in existingAnnotations) {
      if (hasOverlaps(newAnnotation, existingAnnotation)) {
        result.add(existingAnnotation)
      }
    }

    return result
  }

  fun pluckHighlightID(annotation: Annotation): String? {
    val omnivoreHighlight = annotation.customData?.get("omnivoreHighlight") as? JSONObject
    return omnivoreHighlight?.get("id") as? String
  }

  fun pluckExistingNote(annotation: Annotation): String? {
    val omnivoreHighlight = annotation.customData?.opt("omnivoreHighlight") as? JSONObject ?: return null

    val editedNote = omnivoreHighlight.opt("editedNote") as? String
    if (editedNote != null) { return editedNote }

    val shortID = omnivoreHighlight.get("shortId") as? String ?: return null

    pdfReaderParamsLiveData.value?.articleContent?.highlights?.let {
      val matchingHighlight = it.firstOrNull { highlight -> highlight.shortId == shortID }
      return matchingHighlight?.annotation
    }

    return null
  }

  private fun hasOverlaps(leftAnnotation: Annotation, rightAnnotation: Annotation): Boolean {
    for (leftRect in (leftAnnotation as? HighlightAnnotation)?.rects ?: listOf()) {
      for (rightRect in (rightAnnotation as? HighlightAnnotation)?.rects ?: listOf()) {
        if (rightRect.intersect(leftRect)) {
          return true
        }
      }
    }

    return false
  }
}
