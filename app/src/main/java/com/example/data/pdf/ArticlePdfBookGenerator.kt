package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

/**
 * Structured content blocks extracted from saved article text/markdown.
 * Kept pure-Kotlin so unit tests can run directly on the JVM.
 */
sealed class ArticleContentBlock {
    data class Heading(val level: Int, val text: String) : ArticleContentBlock()
    data class BulletItem(val marker: String, val text: String, val isOrdered: Boolean) : ArticleContentBlock()
    data class Blockquote(val text: String) : ArticleContentBlock()
    data class ImageBlock(val caption: String, val url: String) : ArticleContentBlock()
    data class Paragraph(val text: String) : ArticleContentBlock()
}

data class InlineRichSegment(
    val text: String,
    val url: String? = null
)

data class ScriptRun(
    val text: String,
    val isBengali: Boolean
)

data class TocEntryInfo(
    val index: Int,
    val articleId: String,
    val title: String,
    val author: String,
    val websiteName: String,
    val startPage: Int
)

data class GeneratedBookPdfResult(
    val pdfFile: File,
    val pageCount: Int,
    val tocPageCount: Int,
    val tocEntries: List<TocEntryInfo>,
    val bookTitle: String
)

data class DownloadedPdfInfo(
    val uri: Uri,
    val fileName: String,
    val displayLocation: String
)

object ArticleRichContentParser {

    private val markdownLinkRegex = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
    private val imageBlockRegex = Regex("""^!\[([^\]]*)]\((https?://[^)\s]+)\)$""")
    private val rawUrlRegex = Regex("""https?://[^\s<>()]+""")
    private val orderedListRegex = Regex("""^(\d{1,3}[.)])\s+(.*)$""")

    fun parseArticleIdsJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx, "").trim().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeArticleIdsJson(ids: List<String>): String {
        val arr = JSONArray()
        ids.distinct().forEach { arr.put(it) }
        return arr.toString()
    }

    fun isBengaliChar(ch: Char): Boolean = ch in '\u0980'..'\u09FF'

    /**
     * Splits text into contiguous Bengali vs non-Bengali (English/Latin/numbers) runs
     * so bilingual typography applies Aikya for Bengali and Times New Roman for English.
     */
    fun splitScriptRuns(text: String): List<ScriptRun> {
        if (text.isEmpty()) return emptyList()
        val runs = mutableListOf<ScriptRun>()
        var currentIsBengali: Boolean? = null
        val sb = StringBuilder()

        for (ch in text) {
            val charScript: Boolean? = when {
                isBengaliChar(ch) -> true
                ch.isLetter() -> false
                else -> null
            }
            val effectiveIsBengali = charScript ?: (currentIsBengali ?: false)
            if (currentIsBengali == null) {
                currentIsBengali = effectiveIsBengali
                sb.append(ch)
            } else if (charScript != null && charScript != currentIsBengali) {
                if (sb.isNotEmpty()) {
                    runs.add(ScriptRun(sb.toString(), currentIsBengali))
                    sb.setLength(0)
                }
                currentIsBengali = charScript
                sb.append(ch)
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            runs.add(ScriptRun(sb.toString(), currentIsBengali ?: false))
        }
        return runs
    }

    /**
     * Parses raw article content into structured headings, lists, quotes, images, and paragraphs.
     */
    fun parseBlocks(rawContent: String): List<ArticleContentBlock> {
        val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.lines()
        val blocks = mutableListOf<ArticleContentBlock>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val joined = paragraphBuffer.joinToString(" ").trim()
                if (joined.isNotBlank()) {
                    blocks.add(ArticleContentBlock.Paragraph(joined))
                }
                paragraphBuffer.clear()
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }

            // Image marker: ![caption](url)
            val imgMatch = imageBlockRegex.matchEntire(trimmed)
            if (imgMatch != null) {
                flushParagraph()
                val caption = imgMatch.groupValues[1].trim()
                val url = imgMatch.groupValues[2].trim()
                blocks.add(ArticleContentBlock.ImageBlock(caption, url))
                continue
            }

            // Headings: ### or ##
            if (trimmed.startsWith("### ")) {
                flushParagraph()
                val text = trimmed.removePrefix("### ").trim()
                if (text.isNotBlank()) blocks.add(ArticleContentBlock.Heading(3, text))
                continue
            }
            if (trimmed.startsWith("## ")) {
                flushParagraph()
                val text = trimmed.removePrefix("## ").trim()
                if (text.isNotBlank()) blocks.add(ArticleContentBlock.Heading(2, text))
                continue
            }
            if (trimmed.startsWith("# ")) {
                flushParagraph()
                val text = trimmed.removePrefix("# ").trim()
                if (text.isNotBlank()) blocks.add(ArticleContentBlock.Heading(2, text))
                continue
            }

            // Blockquote: > ...
            if (trimmed.startsWith("> ")) {
                flushParagraph()
                val quote = trimmed.removePrefix("> ").trim()
                if (quote.isNotBlank()) blocks.add(ArticleContentBlock.Blockquote(quote))
                continue
            }

            // Bullet lists: • , - , *
            if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph()
                val itemText = trimmed.drop(2).trim()
                if (itemText.isNotBlank()) {
                    blocks.add(ArticleContentBlock.BulletItem("•", itemText, isOrdered = false))
                }
                continue
            }

            // Ordered lists: 1. , 2)
            val ordMatch = orderedListRegex.matchEntire(trimmed)
            if (ordMatch != null) {
                flushParagraph()
                val marker = ordMatch.groupValues[1]
                val itemText = ordMatch.groupValues[2].trim()
                if (itemText.isNotBlank()) {
                    blocks.add(ArticleContentBlock.BulletItem(marker, itemText, isOrdered = true))
                }
                continue
            }

            paragraphBuffer.add(trimmed)
        }
        flushParagraph()

        return blocks
    }

    /**
     * Parses inline `[label](url)` and raw URLs into display segments.
     */
    fun parseInlineSegments(text: String): List<InlineRichSegment> {
        if (text.isEmpty()) return emptyList()
        val segments = mutableListOf<InlineRichSegment>()
        var cursor = 0
        val matches = markdownLinkRegex.findAll(text).toList()
        for (m in matches) {
            if (m.range.first > cursor) {
                appendWithRawUrls(text.substring(cursor, m.range.first), segments)
            }
            val label = m.groupValues[1].trim()
            val url = m.groupValues[2].trim()
            segments.add(InlineRichSegment(label.ifBlank { url }, url))
            cursor = m.range.last + 1
        }
        if (cursor < text.length) {
            appendWithRawUrls(text.substring(cursor), segments)
        }
        return segments
    }

    private fun appendWithRawUrls(chunk: String, out: MutableList<InlineRichSegment>) {
        if (chunk.isEmpty()) return
        var cursor = 0
        for (m in rawUrlRegex.findAll(chunk)) {
            if (m.range.first > cursor) {
                out.add(InlineRichSegment(chunk.substring(cursor, m.range.first), null))
            }
            val cleanUrl = m.value.trimEnd('.', ',', ';', ':')
            val trailing = m.value.removePrefix(cleanUrl)
            out.add(InlineRichSegment(cleanUrl, cleanUrl))
            if (trailing.isNotEmpty()) {
                out.add(InlineRichSegment(trailing, null))
            }
            cursor = m.range.last + 1
        }
        if (cursor < chunk.length) {
            out.add(InlineRichSegment(chunk.substring(cursor), null))
        }
    }

    /**
     * Converts rich article markdown (`##`, `[text](url)`, `![caption](url)`) into clean readable
     * text for the in-app reader while preserving paragraph spacing and vocabulary matching.
     */
    fun toCleanReaderText(rawContent: String): String {
        val blocks = parseBlocks(rawContent)
        if (blocks.isEmpty()) return rawContent
        return blocks.mapNotNull { block ->
            when (block) {
                is ArticleContentBlock.Heading -> cleanInlineMarkup(block.text)
                is ArticleContentBlock.BulletItem -> "${block.marker} ${cleanInlineMarkup(block.text)}"
                is ArticleContentBlock.Blockquote -> "“${cleanInlineMarkup(block.text)}”"
                is ArticleContentBlock.ImageBlock -> block.caption.takeIf { it.isNotBlank() }?.let { "[$it]" }
                is ArticleContentBlock.Paragraph -> cleanInlineMarkup(block.text)
            }
        }.joinToString("\n\n")
    }

    private fun cleanInlineMarkup(text: String): String {
        return markdownLinkRegex.replace(text) { m -> m.groupValues[1].trim() }
    }

    fun deriveWebsiteName(article: ArticleEntity): String {
        if (article.websiteName.isNotBlank()) return article.websiteName.trim()
        if (article.sourceUrl.isNotBlank()) {
            try {
                val host = URI(article.sourceUrl.trim()).host?.removePrefix("www.")
                if (!host.isNullOrBlank()) return host
            } catch (_: Exception) {}
        }
        val author = article.author.trim()
        if (author.contains(".") && !author.contains(" ")) return author
        return "Saved Article"
    }

    fun derivePublishedDate(article: ArticleEntity): String {
        if (article.publishedDate.isNotBlank()) return article.publishedDate.trim()
        if (article.createdAt > 0L) {
            return try {
                SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(article.createdAt))
            } catch (_: Exception) {
                ""
            }
        }
        return ""
    }
}

