package com.booknext.app.data.repository

import android.content.Context
import android.net.Uri
import com.booknext.app.data.local.db.BookDao
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.data.remote.MetadataService
import com.booknext.app.data.service.BookFileService
import com.booknext.app.data.service.CoverService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedBook(
    val book: BookEntity,
    val file: File,
    val format: String,
)

sealed class SyncResult {
    data object Success : SyncResult()
    data class Error(val msg: String) : SyncResult()
}

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val apiClient: ApiClient,
    private val accountPrefs: AccountPrefs,
    private val metadataService: MetadataService,
    private val bookFileService: BookFileService,
    private val coverService: CoverService,
    @ApplicationContext private val context: Context,
) {
    // ── Observables ────────────────────────────────────
    fun observeAll(): Flow<List<BookEntity>> = bookDao.observeAll()
    fun observeRecentlyRead(): Flow<List<BookEntity>> = bookDao.observeRecentlyRead()
    fun observeFinished(): Flow<List<BookEntity>> = bookDao.observeFinished()
    fun search(query: String): Flow<List<BookEntity>> = bookDao.search(query)
    fun observeByCategory(cat: String): Flow<List<BookEntity>> = bookDao.observeByCategory(cat)

    // ── Single CRUD ────────────────────────────────────
    suspend fun getById(id: String): BookEntity? = bookDao.getById(id)
    suspend fun upsert(book: BookEntity) = bookDao.upsert(book)
    suspend fun deleteById(bookId: String) = bookDao.deleteById(bookId)
    suspend fun toggleFavorite(bookId: String) = bookDao.toggleFavorite(bookId)
    suspend fun updateCategory(bookId: String, category: String) {
        bookDao.updateCategory(bookId, category)
        try {
            val body = category.toRequestBody("text/plain".toMediaType())
            apiClient.api().updateBook(id = bookId, category = body)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "远程同步分类失败: ${e.message}")
        }
    }
    suspend fun getByCategory(category: String): List<BookEntity> = bookDao.getByCategory(category)
    suspend fun updateCoverPath(bookId: String, path: String) = bookDao.updateCoverPath(bookId, path)

    // ── Progress ───────────────────────────────────────
    suspend fun updateProgress(id: String, progress: String, percent: Float, time: Long) =
        bookDao.updateProgress(id, progress, percent, time)
    suspend fun updateProgressNumeric(bookId: String, progress: String, time: Long) =
        bookDao.updateProgressNumeric(bookId, progress, time)
    suspend fun updateLastReadAt(bookId: String, ts: Long) = bookDao.updateLastReadAt(bookId, ts)
    suspend fun addReadingTime(bookId: String, seconds: Long) = bookDao.addReadingTime(bookId, seconds)
    suspend fun getTotalReadingSeconds(): Long? = bookDao.getTotalReadingSeconds()

    // ── Cloud sync ────────────────────────────────────
    suspend fun syncBooks(): SyncResult = withContext(Dispatchers.IO) {
        val url = accountPrefs.serverUrl.first()
        if (url.isEmpty()) return@withContext SyncResult.Success
        try {
            var page = 1
            val allRemoteBooks = mutableListOf<com.booknext.app.data.remote.dto.BookDto>()
            while (true) {
                val resp = apiClient.api().listBooks(page = page, pageSize = 100)
                allRemoteBooks.addAll(resp.books)
                if (resp.books.size < 100) break
                page++
            }
            val fmtT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val fmtS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val entities = allRemoteBooks.map { dto ->
                val uploadTs = try {
                    val raw = dto.uploadTime.take(19)
                    val t = fmtT.parse(raw)?.time
                    if (t != null) t else fmtS.parse(raw.replace("T", " "))?.time ?: 0L
                } catch (e: Exception) {
                        android.util.Log.w("BookRepository", "解析上传时间失败: ${e.message}")
                        0L
                    }
                BookEntity(
                    bookId = dto.bookId, title = dto.title, author = dto.author,
                    format = dto.format, fileSize = dto.size, uploadTime = uploadTs,
                    category = dto.category, hasCover = dto.hasCover, status = dto.status,
                    pageCount = dto.pageCount,
                )
            }
            val localMap = bookDao.observeAll().first().associateBy { it.bookId }
            entities.forEach { entity ->
                val old = localMap[entity.bookId]
                if (old != null) {
                    bookDao.upsert(entity.copy(
                        lastReadAt = old.lastReadAt, progress = old.progress,
                        readingPercent = old.readingPercent, lastReadTime = old.lastReadTime,
                        totalReadingSeconds = old.totalReadingSeconds, isFinished = old.isFinished,
                        isFavorite = old.isFavorite, filePath = old.filePath,
                        isDownloaded = old.isDownloaded, pendingSync = old.pendingSync,
                        readingSessionStart = old.readingSessionStart, lastSyncTime = old.lastSyncTime,
                        category = old.category,
                    ))
                } else {
                    bookDao.upsert(entity)
                }
            }
            val remoteIds = allRemoteBooks.map { it.bookId }.toSet()
            val localAll = bookDao.observeAll().first()
            localAll.filter { it.bookId !in remoteIds && !it.bookId.startsWith("local_") }
                .forEach { bookDao.deleteById(it.bookId) }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "同步失败")
        }
    }

    suspend fun deleteRemoteBook(bookId: String) {
        try {
            apiClient.api().deleteBook(bookId)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "删除远程书籍失败: ${e.message}")
        }
        bookDao.deleteById(bookId)
    }

    // ── Book loading (download + format conversion) ────
    suspend fun loadBook(bookId: String): PreparedBook = withContext(Dispatchers.IO) {
        val entity = bookDao.getById(bookId)
            ?: throw IllegalStateException("书籍信息不存在")

        if (entity.filePath != null && File(entity.filePath).exists()) {
            val localFile = File(entity.filePath)
            return@withContext prepareFile(entity, localFile)
        }

        val cacheFile = File(context.filesDir, "books/${bookId}.${entity.format}")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext prepareFile(entity, cacheFile)
        }

        val downloaded = bookFileService.downloadBook(bookId, entity.format)
        prepareFile(entity, downloaded)
    }

    private suspend fun prepareFile(entity: BookEntity, file: File): PreparedBook {
        bookDao.updateLastReadAt(entity.bookId, System.currentTimeMillis())
        val format = resolveFormat(entity, file) ?: return PreparedBook(entity, file, entity.format)
        return PreparedBook(entity, format.first, format.second)
    }

    private suspend fun resolveFormat(entity: BookEntity, file: File): Pair<File, String>? {
        return when {
            entity.format in listOf("mobi", "azw3") -> {
                val convertedFile = File(context.filesDir, "books/${entity.bookId}_converted.epub")
                if (convertedFile.exists() && convertedFile.length() > 0) {
                    convertedFile to "epub"
                } else {
                    try {
                        bookFileService.convertToEpub(entity.bookId)
                        convertedFile to "epub"
                    } catch (e: Exception) {
                        throw IllegalStateException("MOBI 转换失败：${e.message}")
                    }
                }
            }
            entity.format in listOf("doc", "docx") -> {
                val extracted = bookFileService.convertDocToTxt(file)
                (extracted ?: file) to "txt"
            }
            entity.format == "txt" -> {
                val epubFile = bookFileService.convertTxtToEpub(
                    txtFile = file, bookId = entity.bookId,
                    title = entity.title.ifEmpty { file.nameWithoutExtension },
                )
                if (epubFile != null) epubFile to "epub" else file to "txt"
            }
            else -> null
        }
    }

    // ── Covers ─────────────────────────────────────────
    suspend fun saveCoverFromUri(bookId: String, uri: Uri) = withContext(Dispatchers.IO) {
        coverService.saveCoverFromUri(bookId, uri)
        bookDao.updateCoverPath(bookId, "covers/$bookId.jpg")
    }

    suspend fun extractCoverFromFile(bookId: String, filePath: String, format: String) = withContext(Dispatchers.IO) {
        val bytes = coverService.extractCoverFromFile(filePath, format)
        if (bytes != null) {
            val path = coverService.saveCoverBytes(bookId, bytes)
            bookDao.updateCoverPath(bookId, path)
        }
    }

    // ── Metadata ───────────────────────────────────────
    suspend fun autoFillMetadata(): Int = withContext(Dispatchers.IO) {
        val apiKey = accountPrefs.googleBooksApiKey.first()
        if (apiKey.isBlank()) return@withContext 0
        val allBooks = bookDao.observeAll().first()
        val needsFill = allBooks.filter { book ->
            val a = book.author
            when {
                a.isNullOrBlank() -> true
                a.trim() == "未知" || a.trim() == "佚名" || a.trim().equals("unknown", true) -> true
                a.length > 40 -> true
                a.contains("/") || a.contains("\\") || a.contains(".") -> true
                a.trim() == book.title.trim().take(20) -> true
                else -> false
            }
        }
        val total = needsFill.size
        if (total == 0) return@withContext 0
        var updated = 0
        for ((i, book) in needsFill.withIndex()) {
            try {
                val meta = metadataService.lookup(book.title, apiKey)
                if (meta != null) {
                    val author = if (meta.authors.isNotEmpty()) meta.authors.joinToString("、") else book.author
                    var coverPath = book.coverPath
                    if (meta.coverUrl != null) {
                        try {
                            val bytes = metadataService.downloadCover(meta.coverUrl)
                            if (bytes != null) {
                                coverPath = coverService.saveCoverBytes(book.bookId, bytes)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("BookRepository", "下载封面图片失败: ${e.message}")
                        }
                    }
                    bookDao.upsert(book.copy(author = author, coverPath = coverPath ?: book.coverPath))
                    updated++
                }
            } catch (e: Exception) {
                android.util.Log.w("BookRepository", "元数据查询失败: ${e.message}")
            }
            if (i < total - 1) delay(250)
        }
        updated
    }
}
