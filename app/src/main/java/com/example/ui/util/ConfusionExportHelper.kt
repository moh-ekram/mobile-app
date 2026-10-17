package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Helper to export Confusion Sets as individual .jpg images and a combined .pdf document.
 * Files are automatically saved into the Downloads folder and registered with MediaScanner.
 */
object ConfusionExportHelper {

    private const val TAG = "ConfusionExport"

    data class ExportResult(
        val isSuccess: Boolean,
        val jpgCount: Int,
        val pdfFile: File?,
        val exportDir: File?,
        val errorMessage: String? = null
    )

    suspend fun exportAllGroups(
        context: Context,
        courseTitle: String,
        groups: List<ConfusionGroup>
    ): ExportResult = withContext(Dispatchers.IO) {
        if (groups.isEmpty()) {
            return@withContext ExportResult(
                isSuccess = false,
                jpgCount = 0,
                pdfFile = null,
                exportDir = null,
                errorMessage = "No confusion sets to export"
            )
        }

        try {
            val safeTitle = courseTitle.replace(Regex("""[^a-zA-Z0-9_-]"""), "_").take(25).ifBlank { "Course" }
            val timeStamp = System.currentTimeMillis()

            // 1. Determine target directory in Downloads
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = if (publicDownloads != null && publicDownloads.canWrite()) {
                File(publicDownloads, "ConfusionSets_${safeTitle}_$timeStamp")
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "ConfusionSets_${safeTitle}_$timeStamp")
            }
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val exportedJpgPaths = mutableListOf<String>()
            val pdfDocument = PdfDocument()

            // 2. Render each group as a JPG bitmap & add to PDF document
            for (i in groups.indices) {
                val group = groups[i]
                val bitmap = renderGroupBitmap(courseTitle, i + 1, groups.size, group)

                // Save individual JPG
                val jpgFile = File(exportDir, "Group_${i + 1}.jpg")
                FileOutputStream(jpgFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                exportedJpgPaths.add(jpgFile.absolutePath)

                // Render page in PDF
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val pdfCanvas = page.canvas
                pdfCanvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)

                bitmap.recycle()
            }

            // 3. Save combined PDF document
            val pdfFile = File(exportDir, "All_Confusion_Sets_${safeTitle}.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            exportedJpgPaths.add(pdfFile.absolutePath)

            // Also copy the PDF directly to Downloads root for instant visibility
            try {
                if (publicDownloads != null && publicDownloads.exists()) {
                    val rootPdf = File(publicDownloads, "ConfusionSets_${safeTitle}.pdf")
                    pdfFile.copyTo(rootPdf, overwrite = true)
                    exportedJpgPaths.add(rootPdf.absolutePath)
                }
            } catch (_: Exception) {}

            // 4. Register with MediaScanner
            try {
                MediaScannerConnection.scanFile(
                    context,
                    exportedJpgPaths.toTypedArray(),
                    null,
                    null
                )
            } catch (_: Exception) {}

            ExportResult(
                isSuccess = true,
                jpgCount = groups.size,
                pdfFile = pdfFile,
                exportDir = exportDir
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            ExportResult(
                isSuccess = false,
                jpgCount = 0,
                pdfFile = null,
                exportDir = null,
                errorMessage = e.message ?: "Export failed"
            )
        }
    }

    /**
     * Renders a clean, high-resolution Bitmap card for a single Confusion Group.
     */
    private fun renderGroupBitmap(
        courseTitle: String,
        groupNumber: Int,
        totalGroups: Int,
        group: ConfusionGroup
    ): Bitmap {
        val width = 800
        val padding = 40

        // Calculate dynamic height
        var contentHeight = padding + 120 // Header height
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            color = Color.parseColor("#334155")
        }

        for (w in group.words) {
            contentHeight += 60 // Word place1
            val meaningLayout = StaticLayout.Builder.obtain(
                w.place2,
                0,
                w.place2.length,
                textPaint,
                width - (padding * 2)
            ).build()
            contentHeight += meaningLayout.height + 25

            if (!w.place3.isNullOrBlank()) {
                contentHeight += 35
            }
            contentHeight += 25 // Divider / spacing
        }
        contentHeight += 70 // Footer height
        val height = maxOf(450, contentHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // Card Container
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val cardRect = RectF(16f, 16f, (width - 16).toFloat(), (height - 16).toFloat())
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)
        canvas.drawRoundRect(cardRect, 24f, 24f, borderPaint)

        var yPos = padding + 30f

        // Top Header: Course & Group Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Confusion Group $groupNumber of $totalGroups", padding.toFloat(), yPos, titlePaint)

        // Match Badge
        val badgeText = String.format(Locale.US, "%.0f%% Match", group.maxSimilarity)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F46E5")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF2FF")
            style = Paint.Style.FILL
        }
        val badgeWidth = badgePaint.measureText(badgeText) + 32f
        val badgeRect = RectF(
            width - padding - badgeWidth,
            yPos - 26f,
            width - padding.toFloat(),
            yPos + 8f
        )
        canvas.drawRoundRect(badgeRect, 12f, 12f, badgeBgPaint)
        canvas.drawText(badgeText, badgeRect.left + 16f, yPos - 2f, badgePaint)

        yPos += 30f

        // Subtitle (Course Name)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
        }
        canvas.drawText(courseTitle, padding.toFloat(), yPos, subPaint)

        yPos += 24f
        // Divider
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 2f
        }
        canvas.drawLine(padding.toFloat(), yPos, (width - padding).toFloat(), yPos, linePaint)
        yPos += 34f

        // Render Words in Group
        val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val meaningPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.parseColor("#334155")
        }
        val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = Color.parseColor("#64748B")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        for (item in group.words) {
            // Word colorized by status
            wordPaint.color = when (item.wordEntity.status) {
                "know" -> Color.parseColor("#059669")
                "dont_know" -> Color.parseColor("#E11D48")
                "confusion" -> Color.parseColor("#D97706")
                else -> Color.parseColor("#1E293B")
            }

            canvas.drawText(item.place1, padding.toFloat(), yPos, wordPaint)
            yPos += 30f

            // Meaning
            val layout = StaticLayout.Builder.obtain(
                item.place2,
                0,
                item.place2.length,
                meaningPaint,
                width - (padding * 2)
            ).build()
            canvas.save()
            canvas.translate(padding.toFloat(), yPos)
            layout.draw(canvas)
            canvas.restore()
            yPos += layout.height + 8f

            // Synonyms / Place 3 if present
            if (!item.place3.isNullOrBlank()) {
                canvas.drawText(item.place3, padding.toFloat(), yPos + 16f, extraPaint)
                yPos += 28f
            }

            yPos += 20f
            // Word divider
            canvas.drawLine(padding.toFloat(), yPos, (width - padding).toFloat(), yPos, linePaint)
            yPos += 30f
        }

        // Footer Watermark
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 16f
        }
        canvas.drawText("Memorizer • Smart Vocabulary Study", padding.toFloat(), height - 32f, footerPaint)

        return bitmap
    }

    /**
     * Launches an intent to view the exported PDF or share the result.
     */
    fun openExportedPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Confusion Sets PDF"))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF intent: ${e.message}")
        }
    }
}
