package com.miaodi.note.ui.viewmodel

import androidx.lifecycle.*
import com.miaodi.note.data.model.Article
import com.miaodi.note.data.model.Book
import com.miaodi.note.data.model.Chapter
import com.miaodi.note.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _currentBookId = MutableStateFlow<Long>(-1)
    private val _currentChapterId = MutableStateFlow<Long>(-1)
    private val _searchQuery = MutableStateFlow("")
    private val _sortType = MutableStateFlow(SortType.UPDATE_TIME_DESC)

    val sortType: StateFlow<SortType> = _sortType.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val books: StateFlow<List<Book>> = repository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBookId: StateFlow<Long> = _currentBookId.asStateFlow()
    val currentChapterId: StateFlow<Long> = _currentChapterId.asStateFlow()

    val chapters: StateFlow<List<Chapter>> = _currentBookId
        .flatMapLatest { bookId ->
            if (bookId > 0) repository.getChaptersByBook(bookId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articles: StateFlow<List<Article>> = combine(
        _currentChapterId,
        _sortType,
        _searchQuery
    ) { chapterId, sort, query ->
        Triple(chapterId, sort, query)
    }.flatMapLatest { (chapterId, sort, query) ->
        if (chapterId <= 0) {
            flowOf(emptyList())
        } else if (query.isNotBlank()) {
            repository.searchArticles(chapterId, query)
        } else {
            when (sort) {
                SortType.UPDATE_TIME_DESC -> repository.getArticlesByChapter(chapterId)
                SortType.TITLE_ASC -> repository.getArticlesByChapterOrderTitleAsc(chapterId)
                SortType.TITLE_DESC -> repository.getArticlesByChapterOrderTitleDesc(chapterId)
                SortType.MANUAL -> repository.getArticlesByChapterOrderManual(chapterId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _navigateToEdit = MutableLiveData<Long?>()
    val navigateToEdit: LiveData<Long?> = _navigateToEdit

    private val _showSortSheet = MutableLiveData<Boolean>()
    val showSortSheet: LiveData<Boolean> = _showSortSheet

    private val _pendingQuickNoteText = MutableStateFlow<String?>(null)
    val pendingQuickNoteText: StateFlow<String?> = _pendingQuickNoteText.asStateFlow()

    init {
        viewModelScope.launch {
            val bookList = repository.getAllBooksOnce()
            if (bookList.isEmpty()) {
                // Create default book
                val bookId = repository.insertBook(Book(name = "默认"))
                _currentBookId.value = bookId
                val chapterId = repository.insertChapter(Chapter(bookId = bookId, name = "默认"))
                _currentChapterId.value = chapterId
            } else {
                _currentBookId.value = bookList.first().id
                val chapterList = repository.getChaptersByBookOnce(bookList.first().id)
                if (chapterList.isNotEmpty()) {
                    _currentChapterId.value = chapterList.first().id
                }
            }
        }
    }

    fun selectBook(bookId: Long) {
        _currentBookId.value = bookId
        viewModelScope.launch {
            val chapterList = repository.getChaptersByBookOnce(bookId)
            _currentChapterId.value = if (chapterList.isNotEmpty()) chapterList.first().id else -1
        }
    }

    fun selectChapter(chapterId: Long) {
        _currentChapterId.value = chapterId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun onSortClick() {
        _showSortSheet.value = true
    }

    fun onSortSheetDismissed() {
        _showSortSheet.value = false
    }

    fun onArticleClick(articleId: Long) {
        _navigateToEdit.value = articleId
    }

    fun onEditNavigated() {
        _navigateToEdit.value = null
    }

    fun setPendingQuickNoteText(text: String?) {
        _pendingQuickNoteText.value = text
    }

    /**
     * Creates a new article in the current chapter.
     * Note: This is an async operation. The returned ID will be -1 until the coroutine completes.
     * Callers should observe [navigateToEdit] for the actual article ID after creation.
     */
    fun createNewArticle() {
        val chapterId = _currentChapterId.value
        if (chapterId > 0) {
            viewModelScope.launch {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd(HHmmss)", java.util.Locale.getDefault())
                val defaultTitle = dateFormat.format(java.util.Date())
                val article = Article(chapterId = chapterId, title = defaultTitle, content = "", isNew = true)
                val newId = repository.insertArticle(article)
                _navigateToEdit.postValue(newId)
            }
        }
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            repository.deleteArticle(article)
        }
    }

    fun deleteArticles(articles: List<Article>) {
        viewModelScope.launch {
            articles.forEach { repository.deleteArticle(it) }
        }
    }

    fun insertBook(name: String) {
        viewModelScope.launch {
            repository.insertBook(Book(name = name))
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
            // After deletion, reset to first available book
            val books = repository.getAllBooksOnce()
            if (books.isNotEmpty()) {
                selectBook(books.first().id)
            }
        }
    }

    fun insertChapter(bookId: Long, name: String) {
        viewModelScope.launch {
            repository.insertChapter(Chapter(bookId = bookId, name = name))
        }
    }

    fun getDefaultBookId(): Long {
        return books.value.firstOrNull()?.id ?: -1
    }

    enum class SortType {
        UPDATE_TIME_DESC, TITLE_ASC, TITLE_DESC, MANUAL
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
