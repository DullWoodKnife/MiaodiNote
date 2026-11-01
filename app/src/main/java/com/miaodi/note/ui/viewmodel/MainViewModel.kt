package com.miaodi.note.ui.viewmodel

import androidx.lifecycle.*
import com.miaodi.note.data.MarkdownGuide
import com.miaodi.note.data.model.Article
import com.miaodi.note.data.model.Book
import com.miaodi.note.data.model.Chapter
import com.miaodi.note.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: NoteRepository,
    private val prefs: android.content.SharedPreferences? = null
) : ViewModel() {

    private val _currentBookId = MutableStateFlow<Long>(-1)
    private val _currentChapterId = MutableStateFlow<Long>(-1)
    private val _searchQuery = MutableStateFlow("")
    private val _sortType = MutableStateFlow(SortType.UPDATE_TIME_DESC)

    private companion object {
        /** 上次选中的书本 id（用于重启后恢复定位）。 */
        const val KEY_SELECTED_BOOK = "selected_book_id"

        /** 上次选中的章节 id（用于重启后恢复定位）。 */
        const val KEY_SELECTED_CHAPTER = "selected_chapter_id"
    }

    /** 记录当前选中的书本与章节，供下次启动时恢复，避免总是回到默认书本。 */
    private fun persistSelection(bookId: Long, chapterId: Long) {
        val p = prefs ?: return
        p.edit()
            .putLong(KEY_SELECTED_BOOK, bookId)
            .putLong(KEY_SELECTED_CHAPTER, chapterId)
            .apply()
    }

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
                // 首次启动：创建默认书本与默认章节
                val bookId = repository.insertBook(Book(name = "默认"))
                val chapterId = repository.insertChapter(Chapter(bookId = bookId, name = "默认"))
                _currentBookId.value = bookId
                _currentChapterId.value = chapterId
                persistSelection(bookId, chapterId)
            } else {
                // 恢复上次打开的书本；若已被删除则回退到第一本
                val savedBookId = prefs?.getLong(KEY_SELECTED_BOOK, -1L) ?: -1L
                val book = bookList.firstOrNull { it.id == savedBookId } ?: bookList.first()
                _currentBookId.value = book.id

                // 恢复该书本下上次打开的章节；若已被删除则回退到第一章节
                val chapterList = repository.getChaptersByBookOnce(book.id)
                val savedChapterId = prefs?.getLong(KEY_SELECTED_CHAPTER, -1L) ?: -1L
                val chapter = chapterList.firstOrNull { it.id == savedChapterId }
                    ?: chapterList.firstOrNull()
                val chapterId = chapter?.id ?: -1L
                _currentChapterId.value = chapterId
                persistSelection(book.id, chapterId)
            }
            seedMarkdownGuideIfNeeded()
        }
    }

    fun selectBook(bookId: Long) {
        _currentBookId.value = bookId
        viewModelScope.launch {
            val chapterList = repository.getChaptersByBookOnce(bookId)
            val chapterId = if (chapterList.isNotEmpty()) chapterList.first().id else -1L
            _currentChapterId.value = chapterId
            persistSelection(bookId, chapterId)
        }
    }

    fun selectChapter(chapterId: Long) {
        _currentChapterId.value = chapterId
        persistSelection(_currentBookId.value, chapterId)
    }

    /**
     * 确保默认章节中存在内置的 Markdown 教程文章（幂等，重复调用不会重复插入）。
     */
    private suspend fun seedMarkdownGuideIfNeeded() {
        val chapterId = _currentChapterId.value
        if (chapterId <= 0) return
        val existing = repository.getArticlesByChapterOnce(chapterId)
        if (existing.any { it.title == MarkdownGuide.TITLE }) return
        repository.insertArticle(
            Article(
                chapterId = chapterId,
                title = MarkdownGuide.TITLE,
                content = MarkdownGuide.CONTENT,
                isMarkdown = true,
                wordCount = MarkdownGuide.CONTENT.length,
                isNew = false
            )
        )
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
            val bookId = repository.insertBook(Book(name = name))
            // 新建后立即定位到新书本，避免用户还要手动再点一次
            selectBook(bookId)
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

    fun updateChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.updateChapter(chapter)
        }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.deleteChapter(chapter)
            // If the deleted chapter was the selected one, re-select the first chapter of the current book
            if (_currentChapterId.value == chapter.id) {
                val bookId = _currentBookId.value
                if (bookId > 0) {
                    val chapterList = repository.getChaptersByBookOnce(bookId)
                    val nextId = if (chapterList.isNotEmpty()) chapterList.first().id else -1L
                    _currentChapterId.value = nextId
                    persistSelection(bookId, nextId)
                } else {
                    _currentChapterId.value = -1
                }
            }
        }
    }

    fun getDefaultBookId(): Long {
        return books.value.firstOrNull()?.id ?: -1
    }

    /** 获取指定书本下的全部文章（用于导出功能） */
    suspend fun getArticlesByBookOnce(bookId: Long): List<Article> {
        return repository.getArticlesByBookOnce(bookId)
    }

    /** 按 id 读取文章（用于外部文档导入后跳转编辑页）。 */
    suspend fun getArticleById(articleId: Long): Article? {
        return repository.getArticleById(articleId)
    }

    /**
     * 大文档导入时先插入一条空正文文章（正文随后落盘），返回新文章 id。
     * 无可用章节时返回 -1。
     */
    suspend fun insertArticleShell(title: String): Long {
        var chapterId = _currentChapterId.value
        if (chapterId <= 0) {
            val bookId = _currentBookId.value.takeIf { it > 0 }
                ?: repository.getAllBooksOnce().firstOrNull()?.id
                ?: -1L
            if (bookId > 0) {
                chapterId = repository.getChaptersByBookOnce(bookId).firstOrNull()?.id ?: -1L
            }
        }
        if (chapterId <= 0) return -1L
        val article = Article(
            chapterId = chapterId,
            title = title,
            content = "",
            isMarkdown = true,
            isNew = true
        )
        return repository.insertArticle(article)
    }

    /** 回填大文档正文的落盘路径。 */
    suspend fun setArticleContentPath(articleId: Long, path: String) {
        repository.setContentExternalized(articleId, path)
    }

    /** 读取文章正文列内容（小文档内联正文；大文档返回空串）。 */
    suspend fun getArticleContent(articleId: Long): String {
        return repository.getContentOnly(articleId) ?: ""
    }

    /**
     * 将外部 Markdown 文档内容导入并保存到默认书本的当前章节。
     * 返回新建文章的 id；若无可用章节则返回 -1。
     */
    suspend fun importExternalMarkdown(title: String, content: String): Long {
        // 优先使用当前章节；若不可用则回退到默认（第一个）书本的第一个章节
        var chapterId = _currentChapterId.value
        if (chapterId <= 0) {
            val bookId = _currentBookId.value.takeIf { it > 0 }
                ?: repository.getAllBooksOnce().firstOrNull()?.id
                ?: -1L
            if (bookId > 0) {
                val chapterList = repository.getChaptersByBookOnce(bookId)
                chapterId = chapterList.firstOrNull()?.id ?: -1L
            }
        }
        if (chapterId <= 0) return -1L

        val article = Article(
            chapterId = chapterId,
            title = title,
            content = content,
            isMarkdown = true,
            wordCount = content.length,
            isNew = true
        )
        return repository.insertArticle(article)
    }

    enum class SortType {
        UPDATE_TIME_DESC, TITLE_ASC, TITLE_DESC, MANUAL
    }

    class Factory(
        private val repository: NoteRepository,
        private val prefs: android.content.SharedPreferences? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository, prefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
