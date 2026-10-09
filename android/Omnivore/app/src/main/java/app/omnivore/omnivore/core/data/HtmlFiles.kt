package app.omnivore.omnivore.core.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File

// File-storage conventions shared by the local importer and both readers.
//   html  -> filesDir/<savedItemId>.html   (WebReaderViewModel.loadItemFromDB)
//   pdf   -> filesDir/<savedItemId>.pdf    (PDFReaderViewModel.loadItemFromDB)

fun writeToInternalStorage(context: Context, content: String, fileName: String) {
    try {
        context.openFileOutput(fileName, MODE_PRIVATE).use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
            Log.d("FileWrite", "File written successfully to internal storage.")
        }
    } catch (e: Exception) {
        Log.e("FileWrite", "Error writing file", e)
        throw e
    }
}

fun writeToInternalStorage(context: Context, bytes: ByteArray, fileName: String) {
    try {
        context.openFileOutput(fileName, MODE_PRIVATE).use { outputStream ->
            outputStream.write(bytes)
            outputStream.flush()
            Log.d("FileWrite", "File written successfully to internal storage.")
        }
    } catch (e: Exception) {
        Log.e("FileWrite", "Error writing file", e)
        throw e
    }
}

fun readFromInternalStorage(context: Context, fileName: String): String? {
    return try {
        // Read the whole stream in one go. Concatenating per line is O(n^2) in
        // the file size and blows up on multi-megabyte imports.
        context.openFileInput(fileName).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        Log.e("FileRead", "Error reading file", e)
        null
    }
}

fun getUriForInternalFile(context: Context, fileName: String): Uri {
    val file = File(context.filesDir, fileName)
    return file.toUri()
}

fun loadLibraryItemContent(context: Context, libraryItemId: String): String? {
    return try {
        readFromInternalStorage(context = context, fileName = "${libraryItemId}.html")
    } catch (e: Exception) {
        null
    }
}
