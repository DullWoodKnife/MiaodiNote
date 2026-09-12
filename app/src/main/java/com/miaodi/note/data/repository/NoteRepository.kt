package com.miaodi.note.data.repository

import com.miaodi.note.data.dao.ArticleDao
import com.miaodi.note.data.dao.BookDao
import com.miaodi.note.data.dao.ChapterDao
import com.miaodi.note.data.dao.ClipboardRecordDao
import com.miaodi.note.data.dao.TodoDao
import com.miaodi.note.data.model.Article
import com.miaodi.note.data.model.Book
import com.miaodi.note.data.model.Chapter
import com.miaodi.note.data.model.ClipboardRecord
import com.miaodi.note.data.model.Todo
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val articleDao: ArticleDao,
    private val todoDao: TodoDao,
    private val clipboardRecordDao: ClipboardRecordDao
) {
    // Books
    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()
    suspend fun getAllBooksOnce(): List<Book> = bookDao.getAllBooksOnce()
    suspend fun insertBook(book: Book): Long = bookDao.insert(book)
    suspend fun updateBook(book: Book) = bookDao.update(book)
    suspend fun deleteBook(book: Book) = bookDao.delete(book)
    suspend fun getBookById(id: Long): Book? = bookDao.getBookById(id)
    suspend fun getBookCount(): Int = bookDao.getBookCount()

    // Chapters
    fun getChaptersByBook(bookId: Long): Flow<List<Chapter>> = chapterDao.getChaptersByBook(bookId)
    suspend fun getChaptersByBookOnce(bookId: Long): List<Chapter> = chapterDao.getChaptersByBookOnce(bookId)
    suspend fun insertChapter(chapter: Chapter): Long = chapterDao.insert(chapter)
    suspend fun updateChapter(chapter: Chapter) = chapterDao.update(chapter)
    suspend fun deleteChapter(chapter: Chapter) = chapterDao.delete(chapter)
    suspend fun getChapterById(id: Long): Chapter? = chapterDao.getChapterById(id)
    suspend fun getChapterCount(bookId: Long): Int = chapterDao.getChapterCount(bookId)

    // Articles
    fun getArticlesByChapter(chapterId: Long): Flow<List<Article>> = articleDao.getArticlesByChapter(chapterId)
    fun getArticlesByChapterOrderTitleAsc(chapterId: Long): Flow<List<Article>> = articleDao.getArticlesByChapterOrderTitleAsc(chapterId)
    fun getArticlesByChapterOrderTitleDesc(chapterId: Long): Flow<List<Article>> = articleDao.getArticlesByChapterOrderTitleDesc(chapterId)
    fun getArticlesByChapterOrderManual(chapterId: Long): Flow<List<Article>> = articleDao.getArticlesByChapterOrderManual(chapterId)
    fun searchArticles(chapterId: Long, query: String): Flow<List<Article>> = articleDao.searchArticles(chapterId, query)
    suspend fun getArticlesByChapterOnce(chapterId: Long): List<Article> = articleDao.getArticlesByChapterOnce(chapterId)
    suspend fun insertArticle(article: Article): Long = articleDao.insert(article)
    suspend fun updateArticle(article: Article) = articleDao.update(article)
    suspend fun deleteArticle(article: Article) = articleDao.delete(article)
    suspend fun getArticleById(id: Long): Article? = articleDao.getArticleById(id)
    suspend fun getArticleCount(chapterId: Long): Int = articleDao.getArticleCount(chapterId)

    // Todos
    fun getAllTodos(): Flow<List<Todo>> = todoDao.getAllTodos()
    suspend fun getAllTodosOnce(): List<Todo> = todoDao.getAllTodosOnce()
    suspend fun insertTodo(todo: Todo): Long = todoDao.insert(todo)
    suspend fun updateTodo(todo: Todo) = todoDao.update(todo)
    suspend fun deleteTodo(todo: Todo) = todoDao.delete(todo)
    suspend fun getTodoById(id: Long): Todo? = todoDao.getTodoById(id)

    // Clipboard records
    fun getAllClipboardRecords(): Flow<List<ClipboardRecord>> = clipboardRecordDao.getAllRecords()
    suspend fun getAllClipboardRecordsOnce(): List<ClipboardRecord> = clipboardRecordDao.getAllRecordsOnce()
    suspend fun insertClipboardRecord(record: ClipboardRecord): Long = clipboardRecordDao.insert(record)
    suspend fun deleteClipboardRecordById(id: Long) = clipboardRecordDao.deleteById(id)
    suspend fun clearClipboardRecords() = clipboardRecordDao.clearAll()
}
