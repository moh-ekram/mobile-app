package com.example.data.parser

import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

data class ParsedSheet(
    val sheetName: String,
    val rows: List<List<String>>
)

object FileParsers {

    private val coursePlaceHeaderRegex = Regex("""^(?i)place[\s_-]*([1-6])(?:\s*[:：]\s*(.*))?$""")
    private val courseGroupHeaderRegex = Regex("""^(?i)group[\s_-]*(\d+)(?:\s*[:：]\s*(.*))?$""")
    private val qbFilterHeaderRegex = Regex("""^(?i)filter[\s_-]*([1-3])(?:\s*[:：]\s*(.*))?$""")

    /**
     * Extracts column headers from CSV content.
     */
    fun extractHeaders(content: String): List<String> {
        val rows = XlsxWorkbookReader.parseCsvTabularRows(content)
        val headerIdx = ImportNormalization.findIdHeaderRowIndex(rows)
        val targetRow = if (headerIdx >= 0) rows[headerIdx] else rows.firstOrNull()
        return targetRow?.cells?.map { it.trim() } ?: emptyList()
    }

    /**
     * Parses CSV course content strictly via parseCourseSheetStrict.
     */
    fun parseCourseCsv(content: String, courseId: String = "course_default"): List<VocabularyWordEntity> {
        val tabularRows = XlsxWorkbookReader.parseCsvTabularRows(content)
        if (tabularRows.isEmpty()) return emptyList()
        val sheet = ParsedSheetData(sheetName = "CSV", rows = tabularRows)
        if (ImportNormalization.findIdHeaderRowIndex(tabularRows) < 0) return emptyList()
        return parseCourseSheetStrict(sheet, courseId).validEntries.map { it.word }
    }

    /**
     * Legacy compatibility entry point delegating strictly to [parseCourseSheetStrict].
     */
    fun parseCourseRows(
        rows: List<List<String>>,
        courseId: String = "course_default",
        defaultGroupName: String = "1"
    ): List<VocabularyWordEntity> {
        if (rows.isEmpty()) return emptyList()
        val tabularRows = rows.mapIndexed { idx, cells -> TabularRow(idx + 1, cells) }
        if (ImportNormalization.findIdHeaderRowIndex(tabularRows) < 0) return emptyList()
        val sheet = ParsedSheetData(sheetName = defaultGroupName.ifBlank { "Sheet1" }, rows = tabularRows)
        return parseCourseSheetStrict(sheet, courseId).validEntries.map { it.word }
    }

