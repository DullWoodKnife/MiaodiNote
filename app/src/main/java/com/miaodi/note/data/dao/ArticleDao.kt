package com.miaodi.note.data.dao

import androidx.room.*
import com.miaodi.note.data.model.Article
import kotlinx.coroutines.flow.Flow

// 列表/搜索查询只取正文前 1000 个字符作为预览，避免把超大正文整行读入
// CursorWindow（约 2MB 上限）导致崩溃。完整正文由 getArticleById 读取，
// 且大文档正文已改为落盘存储（content 列为空）。
const val ARTICLE_LIST_COLUMNS = "id, chapterId, title, substr(content, 1, 1000) AS content, contentPath, isMarkdown, wordCount, statusColor, isNew, sortOrder, createdAt, updatedAt"
@Dao
interface ArticleDao {
    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId ORDER BY updatedAt DESC")
    fun getArticlesByChapter(chapterId: Long): Flow<List<Article>>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId ORDER BY title ASC")
    fun getArticlesByChapterOrderTitleAsc(chapterId: Long): Flow<List<Article>>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId ORDER BY title DESC")
    fun getArticlesByChapterOrderTitleDesc(chapterId: Long): Flow<List<Article>>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId ORDER BY sortOrder ASC, updatedAt DESC")
    fun getArticlesByChapterOrderManual(chapterId: Long): Flow<List<Article>>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchArticles(chapterId: Long, query: String): Flow<List<Article>>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId = :chapterId ORDER BY updatedAt DESC")
    suspend fun getArticlesByChapterOnce(chapterId: Long): List<Article>

    @Query("SELECT $ARTICLE_LIST_COLUMNS FROM articles WHERE chapterId IN (SELECT id FROM chapters WHERE bookId = :bookId) ORDER BY updatedAt DESC")
    suspend fun getArticlesByBookOnce(bookId: Long): List<Article>

    @Insert
    suspend fun insert(article: Article): Long

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): Article?

    @Query("SELECT COUNT(*) FROM articles WHERE chapterId = :chapterId")
    suspend fun getArticleCount(chapterId: Long): Int

    // ===== 大文档正文落盘迁移支持 =====
    @Query("SELECT id FROM articles WHERE content IS NOT NULL AND length(content) > :threshold")
    suspend fun getIdsWithLargeContent(threshold: Int): List<Long>

    @Query("SELECT length(content) FROM articles WHERE id = :id")
    suspend fun getContentLength(id: Long): Int?

    @Query("SELECT substr(content, :offset, :length) FROM articles WHERE id = :id")
    suspend fun getContentChunk(id: Long, offset: Int, length: Int): String?

    @Query("SELECT content FROM articles WHERE id = :id")
    suspend fun getContentOnly(id: Long): String?

    @Query("UPDATE articles SET content = '', contentPath = :path WHERE id = :id")
    suspend fun setContentExternalized(id: Long, path: String)

    @Query("UPDATE articles SET content = :content, contentPath = NULL WHERE id = :id")
    suspend fun setContentInline(id: Long, content: String)
}
