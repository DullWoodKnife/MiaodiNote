package com.miaodi.note.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.miaodi.note.data.model.ClipboardRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardRecordDao {
    @Query("SELECT * FROM clipboard_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<ClipboardRecord>>

    @Query("SELECT * FROM clipboard_records ORDER BY createdAt DESC")
    suspend fun getAllRecordsOnce(): List<ClipboardRecord>

    @Query("SELECT * FROM clipboard_records ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRecord(): ClipboardRecord?

    @Insert
    suspend fun insert(record: ClipboardRecord): Long

    @Query("DELETE FROM clipboard_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_records")
    suspend fun clearAll()
}