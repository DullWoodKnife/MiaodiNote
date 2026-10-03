package com.miaodi.note

import android.app.Application
import com.miaodi.note.data.AppDatabase
import com.miaodi.note.data.ArticleContentStore
import com.miaodi.note.data.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MiaodiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun onCreate() {
        super.onCreate()
        migrateLargeContentToFiles()
    }

    /**
     * 把历史遗留的“超大正文”从数据库迁移到文件存储，修复升级后启动崩溃。
     *
     * 旧版本会把外部大文档（10MB+）的全文写入 articles.content 列，导致
     * Room 读取该行时超过 CursorWindow（约 2MB）上限而崩溃。这里在后台线程中
     * 通过分块查询（substr）安全地把正文搬到文件，并把 content 列清空、
     * 写入 contentPath，全程不整行读取超大文本。
     */
    private fun migrateLargeContentToFiles() {
        applicationScope.launch {
            try {
                val repo = repository
                val ids = repo.getIdsWithLargeContent(ArticleContentStore.LARGE_CONTENT_THRESHOLD)
                for (id in ids) {
                    val len = repo.getContentLength(id) ?: 0
                    if (len <= 0) continue
                    val sb = StringBuilder(len)
                    var offset = 1 // SQLite substr 以 1 为起始
                    val chunk = 200_000
                    while (offset <= len) {
                        val part = repo.getContentChunk(id, offset, chunk) ?: break
                        if (part.isEmpty()) break
                        sb.append(part)
                        offset += part.length
                    }
                    val path = ArticleContentStore.relativePathFor(id)
                    ArticleContentStore.write(this@MiaodiApplication, path, sb.toString())
                    repo.setContentExternalized(id, path)
                }
            } catch (t: Throwable) {
                // 迁移失败不应影响应用启动
            }
        }
    }
}
