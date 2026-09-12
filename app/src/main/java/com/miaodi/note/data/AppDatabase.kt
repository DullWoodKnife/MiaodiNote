package com.miaodi.note.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(entities = [Book::class, Chapter::class, Article::class, Todo::class, ClipboardRecord::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun articleDao(): ArticleDao
    abstract fun todoDao(): TodoDao
    abstract fun clipboardRecordDao(): ClipboardRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "miaodi_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * All database migrations are defined here and must be tested before release.
         * Migrations are mandatory - never use fallbackToDestructiveMigration().
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `todos` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`time` TEXT NOT NULL, " +
                            "`dailyReminder` INTEGER NOT NULL, " +
                            "`completed` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL)"
                    )
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `clipboard_records` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`content` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                    )
                }
            }
        )
    }
}