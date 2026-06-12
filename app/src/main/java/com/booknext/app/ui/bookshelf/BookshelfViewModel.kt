package com.booknext.app.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.local.prefs.AccountPrefs
import com.booknext.app.data.local.prefs.UiPrefs
import com.booknext.app.data.remote.ApiClient
import com.booknext.app.data.repository.BookRepository
import com.booknext.app.data.repository.SyncResult
import com.booknext.app.data.service.DownloadManager
import com.booknext.app.data.service.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val uiPrefs: UiPrefs,
    private val apiClient: ApiClient,
    private val accountPrefs: AccountPrefs,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = bookRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _metadataState = MutableStateFlow<MetadataState>(MetadataState.Idle)
    val metadataState: StateFlow<MetadataState> = _metadataState

    val folders: StateFlow<List<String>> = combine(
        books,
        uiPrefs.emptyFolders,
    ) { books, emptyFolders ->
        val fromBooks = books
            .mapNotNull { it.category.ifEmpty { null } }
            .filter { it != "__ocr__" }
            .toSet()
        (fromBooks + emptyFolders).sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBooks: StateFlow<List<BookEntity>> = combine(
        books, _selectedFolder, _searchQuery
    ) { books, folder, query ->
        books.filter { book ->
            val matchFolder = folder == null || book.category == folder
            val matchQuery = query.isEmpty() ||
                book.title.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true)
            matchFolder && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentBooks: StateFlow<List<BookEntity>> = bookRepository.observeRecentlyRead()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 暴露下载进度供书架 BookCard 订阅
    val downloads: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloads

    init {
        syncBooks()
    }

    fun syncBooks() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            when (val result = bookRepository.syncBooks()) {
                is SyncResult.Success -> _syncState.value = SyncState.Success
                is SyncResult.Error -> _syncState.value = SyncState.Error(result.msg)
            }
        }
    }

    fun selectFolder(folder: String?) { _selectedFolder.value = folder }
    fun onSearch(query: String) { _searchQuery.value = query }

    fun createFolder(name: String) {
        viewModelScope.launch { uiPrefs.addEmptyFolder(name) }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch {
            val booksInFolder = bookRepository.getByCategory(name)
            booksInFolder.forEach { book ->
                bookRepository.updateCategory(book.bookId, "")
            }
            uiPrefs.removeEmptyFolder(name)
        }
    }

    fun addBookToFolder(bookId: String, folderName: String) {
        viewModelScope.launch {
            bookRepository.updateCategory(bookId, folderName)
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteRemoteBook(bookId)
        }
    }

    fun toggleFavorite(bookId: String) {
        viewModelScope.launch { bookRepository.toggleFavorite(bookId) }
    }

    fun autoFillMetadata() {
        _metadataState.value = MetadataState.Running(0, 0)
        viewModelScope.launch(Dispatchers.IO) {
            val updated = bookRepository.autoFillMetadata()
            _metadataState.value = MetadataState.Done(updated)
        }
    }

    fun resetMetadataState() { _metadataState.value = MetadataState.Idle }

    fun saveCoverFromUri(bookId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            bookRepository.saveCoverFromUri(bookId, uri)
        }
    }

    fun extractCoverFromFile(bookId: String, filePath: String, format: String) {
        viewModelScope.launch {
            bookRepository.extractCoverFromFile(bookId, filePath, format)
        }
    }

    fun cancelDownload(bookId: String) {
        downloadManager.cancelDownload(bookId)
    }

    // ── 上传到云盘（本地书→云端） ──
    fun uploadToCloud(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = bookRepository.getById(bookId) ?: return@launch
                val file = java.io.File(entity.filePath ?: return@launch)
                if (!file.exists()) return@launch

                val body = file.asRequestBody("application/octet-stream".toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", file.name, body)
                val titleBody = entity.title.toRequestBody("text/plain".toMediaType())
                val authorBody = entity.author.ifEmpty { "未知" }.toRequestBody("text/plain".toMediaType())
                val ocrBody = "false".toRequestBody("text/plain".toMediaType())

                apiClient.api().uploadBook(filePart, titleBody, authorBody, ocrBody)
            } catch (e: Exception) {
                android.util.Log.w("BookshelfVM", "上传失败: ${e.message}")
            }
        }
    }

    // ── 下载到本地（云端书→本地） ──
    fun downloadToLocal(bookId: String, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = bookRepository.getById(bookId) ?: return@launch
                val baseUrl = accountPrefs.serverUrl.first().trimEnd('/')
                val apiKey = accountPrefs.apiKey.first()
                if (baseUrl.isBlank() || apiKey.isBlank()) return@launch

                val safeName = entity.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                val localDir = java.io.File(context.filesDir, "local_books")
                localDir.mkdirs()
                val destFile = java.io.File(localDir, "$safeName.${entity.format}")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/api/stream/$bookId?k=$apiKey")
                    .addHeader("Authorization", "Bearer $apiKey").build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@launch

                response.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                bookRepository.upsert(entity.copy(filePath = destFile.absolutePath, isDownloaded = true))
            } catch (e: Exception) {
                android.util.Log.w("BookshelfVM", "下载失败: ${e.message}")
            }
        }
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Loading : SyncState()
    data object Success : SyncState()
    data class Error(val msg: String) : SyncState()
}

sealed class MetadataState {
    data object Idle : MetadataState()
    data class Running(val current: Int, val total: Int) : MetadataState()
    data class Done(val updated: Int) : MetadataState()
}