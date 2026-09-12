package com.miaodi.note.data.dao

import androidx.room.*
import com.miaodi.note.data.model.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE chapterId = :chapterId ORDER BY updatedAt DESC")
    fun getArticlesByChapter(chapterId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE chapterId = :chapterId ORDER BY title ASC")
    fun getArticlesByChapterOrderTitleAsc(chapterId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE chapterId = :chapterId ORDER BY title DESC")
    fun getArticlesByChapterOrderTitleDesc(chapterId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE chapterId = :chapterId ORDER BY sortOrder ASC, updatedAt DESC")
    fun getArticlesByChapterOrderManual(chapterId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE chapterId = :chapterId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchArticles(chapterId: Long, query: String): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE chapterId = :chapterId ORDER BY updatedAt DESC")
    suspend fun getArticlesByChapterOnce(chapterId: Long): List<Article>

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
}
