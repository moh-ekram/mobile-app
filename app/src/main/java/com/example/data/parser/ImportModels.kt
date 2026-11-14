package com.example.data.parser

import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import java.math.BigDecimal

/**
 * Custom exception carrying the real underlying cause when reading CSV/Excel files fails.
 */
class WorkbookReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TabularRow(
    val oneBasedRowNumber: Int,
    val cells: List<String>
)

data class ParsedSheetData(
    val sheetName: String,
    val rows: List<TabularRow>
) {
    fun toPlainRows(): List<List<String>> = rows.map { it.cells }
}

enum class ImportTargetType {
    COURSE,
    QUESTION_BANK
}

enum class ImportIssueType {
    SKIPPED_BLANK_ID,
    SKIPPED_DUPLICATE_ID,
    SKIPPED_EMPTY_CONTENT,
    NEEDS_REVIEW_INVALID_ANSWER,
    NEEDS_REVIEW_EMPTY_OPTION,
    NEEDS_REVIEW_MISSING_REQUIRED
}

data class ImportRowIssue(
    val sheetName: String,
    val rowNumber: Int,
    val rawId: String,
    val type: ImportIssueType,
    val reason: String,
    val previewText: String = ""
)

data class MappedColumnInfo(
    val colIndex: Int,
    val rawHeader: String,
    val assignedRole: String
)

data class ParsedCourseEntry(
    val word: VocabularyWordEntity,
    val rowNumber: Int,
    val sheetName: String,
    val placeValues: Map<Int, String>,
    val placeLabels: Map<Int, String>,
    val groupValues: Map<Int, String>,
    val groupLabels: Map<Int, String>
)

data class ParsedCourseSheetResult(
    val sheetName: String,
    val headerRowNumber: Int,
    val columnMappings: List<MappedColumnInfo>,
    val placeLabels: Map<Int, String>,
    val groupLabels: Map<Int, String>,
    val validEntries: List<ParsedCourseEntry>,
    val skippedIssues: List<ImportRowIssue>,
    val previewRows: List<Map<String, String>>
)

data class ParsedQuestionBankSheetResult(
    val sheetName: String,
    val headerRowNumber: Int,
    val columnMappings: List<MappedColumnInfo>,
    val filter1Label: String,
    val filter2Label: String,
    val filter3Label: String,
    val validItems: List<QuestionBankEntity>,
    val skippedIssues: List<ImportRowIssue>,
    val needsReviewIssues: List<ImportRowIssue>,
    val previewRows: List<Map<String, String>>
)

object ImportNormalization {
    private val wholeDecimalRegex = Regex("""^([+-]?\d+)\.0+$""")
    private val multiWhitespaceRegex = Regex("""\s+""")

    fun cleanInvisibleChars(raw: String): String =
        raw.replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u00A0", " ")

    /**
     * Normalizes cell Id strictly per rule:
     * - Trims leading/trailing whitespace and invisible characters
     * - Converts Excel whole-number float representations like "1.0" or "42.00" to "1" / "42"
     */
    fun normalizeId(raw: String): String {
        val cleaned = cleanInvisibleChars(raw).trim()
        if (cleaned.isEmpty()) return ""
        val wholeMatch = wholeDecimalRegex.matchEntire(cleaned)
        if (wholeMatch != null) {
            return wholeMatch.groupValues[1]
        }
        // Handle scientific notation only if it explicitly looks like an Excel integer float E.g. 1.0E2
        if (cleaned.contains('.') && (cleaned.contains('E', ignoreCase = true))) {
            try {
                val bd = BigDecimal(cleaned).stripTrailingZeros()
                if (bd.scale() <= 0) {
                    return bd.toBigInteger().toString()
                }
            } catch (_: Exception) {}
        }
        return cleaned
    }

    /**
     * Normalizes group text for comparison ("Unit 1" == "unit 1 ").
     * Blank value normalizes to empty string "" so UI can label it "(গ্রুপ নেই)".
     */
    fun normalizeGroupKey(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return cleanInvisibleChars(raw).trim().replace(multiWhitespaceRegex, " ").lowercase()
    }

    fun cleanGroupDisplayValue(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return cleanInvisibleChars(raw).trim().replace(multiWhitespaceRegex, " ")
    }

    const val BLANK_GROUP_LABEL = "(গ্রুপ নেই)"

    /**
     * Natural numeric comparator so "Group 1", "Group 2", "Group 10" (and "1", "2", "10") sort naturally.
     * Blank group "(গ্রুপ নেই)" sorts last.
     */
    val naturalGroupComparator: Comparator<String> = Comparator { a, b ->
        val aBlank = a.isBlank() || a == BLANK_GROUP_LABEL
        val bBlank = b.isBlank() || b == BLANK_GROUP_LABEL
        if (aBlank && bBlank) return@Comparator 0
        if (aBlank) return@Comparator 1
        if (bBlank) return@Comparator -1
        compareNaturalStrings(a, b)
    }

    fun compareNaturalStrings(a: String, b: String): Int {
        val tokensA = tokenizeNatural(a.trim())
        val tokensB = tokenizeNatural(b.trim())
        val minSize = minOf(tokensA.size, tokensB.size)
        for (i in 0 until minSize) {
            val ta = tokensA[i]
            val tb = tokensB[i]
            val numA = ta.toBigIntegerOrNull()
            val numB = tb.toBigIntegerOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                ta.compareTo(tb, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return tokensA.size.compareTo(tokensB.size)
    }

    private val naturalTokenRegex = Regex("""\d+|\D+""")
    private fun tokenizeNatural(s: String): List<String> =
        naturalTokenRegex.findAll(s).map { it.value }.toList()

    /**
     * Finds the 0-based index of the header row in [rows]:
     * the first row containing an exact Id column ("id" or "id*", ignoring whitespace/BOM/case).
     * Returns -1 if no row contains an Id column.
     */
    fun findIdHeaderRowIndex(rows: List<TabularRow>): Int {
        for (i in rows.indices) {
            val cells = rows[i].cells
            val hasIdCol = cells.any { cell -> isIdHeader(cell) }
            if (hasIdCol) return i
        }
        return -1
    }

    fun isIdHeader(rawHeader: String): Boolean {
        val cleaned = cleanInvisibleChars(rawHeader).trim().lowercase()
        return cleaned == "id" || cleaned == "id*"
    }
}
