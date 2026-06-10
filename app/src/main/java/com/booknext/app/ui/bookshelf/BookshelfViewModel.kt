package com.booknext.app.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booknext.app.data.local.db.BookEntity
import com.booknext.app.data.local.prefs.UiPrefs
import com.booknext.app.data.repository.BookRepository
import com.booknext.app.data.repository.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val uiPrefs: UiPrefs,
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