package com.miaodi.note.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/**
 * 真实 Bitmap 渲染工具：用 Canvas + StaticLayout 将文章绘制成长图，输出 PNG。
 */
object ImageExportUtils {

    private const val IMAGE_WIDTH = 1080          // 长图宽度 px
    private const val MARGIN = 60                 // 左右边距 px
    private const val TOP_PADDING = 100           // 顶部留白
    private const val BOTTOM_PADDING = 120        // 底部留白
    private const val SECTION_GAP = 40            // 标题与正文间距

    fun exportToImage(
        context: Context,
        uri: Uri,
        title: String,
        content: String
    ) {
        val availableWidth = IMAGE_WIDTH - 2 * MARGIN

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 56f
            isFakeBoldText = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.DEFAULT
        }

        // 构建标题 StaticLayout（用于测量）
        val titleLayout = if (title.isNotBlank()) {
            StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
        } else {
            null
        }
        // 构建正文 StaticLayout（用于测量 + 绘制）
        val bodyLayout = StaticLayout.Builder.obtain(content, 0, content.length, bodyPaint, availableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.35f)
            .build()

        val titleHeight = titleLayout?.height ?: 0
        val imageHeight = TOP_PADDING +
            titleHeight +
            (if (titleLayout != null) SECTION_GAP else 0) +
            bodyLayout.height +
            BOTTOM_PADDING

        // 创建 Bitmap 并用 Canvas 真实绘制
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, imageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = TOP_PADDING.toFloat()
        if (titleLayout != null) {
            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleHeight + SECTION_GAP
        }

        canvas.save()
        canvas.translate(MARGIN.toFloat(), y)
        bodyLayout.draw(canvas)
        canvas.restore()

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        bitmap.recycle()
    }
}