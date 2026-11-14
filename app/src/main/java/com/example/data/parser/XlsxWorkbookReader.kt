package com.example.data.parser

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure Kotlin OOXML (.xlsx) and RFC-4180 CSV reader supporting:
 * - Skipping hidden and veryHidden sheets via workbook.xml
 * - Merged cells propagation (top-left value copied into empty cells of range)
 * - Excel serial date & time formatting via styles.xml (numFmt / cellXfs)
 * - Full rich-text <si> & <is> multi-run reading while excluding <rPh> phonetic ruby text
 * - Preserving exact 1-based row numbers for accurate error reporting
 * - Surfacing real read/parse errors instead of masking them
 */
object XlsxWorkbookReader {

    private data class SheetMeta(
        val name: String,
        val filePath: String,
        val isHidden: Boolean
    )

    private data class MergeRange(
        val startRow0: Int,
        val endRow0: Int,
        val startCol0: Int,
        val endCol0: Int
    )

    private enum class NumFmtKind {
        GENERAL,
        DATE_ONLY,
        TIME_ONLY,
        DATE_TIME
    }

    private data class WorkbookStyles(
        val xfIndexToNumFmtId: List<Int>,
        val customFormats: Map<Int, String>
    ) {
        fun classifyStyle(styleIdx: Int?): NumFmtKind {
            if (styleIdx == null || styleIdx !in xfIndexToNumFmtId.indices) return NumFmtKind.GENERAL
            val numFmtId = xfIndexToNumFmtId[styleIdx]
            val customCode = customFormats[numFmtId]
            return classifyNumFmt(numFmtId, customCode)
        }
    }

