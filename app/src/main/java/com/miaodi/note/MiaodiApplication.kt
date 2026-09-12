package com.miaodi.note

import android.app.Application
import com.miaodi.note.data.AppDatabase
import com.miaodi.note.data.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MiaodiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        NoteRepository(
            database.bookDao(),
            database.chapterDao(),
            database.articleDao(),
            database.todoDao(),
            database.clipboardRecordDao()
        )
    }
}
