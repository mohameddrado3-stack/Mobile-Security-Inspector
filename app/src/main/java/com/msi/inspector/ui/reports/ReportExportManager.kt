package com.msi.inspector.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class ReportFileFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    PDF("pdf", "application/pdf")
}

object ReportExportManager {
    fun export(context: Context, format: ReportFileFormat, content: String): Uri {
        val directory = File(context.cacheDir, "reports").apply {
            if (!exists() && !mkdirs()) error("تعذر إنشاء مجلد التقرير المؤقت.")
        }
        val file = File(directory, "msi-report-${System.currentTimeMillis()}.${format.extension}")
        when (format) {
            ReportFileFormat.PDF -> writePdf(file, content)
            else -> file.writeText(content, Charsets.UTF_8)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun writePdf(file: File, content: String) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        val contentWidth = pageWidth - margin * 2
        val contentTop = 58
        val contentBottom = pageHeight - 42
        val contentHeight = contentBottom - contentTop
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(24, 36, 48)
            textSize = 10.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(true)
            .setLineSpacing(2f, 1f)
            .build()

        val document = PdfDocument()
        try {
            var firstLine = 0
            var pageNumber = 1
            while (firstLine < layout.lineCount || pageNumber == 1) {
                val startY = layout.getLineTop(firstLine)
                var lastLine = firstLine
                while (
                    lastLine < layout.lineCount &&
                    layout.getLineBottom(lastLine) - startY <= contentHeight
                ) {
                    lastLine += 1
                }
                if (lastLine == firstLine && firstLine < layout.lineCount) lastLine += 1
                val endY = if (lastLine == 0) 0 else layout.getLineBottom(lastLine - 1)
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                val canvas: Canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(0, 107, 97)
                    textSize = 13f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8f
                }
                canvas.drawText("MSI — Local Security Indicators Report", margin.toFloat(), 32f, headerPaint)
                canvas.save()
                canvas.clipRect(
                    margin.toFloat(),
                    contentTop.toFloat(),
                    (pageWidth - margin).toFloat(),
                    contentBottom.toFloat()
                )
                canvas.translate(margin.toFloat(), (contentTop - startY).toFloat())
                layout.draw(canvas)
                canvas.restore()
                canvas.drawText("Page $pageNumber", margin.toFloat(), (pageHeight - 18).toFloat(), footerPaint)
                document.finishPage(page)
                pageNumber += 1
                firstLine = lastLine
                if (firstLine >= layout.lineCount) break
                if (endY <= startY) break
            }
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