    private fun newSaxParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
    }.newSAXParser()

    fun parseCsvTabularRows(content: String): List<TabularRow> {
        val cleanContent = ImportNormalization.cleanInvisibleChars(content)
        if (cleanContent.isBlank()) return emptyList()

        val firstLine = cleanContent.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        var inQ = false
        var commas = 0
        var semicolons = 0
        var tabs = 0
        for (ch in firstLine) {
            if (ch == '"') inQ = !inQ
            else if (!inQ) {
                when (ch) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                }
            }
        }
        val delimiter = when {
            semicolons > commas && semicolons > tabs -> ';'
            tabs > commas && tabs > semicolons -> '\t'
            else -> ','
        }

        val rows = mutableListOf<TabularRow>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0
        val len = cleanContent.length
        var currentRowNumber = 1

        while (i < len) {
            val c = cleanContent[i]
            if (insideQuotes) {
                if (c == '"') {
                    if (i + 1 < len && cleanContent[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        insideQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> insideQuotes = true
                    delimiter -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                    }
                    '\r' -> {
                        if (i + 1 < len && cleanContent[i + 1] == '\n') i++
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(TabularRow(currentRowNumber, ArrayList(currentRow)))
                        }
                        currentRow.clear()
                        currentRowNumber++
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(TabularRow(currentRowNumber, ArrayList(currentRow)))
                        }
                        currentRow.clear()
                        currentRowNumber++
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(TabularRow(currentRowNumber, ArrayList(currentRow)))
            }
        }

        return rows
    }

    fun parseMultiSheetXlsx(inputStream: InputStream): List<ParsedSheetData> {
        val entries = mutableMapOf<String, ByteArray>()
        var zipEntryCount = 0
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    zipEntryCount++
                    val name = entry.name.replace('\\', '/').lowercase()
                    if (name == "encryptedpackage" || name.endsWith("encryptioninfo")) {
                        throw WorkbookReadException("Excel ফাইলটি পাসওয়ার্ড সুরক্ষিত (encrypted) — ডিক্রিপ্ট ছাড়া পড়া যাবে না।")
                    }
                    if (name.endsWith("sharedstrings.xml") ||
                        name.endsWith("styles.xml") ||
                        name.endsWith("workbook.xml") ||
                        name.endsWith("workbook.xml.rels") ||
                        (name.contains("worksheets/") && name.endsWith(".xml"))
                    ) {
                        entries[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: WorkbookReadException) {
            throw e
        } catch (e: Exception) {
            throw WorkbookReadException("Excel (.xlsx) ফাইলটি খোলা যায়নি: ${e.message ?: "অবৈধ ফাইল ফরম্যাট"}", e)
        }

        if (zipEntryCount == 0 || entries.isEmpty()) {
            throw WorkbookReadException("Excel ফাইলটি খালি বা বৈধ .xlsx (OOXML) ফরম্যাটে নেই।")
        }

        val sharedStrings = entries.entries.find { it.key.endsWith("sharedstrings.xml") }?.let {
            parseSharedStrings(ByteArrayInputStream(it.value))
        } ?: emptyList()

        val styles = entries.entries.find { it.key.endsWith("styles.xml") }?.let {
            parseStyles(ByteArrayInputStream(it.value))
        } ?: WorkbookStyles(emptyList(), emptyMap())

        val sheetMetaList = parseWorkbookSheetNames(entries)
        val result = mutableListOf<ParsedSheetData>()
        var hiddenCount = 0

        if (sheetMetaList.isNotEmpty()) {
            for (sheetInfo in sheetMetaList) {
                if (sheetInfo.isHidden) {
                    hiddenCount++
                    continue
                }
                val resolvedBytes = findSheetEntryBytes(entries, sheetInfo.filePath)
                    ?: throw WorkbookReadException("শিট '${sheetInfo.name}'-এর XML ডেটা (${sheetInfo.filePath}) খুঁজে পাওয়া যায়নি।")

                val rows = parseSheetXml(ByteArrayInputStream(resolvedBytes), sharedStrings, styles)
                if (rows.isNotEmpty()) {
                    result.add(ParsedSheetData(sheetInfo.name, rows))
                }
            }
            if (result.isEmpty() && hiddenCount == sheetMetaList.size) {
                throw WorkbookReadException("ফাইলের সবগুলো শিট লুকানো (hidden) অবস্থায় আছে; কোনো দৃশ্যমান শিট নেই।")
            }
        } else {
            val sheetEntries = entries.entries
                .filter { it.key.contains("worksheets/sheet") && it.key.endsWith(".xml") }
                .sortedBy { it.key }
            for (e in sheetEntries) {
                val rows = parseSheetXml(ByteArrayInputStream(e.value), sharedStrings, styles)
                if (rows.isNotEmpty()) {
                    val defaultName = e.key.substringAfterLast("/").removeSuffix(".xml")
                        .replaceFirstChar { it.uppercase() }
                    result.add(ParsedSheetData(defaultName, rows))
                }
            }
        }

        if (result.isEmpty()) {
            throw WorkbookReadException("Excel শিটে কোনো পাঠযোগ্য ডেটা পাওয়া যায়নি।")
        }

        return result
    }

    private fun findSheetEntryBytes(entries: Map<String, ByteArray>, rawTarget: String): ByteArray? {
        var clean = rawTarget.replace('\\', '/').lowercase().trimStart('/')
        while (clean.startsWith("../")) {
            clean = clean.removePrefix("../")
        }
        val candidates = listOf(
            clean,
            if (clean.startsWith("xl/")) clean else "xl/$clean"
        )
        for (c in candidates) {
            entries[c]?.let { return it }
        }
        val fileName = clean.substringAfterLast('/')
        return entries.entries.find {
            it.key.endsWith("/$clean") || it.key.endsWith("/$fileName")
        }?.value
    }

    private fun parseWorkbookSheetNames(entries: Map<String, ByteArray>): List<SheetMeta> {
        val wbBytes = entries.entries.find { it.key.endsWith("workbook.xml") }?.value ?: return emptyList()
        val relsBytes = entries.entries.find { it.key.endsWith("workbook.xml.rels") }?.value

        val rIdToTarget = mutableMapOf<String, String>()
        if (relsBytes != null) {
            try {
                newSaxParser().parse(ByteArrayInputStream(relsBytes), object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                        if (localTagName(qName).equals("relationship", ignoreCase = true)) {
                            val id = attributes.getValue("Id")
                            val target = attributes.getValue("Target")
                            if (id != null && target != null) {
                                rIdToTarget[id] = target
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                throw WorkbookReadException("workbook.xml.rels পড়তে ব্যর্থ হয়েছে: ${e.message}", e)
            }
        }

        val result = mutableListOf<SheetMeta>()
        try {
            var sheetIndex = 1
            newSaxParser().parse(ByteArrayInputStream(wbBytes), object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    if (localTagName(qName).equals("sheet", ignoreCase = true)) {
                        val name = attributes.getValue("name") ?: "Sheet$sheetIndex"
                        val state = attributes.getValue("state")?.trim()?.lowercase() ?: "visible"
                        val isHidden = state == "hidden" || state == "veryhidden"
                        val rId = attributes.getValue("r:id")
                            ?: findAttrEndingWith(attributes, "id")
                            ?: "rId$sheetIndex"
                        val target = rIdToTarget[rId] ?: "worksheets/sheet$sheetIndex.xml"
                        result.add(SheetMeta(name = name, filePath = target, isHidden = isHidden))
                        sheetIndex++
                    }
                }
            })
        } catch (e: Exception) {
            throw WorkbookReadException("workbook.xml পড়তে ব্যর্থ হয়েছে: ${e.message}", e)
        }
        return result
    }

    private fun findAttrEndingWith(attributes: Attributes, suffix: String): String? {
        for (idx in 0 until attributes.length) {
            val attrName = attributes.getQName(idx).ifBlank { attributes.getLocalName(idx) }
            if (attrName.equals(suffix, ignoreCase = true) || attrName.endsWith(":$suffix", ignoreCase = true)) {
                return attributes.getValue(idx)
            }
        }
        return null
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val strings = mutableListOf<String>()
        var inSi = false
        var inT = false
        var rPhDepth = 0
        val currentSi = StringBuilder()

        newSaxParser().parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                val tag = localTagName(qName)
                when {
                    tag.equals("si", ignoreCase = true) -> {
                        inSi = true
                        rPhDepth = 0
                        currentSi.clear()
                    }
                    inSi && (tag.equals("rPh", ignoreCase = true) || tag.equals("phoneticPr", ignoreCase = true)) -> {
                        rPhDepth++
                    }
                    inSi && rPhDepth == 0 && tag.equals("t", ignoreCase = true) -> {
                        inT = true
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && rPhDepth == 0) {
                    currentSi.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val tag = localTagName(qName)
                when {
                    tag.equals("t", ignoreCase = true) -> inT = false
                    inSi && (tag.equals("rPh", ignoreCase = true) || tag.equals("phoneticPr", ignoreCase = true)) -> {
                        if (rPhDepth > 0) rPhDepth--
                    }
                    tag.equals("si", ignoreCase = true) -> {
                        inSi = false
                        strings.add(ImportNormalization.cleanInvisibleChars(currentSi.toString()))
                    }
                }
            }
        })
        return strings
    }

    private fun parseStyles(input: InputStream): WorkbookStyles {
        val customFormats = mutableMapOf<Int, String>()
        val xfList = mutableListOf<Int>()
        try {
            var inCellXfs = false
            newSaxParser().parse(input, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    val tag = localTagName(qName)
                    when {
                        tag.equals("numFmt", ignoreCase = true) -> {
                            val id = attributes.getValue("numFmtId")?.toIntOrNull()
                            val code = attributes.getValue("formatCode")
                            if (id != null && code != null) {
                                customFormats[id] = code
                            }
                        }
                        tag.equals("cellXfs", ignoreCase = true) -> inCellXfs = true
                        inCellXfs && tag.equals("xf", ignoreCase = true) -> {
                            val numFmtId = attributes.getValue("numFmtId")?.toIntOrNull() ?: 0
                            xfList.add(numFmtId)
                        }
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    if (localTagName(qName).equals("cellXfs", ignoreCase = true)) {
                        inCellXfs = false
                    }
                }
            })
        } catch (_: Exception) {
            // Non-fatal: fall back to general formatting if styles.xml is malformed
        }
        return WorkbookStyles(xfList, customFormats)
    }

    private fun classifyNumFmt(numFmtId: Int, customCode: String?): NumFmtKind {
        if (!customCode.isNullOrBlank()) {
            return classifyFormatCode(customCode)
        }
        return when (numFmtId) {
            14, 15, 16, 17, 27, 28, 29, 30, 31, 36, 50, 51, 52, 53, 54, 55, 56, 57, 58 -> NumFmtKind.DATE_ONLY
            18, 19, 20, 21, 32, 33, 34, 35, 45, 46, 47 -> NumFmtKind.TIME_ONLY
            22 -> NumFmtKind.DATE_TIME
            else -> NumFmtKind.GENERAL
        }
    }

    private fun classifyFormatCode(rawCode: String): NumFmtKind {
        var cleaned = rawCode.replace(Regex("\"[^\"]*\""), "")
        cleaned = cleaned.replace(Regex("\\\\."), "")
        cleaned = cleaned.replace(Regex("""\[(?!(?:h|m|s|hh|mm|ss)\])[^\]]*\]""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.trim().lowercase()
        if (cleaned.isEmpty() || cleaned == "general" || cleaned == "@") return NumFmtKind.GENERAL

        val firstSection = cleaned.substringBefore(';')
        val hasDateLetters = Regex("""y+|d+""").containsMatchIn(firstSection) ||
            Regex("""(?:^|[^a-z])m{1,5}(?:[/\-.,\s]|$)""").containsMatchIn(firstSection)
        val hasTimeLetters = Regex("""\[?[hs]+\]?|am/pm|a/p""").containsMatchIn(firstSection)

        return when {
            hasDateLetters && hasTimeLetters -> NumFmtKind.DATE_TIME
            hasDateLetters -> NumFmtKind.DATE_ONLY
            hasTimeLetters -> NumFmtKind.TIME_ONLY
            else -> NumFmtKind.GENERAL
        }
    }

    private fun parseSheetXml(
        input: InputStream,
        sharedStrings: List<String>,
        styles: WorkbookStyles
    ): List<TabularRow> {
        val grid = sortedMapOf<Int, MutableMap<Int, String>>()
        val mergeRanges = mutableListOf<MergeRange>()

        var currentRowIdx0 = -1
        var nextSequentialRow0 = 0
        var currentCol0 = 0
        var cellType: String? = null
        var cellStyleIdx: Int? = null
        var inCell = false
        var inV = false
        var inIs = false
        var inIsT = false
        var isRPhDepth = 0
        val vContent = StringBuilder()
        val isContent = StringBuilder()

        newSaxParser().parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                val tag = localTagName(qName)
                when {
                    tag.equals("row", ignoreCase = true) -> {
                        val rAttr = attributes.getValue("r")?.toIntOrNull()
                        currentRowIdx0 = if (rAttr != null && rAttr >= 1) rAttr - 1 else nextSequentialRow0
                        nextSequentialRow0 = currentRowIdx0 + 1
                        currentCol0 = 0
                    }
                    tag.equals("c", ignoreCase = true) -> {
                        inCell = true
                        cellType = attributes.getValue("t")
                        cellStyleIdx = attributes.getValue("s")?.toIntOrNull()
                        val ref = attributes.getValue("r")
                        if (!ref.isNullOrBlank()) {
                            val (refRow0, refCol0) = parseCellRef(ref)
                            if (refRow0 >= 0) currentRowIdx0 = refRow0
                            currentCol0 = refCol0
                        }
                        vContent.clear()
                        isContent.clear()
                        inV = false
                        inIs = false
                        inIsT = false
                        isRPhDepth = 0
                    }
                    inCell && tag.equals("v", ignoreCase = true) -> {
                        inV = true
                        vContent.clear()
                    }
                    inCell && tag.equals("is", ignoreCase = true) -> {
                        inIs = true
                        isRPhDepth = 0
                        isContent.clear()
                    }
                    inIs && (tag.equals("rPh", ignoreCase = true) || tag.equals("phoneticPr", ignoreCase = true)) -> {
                        isRPhDepth++
                    }
                    inIs && isRPhDepth == 0 && tag.equals("t", ignoreCase = true) -> {
                        inIsT = true
                    }
                    inCell && !inIs && tag.equals("t", ignoreCase = true) -> {
                        inIsT = true
                    }
                    tag.equals("mergeCell", ignoreCase = true) -> {
                        val ref = attributes.getValue("ref")
                        if (!ref.isNullOrBlank()) {
                            parseMergeRange(ref)?.let { mergeRanges.add(it) }
                        }
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV) {
                    vContent.append(ch, start, length)
                } else if (inIsT && isRPhDepth == 0) {
                    isContent.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val tag = localTagName(qName)
                when {
                    tag.equals("v", ignoreCase = true) -> inV = false
                    tag.equals("t", ignoreCase = true) -> inIsT = false
                    inIs && (tag.equals("rPh", ignoreCase = true) || tag.equals("phoneticPr", ignoreCase = true)) -> {
                        if (isRPhDepth > 0) isRPhDepth--
                    }
                    tag.equals("is", ignoreCase = true) -> inIs = false
                    tag.equals("c", ignoreCase = true) -> {
                        val finalVal = resolveCellValue(
                            cellType = cellType,
                            styleIdx = cellStyleIdx,
                            rawV = vContent.toString(),
                            rawIs = isContent.toString(),
                            sharedStrings = sharedStrings,
                            styles = styles
                        )
                        if (finalVal.isNotEmpty() && currentRowIdx0 >= 0) {
                            val rowMap = grid.getOrPut(currentRowIdx0) { mutableMapOf() }
                            rowMap[currentCol0] = finalVal
                        }
                        inCell = false
                        currentCol0++
                    }
                }
            }
        })

        // Propagate merged cell top-left values into empty cells of the merged range
        for (range in mergeRanges) {
            val topLeftValue = grid[range.startRow0]?.get(range.startCol0)
            if (!topLeftValue.isNullOrBlank()) {
                for (r in range.startRow0..range.endRow0) {
                    val rowMap = grid.getOrPut(r) { mutableMapOf() }
                    for (c in range.startCol0..range.endCol0) {
                        val existing = rowMap[c]
                        if (existing.isNullOrBlank()) {
                            rowMap[c] = topLeftValue
                        }
                    }
                }
            }
        }

        val rows = mutableListOf<TabularRow>()
        for ((rowIdx0, colMap) in grid) {
            val maxCol = colMap.keys.maxOrNull() ?: continue
            val rowCells = (0..maxCol).map { c -> colMap[c]?.trim() ?: "" }
            if (rowCells.any { it.isNotBlank() }) {
                rows.add(TabularRow(oneBasedRowNumber = rowIdx0 + 1, cells = rowCells))
            }
        }
        return rows
    }

    private fun resolveCellValue(
        cellType: String?,
        styleIdx: Int?,
        rawV: String,
        rawIs: String,
        sharedStrings: List<String>,
        styles: WorkbookStyles
    ): String {
        return when (cellType) {
            "s" -> {
                val idx = rawV.trim().toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx].trim() else rawV.trim()
            }
            "inlineStr" -> {
                ImportNormalization.cleanInvisibleChars(rawIs).trim()
            }
            "str", "e" -> {
                ImportNormalization.cleanInvisibleChars(rawV).trim()
            }
            "b" -> {
                when (rawV.trim()) {
                    "1", "true", "TRUE" -> "TRUE"
                    else -> "FALSE"
                }
            }
            else -> {
                val trimmedV = rawV.trim()
                if (trimmedV.isEmpty()) {
                    val fallbackIs = ImportNormalization.cleanInvisibleChars(rawIs).trim()
                    return fallbackIs
                }
                val fmtKind = styles.classifyStyle(styleIdx)
                if (fmtKind != NumFmtKind.GENERAL) {
                    val serial = trimmedV.toDoubleOrNull()
                    if (serial != null && serial >= 0.0 && serial < 2958466.0) {
                        return formatExcelSerialDate(serial, fmtKind)
                    }
                }
                normalizeNumericString(trimmedV)
            }
        }
    }

    private fun formatExcelSerialDate(serial: Double, kind: NumFmtKind): String {
        var wholeDays = floor(serial).toInt()
        val frac = serial - wholeDays
        var totalSeconds = (frac * 86400.0).roundToInt()
        if (totalSeconds >= 86400) {
            wholeDays += 1
            totalSeconds -= 86400
        }

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val timePart = if (totalSeconds > 0 || kind == NumFmtKind.TIME_ONLY) {
            if (seconds != 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            else String.format(Locale.US, "%02d:%02d", hours, minutes)
        } else {
            ""
        }

        if (wholeDays == 0 || kind == NumFmtKind.TIME_ONLY) {
            return if (timePart.isNotEmpty()) timePart else "00:00"
        }

        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.clear()
        if (wholeDays <= 59) {
            cal.set(1899, Calendar.DECEMBER, 31, 0, 0, 0)
        } else {
            // Adjust for Excel's intentional 1900 leap year bug (serial 60 = 1900-02-29)
            cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        }
        cal.add(Calendar.DAY_OF_YEAR, wholeDays)

        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val datePart = df.format(cal.time)
        return when {
            kind == NumFmtKind.DATE_ONLY && totalSeconds == 0 -> datePart
            timePart.isNotEmpty() -> "$datePart $timePart"
            else -> datePart
        }
    }

    internal fun normalizeNumericString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val bd = BigDecimal(trimmed).stripTrailingZeros()
            if (bd.scale() <= 0) {
                bd.toBigInteger().toString()
            } else {
                bd.toPlainString()
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        var col = 0
        var row = 0
        for (ch in ref.uppercase()) {
            when (ch) {
                in 'A'..'Z' -> col = col * 26 + (ch - 'A' + 1)
                in '0'..'9' -> row = row * 10 + (ch - '0')
            }
        }
        val col0 = if (col > 0) col - 1 else 0
        val row0 = if (row > 0) row - 1 else -1
        return row0 to col0
    }

    private fun parseMergeRange(ref: String): MergeRange? {
        val parts = ref.split(':')
        if (parts.size != 2) return null
        val (r1, c1) = parseCellRef(parts[0].trim())
        val (r2, c2) = parseCellRef(parts[1].trim())
        if (r1 < 0 || r2 < 0) return null
        return MergeRange(
            startRow0 = minOf(r1, r2),
            endRow0 = maxOf(r1, r2),
            startCol0 = minOf(c1, c2),
            endCol0 = maxOf(c1, c2)
        )
    }

    private fun localTagName(name: String): String =
        if (name.contains(':')) name.substringAfter(':') else name
}
