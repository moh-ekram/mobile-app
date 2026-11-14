package com.example.data.parser

import android.content.Context
import com.example.data.model.BookPageEntity
import com.example.data.model.BookTocEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ParsedBookContent(
    val title: String,
    val author: String,
    val subject: String,
    val format: String, // "DOCX"
    val totalPages: Int,
    val pages: List<BookPageEntity>,
    val tocEntries: List<BookTocEntity>
)

object BookParserService {

    fun detectFormat(fileName: String, headerBytes: ByteArray? = null): String {
        val lower = fileName.lowercase().trim()
        if (lower.endsWith(".docx")) return "DOCX"
        if (headerBytes != null && headerBytes.size >= 4) {
            // PK.. (ZIP / DOCX)
            if (headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()) {
                return "DOCX"
            }
        }
        return ""
    }

    fun parseBookFile(
        context: Context,
        bookId: String,
        localFile: File,
        fallbackTitle: String,
        formatHint: String
    ): ParsedBookContent {
        val header = ByteArray(8).also { buf ->
            try {
                localFile.inputStream().use { it.read(buf) }
            } catch (_: Exception) {}
        }
        val format = detectFormat(
            if (formatHint.isNotBlank()) "file.${formatHint.lowercase()}" else localFile.name,
            header
        )
        if (format != "DOCX") {
            throw IllegalArgumentException("Only DOCX eBook format is supported.")
        }
        return parseDocxFile(bookId, localFile, fallbackTitle)
    }

    private data class DocxParagraph(
        val text: String,
        val headingLevel: Int = 0, // 0 = body, 1..3 = heading
        val hasExplicitBreakBefore: Boolean = false,
        val hasBreakInsideOrAfter: Boolean = false
    )