    /**
     * Strictly parses a single Course sheet according to import rules:
     * - Header row is the first row containing an exact 'Id' column (skips title/banner rows above it).
     * - Mandatory 'Id' column: trims whitespace, normalizes "1.0" -> "1", NEVER auto-generates IDs.
     * - Blank Id rows and duplicate Id rows within the sheet are skipped & recorded with exact row numbers.
     * - Supports place1..place6[: label] strictly keyed by place number (no hardcoded word/meaning/mnemonic assumptions,
     *   no missing-column substitution).
     * - Supports group1..groupN[: name] (plus legacy 'group' -> group1). Blank group stays empty for "(গ্রুপ নেই)".
     */
    fun parseCourseSheetStrict(
        sheet: ParsedSheetData,
        defaultCourseId: String = "course_default"
    ): ParsedCourseSheetResult {
        val headerIdx = ImportNormalization.findIdHeaderRowIndex(sheet.rows)
        if (headerIdx < 0) {
            throw WorkbookReadException("কোর্স শিট '${sheet.sheetName}'-এ বাধ্যতামূলক 'Id' কলাম পাওয়া যায়নি।")
        }

        val headerRow = sheet.rows[headerIdx]
        val rawHeaders = headerRow.cells

        var idColIdx = -1
        var courseIdColIdx = -1
        var statusColIdx = -1
        var isReportedColIdx = -1
        var reportReasonColIdx = -1

        val placeCols = linkedMapOf<Int, Int>() // placeNumber (1..6) -> colIndex
        val placeLabels = linkedMapOf<Int, String>()
        val groupCols = linkedMapOf<Int, Int>() // groupAxis (1..N) -> colIndex
        val groupLabels = linkedMapOf<Int, String>()
        val columnMappings = mutableListOf<MappedColumnInfo>()

        rawHeaders.forEachIndexed { colIdx, rawH ->
            val cleaned = ImportNormalization.cleanInvisibleChars(rawH).trim()
            val lower = cleaned.lowercase()

            when {
                idColIdx == -1 && ImportNormalization.isIdHeader(cleaned) -> {
                    idColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Id"))
                }
                lower == "courseid" || lower == "course_id" -> {
                    courseIdColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "CourseId"))
                }
                lower == "status" -> {
                    statusColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Status"))
                }
                lower == "isreported" || lower == "is_reported" -> {
                    isReportedColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "IsReported"))
                }
                lower == "reportreason" || lower == "report_reason" -> {
                    reportReasonColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "ReportReason"))
                }
                coursePlaceHeaderRegex.matches(cleaned) -> {
                    val m = coursePlaceHeaderRegex.matchEntire(cleaned)!!
                    val placeNum = m.groupValues[1].toInt()
                    val labelAfterColon = m.groupValues[2].trim()
                    val label = labelAfterColon.ifBlank { "Place $placeNum" }
                    if (!placeCols.containsKey(placeNum)) {
                        placeCols[placeNum] = colIdx
                        placeLabels[placeNum] = label
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "Place $placeNum: $label"))
                    } else {
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "উপেক্ষিত (ডুপ্লিকেট Place $placeNum)"))
                    }
                }
                lower == "group" -> {
                    if (!groupCols.containsKey(1)) {
                        groupCols[1] = colIdx
                        groupLabels[1] = "Group 1"
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "Group 1"))
                    } else {
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "উপেক্ষিত (ডুপ্লিকেট Group 1)"))
                    }
                }
                courseGroupHeaderRegex.matches(cleaned) -> {
                    val m = courseGroupHeaderRegex.matchEntire(cleaned)!!
                    val axisNum = m.groupValues[1].toIntOrNull()
                    val labelAfterColon = m.groupValues[2].trim()
                    if (axisNum != null && axisNum >= 1 && !groupCols.containsKey(axisNum)) {
                        val label = labelAfterColon.ifBlank { "Group $axisNum" }
                        groupCols[axisNum] = colIdx
                        groupLabels[axisNum] = label
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "Group $axisNum: $label"))
                    } else {
                        columnMappings.add(MappedColumnInfo(colIdx, rawH, "উপেক্ষিত"))
                    }
                }
                else -> {
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "উপেক্ষিত (অজানা কলাম)"))
                }
            }
        }

        val sortedPlaceNums = placeCols.keys.sorted()
        val sortedGroupNums = groupCols.keys.sorted()

        val validEntries = mutableListOf<ParsedCourseEntry>()
        val skippedIssues = mutableListOf<ImportRowIssue>()
        val previewRows = mutableListOf<Map<String, String>>()
        val seenIds = HashSet<String>()

        for (i in (headerIdx + 1) until sheet.rows.size) {
            val row = sheet.rows[i]
            val values = row.cells
            if (values.all { it.isBlank() }) continue

            val rawId = if (idColIdx in values.indices) values[idColIdx] else ""
            val normId = ImportNormalization.normalizeId(rawId)

            val firstNonBlankPreview = values.firstOrNull { it.isNotBlank() && it != rawId }?.take(60) ?: ""

            if (normId.isEmpty()) {
                skippedIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = rawId,
                        type = ImportIssueType.SKIPPED_BLANK_ID,
                        reason = "Id কলাম ফাঁকা থাকায় সারিটি বাদ দেওয়া হয়েছে।",
                        previewText = firstNonBlankPreview
                    )
                )
                continue
            }

            if (!seenIds.add(normId)) {
                skippedIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = normId,
                        type = ImportIssueType.SKIPPED_DUPLICATE_ID,
                        reason = "শিট '${sheet.sheetName}'-এ Id '$normId' একাধিকবার থাকায় পরবর্তী সারিটি বাদ দেওয়া হয়েছে।",
                        previewText = firstNonBlankPreview
                    )
                )
                continue
            }

            val placeValues = linkedMapOf<Int, String>()
            for (pNum in sortedPlaceNums) {
                val colIdx = placeCols[pNum] ?: continue
                val cellVal = if (colIdx in values.indices) {
                    ImportNormalization.cleanInvisibleChars(values[colIdx]).trim()
                } else ""
                if (cellVal.isNotEmpty()) {
                    placeValues[pNum] = cellVal
                }
            }

            val groupValues = linkedMapOf<Int, String>()
            for (gNum in sortedGroupNums) {
                val colIdx = groupCols[gNum] ?: continue
                val cellVal = if (colIdx in values.indices) {
                    ImportNormalization.cleanGroupDisplayValue(values[colIdx])
                } else ""
                groupValues[gNum] = cellVal
            }

            if (placeValues.isEmpty()) {
                skippedIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = normId,
                        type = ImportIssueType.SKIPPED_EMPTY_CONTENT,
                        reason = "কোনো place কলামেই ডেটা না থাকায় সারিটি বাদ দেওয়া হয়েছে।",
                        previewText = ""
                    )
                )
                continue
            }

            val rowCourseId = if (courseIdColIdx in values.indices && values[courseIdColIdx].isNotBlank()) {
                values[courseIdColIdx].trim()
            } else {
                defaultCourseId
            }

            val status = if (statusColIdx in values.indices && values[statusColIdx].isNotBlank()) {
                values[statusColIdx].trim().lowercase()
            } else {
                "unrated"
            }

            val isReported = if (isReportedColIdx in values.indices) {
                val rv = values[isReportedColIdx].trim()
                rv.equals("true", ignoreCase = true) || rv == "1"
            } else false

            val reportReason = if (reportReasonColIdx in values.indices && values[reportReasonColIdx].isNotBlank()) {
                values[reportReasonColIdx].trim()
            } else null

            val placeMap = linkedMapOf<String, String>()
            for (pNum in sortedPlaceNums) {
                val pVal = placeValues[pNum]
                if (!pVal.isNullOrEmpty()) {
                    val lbl = placeLabels[pNum] ?: "Place $pNum"
                    placeMap["Place $pNum: $lbl"] = pVal
                }
            }

            val wordEntity = VocabularyWordEntity(
                id = normId,
                word = placeValues[1] ?: "",
                meaning = placeValues[2] ?: "",
                group = groupValues[1] ?: "",
                synonyms = null,
                extraWord = null,
                example = null,
                mnemonic = null,
                status = status,
                customPlacesJson = encodeJsonStringMap(placeMap),
                courseId = rowCourseId,
                isReported = isReported,
                reportReason = reportReason
            )

            validEntries.add(
                ParsedCourseEntry(
                    word = wordEntity,
                    rowNumber = row.oneBasedRowNumber,
                    sheetName = sheet.sheetName,
                    placeValues = placeValues,
                    placeLabels = placeLabels.toMap(),
                    groupValues = groupValues,
                    groupLabels = groupLabels.toMap()
                )
            )

            if (previewRows.size < 5) {
                val rowPreview = linkedMapOf<String, String>()
                rowPreview["Id"] = normId
                for (pNum in sortedPlaceNums) {
                    val lbl = placeLabels[pNum] ?: "Place $pNum"
                    rowPreview["Place $pNum ($lbl)"] = placeValues[pNum] ?: ""
                }
                for (gNum in sortedGroupNums) {
                    val lbl = groupLabels[gNum] ?: "Group $gNum"
                    rowPreview["Group $gNum ($lbl)"] = groupValues[gNum].orEmpty().ifBlank { ImportNormalization.BLANK_GROUP_LABEL }
                }
                previewRows.add(rowPreview)
            }
        }

        return ParsedCourseSheetResult(
            sheetName = sheet.sheetName,
            headerRowNumber = headerRow.oneBasedRowNumber,
            columnMappings = columnMappings,
            placeLabels = placeLabels.toMap(),
            groupLabels = groupLabels.toMap(),
            validEntries = validEntries,
            skippedIssues = skippedIssues,
            previewRows = previewRows
        )
    }

    private fun matchOptionSlot(rawHeader: String): Int? {
        val cleaned = ImportNormalization.cleanInvisibleChars(rawHeader).trim().lowercase()
        val compact = cleaned.replace(Regex("""[\s_\-*]+"""), "")
        return when (compact) {
            "opt1", "option1", "optiona", "opta", "choice1", "choicea", "a)", "(a)", "a", "a." -> 1
            "opt2", "option2", "optionb", "optb", "choice2", "choiceb", "b)", "(b)", "b", "b." -> 2
            "opt3", "option3", "optionc", "optc", "choice3", "choicec", "c)", "(c)", "c", "c." -> 3
            "opt4", "option4", "optiond", "optd", "choice4", "choiced", "d)", "(d)", "d", "d." -> 4
            "opt5", "option5", "optione", "opte", "choice5", "choicee", "e)", "(e)", "e", "e." -> 5
            else -> null
        }
    }

    private fun normalizeStemKey(rawKey: String): String =
        ImportNormalization.cleanInvisibleChars(rawKey)
            .trim()
            .lowercase()
            .replace(Regex("""[\s_\-]+"""), "")

    /**
     * Recognizes a stem definition like "stem1: Full passage text..."
     * Returns Pair(canonicalId, fullText), or null if the cell is just prose or a bare ID reference.
     */
    private fun extractStemDefinition(rawCell: String): Pair<String, String>? {
        val colonIdx = rawCell.indexOfAny(charArrayOf(':', '：'))
        if (colonIdx <= 0) return null
        val prefix = rawCell.substring(0, colonIdx).trim()
        val after = rawCell.substring(colonIdx + 1).trim()
        if (prefix.isEmpty() || after.isEmpty()) return null
        if (prefix.length > 25 || prefix.contains('\n') || prefix.contains('\r')) return null
        if (prefix.any { it == '.' || it == '?' || it == '!' }) return null
        if (prefix.split(Regex("""\s+""")).size > 2) return null
        return prefix to after
    }

    /**
     * Strictly parses a single Question Bank sheet according to import rules:
     * - Sheet name is used as bankName.
     * - Header row is the first row containing exact 'Id'.
     * - Exact role matching: Opt1..5 variants, exact Ans/Answer (never Translation), exact Stem (never system).
     * - Two-pass Stem resolution (works whether stem definition appears before or after referencing rows).
     * - Strictly requires A-E answer pointing to a non-blank option; invalid/empty answers are flagged
     *   in [needsReviewIssues] with sheet name, row number, and reason (never defaults to Opt1, no '#' magic).
     */
    fun parseQuestionBankSheetStrict(
        sheet: ParsedSheetData,
        bankName: String = sheet.sheetName.ifBlank { "General QB" }
    ): ParsedQuestionBankSheetResult {
        val headerIdx = ImportNormalization.findIdHeaderRowIndex(sheet.rows)
        if (headerIdx < 0) {
            throw WorkbookReadException("প্রশ্নব্যাংক শিট '${sheet.sheetName}'-এ বাধ্যতামূলক 'Id' কলাম পাওয়া যায়নি।")
        }

        val headerRow = sheet.rows[headerIdx]
        val rawHeaders = headerRow.cells

        var idColIdx = -1
        var questionColIdx = -1
        val optColMap = linkedMapOf<Int, Int>() // slot 1..5 -> colIdx
        var ansColIdx = -1
        var expColIdx = -1
        var stemColIdx = -1
        var statusColIdx = -1
        var f1ColIdx = -1
        var f2ColIdx = -1
        var f3ColIdx = -1
        var f1Label = "Filter 1"
        var f2Label = "Filter 2"
        var f3Label = "Filter 3"

        val columnMappings = mutableListOf<MappedColumnInfo>()

        rawHeaders.forEachIndexed { colIdx, rawH ->
            val cleaned = ImportNormalization.cleanInvisibleChars(rawH).trim()
            val lower = cleaned.lowercase()
            val optSlot = matchOptionSlot(cleaned)

            when {
                idColIdx == -1 && ImportNormalization.isIdHeader(cleaned) -> {
                    idColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Id"))
                }
                questionColIdx == -1 && lower in setOf("question", "question*", "ques", "prompt", "question text", "question_text") -> {
                    questionColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Question"))
                }
                optSlot != null && !optColMap.containsKey(optSlot) -> {
                    optColMap[optSlot] = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Opt$optSlot"))
                }
                ansColIdx == -1 && lower in setOf("ans", "ans*", "answer", "answer*") -> {
                    ansColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Ans"))
                }
                expColIdx == -1 && lower in setOf("explanation", "explanation*", "explain", "exp") -> {
                    expColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Explanation"))
                }
                stemColIdx == -1 && lower in setOf("stem", "stem*") -> {
                    stemColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Stem"))
                }
                lower == "status" -> {
                    statusColIdx = colIdx
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "Status"))
                }
                qbFilterHeaderRegex.matches(cleaned) -> {
                    val m = qbFilterHeaderRegex.matchEntire(cleaned)!!
                    val fNum = m.groupValues[1].toInt()
                    val labelAfterColon = m.groupValues[2].trim()
                    when (fNum) {
                        1 -> if (f1ColIdx == -1) {
                            f1ColIdx = colIdx
                            f1Label = labelAfterColon.ifBlank { "Filter 1" }
                            columnMappings.add(MappedColumnInfo(colIdx, rawH, "Filter 1: $f1Label"))
                        }
                        2 -> if (f2ColIdx == -1) {
                            f2ColIdx = colIdx
                            f2Label = labelAfterColon.ifBlank { "Filter 2" }
                            columnMappings.add(MappedColumnInfo(colIdx, rawH, "Filter 2: $f2Label"))
                        }
                        3 -> if (f3ColIdx == -1) {
                            f3ColIdx = colIdx
                            f3Label = labelAfterColon.ifBlank { "Filter 3" }
                            columnMappings.add(MappedColumnInfo(colIdx, rawH, "Filter 3: $f3Label"))
                        }
                    }
                }
                else -> {
                    columnMappings.add(MappedColumnInfo(colIdx, rawH, "উপেক্ষিত (অজানা কলাম)"))
                }
            }
        }

        // Pass 1: Collect all Stem definitions from the entire sheet so references work in any row order
        val stemRegistry = linkedMapOf<String, Pair<String, String>>() // normalizedKey -> (canonicalId, fullText)
        if (stemColIdx != -1) {
            for (i in (headerIdx + 1) until sheet.rows.size) {
                val values = sheet.rows[i].cells
                val rawStem = if (stemColIdx in values.indices) {
                    ImportNormalization.cleanInvisibleChars(values[stemColIdx]).trim()
                } else ""
                if (rawStem.isNotEmpty()) {
                    val def = extractStemDefinition(rawStem)
                    if (def != null) {
                        stemRegistry[normalizeStemKey(def.first)] = def
                    }
                }
            }
        }

        val validItems = mutableListOf<QuestionBankEntity>()
        val skippedIssues = mutableListOf<ImportRowIssue>()
        val needsReviewIssues = mutableListOf<ImportRowIssue>()
        val previewRows = mutableListOf<Map<String, String>>()
        val seenIds = HashSet<String>()

        // Pass 2: Validate rows and resolve Stems & Answers
        for (i in (headerIdx + 1) until sheet.rows.size) {
            val row = sheet.rows[i]
            val values = row.cells
            if (values.all { it.isBlank() }) continue

            val rawId = if (idColIdx in values.indices) values[idColIdx] else ""
            val normId = ImportNormalization.normalizeId(rawId)
            val question = if (questionColIdx != -1 && questionColIdx in values.indices) {
                ImportNormalization.cleanInvisibleChars(values[questionColIdx]).trim()
            } else ""
            val previewSnippet = question.ifBlank {
                values.firstOrNull { it.isNotBlank() && it != rawId } ?: ""
            }.take(80)

            if (normId.isEmpty()) {
                skippedIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = rawId,
                        type = ImportIssueType.SKIPPED_BLANK_ID,
                        reason = "Id কলাম ফাঁকা থাকায় সারিটি বাদ দেওয়া হয়েছে।",
                        previewText = previewSnippet
                    )
                )
                continue
            }

            if (!seenIds.add(normId)) {
                skippedIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = normId,
                        type = ImportIssueType.SKIPPED_DUPLICATE_ID,
                        reason = "শিট '${sheet.sheetName}'-এ Id '$normId' একাধিকবার থাকায় পরবর্তী সারিটি বাদ দেওয়া হয়েছে।",
                        previewText = previewSnippet
                    )
                )
                continue
            }

            if (question.isEmpty()) {
                needsReviewIssues.add(
                    ImportRowIssue(
                        sheetName = sheet.sheetName,
                        rowNumber = row.oneBasedRowNumber,
                        rawId = normId,
                        type = ImportIssueType.NEEDS_REVIEW_MISSING_REQUIRED,
                        reason = "Question কলাম ফাঁকা আছে।",
                        previewText = previewSnippet
                    )
                )
                continue
            }

            fun optAt(slot: Int): String {
                val cIdx = optColMap[slot] ?: return ""
                return if (cIdx in values.indices) ImportNormalization.cleanInvisibleChars(values[cIdx]).trim() else ""
            }

            val opt1 = optAt(1)
            val opt2 = optAt(2)
            val opt3 = optAt(3)
            val opt4 = optAt(4)
            val opt5 = optAt(5)
            val rawAns = if (ansColIdx != -1 && ansColIdx in values.indices) values[ansColIdx] else ""

            when (val ansResult = QuestionAnswerResolver.resolveStrictImport(opt1, opt2, opt3, opt4, opt5, rawAns)) {
                is StrictAnswerResult.NeedsReview -> {
                    needsReviewIssues.add(
                        ImportRowIssue(
                            sheetName = sheet.sheetName,
                            rowNumber = row.oneBasedRowNumber,
                            rawId = normId,
                            type = ansResult.issueType,
                            reason = ansResult.reason,
                            previewText = previewSnippet
                        )
                    )
                    continue
                }
                is StrictAnswerResult.Valid -> {
                    val explanation = if (expColIdx != -1 && expColIdx in values.indices) {
                        ImportNormalization.cleanInvisibleChars(values[expColIdx]).trim().ifEmpty { null }
                    } else null

                    var resolvedStem: String? = null
                    var resolvedStemId: String? = null
                    if (stemColIdx != -1 && stemColIdx in values.indices) {
                        val rawStem = ImportNormalization.cleanInvisibleChars(values[stemColIdx]).trim()
                        if (rawStem.isNotEmpty()) {
                            val def = extractStemDefinition(rawStem)
                            if (def != null) {
                                resolvedStemId = def.first
                                resolvedStem = def.second
                            } else {
                                val refLookup = rawStem.trimEnd(':', '：').trim()
                                val match = stemRegistry[normalizeStemKey(rawStem)]
                                    ?: stemRegistry[normalizeStemKey(refLookup)]
                                if (match != null) {
                                    resolvedStemId = match.first
                                    resolvedStem = match.second
                                } else {
                                    resolvedStem = rawStem
                                    resolvedStemId = null
                                }
                            }
                        }
                    }

                    val f1 = if (f1ColIdx != -1 && f1ColIdx in values.indices) values[f1ColIdx].trim().ifEmpty { null } else null
                    val f2 = if (f2ColIdx != -1 && f2ColIdx in values.indices) values[f2ColIdx].trim().ifEmpty { null } else null
                    val f3 = if (f3ColIdx != -1 && f3ColIdx in values.indices) values[f3ColIdx].trim().ifEmpty { null } else null
                    val rowStatus = if (statusColIdx != -1 && statusColIdx in values.indices && values[statusColIdx].isNotBlank()) {
                        values[statusColIdx].trim().lowercase()
                    } else {
                        "unrated"
                    }

                    validItems.add(
                        QuestionBankEntity(
                            id = normId,
                            question = question,
                            opt1 = opt1,
                            opt2 = opt2,
                            opt3 = opt3,
                            opt4 = opt4,
                            opt5 = opt5,
                            answer = ansResult.selectedOptionText,
                            explanation = explanation,
                            stem = resolvedStem,
                            stemId = resolvedStemId,
                            filter1 = f1,
                            filter2 = f2,
                            filter3 = f3,
                            filter1Label = f1Label,
                            filter2Label = f2Label,
                            filter3Label = f3Label,
                            bankName = bankName,
                            status = rowStatus
                        )
                    )

                    if (previewRows.size < 5) {
                        val rowPreview = linkedMapOf<String, String>()
                        rowPreview["Id"] = normId
                        rowPreview["Question"] = question
                        rowPreview["Opt1"] = opt1
                        rowPreview["Opt2"] = opt2
                        rowPreview["Opt3"] = opt3
                        rowPreview["Opt4"] = opt4
                        if (opt5.isNotBlank()) rowPreview["Opt5"] = opt5
                        rowPreview["Ans"] = "${ansResult.letter} (${ansResult.selectedOptionText})"
                        previewRows.add(rowPreview)
                    }
                }
            }
        }

        return ParsedQuestionBankSheetResult(
            sheetName = sheet.sheetName,
            headerRowNumber = headerRow.oneBasedRowNumber,
            columnMappings = columnMappings,
            filter1Label = f1Label,
            filter2Label = f2Label,
            filter3Label = f3Label,
            validItems = validItems,
            skippedIssues = skippedIssues,
            needsReviewIssues = needsReviewIssues,
            previewRows = previewRows
        )
    }

    /**
     * Legacy compatibility wrapper delegating to [parseQuestionBankSheetStrict].
     */
    fun parseQuestionBankRows(rows: List<List<String>>, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        if (rows.isEmpty()) return emptyList()
        val tabularRows = rows.mapIndexed { idx, cells -> TabularRow(idx + 1, cells) }
        if (ImportNormalization.findIdHeaderRowIndex(tabularRows) < 0) return emptyList()
        val sheet = ParsedSheetData(sheetName = defaultBankName, rows = tabularRows)
        return parseQuestionBankSheetStrict(sheet, defaultBankName).validItems
    }

    /**
     * Parses Question Bank CSV via [parseQuestionBankSheetStrict].
     */
    fun parseQuestionBankCsv(content: String, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        val tabularRows = XlsxWorkbookReader.parseCsvTabularRows(content)
        if (tabularRows.isEmpty()) return emptyList()
        val sheet = ParsedSheetData(sheetName = defaultBankName, rows = tabularRows)
        if (ImportNormalization.findIdHeaderRowIndex(tabularRows) < 0) {
            throw WorkbookReadException("CSV ফাইলে বাধ্যতামূলক 'Id' কলাম পাওয়া যায়নি।")
        }
        return parseQuestionBankSheetStrict(sheet, defaultBankName).validItems
    }

    /**
     * Parses Question Bank JSON (Array of objects or Object with questions/questionBank key).
     */
    fun parseQuestionBankJson(jsonStr: String, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        val result = mutableListOf<QuestionBankEntity>()
        try {
            val trimmed = jsonStr.trim()
            val arr: JSONArray = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                root.optJSONArray("questionBank")
                    ?: root.optJSONArray("questions")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("qb")
                    ?: JSONArray().put(root)
            } else {
                return emptyList()
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val qText = obj.optString("question", obj.optString("Question", obj.optString("q", obj.optString("prompt", "")))).trim()
                if (qText.isBlank()) continue

                val idRaw = obj.optString("id", obj.optString("Id", obj.optString("qbId", ""))).trim()
                val normId = ImportNormalization.normalizeId(idRaw)
                if (normId.isEmpty()) continue

                val o1 = obj.optString("opt1", obj.optString("Opt1", obj.optString("option1", obj.optString("a", "")))).trim()
                val o2 = obj.optString("opt2", obj.optString("Opt2", obj.optString("option2", obj.optString("b", "")))).trim()
                val o3 = obj.optString("opt3", obj.optString("Opt3", obj.optString("option3", obj.optString("c", "")))).trim()
                val o4 = obj.optString("opt4", obj.optString("Opt4", obj.optString("option4", obj.optString("d", "")))).trim()
                val o5 = obj.optString("opt5", obj.optString("Opt5", obj.optString("option5", obj.optString("e", "")))).trim()
                val ansRaw = obj.optString("answer", obj.optString("Answer", obj.optString("ans", obj.optString("correctAnswer", "")))).trim()

                val resolved = QuestionAnswerResolver.resolve(o1, o2, o3, o4, o5, ansRaw)
                val exp = obj.optString("explanation", obj.optString("Explanation", obj.optString("exp", ""))).trim().ifEmpty { null }
                var f1: String? = null
                var f1Label = "Filter 1"
                var f2: String? = null
                var f2Label = "Filter 2"
                var f3: String? = null
                var f3Label = "Filter 3"

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = ImportNormalization.cleanInvisibleChars(k).trim()
                    val match = qbFilterHeaderRegex.matchEntire(kClean)
                    if (match != null) {
                        val fNum = match.groupValues[1].toInt()
                        val lbl = match.groupValues[2].trim().ifBlank { "Filter $fNum" }
                        val v = obj.optString(k, "").trim().ifEmpty { null }
                        when (fNum) {
                            1 -> { f1 = v; f1Label = lbl }
                            2 -> { f2 = v; f2Label = lbl }
                            3 -> { f3 = v; f3Label = lbl }
                        }
                    }
                }
                if (obj.has("filter1Label") && obj.optString("filter1Label").isNotBlank()) f1Label = obj.optString("filter1Label")
                if (obj.has("filter2Label") && obj.optString("filter2Label").isNotBlank()) f2Label = obj.optString("filter2Label")
                if (obj.has("filter3Label") && obj.optString("filter3Label").isNotBlank()) f3Label = obj.optString("filter3Label")

                val bankName = obj.optString("bankName", obj.optString("bank_name", obj.optString("bank", defaultBankName))).ifBlank { defaultBankName }
                val status = obj.optString("status", "unrated").ifBlank { "unrated" }
                val stemText = obj.optString("stem", obj.optString("Stem", "")).trim().ifEmpty { null }
                val stemId = obj.optString("stemId", obj.optString("stem_id", obj.optString("StemId", ""))).trim().ifEmpty { null }

                result.add(
                    QuestionBankEntity(
                        id = normId,
                        question = qText,
                        opt1 = resolved.cleanOpt1,
                        opt2 = resolved.cleanOpt2,
                        opt3 = resolved.cleanOpt3,
                        opt4 = resolved.cleanOpt4,
                        opt5 = resolved.cleanOpt5,
                        answer = resolved.correctAnswer,
                        explanation = exp,
                        stem = stemText,
                        stemId = stemId,
                        filter1 = f1,
                        filter2 = f2,
                        filter3 = f3,
                        filter1Label = f1Label,
                        filter2Label = f2Label,
                        filter3Label = f3Label,
                        bankName = bankName,
                        status = status
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Parses Question Bank from text content (auto-detecting JSON vs CSV).
     */
    fun parseQuestionBankAny(content: String, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonItems = parseQuestionBankJson(trimmed, defaultBankName)
            if (jsonItems.isNotEmpty()) return jsonItems
        }
        return parseQuestionBankCsv(trimmed, defaultBankName)
    }

    /**
     * Parses Question Bank from raw bytes, using Excel sheet name as bankName for multi-sheet .xlsx,
     * and propagating real WorkbookReadException errors for corrupted/encrypted Excel files.
     */
    fun parseQuestionBankFromBytes(bytes: ByteArray, fileName: String = ""): List<QuestionBankEntity> {
        val defaultBankName = if (fileName.isNotBlank()) {
            val base = fileName.substringBeforeLast(".").trim()
            if (base.isNotBlank()) base else "General QB"
        } else {
            "General QB"
        }

        val isZipOrXlsx = (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) ||
            fileName.endsWith(".xlsx", ignoreCase = true)
        if (isZipOrXlsx && !fileName.endsWith(".csv", ignoreCase = true) && !fileName.endsWith(".tsv", ignoreCase = true) && !fileName.endsWith(".txt", ignoreCase = true)) {
            val sheets = XlsxWorkbookReader.parseMultiSheetXlsx(ByteArrayInputStream(bytes))
            val allItems = mutableListOf<QuestionBankEntity>()
            for (sheet in sheets) {
                allItems.addAll(parseQuestionBankSheetStrict(sheet, sheet.sheetName).validItems)
            }
            return allItems
        }

        val content = try {
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            try {
                String(bytes, Charsets.ISO_8859_1)
            } catch (_: Exception) {
                ""
            }
        }
        if (content.isNotBlank()) {
            return parseQuestionBankAny(content, defaultBankName)
        }
        return emptyList()
    }

    /**
     * Parses a structured JSON string containing courses/words (Backup JSON).
     */
    fun parseJsonCourse(jsonStr: String, courseId: String = "course_default"): List<VocabularyWordEntity> {
        val list = mutableListOf<VocabularyWordEntity>()
        val root = try {
            if (jsonStr.trim().startsWith("[")) {
                JSONArray(jsonStr)
            } else {
                val obj = JSONObject(jsonStr)
                if (obj.has("words")) obj.getJSONArray("words")
                else if (obj.has("courses")) obj.getJSONArray("courses")
                else JSONArray().put(obj)
            }
        } catch (_: Exception) {
            return emptyList()
        }

        for (i in 0 until root.length()) {
            val item = root.optJSONObject(i) ?: continue
            val rowCourseId = item.optString("courseId", courseId).ifBlank { courseId }
            val rawId = item.optString("id", item.optString("Id", ""))
            val normId = ImportNormalization.normalizeId(rawId)
            if (normId.isEmpty()) continue
            val word = item.optString("word", item.optString("Word", ""))
            val meaning = item.optString("meaning", item.optString("Meaning", ""))
            val rawGroup = item.optString("group", item.optString("Group", "")).trim()
            val status = item.optString("status", "unrated")
            val customPlacesJson = item.optString("customPlacesJson", "").ifBlank { null }

            list.add(
                VocabularyWordEntity(
                    id = normId,
                    word = word,
                    meaning = meaning,
                    group = rawGroup,
                    synonyms = item.optString("synonyms", "").ifBlank { item.optString("Synonyms", "").ifBlank { null } },
                    extraWord = item.optString("extraWord", "").ifBlank { item.optString("ExtraWord", "").ifBlank { null } },
                    example = item.optString("example", "").ifBlank { item.optString("Example", "").ifBlank { null } },
                    mnemonic = item.optString("mnemonic", "").ifBlank { item.optString("Mnemonic", "").ifBlank { null } },
                    status = status,
                    customPlacesJson = customPlacesJson,
                    courseId = rowCourseId
                )
            )
        }
        return list
    }

    fun parseCsv(content: String): List<List<String>> =
        XlsxWorkbookReader.parseCsvTabularRows(content).map { it.cells }

    fun parseXlsx(inputStream: InputStream): List<List<String>> =
        parseMultiSheetXlsx(inputStream).firstOrNull()?.rows ?: emptyList()

    fun parseMultiSheetXlsx(inputStream: InputStream): List<ParsedSheet> =
        XlsxWorkbookReader.parseMultiSheetXlsx(inputStream).map {
            ParsedSheet(sheetName = it.sheetName, rows = it.toPlainRows())
        }

    internal fun encodeJsonStringMap(map: Map<String, String>): String? {
        if (map.isEmpty()) return null
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            appendJsonString(sb, k)
            sb.append(':')
            appendJsonString(sb, v)
        }
        sb.append('}')
        return sb.toString()
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }
}
