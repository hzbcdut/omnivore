package app.omnivore.omnivore.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import app.omnivore.omnivore.core.database.dao.SavedItemDao
import app.omnivore.omnivore.core.database.dao.SavedItemWithLabelsAndHighlightsDao
import app.omnivore.omnivore.core.database.entities.SavedItem
import app.omnivore.omnivore.core.database.entities.SavedItemWithLabelsAndHighlights
import app.omnivore.omnivore.core.datastore.DatastoreRepository
import app.omnivore.omnivore.core.datastore.localScanDirUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLConnection
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The two document kinds the local reader can ingest. */
enum class LocalDocumentKind(val extension: String, val contentReader: String) {
    HTML("html", "WEB"),
    PDF("pdf", "PDF");
}

/**
 * Imports local .html / .pdf files into the Room library and drops the content on
 * internal storage under the ids the readers already expect
 * (`filesDir/<savedItemId>.html` / `filesDir/<savedItemId>.pdf`).
 *
 * // ponytail: uri 去重，内容变更靠重复导入刷新
 */
@Singleton
class LocalHtmlImporter @Inject constructor(
    private val savedItemDao: SavedItemDao,
    private val savedItemWithLabelsAndHighlightsDao: SavedItemWithLabelsAndHighlightsDao,
    private val datastoreRepository: DatastoreRepository,
    @ApplicationContext private val context: Context
) {
    /** Import a single document. Idempotent: a known uri refreshes content in place. */
    suspend fun importUri(uri: Uri): SavedItem? = withContext(Dispatchers.IO) {
        val kind = detectKind(uri) ?: return@withContext null
        val bytes = readBytes(uri) ?: return@withContext null

        val savedItemId = localIdFor(uri)
        val fileName = displayName(uri) ?: "$savedItemId.${kind.extension}"
        val title = if (kind == LocalDocumentKind.HTML) extractHtmlTitle(String(bytes, Charsets.UTF_8)) ?: fileName else fileName

        // Persist content under the ids both readers resolve on disk.
        writeToInternalStorage(context, bytes, "$savedItemId.${kind.extension}")

        val existing = savedItemDao.findById(savedItemId)
        val now = Instant.now().toString()
        val item = SavedItem(
            savedItemId = savedItemId,
            title = title,
            folder = "inbox",
            createdAt = existing?.createdAt ?: now,
            savedAt = existing?.savedAt ?: now,
            readAt = existing?.readAt,
            updatedAt = now,
            readingProgress = existing?.readingProgress ?: 0.0,
            readingProgressAnchor = existing?.readingProgressAnchor ?: 0,
            imageURLString = existing?.imageURLString,
            pageURLString = uri.toString(),
            descriptionText = existing?.descriptionText,
            publisherURLString = existing?.publisherURLString,
            siteName = existing?.siteName,
            author = existing?.author,
            publishDate = existing?.publishDate,
            slug = savedItemId,
            isArchived = existing?.isArchived ?: false,
            contentReader = kind.contentReader,
            serverSyncStatus = 0
        )

        savedItemWithLabelsAndHighlightsDao.insertAll(
            listOf(SavedItemWithLabelsAndHighlights(savedItem = item, labels = listOf(), highlights = listOf()))
        )
        item
    }

    /** Scan every .html / .pdf directly under [treeUri]. Returns the number of items (re)imported. */
    suspend fun scanTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        if (!root.isDirectory) return@withContext 0

        var imported = 0
        for (child in root.listFiles()) {
            if (!child.isFile) continue
            if (detectKind(child.uri) == null) continue
            if (importUri(child.uri) != null) imported++
        }
        Log.d("LocalHtmlImporter", "scanTree($treeUri) imported $imported")
        imported
    }

    /** Re-run the previously configured scan directory, if any. */
    suspend fun rescanPersistedTree(): Int {
        val stored = datastoreRepository.getString(localScanDirUri) ?: return 0
        return try {
            scanTree(Uri.parse(stored))
        } catch (e: Exception) {
            Log.e("LocalHtmlImporter", "rescanPersistedTree failed", e)
            0
        }
    }

    /** Remember [uri] as the auto-scan directory and keep read access across restarts. */
    suspend fun setScanDir(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Some providers refuse persistable grants; we still remember the uri.
            Log.w("LocalHtmlImporter", "takePersistableUriPermission failed", e)
        }
        datastoreRepository.putString(localScanDirUri, uri.toString())
    }

    private fun detectKind(uri: Uri): LocalDocumentKind? {
        val name = displayName(uri)?.lowercase(Locale.ROOT) ?: ""
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: URLConnection.guessContentTypeFromName(name)

        return when {
            mime == "application/pdf" || name.endsWith(".pdf") -> LocalDocumentKind.PDF
            mime == "text/html" || mime == "application/xhtml+xml" ||
                name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") -> LocalDocumentKind.HTML
            else -> null
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        when (uri.scheme) {
            "file" -> uri.toFile().readBytes()
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    } catch (e: Exception) {
        Log.e("LocalHtmlImporter", "failed to read $uri", e)
        null
    }

    private fun displayName(uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.toFile().name
        else -> DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun localIdFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "local-$hex"
    }

    private fun extractHtmlTitle(html: String): String? =
        titleRegex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        val titleRegex = Regex(
            "<title[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
