package com.miaodi.note.data

import android.content.Context
import java.io.File

/**
 * 大文档正文的磁盘存储。
 *
 * 背景：Room/SQLite 在读取单行时使用 CursorWindow，其大小上限约 2MB。
 * 当某篇文章的正文（如 10MB+ 的 Markdown 词典）以单行存入 content 列时，
 * 正常的 SELECT * 查询会因为“Row too big to fit into CursorWindow”而抛异常，
 * 导致应用在列表加载/启动引导时崩溃。
 *
 * 因此，超过阈值的正文不再写入数据库，而是以文件形式保存于应用私有目录，
 * 数据库中仅保存相对路径（[com.miaodi.note.data.model.Article.contentPath]）。
 * 小文档仍以内联方式保存在 content 列，保持既有行为。
 */
object ArticleContentStore {

    /** 正文超过该字符数即视为“大文档”，改为落盘存储。 */
    const val LARGE_CONTENT_THRESHOLD = 200_000

    private const val DIR_NAME = "article_contents"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** 生成存库用的相对路径（不保存绝对路径，避免应用目录变化后失效）。 */
    fun relativePathFor(articleId: Long): String = "$DIR_NAME/$articleId.md"

    private fun fileFor(context: Context, relativePath: String): File =
        File(context.filesDir, relativePath)

    /** 写入正文；失败时抛出异常由调用方处理。 */
    fun write(context: Context, relativePath: String, content: String) {
        val f = fileFor(context, relativePath)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    /** 读取正文；文件不存在或读取异常时返回 null。 */
    fun read(context: Context, relativePath: String): String? {
        return try {
            val f = fileFor(context, relativePath)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (t: Throwable) {
            null
        }
    }

    /** 删除正文文件（忽略失败）。 */
    fun delete(context: Context, relativePath: String) {
        try {
            fileFor(context, relativePath).delete()
        } catch (_: Throwable) {
            // 忽略
        }
    }

    /** 读取外部 Uri 的文本内容，采用缓冲流分块读取，避免一次性载入大文件。 */
    fun readTextFromUri(context: Context, uri: android.net.Uri, maxChars: Int = 25_000_000): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    val buffer = CharArray(64 * 1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        sb.append(buffer, 0, read)
                        if (sb.length >= maxChars) break
                    }
                    sb.toString()
                }
            }
        } catch (t: Throwable) {
            null
        }
    }
}
