package com.booknext.app.data.service

import android.content.Context
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.util.TxtToEpubConverter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookFileService @Inject constructor(
    private val apiClient: ApiClient,
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val accountPrefs: AccountPrefs,
) {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun downloadBook(bookId: String, format: String): File = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, "books/${bookId}.${format}")
        cacheFile.parentFile?.mkdirs()
        if (cacheFile.exists()) cacheFile.delete()

        val baseUrl = accountPrefs.serverUrl.first().trimEnd('/')
        val apiKey = accountPrefs.apiKey.first()
        val url = "$baseUrl/api/stream/$bookId?k=$apiKey"
        val request = okhttp3.Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        val response = okHttp.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val body = response.body ?: throw Exception("空响应")
        val totalBytes = body.contentLength().coerceAtLeast(0L)
        downloadManager.updateProgress(bookId, 0L, totalBytes, DownloadStatus.DOWNLOADING, cacheFile.absolutePath)
        var downloaded = 0L
        cacheFile.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(65536)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (downloaded % (65536 * 8) == 0L || (totalBytes > 0 && downloaded >= totalBytes)) {
                        val reportTotal = if (totalBytes > 0) totalBytes else downloaded.coerceAtLeast(1L)
                        downloadManager.updateProgress(bookId, downloaded, reportTotal, DownloadStatus.DOWNLOADING, cacheFile.absolutePath)
                    }
                }
            }
        }
        downloadManager.updateProgress(bookId, downloaded, downloaded.coerceAtLeast(1L), DownloadStatus.DONE, cacheFile.absolutePath)
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
