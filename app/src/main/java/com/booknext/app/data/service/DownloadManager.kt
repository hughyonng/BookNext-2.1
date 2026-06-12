package com.booknext.app.data.service

import android.content.Context
import com.booknext.app.data.local.db.BookDao
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.remote.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

data class DownloadProgress(
    val bookId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val filePath: String? = null,
)

enum class DownloadStatus { DOWNLOADING, PAUSED, DONE, ERROR }

@Singleton
class DownloadManager @Inject constructor(
    private val bookDao: BookDao,
    private val apiClient: ApiClient,
    @ApplicationContext private val context: Context,
) {
    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val progressFile = File(context.filesDir, "download_progress.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        restoreProgress()
    }

    private fun restoreProgress() {
        try {
            if (progressFile.exists()) {
                val json = progressFile.readText()
                val map = com.google.gson.Gson().fromJson(json, Map::class.java) as? Map<String, Any>
                if (map != null) {
                    val restored = mutableMapOf<String, DownloadProgress>()
                    @Suppress("UNCHECKED_CAST")
                    (map["entries"] as? List<Map<String, Any>>)?.forEach { entry ->
                        val bookId = entry["bookId"] as? String ?: return@forEach
                        val downloadedBytes = (entry["downloadedBytes"] as? Number)?.toLong() ?: 0L
                        val totalBytes = (entry["totalBytes"] as? Number)?.toLong() ?: 0L
                        val status = DownloadStatus.PAUSED
                        val filePath = entry["filePath"] as? String
                        restored[bookId] = DownloadProgress(bookId, downloadedBytes, totalBytes, status, filePath)
                    }
                    _downloads.value = restored
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistProgress() {
        try {
            val entries = _downloads.value.values.filter { it.status == DownloadStatus.PAUSED }
            val json = """{"entries":${com.google.gson.Gson().toJson(entries.map { mapOf(
                "bookId" to it.bookId,
                "downloadedBytes" to it.downloadedBytes,
                "totalBytes" to it.totalBytes,
                "filePath" to (it.filePath ?: ""),
            )})}}"""
            progressFile.parentFile?.mkdirs()
            progressFile.writeText(json)
        } catch (_: Exception) {}
    }

    fun startDownload(bookId: String, format: String, baseUrl: String, apiKey: String) {
        android.util.Log.i("DownloadManager", "startDownload: bookId=$bookId format=$format")
        val existing = _downloads.value[bookId]
        if (existing != null && existing.status == DownloadStatus.DOWNLOADING) {
            android.util.Log.i("DownloadManager", "已有进行中的任务 $bookId")
            return
        }

        val destFile = File(context.filesDir, "books/${bookId}.${format}")
        destFile.parentFile?.mkdirs()
        if (destFile.exists()) destFile.delete()

        _downloads.value = _downloads.value.toMutableMap().apply {
            put(bookId, DownloadProgress(bookId, 0L, 0L, DownloadStatus.DOWNLOADING, destFile.absolutePath))
        }

        scope.launch {
            try {
                val body = apiClient.api().streamBook(bookId)
                val totalBytes = body.contentLength().coerceAtLeast(0L)
                _downloads.value = _downloads.value.toMutableMap().apply {
                    put(bookId, DownloadProgress(bookId, 0L, totalBytes, DownloadStatus.DOWNLOADING, destFile.absolutePath))
                }
                var downloaded = 0L
                destFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(65536)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            if (downloaded % (65536 * 8) == 0L) {
                                val reportTotal = if (totalBytes > 0) totalBytes else downloaded.coerceAtLeast(1L)
                                _downloads.value = _downloads.value.toMutableMap().apply {
                                    put(bookId, DownloadProgress(bookId, downloaded, reportTotal, DownloadStatus.DOWNLOADING, destFile.absolutePath))
                                }
                            }
                        }
                    }
                }
                // 完成
                if (downloaded < 100) {
                    throw Exception("下载文件过小 ($downloaded bytes)")
                }
                delay(300)
                _downloads.value = _downloads.value.toMutableMap().apply {
                    put(bookId, DownloadProgress(bookId, downloaded, downloaded.coerceAtLeast(1L), DownloadStatus.DONE, destFile.absolutePath))
                }
                bookDao.markDownloaded(bookId, destFile.absolutePath)
                persistProgress()
            } catch (e: Exception) {
                _downloads.value = _downloads.value.toMutableMap().apply {
                    put(bookId, DownloadProgress(bookId, 0L, 0L, DownloadStatus.ERROR, destFile.absolutePath))
                }
                android.util.Log.w("DownloadManager", "下载失败: ${e.message}")
                // 删除无效文件
                if (destFile.exists()) destFile.delete()
            }
        }
    }

    // 检查某本书是否正在下载
    fun isDownloading(bookId: String): Boolean =
        _downloads.value[bookId]?.status == DownloadStatus.DOWNLOADING

    fun cancelDownload(bookId: String) {
        // 标记为 PAUSED，下次点继续
        val current = _downloads.value[bookId] ?: return
        _downloads.value = _downloads.value.toMutableMap().apply {
            put(bookId, current.copy(status = DownloadStatus.PAUSED))
        }
        persistProgress()
    }

    fun getProgress(bookId: String): DownloadProgress? = _downloads.value[bookId]

    /**
     * 供 BookFileService 在 Retrofit 下载过程中同步更新进度，
     * 让书架 BookCard 能显示进度环。
     */
    fun updateProgress(bookId: String, downloadedBytes: Long, totalBytes: Long, status: DownloadStatus, filePath: String? = null) {
        _downloads.value = _downloads.value.toMutableMap().apply {
            put(bookId, DownloadProgress(bookId, downloadedBytes, totalBytes, status, filePath))
        }
    }

    fun onDestroy() {
        // 标记所有进行中的为 PAUSED
        _downloads.value = _downloads.value.mapValues { (_, v) ->
            if (v.status == DownloadStatus.DOWNLOADING) v.copy(status = DownloadStatus.PAUSED) else v
        }
        persistProgress()
    }
}
