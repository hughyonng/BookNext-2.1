package com.booknext.app.data.service

import android.content.Context
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.util.TxtToEpubConverter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookFileService @Inject constructor(
    private val apiClient: ApiClient,
    @ApplicationContext private val context: Context,
) {

    suspend fun downloadBook(bookId: String, format: String): File = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, "books/${bookId}.${format}")
        cacheFile.parentFile?.mkdirs()
        val body = apiClient.api().streamBook(bookId)
        cacheFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        cacheFile
    }

    suspend fun convertToEpub(bookId: String): File = withContext(Dispatchers.IO) {
        val convertedFile = File(context.filesDir, "books/${bookId}_converted.epub")
        convertedFile.parentFile?.mkdirs()
        val body = apiClient.api().convertBook(bookId)
        convertedFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        convertedFile
    }

    fun convertDocToTxt(file: File): File? {
        return try {
            val outFile = File(context.filesDir, "books/${System.currentTimeMillis()}_extracted.txt")
            if (outFile.exists() && outFile.length() > 0) return outFile
            val text = XWPFDocument(file.inputStream()).use { doc ->
                doc.paragraphs.joinToString("\n") { it.text }
            }
            outFile.writeText(text)
            outFile
        } catch (e: Exception) {
            android.util.Log.w("BookFileService", "提取Word文本失败: ${e.message}")
            null
        }
    }

    fun convertTxtToEpub(txtFile: File, bookId: String, title: String): File? {
        val epubCache = File(context.cacheDir, "epub_from_txt_${bookId}.epub")
        return try {
            if (epubCache.exists() && epubCache.length() > 0) {
                epubCache
            } else {
                TxtToEpubConverter.convert(
                    txtFile = txtFile, outputFile = epubCache,
                    bookTitle = title.ifEmpty { txtFile.nameWithoutExtension },
                    bookId = bookId,
                )
                if (epubCache.exists() && epubCache.length() > 0) epubCache else null
            }
        } catch (e: Exception) {
            android.util.Log.w("BookFileService", "TXT→EPUB转换失败: ${e.message}")
            null
        }
    }

}
