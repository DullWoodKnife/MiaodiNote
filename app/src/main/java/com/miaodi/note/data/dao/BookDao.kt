package com.miaodi.note.data.dao

import androidx.room.*
import com.miaodi.note.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllBooksOnce(): List<Book>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
}