object ArticlePdfBookGenerator {

    // Standard A4 in points (1 pt = 1/72 inch): 595 x 842
    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    private const val MARGIN_LEFT = 46f
    private const val MARGIN_RIGHT = 46f
    private const val CONTENT_WIDTH = A4_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // 503f
    private const val HEADER_Y = 28f
    private const val CONTENT_TOP = 54f
    private const val CONTENT_BOTTOM = 788f
    private const val FOOTER_Y = 818f

    private val imageHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private class BilingualTypefaceSpan(
        private val typeface: Typeface,
        private val fakeBold: Boolean = false,
        private val linkColor: Int? = null,
        private val underline: Boolean = false
    ) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = typeface
            if (fakeBold) tp.isFakeBoldText = true
            if (linkColor != null) tp.color = linkColor
            if (underline) tp.isUnderlineText = true
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = typeface
            if (fakeBold) tp.isFakeBoldText = true
        }
    }

    private class FontSet(context: Context) {
        val tinosRegular: Typeface
        val tinosBold: Typeface
        val tinosItalic: Typeface
        val bengaliRegular: Typeface
        val bengaliBold: Typeface

        init {
            val loadedTinos = ResourcesCompat.getFont(context, R.font.tinos) ?: Typeface.SERIF
            val loadedAikyaReg = ResourcesCompat.getFont(context, R.font.aikya_regular) ?: Typeface.DEFAULT
            val loadedAikyaBold = ResourcesCompat.getFont(context, R.font.aikya_bold) ?: loadedAikyaReg

            tinosRegular = buildCompositeTypeface(context, R.font.tinos, R.font.aikya_regular, R.font.kalpurush)
                ?: loadedTinos
            tinosBold = Typeface.create(tinosRegular, Typeface.BOLD)
            tinosItalic = Typeface.create(tinosRegular, Typeface.ITALIC)

            bengaliRegular = buildCompositeTypeface(context, R.font.aikya_regular, R.font.kalpurush, R.font.tinos)
                ?: loadedAikyaReg
            bengaliBold = buildCompositeTypeface(context, R.font.aikya_bold, R.font.kalpurush, R.font.tinos)
                ?: Typeface.create(loadedAikyaBold, Typeface.BOLD)
        }

        private fun buildCompositeTypeface(
            context: Context,
            primaryRes: Int,
            secondaryRes: Int,
            tertiaryRes: Int
        ): Typeface? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            return try {
                val primaryFamily = FontFamily.Builder(Font.Builder(context.resources, primaryRes).build()).build()
                val builder = Typeface.CustomFallbackBuilder(primaryFamily)
                val secFamily = FontFamily.Builder(Font.Builder(context.resources, secondaryRes).build()).build()
                builder.addCustomFallback(secFamily)
                val terFamily = FontFamily.Builder(Font.Builder(context.resources, tertiaryRes).build()).build()
                builder.addCustomFallback(terFamily)
                builder.build()
            } catch (_: Exception) {
                null
            }
        }
    }

    private data class PreparedArticle(
        val article: ArticleEntity,
        val displayTitle: String,
        val authorDisplay: String,
        val websiteDisplay: String,
        val dateDisplay: String,
        val faviconBitmap: Bitmap?,
        val coverBitmap: Bitmap?,
        val blocks: List<ArticleContentBlock>,
        val bodyImages: Map<String, Bitmap>
    )

    private sealed class PageDrawOp {
        data class DrawStaticLayoutSlice(
            val layout: StaticLayout,
            val firstLine: Int,
            val lastLineExclusive: Int,
            val x: Float,
            val y: Float,
            val quoteBarColor: Int? = null,
            val bulletMarker: String? = null,
            val bulletMarkerPaint: TextPaint? = null,
            val bulletMarkerX: Float = 0f
        ) : PageDrawOp()

        data class DrawTitleWithFavicon(
            val titleLayout: StaticLayout,
            val favicon: Bitmap?,
            val monogram: String,
            val titleX: Float,
            val iconRightEdge: Float,
            val y: Float
        ) : PageDrawOp()

        data class DrawMetadataRow(
            val layout: StaticLayout,
            val x: Float,
            val y: Float
        ) : PageDrawOp()

        data class DrawImage(
            val bitmap: Bitmap,
            val captionLayout: StaticLayout?,
            val x: Float,
            val y: Float,
            val width: Float,
            val height: Float
        ) : PageDrawOp()

        data class DrawDivider(
            val x1: Float,
            val x2: Float,
            val y: Float,
            val color: Int,
            val strokeWidth: Float
        ) : PageDrawOp()
    }

    suspend fun generateBookPdf(
        context: Context,
        bookTitle: String,
        bookSubtitle: String,
        articles: List<ArticleEntity>,
        onProgress: (stage: String) -> Unit = {}
    ): Result<GeneratedBookPdfResult> = withContext(Dispatchers.IO) {
        try {
            if (articles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Select at least one article to compile a book."))
            }

            onProgress("Loading typography & article assets...")
            val fonts = FontSet(context)
            val prepared = prepareArticles(articles) { loaded, total ->
                onProgress("Preparing article $loaded of $total...")
            }

            onProgress("Paginating chapters & Table of Contents...")

            // Estimate TOC pages cleanly first using real measured TOC layout
            val estimatedEntries = prepared.mapIndexed { idx, p ->
                TocEntryInfo(
                    index = idx + 1,
                    articleId = p.article.id,
                    title = p.displayTitle,
                    author = p.authorDisplay,
                    websiteName = p.websiteDisplay,
                    startPage = 100 + idx
                )
            }
            val tocPageCount = measureTocPages(bookTitle, bookSubtitle, estimatedEntries, fonts).size.coerceAtLeast(1)

            // Layout all articles continuously starting right after TOC pages (no inter-article page breaks)
            val articlePages = mutableListOf<MutableList<PageDrawOp>>()
            val resolvedTocEntries = mutableListOf<TocEntryInfo>()
            layoutArticlesContinuously(
                preparedArticles = prepared,
                fonts = fonts,
                firstArticlePageNumber = tocPageCount + 1,
                outPages = articlePages,
                outTocEntries = resolvedTocEntries
            )

            // Re-render TOC pages with exact final page numbers
            val tocPages = buildTocPages(
                bookTitle = bookTitle,
                bookSubtitle = bookSubtitle,
                entries = resolvedTocEntries,
                fonts = fonts,
                expectedPageCount = tocPageCount
            )

            // Adjust startPage numbers if real TOC pages somehow grew beyond initial estimate
            val pageDelta = tocPages.size - tocPageCount
            val finalTocEntries = if (pageDelta != 0) {
                val adjusted = resolvedTocEntries.map { it.copy(startPage = it.startPage + pageDelta) }
                buildTocPages(bookTitle, bookSubtitle, adjusted, fonts, tocPages.size)
                adjusted
            } else {
                resolvedTocEntries
            }
            val finalTocPages = if (pageDelta != 0) {
                buildTocPages(bookTitle, bookSubtitle, finalTocEntries, fonts, tocPages.size)
            } else {
                tocPages
            }

            val totalPages = finalTocPages.size + articlePages.size

            onProgress("Rendering A4 PDF document ($totalPages pages)...")
            val pdfDocument = PdfDocument()

            var pageNumber = 1
            for (ops in finalTocPages) {
                renderPdfPage(
                    pdfDocument = pdfDocument,
                    pageNumber = pageNumber,
                    totalPages = totalPages,
                    bookTitle = bookTitle,
                    isTocPage = true,
                    ops = ops,
                    fonts = fonts
                )
                pageNumber++
            }
            for (ops in articlePages) {
                renderPdfPage(
                    pdfDocument = pdfDocument,
                    pageNumber = pageNumber,
                    totalPages = totalPages,
                    bookTitle = bookTitle,
                    isTocPage = false,
                    ops = ops,
                    fonts = fonts
                )
                pageNumber++
            }

            val outputDir = File(context.cacheDir, "article_books").apply { mkdirs() }
            // Clean older cached previews to avoid clutter
            outputDir.listFiles()?.forEach { old ->
                if (System.currentTimeMillis() - old.lastModified() > 2 * 60 * 60 * 1000L) {
                    old.delete()
                }
            }
            val safeName = bookTitle
                .trim()
                .replace(Regex("""[^a-zA-Z0-9\u0980-\u09FF_-]+"""), "_")
                .trim('_')
                .take(36)
                .ifBlank { "Article_Book" }
            val outFile = File(outputDir, "${safeName}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            Result.success(
                GeneratedBookPdfResult(
                    pdfFile = outFile,
                    pageCount = totalPages,
                    tocPageCount = finalTocPages.size,
                    tocEntries = finalTocEntries,
                    bookTitle = bookTitle.trim().ifBlank { "Compiled Reading Book" }
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to compile PDF book."))
        }
    }

    private suspend fun prepareArticles(
        articles: List<ArticleEntity>,
        onStep: (Int, Int) -> Unit
    ): List<PreparedArticle> = coroutineScope {
        val total = articles.size
        var completed = 0
        articles.map { article ->
            async {
                val websiteDisplay = ArticleRichContentParser.deriveWebsiteName(article)
                val dateDisplay = ArticleRichContentParser.derivePublishedDate(article)
                val authorDisplay = article.author.trim().takeIf {
                    it.isNotBlank() && !it.equals("Anonymous Author", ignoreCase = true)
                } ?: "Editorial Desk"

                val blocks = ArticleRichContentParser.parseBlocks(article.content)

                val faviconUrlCandidate = article.faviconUrl.ifBlank {
                    val host = try { URI(article.sourceUrl).host } catch (_: Exception) { null }
                    if (!host.isNullOrBlank()) "https://www.google.com/s2/favicons?domain=$host&sz=64" else ""
                }

                val favDeferred = async {
                    if (faviconUrlCandidate.isNotBlank()) downloadBitmap(faviconUrlCandidate, maxDim = 96) else null
                }

                // Download cover image if valid and not already present in blocks
                val blockImageUrls = blocks.filterIsInstance<ArticleContentBlock.ImageBlock>()
                    .map { it.url }
                    .distinct()
                    .take(4)

                val coverUrl = article.coverImageUrl.takeIf {
                    it.isNotBlank() && it !in blockImageUrls
                }
                val coverDeferred = async {
                    if (coverUrl != null) downloadBitmap(coverUrl, maxDim = 900) else null
                }

                val bodyImagesDeferred = blockImageUrls.map { url ->
                    async { url to downloadBitmap(url, maxDim = 900) }
                }

                val favBmp = favDeferred.await()
                val coverBmp = coverDeferred.await()
                val bodyImages = bodyImagesDeferred.awaitAll()
                    .mapNotNull { (url, bmp) -> if (bmp != null) url to bmp else null }
                    .toMap()

                completed++
                onStep(completed, total)

                PreparedArticle(
                    article = article,
                    displayTitle = article.title.trim().ifBlank { "Untitled Article" },
                    authorDisplay = authorDisplay,
                    websiteDisplay = websiteDisplay,
                    dateDisplay = dateDisplay,
                    faviconBitmap = favBmp,
                    coverBitmap = coverBmp,
                    blocks = blocks,
                    bodyImages = bodyImages
                )
            }
        }.awaitAll()
    }

    private fun downloadBitmap(url: String, maxDim: Int): Bitmap? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) MemorizerReader/1.0")
                .build()
            imageHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty() || bytes.size > 4_000_000) return null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sample = 1
                while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBilingualSpannable(
        text: String,
        fonts: FontSet,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        parseLinks: Boolean = false,
        linkColor: Int = Color.rgb(37, 99, 235)
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val segments = if (parseLinks) {
            ArticleRichContentParser.parseInlineSegments(text)
        } else {
            listOf(InlineRichSegment(text, null))
        }

        for (seg in segments) {
            val isLink = seg.url != null
            val runs = ArticleRichContentParser.splitScriptRuns(seg.text)
            for (run in runs) {
                val start = builder.length
                builder.append(run.text)
                val end = builder.length
                val tf = when {
                    run.isBengali && isBold -> fonts.bengaliBold
                    run.isBengali -> fonts.bengaliRegular
                    isBold -> fonts.tinosBold
                    isItalic -> fonts.tinosItalic
                    else -> fonts.tinosRegular
                }
                val needsFakeBold = isBold && (run.isBengali || tf === fonts.tinosRegular)
                builder.setSpan(
                    BilingualTypefaceSpan(
                        typeface = tf,
                        fakeBold = needsFakeBold,
                        linkColor = if (isLink) linkColor else null,
                        underline = isLink
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun createStaticLayout(
        source: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        lineSpacingMultiplier: Float = 1.28f,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(source, 0, source.length, paint, widthPx.coerceAtLeast(40))
            .setAlignment(alignment)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    }

    private fun measureTocPages(
        bookTitle: String,
        bookSubtitle: String,
        entries: List<TocEntryInfo>,
        fonts: FontSet
    ): List<MutableList<PageDrawOp>> {
        return buildTocPages(bookTitle, bookSubtitle, entries, fonts, null)
    }

    private fun buildTocPages(
        bookTitle: String,
        bookSubtitle: String,
        entries: List<TocEntryInfo>,
        fonts: FontSet,
        expectedPageCount: Int?
    ): List<MutableList<PageDrawOp>> {
        val pages = mutableListOf<MutableList<PageDrawOp>>()
        var currentPage = mutableListOf<PageDrawOp>()
        pages.add(currentPage)

        var y = CONTENT_TOP + 8f

        // Book Title
        val bookTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 24f
            typeface = fonts.tinosBold
        }
        val cleanBookTitle = bookTitle.trim().ifBlank { "Compiled Reading Book" }
        val bookTitleSpannable = buildBilingualSpannable(cleanBookTitle, fonts, isBold = true)
        val bookTitleLayout = createStaticLayout(bookTitleSpannable, bookTitlePaint, CONTENT_WIDTH.toInt(), 1.18f)
        currentPage.add(PageDrawOp.DrawStaticLayoutSlice(bookTitleLayout, 0, bookTitleLayout.lineCount, MARGIN_LEFT, y))
        y += bookTitleLayout.height + 6f

        if (bookSubtitle.isNotBlank()) {
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 11.5f
                typeface = fonts.tinosItalic
            }
            val subSpannable = buildBilingualSpannable(bookSubtitle.trim(), fonts, isItalic = true)
            val subLayout = createStaticLayout(subSpannable, subtitlePaint, CONTENT_WIDTH.toInt(), 1.25f)
            currentPage.add(PageDrawOp.DrawStaticLayoutSlice(subLayout, 0, subLayout.lineCount, MARGIN_LEFT, y))
            y += subLayout.height + 6f
        }

        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())
        val bookMetaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            typeface = fonts.tinosRegular
        }
        val metaLine = "${entries.size} Articles  •  Compiled on $todayStr  •  A4 Edition"
        val metaSpannable = buildBilingualSpannable(metaLine, fonts)
        val metaLayout = createStaticLayout(metaSpannable, bookMetaPaint, CONTENT_WIDTH.toInt(), 1.2f)
        currentPage.add(PageDrawOp.DrawStaticLayoutSlice(metaLayout, 0, metaLayout.lineCount, MARGIN_LEFT, y))
        y += metaLayout.height + 14f

        currentPage.add(PageDrawOp.DrawDivider(MARGIN_LEFT, MARGIN_LEFT + CONTENT_WIDTH, y, Color.rgb(59, 68, 226), 1.4f))
        y += 18f

        // TOC Section Heading
        val tocHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            typeface = fonts.tinosBold
        }
        val tocHeadingSpannable = buildBilingualSpannable("Table of Contents", fonts, isBold = true)
        val tocHeadingLayout = createStaticLayout(tocHeadingSpannable, tocHeadingPaint, CONTENT_WIDTH.toInt(), 1.2f)
        currentPage.add(PageDrawOp.DrawStaticLayoutSlice(tocHeadingLayout, 0, tocHeadingLayout.lineCount, MARGIN_LEFT, y))
        y += tocHeadingLayout.height + 12f

        val entryTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11.5f
            typeface = fonts.tinosBold
        }
        val entrySubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
            typeface = fonts.tinosRegular
        }
        val pageNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(59, 68, 226)
            textSize = 11f
            typeface = fonts.tinosBold
        }

        val numColWidth = 26f
        val pageColWidth = 38f
        val titleColWidth = (CONTENT_WIDTH - numColWidth - pageColWidth - 10f).toInt()

        for (entry in entries) {
            val idxStr = String.format(Locale.ENGLISH, "%02d.", entry.index)
            val idxLayout = createStaticLayout(buildBilingualSpannable(idxStr, fonts, isBold = true), entryTitlePaint, numColWidth.toInt(), 1.2f)
            val titleLayout = createStaticLayout(buildBilingualSpannable(entry.title, fonts, isBold = true), entryTitlePaint, titleColWidth, 1.22f)
            val subText = "${entry.websiteName}  •  ${entry.author}"
            val subLayout = createStaticLayout(buildBilingualSpannable(subText, fonts), entrySubPaint, titleColWidth, 1.15f)
            val pNumLayout = createStaticLayout(
                buildBilingualSpannable("${entry.startPage}", fonts, isBold = true),
                pageNumPaint,
                pageColWidth.toInt(),
                1.2f,
                Layout.Alignment.ALIGN_OPPOSITE
            )

            val itemHeight = maxOf(titleLayout.height + subLayout.height + 2f, idxLayout.height.toFloat()) + 9f
            if (y + itemHeight > CONTENT_BOTTOM) {
                currentPage = mutableListOf()
                pages.add(currentPage)
                y = CONTENT_TOP + 6f
            }

            currentPage.add(PageDrawOp.DrawStaticLayoutSlice(idxLayout, 0, idxLayout.lineCount, MARGIN_LEFT, y))
            currentPage.add(PageDrawOp.DrawStaticLayoutSlice(titleLayout, 0, titleLayout.lineCount, MARGIN_LEFT + numColWidth, y))
            currentPage.add(PageDrawOp.DrawStaticLayoutSlice(subLayout, 0, subLayout.lineCount, MARGIN_LEFT + numColWidth, y + titleLayout.height + 2f))
            currentPage.add(
                PageDrawOp.DrawStaticLayoutSlice(
                    pNumLayout,
                    0,
                    pNumLayout.lineCount,
                    MARGIN_LEFT + CONTENT_WIDTH - pageColWidth,
                    y
                )
            )
            val dividerY = y + itemHeight - 4f
            currentPage.add(PageDrawOp.DrawDivider(MARGIN_LEFT + numColWidth, MARGIN_LEFT + CONTENT_WIDTH, dividerY, Color.rgb(226, 232, 240), 0.5f))
            y += itemHeight + 3f
        }

        if (expectedPageCount != null && pages.size < expectedPageCount) {
            repeat(expectedPageCount - pages.size) {
                pages.add(mutableListOf())
            }
        }
        return pages
    }

    private fun layoutArticlesContinuously(
        preparedArticles: List<PreparedArticle>,
        fonts: FontSet,
        firstArticlePageNumber: Int,
        outPages: MutableList<MutableList<PageDrawOp>>,
        outTocEntries: MutableList<TocEntryInfo>
    ) {
        var currentPage = mutableListOf<PageDrawOp>()
        outPages.add(currentPage)
        var currentPageNumber = firstArticlePageNumber
        var y = CONTENT_TOP

        fun startNewPage() {
            currentPage = mutableListOf()
            outPages.add(currentPage)
            currentPageNumber++
            y = CONTENT_TOP
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 17.5f
            typeface = fonts.tinosBold
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.2f
            typeface = fonts.tinosRegular
        }
        val heading2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 13f
            typeface = fonts.tinosBold
        }
        val heading3Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 11.5f
            typeface = fonts.tinosBold
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.8f
            typeface = fonts.tinosRegular
        }
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.4f
            typeface = fonts.tinosItalic
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.8f
            typeface = fonts.tinosItalic
        }

        val titleAreaWidth = (CONTENT_WIDTH - 34f).toInt() // Reserve space for right-side website icon

        fun emitSplitLayout(
            layout: StaticLayout,
            x: Float,
            quoteBarColor: Int? = null,
            bulletMarker: String? = null,
            bulletMarkerPaint: TextPaint? = null,
            bulletMarkerX: Float = 0f,
            bottomGap: Float = 7f
        ) {
            var lineIdx = 0
            while (lineIdx < layout.lineCount) {
                val avail = CONTENT_BOTTOM - y
                if (avail < (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx))) {
                    startNewPage()
                }
                val sliceTopPx = layout.getLineTop(lineIdx)
                var endIdx = lineIdx
                while (endIdx < layout.lineCount) {
                    val neededHeight = (layout.getLineBottom(endIdx) - sliceTopPx).toFloat()
                    if (y + neededHeight <= CONTENT_BOTTOM) {
                        endIdx++
                    } else {
                        break
                    }
                }
                if (endIdx == lineIdx) {
                    // Single giant line fallback: render on fresh page
                    startNewPage()
                    endIdx = lineIdx + 1
                }
                currentPage.add(
                    PageDrawOp.DrawStaticLayoutSlice(
                        layout = layout,
                        firstLine = lineIdx,
                        lastLineExclusive = endIdx,
                        x = x,
                        y = y,
                        quoteBarColor = quoteBarColor,
                        bulletMarker = if (lineIdx == 0) bulletMarker else null,
                        bulletMarkerPaint = bulletMarkerPaint,
                        bulletMarkerX = bulletMarkerX
                    )
                )
                val sliceHeight = (layout.getLineBottom(endIdx - 1) - sliceTopPx).toFloat()
                y += sliceHeight
                lineIdx = endIdx
                if (lineIdx < layout.lineCount) {
                    startNewPage()
                }
            }
            y += bottomGap
        }

        fun emitBitmap(bitmap: Bitmap, caption: String?) {
            val maxImgW = CONTENT_WIDTH * 0.88f
            val maxImgH = 220f
            val ratio = min(maxImgW / bitmap.width.toFloat(), maxImgH / bitmap.height.toFloat()).coerceAtMost(1.8f)
            val drawW = (bitmap.width * ratio).coerceAtLeast(60f)
            val drawH = (bitmap.height * ratio).coerceAtLeast(40f)
            val captionLayout = if (!caption.isNullOrBlank()) {
                createStaticLayout(
                    buildBilingualSpannable(caption, fonts, isItalic = true),
                    captionPaint,
                    drawW.toInt(),
                    1.15f,
                    Layout.Alignment.ALIGN_CENTER
                )
            } else null
            val totalH = drawH + (captionLayout?.height?.plus(4f) ?: 0f)
            if (y + totalH > CONTENT_BOTTOM) {
                startNewPage()
            }
            val centeredX = MARGIN_LEFT + (CONTENT_WIDTH - drawW) / 2f
            currentPage.add(PageDrawOp.DrawImage(bitmap, captionLayout, centeredX, y, drawW, drawH))
            y += totalH + 10f
        }

        preparedArticles.forEachIndexed { articleIdx, prep ->
            // Continuous article flow: NO mandatory page break between articles!
            if (articleIdx > 0) {
                if (y + 38f > CONTENT_BOTTOM) {
                    startNewPage()
                } else {
                    y += 10f
                    currentPage.add(
                        PageDrawOp.DrawDivider(
                            MARGIN_LEFT,
                            MARGIN_LEFT + CONTENT_WIDTH,
                            y,
                            Color.rgb(203, 213, 225),
                            0.8f
                        )
                    )
                    y += 18f
                }
            }

            // Prepare Title + Metadata before emitting to ensure title isn't stranded at bottom edge
            val titleSpannable = buildBilingualSpannable(prep.displayTitle, fonts, isBold = true)
            val titleLayout = createStaticLayout(titleSpannable, titlePaint, titleAreaWidth, 1.18f)

            val metaParts = listOfNotNull(
                prep.authorDisplay.takeIf { it.isNotBlank() },
                prep.dateDisplay.takeIf { it.isNotBlank() },
                prep.websiteDisplay.takeIf { it.isNotBlank() }
            )
            val metaText = metaParts.joinToString("   •   ")
            val metaSpannable = buildBilingualSpannable(metaText, fonts)
            val metaLayout = createStaticLayout(metaSpannable, metaPaint, CONTENT_WIDTH.toInt(), 1.2f)

            val headerTotalHeight = maxOf(titleLayout.height.toFloat(), 24f) + 5f + metaLayout.height + 12f
            // Guard only against stranding article title+meta with zero body lines at page bottom
            if (y + headerTotalHeight + 24f > CONTENT_BOTTOM && y > CONTENT_TOP + 20f) {
                startNewPage()
            }

            val articleStartPage = currentPageNumber
            outTocEntries.add(
                TocEntryInfo(
                    index = articleIdx + 1,
                    articleId = prep.article.id,
                    title = prep.displayTitle,
                    author = prep.authorDisplay,
                    websiteName = prep.websiteDisplay,
                    startPage = articleStartPage
                )
            )

            val monogram = prep.websiteDisplay.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
            currentPage.add(
                PageDrawOp.DrawTitleWithFavicon(
                    titleLayout = titleLayout,
                    favicon = prep.faviconBitmap,
                    monogram = monogram,
                    titleX = MARGIN_LEFT,
                    iconRightEdge = MARGIN_LEFT + CONTENT_WIDTH,
                    y = y
                )
            )
            y += maxOf(titleLayout.height.toFloat(), 24f) + 4f

            currentPage.add(PageDrawOp.DrawMetadataRow(metaLayout, MARGIN_LEFT, y))
            y += metaLayout.height + 6f

            currentPage.add(
                PageDrawOp.DrawDivider(
                    MARGIN_LEFT,
                    MARGIN_LEFT + 72f,
                    y,
                    Color.rgb(99, 102, 241),
                    1.2f
                )
            )
            y += 10f

            // Optional cover image at start of article if downloaded
            if (prep.coverBitmap != null) {
                emitBitmap(prep.coverBitmap, null)
            }

            // Render every content block continuously
            for (block in prep.blocks) {
                when (block) {
                    is ArticleContentBlock.Heading -> {
                        val p = if (block.level <= 2) heading2Paint else heading3Paint
                        val hSpannable = buildBilingualSpannable(block.text, fonts, isBold = true, parseLinks = true)
                        val hLayout = createStaticLayout(hSpannable, p, CONTENT_WIDTH.toInt(), 1.22f)
                        if (y + hLayout.height + 18f > CONTENT_BOTTOM) {
                            startNewPage()
                        } else if (y > CONTENT_TOP + 12f) {
                            y += 4f
                        }
                        emitSplitLayout(hLayout, MARGIN_LEFT, bottomGap = 5f)
                    }

                    is ArticleContentBlock.BulletItem -> {
                        val indent = 18f
                        val itemSpannable = buildBilingualSpannable(block.text, fonts, parseLinks = true)
                        val itemLayout = createStaticLayout(itemSpannable, bodyPaint, (CONTENT_WIDTH - indent).toInt(), 1.32f)
                        emitSplitLayout(
                            layout = itemLayout,
                            x = MARGIN_LEFT + indent,
                            bulletMarker = block.marker,
                            bulletMarkerPaint = bodyPaint,
                            bulletMarkerX = MARGIN_LEFT + 4f,
                            bottomGap = 4.5f
                        )
                    }

                    is ArticleContentBlock.Blockquote -> {
                        val quoteIndent = 14f
                        val qSpannable = buildBilingualSpannable(block.text, fonts, isItalic = true, parseLinks = true)
                        val qLayout = createStaticLayout(qSpannable, quotePaint, (CONTENT_WIDTH - quoteIndent).toInt(), 1.32f)
                        emitSplitLayout(
                            layout = qLayout,
                            x = MARGIN_LEFT + quoteIndent,
                            quoteBarColor = Color.rgb(99, 102, 241),
                            bottomGap = 7f
                        )
                    }

                    is ArticleContentBlock.ImageBlock -> {
                        val bmp = prep.bodyImages[block.url]
                        if (bmp != null) {
                            emitBitmap(bmp, block.caption)
                        }
                    }

                    is ArticleContentBlock.Paragraph -> {
                        val pSpannable = buildBilingualSpannable(block.text, fonts, parseLinks = true)
                        val pLayout = createStaticLayout(pSpannable, bodyPaint, CONTENT_WIDTH.toInt(), 1.34f)
                        emitSplitLayout(pLayout, MARGIN_LEFT, bottomGap = 7.5f)
                    }
                }
            }
        }
    }

    private fun renderPdfPage(
        pdfDocument: PdfDocument,
        pageNumber: Int,
        totalPages: Int,
        bookTitle: String,
        isTocPage: Boolean,
        ops: List<PageDrawOp>,
        fonts: FontSet
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Clean crisp white paper canvas
        canvas.drawColor(Color.WHITE)

        // Running Header on non-first TOC / article pages
        if (!(isTocPage && pageNumber == 1)) {
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 8.5f
                typeface = fonts.tinosItalic
            }
            val headerTitle = bookTitle.trim().ifBlank { "Reading Book" }.take(64)
            canvas.drawText(headerTitle, MARGIN_LEFT, HEADER_Y, headerPaint)
            val labelRight = if (isTocPage) "Table of Contents" else "Compiled Articles"
            val rightW = headerPaint.measureText(labelRight)
            canvas.drawText(labelRight, MARGIN_LEFT + CONTENT_WIDTH - rightW, HEADER_Y, headerPaint)

            val hLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.6f
            }
            canvas.drawLine(MARGIN_LEFT, HEADER_Y + 6f, MARGIN_LEFT + CONTENT_WIDTH, HEADER_Y + 6f, hLinePaint)
        }

        // Draw operations
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 242, 255)
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(199, 210, 254)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(67, 56, 202)
            textSize = 11f
            typeface = fonts.tinosBold
            textAlign = Paint.Align.CENTER
        }

        for (op in ops) {
            when (op) {
                is PageDrawOp.DrawStaticLayoutSlice -> {
                    val topPx = op.layout.getLineTop(op.firstLine)
                    val bottomPx = op.layout.getLineBottom(op.lastLineExclusive - 1)
                    val sliceH = (bottomPx - topPx).toFloat()

                    if (op.quoteBarColor != null) {
                        quotePaint.color = op.quoteBarColor
                        quotePaint.strokeWidth = 2.2f
                        canvas.drawLine(op.x - 8f, op.y + 1f, op.x - 8f, op.y + sliceH - 1f, quotePaint)
                    }
                    if (op.bulletMarker != null && op.bulletMarkerPaint != null) {
                        val baseline = op.y + (op.layout.getLineBaseline(0) - op.layout.getLineTop(0))
                        canvas.drawText(op.bulletMarker, op.bulletMarkerX, baseline, op.bulletMarkerPaint)
                    }

                    canvas.save()
                    canvas.clipRect(op.x - 2f, op.y, op.x + op.layout.width + 2f, op.y + sliceH + 1f)
                    canvas.translate(op.x, op.y - topPx)
                    op.layout.draw(canvas)
                    canvas.restore()
                }

                is PageDrawOp.DrawTitleWithFavicon -> {
                    canvas.save()
                    canvas.translate(op.titleX, op.y)
                    op.titleLayout.draw(canvas)
                    canvas.restore()

                    val iconSize = 22f
                    val iconLeft = op.iconRightEdge - iconSize
                    val iconTop = op.y + 2f
                    val rect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    canvas.drawRoundRect(rect, 5f, 5f, badgeBgPaint)
                    canvas.drawRoundRect(rect, 5f, 5f, badgeBorderPaint)

                    if (op.favicon != null) {
                        val inset = 3f
                        val dst = RectF(iconLeft + inset, iconTop + inset, iconLeft + iconSize - inset, iconTop + iconSize - inset)
                        canvas.drawBitmap(op.favicon, null, dst, bitmapPaint)
                    } else {
                        val textY = rect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
                        canvas.drawText(op.monogram, rect.centerX(), textY, badgeTextPaint)
                    }
                }

                is PageDrawOp.DrawMetadataRow -> {
                    canvas.save()
                    canvas.translate(op.x, op.y)
                    op.layout.draw(canvas)
                    canvas.restore()
                }

                is PageDrawOp.DrawImage -> {
                    val rect = RectF(op.x, op.y, op.x + op.width, op.y + op.height)
                    canvas.drawBitmap(op.bitmap, null, rect, bitmapPaint)
                    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(226, 232, 240)
                        style = Paint.Style.STROKE
                        strokeWidth = 0.7f
                    }
                    canvas.drawRect(rect, border)
                    if (op.captionLayout != null) {
                        canvas.save()
                        canvas.translate(op.x, op.y + op.height + 4f)
                        op.captionLayout.draw(canvas)
                        canvas.restore()
                    }
                }

                is PageDrawOp.DrawDivider -> {
                    linePaint.color = op.color
                    linePaint.strokeWidth = op.strokeWidth
                    canvas.drawLine(op.x1, op.y, op.x2, op.y, linePaint)
                }
            }
        }

        // Centered A4 Footer Page Number
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            typeface = fonts.tinosRegular
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNumber of $totalPages", A4_WIDTH / 2f, FOOTER_Y, footerPaint)

        pdfDocument.finishPage(page)
    }

    /**
     * Saves the compiled PDF into the user's device Downloads folder cleanly.
     */
    suspend fun savePdfToDownloads(
        context: Context,
        sourceFile: File,
        bookTitle: String
    ): Result<DownloadedPdfInfo> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Compiled PDF file not found."))
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ENGLISH).format(Date())
            val safeTitle = bookTitle
                .trim()
                .replace(Regex("""[^a-zA-Z0-9\u0980-\u09FF_-]+"""), "_")
                .trim('_')
                .take(40)
                .ifBlank { "Article_Book" }
            val fileName = "${safeTitle}_$stamp.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create file in Downloads."))

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: return@withContext Result.failure(IllegalStateException("Unable to write PDF to Downloads."))
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } catch (writeErr: Exception) {
                    resolver.delete(uri, null, null)
                    throw writeErr
                }

                Result.success(
                    DownloadedPdfInfo(
                        uri = uri,
                        fileName = fileName,
                        displayLocation = "Downloads/$fileName"
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = try {
                    publicDownloads.apply { mkdirs() }
                    if (publicDownloads.canWrite()) publicDownloads else (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
                } catch (_: Exception) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                }
                targetDir.mkdirs()
                val destFile = File(targetDir, fileName)
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
                Result.success(
                    DownloadedPdfInfo(
                        uri = uri,
                        fileName = fileName,
                        displayLocation = destFile.absolutePath
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to save PDF to Downloads."))
        }
    }
}
