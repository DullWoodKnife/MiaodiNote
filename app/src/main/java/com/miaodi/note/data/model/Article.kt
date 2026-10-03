package com.miaodi.note.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapterId"])]
)
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    var title: String = "",
    var content: String = "",
    // 大文档正文以文件形式存储在应用私有目录，此处保存相对路径（无则为 null）。
    // 正文超过阈值的文章不会把全文写入 content 列，避免 Room 读取单行超大文本
    // 触发 CursorWindow（约 2MB）限制导致应用崩溃。
    var contentPath: String? = null,
    var isMarkdown: Boolean = true,
    var wordCount: Int = 0,
    var statusColor: Int = 0xFFFFA500.toInt(), // default orange dot
    var isNew: Boolean = true,
    var sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