    private fun parseDocxFile(
        bookId: String,
        localFile: File,
        fallbackTitle: String
    ): ParsedBookContent {
        var documentXmlBytes: ByteArray? = null
        var stylesXmlBytes: ByteArray? = null
        var coreXmlBytes: ByteArray? = null

        ZipInputStream(localFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name.lowercase()) {
                    "word/document.xml" -> documentXmlBytes = zip.readBytes()
                    "word/styles.xml" -> stylesXmlBytes = zip.readBytes()
                    "docprops/core.xml" -> coreXmlBytes = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val docBytes = documentXmlBytes
            ?: throw IllegalStateException("Invalid DOCX file: word/document.xml not found.")

        val (metaTitle, metaAuthor, metaSubject) = parseDocxCoreMetadata(coreXmlBytes)
        val headingStyleLevels = parseDocxHeadingStyles(stylesXmlBytes)
        val paragraphs = parseDocxParagraphs(docBytes, headingStyleLevels)

        // Paginate DOCX paragraphs cleanly:
        // Honor explicit page breaks / lastRenderedPageBreak if they create reasonable pages,
        // and also split overly long stretches at ~280 words for smooth Book/PDF-layout reading.
        val pageBuilders = mutableListOf<MutableList<DocxParagraph>>()
        var currentList = mutableListOf<DocxParagraph>()
        var currentWords = 0

        fun flushPage() {
            if (currentList.isNotEmpty()) {
                pageBuilders.add(currentList)
                currentList = mutableListOf()
                currentWords = 0
            }
        }

        for (para in paragraphs) {
            if (para.hasExplicitBreakBefore && currentList.isNotEmpty()) {
                flushPage()
            }
            val wCount = countWords(para.text)
            if (currentWords > 0 && currentWords + wCount > 290 && currentWords >= 120) {
                flushPage()
            }
            currentList.add(para)
            currentWords += wCount
            if (para.hasBreakInsideOrAfter && currentWords >= 45) {
                flushPage()
            }
        }
        flushPage()

        if (pageBuilders.isEmpty()) {
            pageBuilders.add(mutableListOf(DocxParagraph("(Empty document)")))
        }

        val tocList = mutableListOf<BookTocEntity>()
        var tocOrder = 0
        val rawPageTexts = mutableListOf<String>()

        pageBuilders.forEachIndexed { idx, paraList ->
            val pageNum = idx + 1
            for (para in paraList) {
                if (para.headingLevel in 1..3 && para.text.length in 2..140) {
                    tocList.add(
                        BookTocEntity(
                            id = "${bookId}_toc_${tocOrder}",
                            bookId = bookId,
                            title = para.text.trim(),
                            level = para.headingLevel,
                            pageNumber = pageNum,
                            sortOrder = tocOrder
                        )
                    )
                    tocOrder++
                }
            }
            val pageText = paraList.joinToString("\n\n") { it.text.trim() }.trim()
            rawPageTexts.add(cleanExtractedText(pageText))
        }

        if (tocList.isEmpty()) {
            tocList.addAll(extractHeuristicTocFromPages(bookId, rawPageTexts))
        }

        val sortedToc = tocList.sortedBy { it.pageNumber }
        val pages = rawPageTexts.mapIndexed { idx, text ->
            val pageNum = idx + 1
            val activeChapter = sortedToc.lastOrNull { it.pageNumber <= pageNum }?.title ?: ""
            BookPageEntity(
                id = "${bookId}_p${pageNum}",
                bookId = bookId,
                pageNumber = pageNum,
                chapterTitle = activeChapter,
                textContent = text,
                wordCount = countWords(text)
            )
        }

        return ParsedBookContent(
            title = metaTitle.ifBlank { fallbackTitle },
            author = metaAuthor,
            subject = metaSubject,
            format = "DOCX",
            totalPages = pages.size,
            pages = pages,
            tocEntries = tocList
        )
    }

    private fun parseDocxCoreMetadata(coreBytes: ByteArray?): Triple<String, String, String> {
        if (coreBytes == null) return Triple("", "", "")
        var title = ""
        var author = ""
        var subject = ""
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(coreBytes), "UTF-8")
            var currentTag = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> currentTag = parser.name?.substringAfter(":") ?: ""
                    XmlPullParser.TEXT -> {
                        val txt = parser.text?.trim().orEmpty()
                        if (txt.isNotBlank()) {
                            when (currentTag) {
                                "title" -> if (title.isBlank()) title = txt
                                "creator" -> if (author.isBlank()) author = txt
                                "subject" -> if (subject.isBlank()) subject = txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return Triple(title, author, subject)
    }

    private fun parseDocxHeadingStyles(stylesBytes: ByteArray?): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (stylesBytes == null) return map
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(stylesBytes), "UTF-8")
            var currentStyleId: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfter(":") ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "style") {
                            currentStyleId = parser.getAttributeValue(null, "w:styleId")
                                ?: parser.getAttributeValue(null, "styleId")
                        } else if (tag == "name" && currentStyleId != null) {
                            val valAttr = (parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val")
                                ?: "").lowercase()
                            val level = detectHeadingLevel(valAttr)
                            if (level > 0) {
                                map[currentStyleId] = level
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "style") currentStyleId = null
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return map
    }

    private fun detectHeadingLevel(styleNameOrId: String): Int {
        val lower = styleNameOrId.lowercase().trim()
        return when {
            lower == "title" || lower == "heading1" || lower == "heading 1" || lower == "h1" -> 1
            lower == "subtitle" || lower == "heading2" || lower == "heading 2" || lower == "h2" -> 2
            lower == "heading3" || lower == "heading 3" || lower == "h3" -> 3
            lower.startsWith("heading") -> 2
            else -> 0
        }
    }

    private fun parseDocxParagraphs(
        docBytes: ByteArray,
        headingStyles: Map<String, Int>
    ): List<DocxParagraph> {
        val paragraphs = mutableListOf<DocxParagraph>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(docBytes), "UTF-8")

        var inParagraph = false
        var inTableRow = false
        var inTableCell = false
        val currentRowCells = mutableListOf<String>()
        val currentCellBuilder = StringBuilder()

        var paraBuilder = StringBuilder()
        var paraStyleLevel = 0
        var paraHasNumPr = false
        var paraAllBold = true
        var paraHasText = false
        var runIsBold = false
        var explicitBreakBefore = false
        var breakInsideOrAfter = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(":") ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "tr" -> {
                            inTableRow = true
                            currentRowCells.clear()
                        }
                        "tc" -> {
                            inTableCell = true
                            currentCellBuilder.clear()
                        }
                        "p" -> {
                            inParagraph = true
                            paraBuilder = StringBuilder()
                            paraStyleLevel = 0
                            paraHasNumPr = false
                            paraAllBold = true
                            paraHasText = false
                            explicitBreakBefore = false
                            breakInsideOrAfter = false
                        }
                        "r" -> {
                            runIsBold = false
                        }
                        "b", "bCs" -> {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val")
                            if (valAttr != "0" && !valAttr.equals("false", true)) {
                                runIsBold = true
                            }
                        }
                        "pStyle" -> if (inParagraph) {
                            val styleVal = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val")
                                ?: ""
                            paraStyleLevel = headingStyles[styleVal] ?: detectHeadingLevel(styleVal)
                        }
                        "numPr" -> if (inParagraph) {
                            paraHasNumPr = true
                        }
                        "pageBreakBefore" -> if (inParagraph) {
                            explicitBreakBefore = true
                        }
                        "lastRenderedPageBreak" -> if (inParagraph) {
                            if (paraBuilder.isEmpty()) explicitBreakBefore = true else breakInsideOrAfter = true
                        }
                        "br" -> if (inParagraph) {
                            val brType = parser.getAttributeValue(null, "w:type")
                                ?: parser.getAttributeValue(null, "type")
                            if (brType.equals("page", ignoreCase = true)) {
                                if (paraBuilder.isEmpty()) explicitBreakBefore = true else breakInsideOrAfter = true
                            } else {
                                paraBuilder.append('\n')
                            }
                        }
                        "cr" -> if (inParagraph) paraBuilder.append('\n')
                        "tab" -> if (inParagraph) paraBuilder.append("    ")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph) {
                        val txt = parser.text ?: ""
                        if (txt.isNotBlank()) {
                            paraHasText = true
                            if (!runIsBold) paraAllBold = false
                        }
                        paraBuilder.append(txt)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "p" -> {
                            if (inParagraph) {
                                val rawText = paraBuilder.toString().trim()
                                if (inTableCell) {
                                    if (rawText.isNotBlank()) {
                                        if (currentCellBuilder.isNotEmpty()) currentCellBuilder.append(" ")
                                        currentCellBuilder.append(rawText)
                                    }
                                } else if (rawText.isNotBlank()) {
                                    val finalText = if (paraHasNumPr && !rawText.startsWith("•") && !rawText.startsWith("-")) {
                                        "• $rawText"
                                    } else {
                                        rawText
                                    }
                                    val inferredLevel = when {
                                        paraStyleLevel > 0 -> paraStyleLevel
                                        paraAllBold && paraHasText && finalText.length in 3..80 && !finalText.endsWith(".") -> 2
                                        else -> 0
                                    }
                                    paragraphs.add(
                                        DocxParagraph(
                                            text = finalText,
                                            headingLevel = inferredLevel,
                                            hasExplicitBreakBefore = explicitBreakBefore,
                                            hasBreakInsideOrAfter = breakInsideOrAfter
                                        )
                                    )
                                } else if (explicitBreakBefore || breakInsideOrAfter) {
                                    // Preserve standalone page break marker
                                    if (paragraphs.isNotEmpty()) {
                                        val last = paragraphs.removeAt(paragraphs.lastIndex)
                                        paragraphs.add(last.copy(hasBreakInsideOrAfter = true))
                                    }
                                }
                                inParagraph = false
                            }
                        }
                        "tc" -> {
                            if (inTableCell) {
                                val cellText = currentCellBuilder.toString().trim()
                                if (cellText.isNotBlank()) {
                                    currentRowCells.add(cellText)
                                }
                                inTableCell = false
                            }
                        }
                        "tr" -> {
                            if (inTableRow) {
                                if (currentRowCells.isNotEmpty()) {
                                    paragraphs.add(
                                        DocxParagraph(
                                            text = currentRowCells.joinToString("  |  "),
                                            headingLevel = 0
                                        )
                                    )
                                }
                                inTableRow = false
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    private fun extractHeuristicTocFromPages(
        bookId: String,
        pages: List<String>
    ): List<BookTocEntity> {
        val toc = mutableListOf<BookTocEntity>()
        val seenTitles = mutableSetOf<String>()
        val chapterRegex = Regex(
            """^(?:chapter|unit|lesson|part|module|section|topic|অধ্যায়|অধ্যায়|পরিচ্ছেদ|পাঠ|পর্ব)\s*[\d০-৯IVXLC]+.*$""",
            RegexOption.IGNORE_CASE
        )
        val numberedHeadingRegex = Regex("""^(?:[\d০-৯]{1,2}\.)\s+[^\n]{3,70}$""")

        var order = 0
        pages.forEachIndexed { idx, text ->
            val pageNum = idx + 1
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }.take(8)
            for (line in lines) {
                if (line.length in 4..85 && !line.endsWith(",") && !line.endsWith(";")) {
                    val isChapter = chapterRegex.matches(line)
                    val isNumbered = numberedHeadingRegex.matches(line) && !line.endsWith(".")
                    if ((isChapter || isNumbered) && seenTitles.add(line.lowercase())) {
                        toc.add(
                            BookTocEntity(
                                id = "${bookId}_toc_${order}",
                                bookId = bookId,
                                title = line,
                                level = if (isChapter) 1 else 2,
                                pageNumber = pageNum,
                                sortOrder = order
                            )
                        )
                        order++
                        if (toc.size >= 80) return toc
                    }
                }
            }
        }
        return toc
    }

    fun cleanExtractedText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
