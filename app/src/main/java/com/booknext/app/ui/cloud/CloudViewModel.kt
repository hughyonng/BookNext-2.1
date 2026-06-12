package com.booknext.app.ui.cloud

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booknext.app.data.local.db.BookDao
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.remote.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CloudFolder(
    val name: String,
    val displayName: String,
    val books: List<BookEntity>,
)

sealed class CloudUiState {
    object Loading : CloudUiState()
    data class Ready(
        val folders: List<CloudFolder>,
        val totalBytes: Long,
        val bookCount: Int,
    ) : CloudUiState()
    data class Error(val msg: String) : CloudUiState()
}

enum class TransferType { UPLOAD, DOWNLOAD }
enum class TransferStatus { RUNNING, SUCCESS, ERROR }

data class TransferItem(
    val id: String,
    val fileName: String,
    val type: TransferType,
    val bookId: String? = null,
    val sourceUri: String? = null, // 上传失败重试用
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.RUNNING,
    val errorMessage: String? = null,
    val taskId: String? = null,       // 后端返回的上传任务 ID
    val localPath: String? = null,     // 本地文件路径，用于复用已有文件
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val apiClient: ApiClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<CloudUiState>(CloudUiState.Loading)
    val state: StateFlow<CloudUiState> = _state

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers

    // 传输记录持久化
    private val transferFile = File(context.filesDir, "transfer_history.json")

    init {
        restoreTransfers()
    }

    private fun restoreTransfers() {
        try {
            if (transferFile.exists()) {
                val json = transferFile.readText()
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, TransferItem::class.java).type
                val saved: List<TransferItem> = com.google.gson.Gson().fromJson(json, type)
                if (saved.isNotEmpty()) {
                    _transfers.value = saved
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistTransfers() {
        try {
            transferFile.writeText(com.google.gson.Gson().toJson(_transfers.value))
        } catch (_: Exception) {}
    }

    init {
        // 不自动加载,由 CloudScreen 控制——登录后才触发 load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = CloudUiState.Loading
            var attempt = 0
            val maxAttempts = 3
            while (attempt < maxAttempts) {
                attempt++
                try {
                    val allBooks = mutableListOf<com.booknext.app.data.remote.dto.BookDto>()
                    var page = 1
                    while (true) {
                        val resp = apiClient.api().listBooks(page = page, pageSize = 100)
                        allBooks.addAll(resp.books)
                        if (allBooks.size >= resp.total) break
                        page++
                    }

                    val entities = allBooks.map { dto ->
                        BookEntity(
                            bookId = dto.bookId,
                            title = dto.title,
                            author = dto.author,
                            format = dto.format,
                            fileSize = dto.size,
                            uploadTime = 0L,
                            hasCover = dto.hasCover,
                            status = dto.status,
                            category = dto.category,
                            pageCount = dto.pageCount,
                        )
                    }
                    // 保留本地已有的 filePath/isDownloaded，不被云端空值覆盖
                    val localMap = bookDao.observeAll().first().associateBy { it.bookId }
                    val merged = entities.map { entity ->
                        val old = localMap[entity.bookId]
                        if (old != null && old.filePath != null) {
                            entity.copy(filePath = old.filePath, isDownloaded = old.isDownloaded)
                        } else entity
                    }
                    bookDao.upsertAll(merged)

                    val grouped = entities
                        .filter { it.category != "__ocr__" }
                        .groupBy { it.category.ifEmpty { "__root__" } }

                    val folders = mutableListOf<CloudFolder>()

                    grouped["__root__"]?.let { books ->
                        folders.add(CloudFolder("__root__", "未分类书籍", books))
                    }

                    grouped.entries
                        .filter { it.key != "__root__" }
                        .sortedBy { it.key }
                        .forEach { (name, books) ->
                            folders.add(CloudFolder(name, name, books))
                        }

                    val totalBytes = entities.sumOf { it.fileSize }

                    _state.value = CloudUiState.Ready(
                        folders = folders,
                        totalBytes = totalBytes,
                        bookCount = entities.size,
                    )
                    return@launch
                } catch (e: Exception) {
                    if (attempt >= maxAttempts) {
                        _state.value = CloudUiState.Error("加载失败：${e.message}")
                    } else {
                        delay(1000L * attempt)
                    }
                }
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri, baseUrl: String = "", apiKey: String = "") {
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(idx)
        } ?: "未知文件"

        val transferId = UUID.randomUUID().toString()
        val optimisticBookId = UUID.randomUUID().toString()
        val title = fileName.substringBeforeLast('.')
        val format = fileName.substringAfterLast('.', "bin").lowercase()

        // 乐观写入：立即在 Room 插入一条"上传中"的书记录
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.upsert(
                BookEntity(
                    bookId = optimisticBookId,
                    title = title,
                    author = "未知",
                    format = format,
                    status = "uploading",
                    uploadTime = System.currentTimeMillis(),
                )
            )
        }

        addTransfer(TransferItem(
            id = transferId,
            fileName = fileName,
            type = TransferType.UPLOAD,
            bookId = optimisticBookId,
            sourceUri = uri.toString(),
        ))

        viewModelScope.launch(Dispatchers.IO) {
            val ext = format
            val tmpFile = File(context.cacheDir, "${UUID.randomUUID()}.$ext")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                val totalBytes = tmpFile.length()
                updateTransfer(transferId) { it.copy(totalBytes = totalBytes) }

                val originalBody = tmpFile.asRequestBody("application/octet-stream".toMediaType())
                val progressBody = ProgressRequestBody(originalBody, totalBytes) { transferred ->
                    updateTransfer(transferId) { it.copy(transferredBytes = transferred) }
                }
                val filePart = MultipartBody.Part.createFormData("file", tmpFile.name, progressBody)
                val titleBody = title.toRequestBody("text/plain".toMediaType())
                val authorBody = "未知".toRequestBody("text/plain".toMediaType())
                val ocrBody = "false".toRequestBody("text/plain".toMediaType())

                val response = apiClient.api().uploadBook(filePart, titleBody, authorBody, ocrBody)
                val realBookId = response.bookId
                val taskId = response.taskId

                // 用真实 bookId 替换乐观记录（删旧插新）
                bookDao.deleteById(optimisticBookId)
                bookDao.upsert(
                    BookEntity(
                        bookId = realBookId,
                        title = title,
                        author = "未知",
                        format = format,
                        status = "uploading",
                        uploadTime = System.currentTimeMillis(),
                    )
                )
                updateTransfer(transferId) { it.copy(bookId = realBookId, taskId = taskId, transferredBytes = totalBytes) }

                // 如果没有 taskId（老后端兼容），直接标成功
                if (taskId == null) {
                    bookDao.getById(realBookId)?.let {
                        bookDao.upsert(it.copy(status = "ready"))
                    }
                    updateTransfer(transferId) { it.copy(status = TransferStatus.SUCCESS) }
                    delay(1500)
                    load()
                    return@launch
                }

                // 轮询任务状态，最多等 5 分钟（3次 × 10秒，指数退避）
                var pollDelay = 3000L
                repeat(6) { attempt ->
                    delay(pollDelay)
                    pollDelay = (pollDelay * 1.5).toLong().coerceAtMost(15000L)
                    try {
                        val statusResp = apiClient.api().getUploadStatus(taskId)
                        when (statusResp.status) {
                            "done" -> {
                                bookDao.getById(realBookId)?.let {
                                    bookDao.upsert(it.copy(status = "ready"))
                                }
                                updateTransfer(transferId) { it.copy(status = TransferStatus.SUCCESS) }
                                delay(1500)
                                load()
                                return@launch
                            }
                            "error" -> {
                                bookDao.deleteById(realBookId)
                                updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = statusResp.message) }
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("CloudViewModel", "轮询失败 attempt=$attempt: ${e.message}")
                    }
                }

                updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = "上传超时，请刷新书架确认") }

            } catch (e: Exception) {
                bookDao.deleteById(optimisticBookId)
                updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = e.message) }
            } finally {
                tmpFile.delete()
            }
        }
    }

    fun downloadBooks(context: Context, books: List<BookEntity>, baseUrl: String, apiKey: String) {
        val localDir = File(context.filesDir, "local_books")
        localDir.mkdirs()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)  // 不设超时，用户可长按取消
            .build()

        books.forEach { book ->
            val ext = book.format.ifEmpty { "epub" }
            val safeName = book.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val fileName = "$safeName.$ext"
            val transferId = UUID.randomUUID().toString()
            addTransfer(TransferItem(id = transferId, fileName = fileName, type = TransferType.DOWNLOAD, bookId = book.bookId, totalBytes = book.fileSize))

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val url = "${baseUrl}/api/stream/${book.bookId}?k=$apiKey"
                    val destFile = File(localDir, fileName)
                    // 不论是否存在，每次都重新下载（防止上次下载不完整）
                    if (destFile.exists()) destFile.delete()
                    destFile.parentFile?.mkdirs()
                    val request = okhttp3.Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = "HTTP ${response.code}") }
                        response.close()
                        return@launch
                    }
                    val body = response.body ?: run {
                        updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = "空响应") }
                        return@launch
                    }
                    val total = body.contentLength().coerceAtLeast(1L)
                    var transferred = 0L
                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buf = ByteArray(65536) // 64KB buffer for faster download
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                transferred += read
                                val t = transferred
                                updateTransfer(transferId) { it.copy(transferredBytes = t) }
                            }
                        }
                    }
                    bookDao.upsert(book.copy(filePath = destFile.absolutePath, isDownloaded = true))
                    updateTransfer(transferId) { it.copy(status = TransferStatus.SUCCESS, transferredBytes = total, localPath = destFile.absolutePath) }
                } catch (e: Exception) {
                    updateTransfer(transferId) { it.copy(status = TransferStatus.ERROR, errorMessage = e.message) }
                }
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            try {
                apiClient.api().deleteBook(bookId)
                bookDao.deleteById(bookId)
                load()
            } catch (_: Exception) {}
        }
    }

    fun moveBook(bookId: String, folderName: String) {
        viewModelScope.launch {
            try {
                val body = folderName.toRequestBody("text/plain".toMediaType())
                apiClient.api().updateBook(id = bookId, category = body)
                bookDao.updateCategory(bookId, folderName)
            } catch (_: Exception) {}
        }
    }

    fun clearCompletedTransfers() {
        _transfers.value = _transfers.value.filter { it.status == TransferStatus.RUNNING }
        persistTransfers()
    }

    fun deleteTransfer(id: String) {
        _transfers.value = _transfers.value.filter { it.id != id }
        persistTransfers()
    }

    fun cancelDownload(bookId: String) {
        // 标记对应的 DOWNLOADING 任务为 ERROR
        _transfers.value = _transfers.value.map { t ->
            if (t.bookId == bookId && t.status == TransferStatus.RUNNING && t.type == TransferType.DOWNLOAD) {
                t.copy(status = TransferStatus.ERROR, errorMessage = "已取消")
            } else t
        }
        persistTransfers()
    }

    fun retryTransfer(context: Context, item: TransferItem, baseUrl: String, apiKey: String) {
        when (item.type) {
            TransferType.UPLOAD -> {
                val uri = item.sourceUri?.let { Uri.parse(it) } ?: return
                _transfers.value = _transfers.value.filter { it.id != item.id }
                uploadFile(context, uri)
            }
            TransferType.DOWNLOAD -> {
                if (item.bookId == null) return
                _transfers.value = _transfers.value.filter { it.id != item.id }
                viewModelScope.launch {
                    val book = bookDao.observeAll().first().find { it.bookId == item.bookId } ?: return@launch
                    downloadBooks(context, listOf(book), baseUrl, apiKey)
                }
            }
        }
    }

    private fun addTransfer(item: TransferItem) {
        _transfers.value = listOf(item) + _transfers.value
        persistTransfers()
    }

    private fun updateTransfer(id: String, block: (TransferItem) -> TransferItem) {
        _transfers.value = _transfers.value.map { if (it.id == id) block(it) else it }
        persistTransfers()
    }
}

// ── 带上传进度的 RequestBody ──
class ProgressRequestBody(
    private val delegate: okhttp3.RequestBody,
    private val totalBytes: Long,
    private val onProgress: (Long) -> Unit,
) : okhttp3.RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = totalBytes
    override fun writeTo(sink: okio.BufferedSink) {
        val buffer = okio.Buffer()
        delegate.writeTo(buffer)
        var written = 0L
        while (buffer.size > 0) {
            val chunk = minOf(buffer.size, 4096L)
            sink.write(buffer, chunk)
            written += chunk
            onProgress(written)
        }
        sink.flush()
    }
}
