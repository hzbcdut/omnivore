package app.omnivore.omnivore.core.data

import android.util.Log
import app.omnivore.omnivore.core.data.model.ServerSyncStatus
import app.omnivore.omnivore.core.database.entities.Highlight
import app.omnivore.omnivore.core.database.entities.SavedItemAndHighlightCrossRef
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

suspend fun DataService.createWebHighlight(jsonString: String, colorName: String?) {
    val params = Gson().fromJson(jsonString, CreateHighlightParams::class.java)
    val savedItemId = params.articleId ?: return

    withContext(Dispatchers.IO) {
        val highlight = Highlight(
            type = params.type ?: "HIGHLIGHT",
            highlightId = params.id ?: UUID.randomUUID().toString(),
            shortId = params.shortId ?: NanoId.generate(size = 14),
            quote = params.quote,
            prefix = null,
            suffix = null,
            patch = params.patch,
            annotation = params.annotation,
            createdAt = null,
            updatedAt = null,
            createdByMe = true,
            color = colorName ?: params.color,
            highlightPositionPercent = params.highlightPositionPercent,
            highlightPositionAnchorIndex = params.highlightPositionAnchorIndex
        )
        highlight.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue

        val crossRef = SavedItemAndHighlightCrossRef(
            highlightId = highlight.highlightId, savedItemId = savedItemId
        )

        db.highlightDao().insertAll(listOf(highlight))
        db.savedItemAndHighlightCrossRefDao().insertAll(listOf(crossRef))
    }
}

suspend fun DataService.createNoteHighlight(savedItemId: String, note: String): String {
    val shortId = NanoId.generate(size = 14)
    val createHighlightId = UUID.randomUUID().toString()

    withContext(Dispatchers.IO) {
        val highlight = Highlight(
            type = "NOTE",
            highlightId = createHighlightId,
            shortId = shortId,
            quote = null,
            prefix = null,
            suffix = null,
            patch = null,
            annotation = note,
            createdAt = null,
            updatedAt = null,
            createdByMe = true,
            color = null,
            highlightPositionAnchorIndex = 0,
            highlightPositionPercent = 0.0
        )
        highlight.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue

        val crossRef = SavedItemAndHighlightCrossRef(
            highlightId = createHighlightId, savedItemId = savedItemId
        )

        db.highlightDao().insertAll(listOf(highlight))
        db.savedItemAndHighlightCrossRefDao().insertAll(listOf(crossRef))
    }

    return createHighlightId
}

suspend fun DataService.mergeWebHighlights(jsonString: String) {
    val params = Gson().fromJson(jsonString, MergeHighlightsParams::class.java)
    val savedItemId = params.articleId ?: return
    Log.d("sync", "mergeWebHighlights: ${params.id}")

    withContext(Dispatchers.IO) {
        val highlight = Highlight(
            type = "HIGHLIGHT",
            highlightId = params.id ?: UUID.randomUUID().toString(),
            shortId = params.shortId ?: NanoId.generate(size = 14),
            quote = params.quote,
            prefix = params.prefix,
            suffix = params.suffix,
            patch = params.patch,
            annotation = params.annotation,
            createdAt = null,
            updatedAt = null,
            createdByMe = true,
            color = params.color,
            highlightPositionPercent = 0.0,
            highlightPositionAnchorIndex = 0
        )
        highlight.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue

        val crossRef = SavedItemAndHighlightCrossRef(
            highlightId = highlight.highlightId, savedItemId = savedItemId
        )

        db.highlightDao().insertAll(listOf(highlight))
        db.savedItemAndHighlightCrossRefDao().insertAll(listOf(crossRef))

        // The overlapped highlights are folded into the merged one.
        for (overlapId in params.overlapHighlightIdList.orEmpty()) {
            if (overlapId == highlight.highlightId) continue
            db.highlightDao().deleteById(overlapId)
        }
    }
}

suspend fun DataService.updateWebHighlight(jsonString: String) {
    val params = Gson().fromJson(jsonString, UpdateHighlightParams::class.java)

    if (params.highlightId == null || params.libraryItemId == null) {
        Log.d("error", "ERROR INVALID HIGHLIGHT DATA")
        return
    }

    withContext(Dispatchers.IO) {
        val highlight = db.highlightDao().findById(highlightId = params.highlightId)
            ?: return@withContext

        highlight.annotation = params.annotation
        highlight.serverSyncStatus = ServerSyncStatus.IS_SYNCED.rawValue
        db.highlightDao().update(highlight)
    }
}

suspend fun DataService.deleteHighlightFromJSON(jsonString: String) {
    val params = Gson().fromJson(jsonString, DeleteHighlightParams::class.java)
    deleteHighlight(params.libraryItemId, params.highlightId)
}

private suspend fun DataService.deleteHighlight(savedItemId: String, highlightID: String) {
    withContext(Dispatchers.IO) {
        db.highlightDao().deleteById(highlightID)
    }
}
