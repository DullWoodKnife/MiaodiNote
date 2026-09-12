package com.miaodi.note.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

object PdfExportUtils {

    private const val PAGE_WIDTH = 595   // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842  // A4 height in points
    private const val MARGIN = 50

    fun exportToPdf(
        context: Context,
        uri: Uri,
        title: String,
        content: String
    ) {
        val pdfDocument = PdfDocument()

        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT
        }

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            typeface = Typeface.DEFAULT_BOLD
        }

        // Build full text with title
        val fullText = buildString {
            if (title.isNotBlank()) {
                append(title)
                append("\n\n")
            }
            append(content)
        }

        val availableWidth = PAGE_WIDTH - 2 * MARGIN

        // Measure how many lines we need
        val staticLayout = StaticLayout.Builder.obtain(
            fullText, 0, fullText.length, bodyPaint, availableWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .build()

        val lineCount = staticLayout.lineCount
        val lineHeight = bodyPaint.fontSpacing // approximate line height
        val titleExtraHeight = if (title.isNotBlank()) (titlePaint.textSize * 1.5f).toInt() else 0
        val linesPerPage = ((PAGE_HEIGHT - 2 * MARGIN - titleExtraHeight) / lineHeight).toInt().coerceAtLeast(1)
        val totalPages = ceil(lineCount.toDouble() / linesPerPage).toInt().coerceAtLeast(1)

        var currentLine = 0
        for (pageNum in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = MARGIN.toFloat()
            val endLine = (currentLine + linesPerPage).coerceAtMost(lineCount)

            while (currentLine < endLine) {
                val lineStart = staticLayout.getLineStart(currentLine)
                val lineEnd = staticLayout.getLineEnd(currentLine)
                val lineText = fullText.substring(lineStart, lineEnd)

                // Check if this line is within the title region
                val isTitleLine = title.isNotBlank() && currentLine == 0
                val paint = if (isTitleLine) titlePaint else bodyPaint
                val textY = y + paint.textSize

                canvas.drawText(lineText, MARGIN.toFloat(), textY, paint)
                y += if (isTitleLine) titlePaint.textSize * 1.5f else lineHeight
                currentLine++
            }

            pdfDocument.finishPage(page)
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
    }
}