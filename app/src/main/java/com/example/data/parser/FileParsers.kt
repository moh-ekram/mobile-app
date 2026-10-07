package com.example.data.parser

import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ParsedSheet(
    val sheetName: String,
    val rows: List<List<String>>
)

data class GameValidationSummary(
    val totalQuestionsFound: Int,
    val validCount: Int,
    val anomalyCount: Int,
    val sheetBreakdown: Map<String, Int>,
    val anomalies: List<String>,
    val validGames: List<GamePracticeEntity>
)

object FileParsers {

    /**
     * Extracts column headers from CSV content.
     */
    fun extractHeaders(content: String): List<String> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        return parseCsvLine(lines[0]).map { it.trim() }
    }

    /**
     * Parses course data from CSV/Excel text.
     * Supports columns: id, group, Place#: label (e.g. Place1: Word, Place2: Meaning, Place3: Example, etc.)
     * Or standard headers: word, meaning, example, synonyms, extra, mnemonic
     */
    fun parseCourseCsv(content: String, courseId: String = "course_default"): List<VocabularyWordEntity> {
        val rows = parseCsv(content)
        if (rows.size < 2) return emptyList()
        return parseCourseRows(rows, courseId)
    }

    data class ColumnPlaceInfo(
        val colIndex: Int,
        var placeNumber: Int,
        val rawHeader: String,
        var cleanLabel: String
    )

    fun extractPlaceNumber(rawHeader: String): Int? {
        val clean = rawHeader.trim()
        // 1. Matches "place_index 1", "place index 1", "place_index: 1", "place-index-1", "placeindex1", etc.
        val placeIndexMatch = Regex("""(?i)place[\s_-]*index[\s_.:#]*(\d+)""").find(clean)
        if (placeIndexMatch != null) {
            return placeIndexMatch.groupValues[1].toIntOrNull()
        }
        // 2. Matches "place 1", "place1", "place_1", "place-1", "place:1", "place#1", "place #1", "place.1"
        val englishMatch = Regex("""(?i)place[\s_.:#]*(\d+)""").find(clean)
        if (englishMatch != null) {
            return englishMatch.groupValues[1].toIntOrNull()
        }
        // 3. Matches "(place 1)", "(place_index 1)", "[place 1]"
        val parenMatch = Regex("""(?i)[\(\[]place[\s_-]*(?:index)?[\s_.:#]*(\d+)[\)\]]""").find(clean)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].toIntOrNull()
        }
        // 4. Matches "p1", "p2", "p3", "p4", "p5" (when prefix like p1, p_1, p-1, p:1)
        val pMatch = Regex("""(?i)^p[\s_.:#]*(\d+)(?:\b|[:_-])""").find(clean)
        if (pMatch != null) {
            return pMatch.groupValues[1].toIntOrNull()
        }
        // 5. Matches "col 1", "column 1", "col_1", "col:1"
        val colMatch = Regex("""(?i)^(?:col|column)[\s_.:#]*(\d+)""").find(clean)
        if (colMatch != null) {
            return colMatch.groupValues[1].toIntOrNull()
        }
        // 6. Bengali matches: "প্লেস ইনডেক্স ১" or "প্লেস 1" (supports Bengali digits ০-৯ and English digits 0-9!)
        val bengaliPlaceIndexMatch = Regex("""(?i)প্লেস[\s_-]*ইনডেক্স[\s_.:#]*([০-৯\d]+)""").find(clean)
        if (bengaliPlaceIndexMatch != null) {
            val digits = bengaliPlaceIndexMatch.groupValues[1].map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
            return digits.toIntOrNull()
        }
        val bengaliMatch = Regex("""(?i)প্লেস[\s_.:#]*([০-৯\d]+)""").find(clean)
        if (bengaliMatch != null) {
            val digits = bengaliMatch.groupValues[1].map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
            return digits.toIntOrNull()
        }
        val bengaliParenMatch = Regex("""(?i)[\(\[]প্লেস[\s_-]*(?:ইনডেক্স)?[\s_.:#]*([০-৯\d]+)[\)\]]""").find(clean)
        if (bengaliParenMatch != null) {
            val digits = bengaliParenMatch.groupValues[1].map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
            return digits.toIntOrNull()
        }
        return null
    }

    fun extractCleanLabel(rawHeader: String, placeNumber: Int): String {
        var cleanLabel = if (rawHeader.contains(":")) rawHeader.substringAfter(":").trim() else rawHeader.trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)^place[\s_-]*index[\s_.:#]*\d*[\s_.:#-]*"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)^place[\s_.:#]*\d*[\s_.:#-]*"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)^p[\s_.:#]*\d*[\s_.:#-]*"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)\s*[\(\[]place[\s_-]*(?:index)?[\s_.:#]*\d*[\)\]]"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)^প্লেস[\s_-]*ইনডেক্স[\s_.:#]*[০-৯\d]*[\s_.:#-]*"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)^প্লেস[\s_.:#]*[০-৯\d]*[\s_.:#-]*"""), "").trim()
        cleanLabel = cleanLabel.replace(Regex("""(?i)\s*[\(\[]প্লেস[\s_-]*(?:ইনডেক্স)?[\s_.:#]*[০-৯\d]*[\)\]]"""), "").trim()
        if (cleanLabel.isBlank() || cleanLabel.equals("place", ignoreCase = true) || cleanLabel.equals("প্লেস", ignoreCase = true)) {
            cleanLabel = if (placeNumber == 1) "Word" else "Place $placeNumber"
        }
        return cleanLabel
    }

    /**
     * Parses tabular rows (from CSV or Excel sheets) into VocabularyWordEntity items.
     * ID tracking is fully supported: uses existing 'id', 'no', 'sl', '#' column or generates
     * a deterministic stable ID based on course and word so subsequent updates match accurately.
     */
    fun parseCourseRows(
        rows: List<List<String>>,
        courseId: String = "course_default",
        defaultGroupName: String = "1"
    ): List<VocabularyWordEntity> {
        if (rows.size < 2) return emptyList()

        val headers = rows[0].map { it.trim() }

        // Map system column indices
        var idIndex = -1
        var groupIndex = -1
        var courseIdIndex = -1
        var courseTitleIndex = -1
        var statusIndex = -1
        var isReportedIndex = -1
        var reportReasonIndex = -1
        var wordIndex = -1
        var meaningIndex = -1
        var exampleIndex = -1
        var synonymsIndex = -1
        var extraIndex = -1
        var mnemonicIndex = -1

        val placeDefs = mutableListOf<ColumnPlaceInfo>()
        val unassignedColIndices = mutableListOf<Int>()

        headers.forEachIndexed { index, rawHeader ->
            val lower = rawHeader.lowercase().trim()

            when {
                lower in listOf("id", "id*", "word_id", "wordid", "no", "no.", "sl", "sl.", "serial", "#") -> idIndex = index
                lower in listOf("group", "group_id", "unit", "chapter") -> groupIndex = index
                lower in listOf("courseid", "course_id") -> courseIdIndex = index
                lower in listOf("coursetitle", "course_title", "coursename") -> courseTitleIndex = index
                lower == "status" -> statusIndex = index
                lower in listOf("isreported", "is_reported", "flagged", "isflagged", "is_flagged") -> isReportedIndex = index
                lower in listOf("reportreason", "report_reason", "flagreason", "flag_reason") -> reportReasonIndex = index
                else -> {
                    val explicitPlace = extractPlaceNumber(rawHeader)
                    if (explicitPlace != null) {
                        val clean = extractCleanLabel(rawHeader, explicitPlace)
                        placeDefs.add(ColumnPlaceInfo(index, explicitPlace, rawHeader, clean))
                    } else {
                        unassignedColIndices.add(index)
                    }
                }
            }
        }

        // Two-pass resolution for unassigned columns:
        // Pass 1: Semantic keywords (word -> place 1, meaning -> place 2, example -> place 3, synonym -> place 4, form/extra -> place 5)
        val remainingUnassigned = mutableListOf<Int>()
        for (colIdx in unassignedColIndices) {
            val rawHeader = headers[colIdx]
            val lower = rawHeader.lowercase().trim()
            val hasPlace1 = placeDefs.any { it.placeNumber == 1 }
            val hasPlace2 = placeDefs.any { it.placeNumber == 2 }
            val hasPlace3 = placeDefs.any { it.placeNumber == 3 }
            val hasPlace4 = placeDefs.any { it.placeNumber == 4 }
            val hasPlace5 = placeDefs.any { it.placeNumber == 5 }

            when {
                !hasPlace1 && (lower.contains("word") || lower == "term" || lower == "vocabulary" || lower == "headword") -> {
                    val clean = extractCleanLabel(rawHeader, 1)
                    placeDefs.add(ColumnPlaceInfo(colIdx, 1, rawHeader, clean))
                }
                !hasPlace2 && (lower.contains("meaning") || lower.contains("definition") || lower.contains("translation") ||
                        lower.contains("bangla") || lower.contains("bengali") || lower.contains("অর্থ")) -> {
                    val clean = extractCleanLabel(rawHeader, 2)
                    placeDefs.add(ColumnPlaceInfo(colIdx, 2, rawHeader, clean))
                }
                !hasPlace3 && (lower.contains("example") || lower.contains("sentence") || lower.contains("usage")) -> {
                    val clean = extractCleanLabel(rawHeader, 3)
                    placeDefs.add(ColumnPlaceInfo(colIdx, 3, rawHeader, clean))
                }
                !hasPlace4 && lower.contains("synonym") -> {
                    val clean = extractCleanLabel(rawHeader, 4)
                    placeDefs.add(ColumnPlaceInfo(colIdx, 4, rawHeader, clean))
                }
                !hasPlace5 && (lower.contains("form") || lower.contains("extra") || lower.contains("derivative")) -> {
                    val clean = extractCleanLabel(rawHeader, 5)
                    placeDefs.add(ColumnPlaceInfo(colIdx, 5, rawHeader, clean))
                }
                else -> {
                    remainingUnassigned.add(colIdx)
                }
            }
        }

        // Pass 2: Remaining unassigned columns (like BY_PART, RULE, etc.) get the next sequential available place numbers
        for (colIdx in remainingUnassigned) {
            val rawHeader = headers[colIdx]
            val usedPlaces = placeDefs.map { it.placeNumber }.toSet()
            var nextPlace = 1
            while (usedPlaces.contains(nextPlace)) {
                nextPlace++
            }
            val clean = extractCleanLabel(rawHeader, nextPlace)
            placeDefs.add(ColumnPlaceInfo(colIdx, nextPlace, rawHeader, clean))
        }

        // Sort by placeNumber ascending (Place 1, Place 2, Place 3, Place 4...)
        val sortedPlaceDefs = placeDefs.sortedBy { it.placeNumber }

        val place1Def = sortedPlaceDefs.firstOrNull { it.placeNumber == 1 }
        val place2Def = sortedPlaceDefs.firstOrNull { it.placeNumber == 2 }

        wordIndex = place1Def?.colIndex ?: (if (headers.isNotEmpty()) 0 else -1)
        meaningIndex = place2Def?.colIndex ?: (if (headers.size > 1) 1 else -1)

        sortedPlaceDefs.forEach { def ->
            val labelLower = def.cleanLabel.lowercase()
            when {
                (def.placeNumber == 3 || labelLower.contains("example") || labelLower.contains("sentence")) && exampleIndex == -1 -> {
                    exampleIndex = def.colIndex
                }
                (def.placeNumber == 4 || labelLower.contains("synonym")) && synonymsIndex == -1 -> {
                    synonymsIndex = def.colIndex
                }
                (def.placeNumber == 5 || labelLower.contains("extra") || labelLower.contains("derivative") || labelLower.contains("form")) && extraIndex == -1 -> {
                    extraIndex = def.colIndex
                }
                (labelLower.contains("mnemonic") || labelLower.contains("trick")) && mnemonicIndex == -1 -> {
                    mnemonicIndex = def.colIndex
                }
            }
        }

        val result = mutableListOf<VocabularyWordEntity>()
        for (i in 1 until rows.size) {
            val values = rows[i]
            if (values.isEmpty() || values.all { it.isBlank() }) continue

            val rowCourseId = if (courseIdIndex in values.indices && values[courseIdIndex].isNotBlank()) {
                values[courseIdIndex].trim()
            } else {
                courseId
            }

            // Word is ALWAYS strictly from Place 1
            val rawWord = if (wordIndex in values.indices && values[wordIndex].isNotBlank()) {
                values[wordIndex].trim()
            } else if (place1Def != null && place1Def.colIndex in values.indices && values[place1Def.colIndex].isNotBlank()) {
                values[place1Def.colIndex].trim()
            } else "Word $i"

            if (rawWord.isBlank()) continue

            val stableWordSlug = rawWord.lowercase().replace(Regex("[^a-z0-9]"), "_").take(24).trim('_')
            val id = if (idIndex in values.indices && values[idIndex].isNotBlank()) {
                values[idIndex].trim()
            } else {
                "w_${rowCourseId}_${stableWordSlug}_$i"
            }

            val rawGroup = if (groupIndex in values.indices) values[groupIndex].trim() else ""
            val group = if (rawGroup.isNotBlank()) {
                rawGroup
            } else {
                val cleanDefaultGroup = defaultGroupName.replace(Regex("(?i)^sheet|^group"), "").trim()
                if (cleanDefaultGroup.isNotBlank() && cleanDefaultGroup.any { it.isDigit() }) cleanDefaultGroup else defaultGroupName
            }

            // Meaning is ALWAYS strictly from Place 2
            val meaning = if (meaningIndex in values.indices && values[meaningIndex].isNotBlank()) {
                values[meaningIndex].trim()
            } else if (place2Def != null && place2Def.colIndex in values.indices) {
                values[place2Def.colIndex].trim()
            } else ""

            val example = if (exampleIndex in values.indices && values[exampleIndex].isNotBlank()) values[exampleIndex].trim() else null
            val synonyms = if (synonymsIndex in values.indices && values[synonymsIndex].isNotBlank()) values[synonymsIndex].trim() else null
            val extraWord = if (extraIndex in values.indices && values[extraIndex].isNotBlank()) values[extraIndex].trim() else null
            val mnemonic = if (mnemonicIndex in values.indices && values[mnemonicIndex].isNotBlank()) values[mnemonicIndex].trim() else null
            val status = if (statusIndex in values.indices && values[statusIndex].isNotBlank()) values[statusIndex].trim() else "unrated"
            val isReported = if (isReportedIndex in values.indices) {
                values[isReportedIndex].trim().equals("true", ignoreCase = true) || values[isReportedIndex].trim() == "1"
            } else false
            val reportReason = if (reportReasonIndex in values.indices && values[reportReasonIndex].isNotBlank()) values[reportReasonIndex].trim() else null

            // Build customPlacesJson strictly ordered by placeNumber (Place 1, Place 2, Place 3, Place 4...)
            val placeJsonObj = JSONObject()
            sortedPlaceDefs.forEach { placeDef ->
                if (placeDef.colIndex in values.indices && values[placeDef.colIndex].isNotBlank()) {
                    val key = "Place ${placeDef.placeNumber}: ${placeDef.cleanLabel}"
                    placeJsonObj.put(key, values[placeDef.colIndex].trim())
                }
            }

            result.add(
                VocabularyWordEntity(
                    id = id,
                    word = rawWord,
                    meaning = meaning,
                    group = group,
                    synonyms = synonyms,
                    extraWord = extraWord,
                    example = example,
                    mnemonic = mnemonic,
                    status = status,
                    customPlacesJson = if (placeJsonObj.length() > 0) placeJsonObj.toString() else null,
                    courseId = rowCourseId,
                    isReported = isReported,
                    reportReason = reportReason
                )
            )
        }
        return result
    }

    /**
     * Parses Game/Practice CSV/Excel.
     * Validates and returns valid GamePracticeEntity items.
     */
    fun parseGameCsv(content: String, defaultSheetType: String = "practice"): List<GamePracticeEntity> {
        return validateAndParseGameCsv(content, defaultSheetType).validGames
    }

    /**
     * Validates and parses Game/Practice data from CSV text with detailed anomaly reporting.
     */
    fun validateAndParseGameCsv(content: String, defaultSheetType: String = "practice"): GameValidationSummary {
        val rows = parseCsv(content)
        return validateAndParseGameSheets(listOf(ParsedSheet(defaultSheetType, rows)))
    }

    /**
     * Validates and parses multiple sheets of Game/Practice data.
     * Identifies anomalies such as missing questions, answers, or insufficient options.
     */
    fun validateAndParseGameSheets(sheets: List<ParsedSheet>): GameValidationSummary {
        var totalFound = 0
        val validGames = mutableListOf<GamePracticeEntity>()
        val anomalies = mutableListOf<String>()
        val sheetBreakdown = mutableMapOf<String, Int>()

        for (sheet in sheets) {
            if (sheet.rows.size < 2) continue
            val headers = sheet.rows[0].map { it.trim().replace("\uFEFF", "").replace("\u200B", "").lowercase() }

            var qIdx = headers.indexOfFirst {
                it.contains("question") || it.contains("ques") || it.contains("prompt") || it.contains("sentence")
            }
            var opt1Idx = headers.indexOfFirst {
                it.contains("opt1") || it.contains("opt 1") || it.contains("option 1") || it.contains("choice 1") || it == "a" || it == "opt_1"
            }
            var opt2Idx = headers.indexOfFirst {
                it.contains("opt2") || it.contains("opt 2") || it.contains("option 2") || it.contains("choice 2") || it == "b" || it == "opt_2"
            }
            var opt3Idx = headers.indexOfFirst {
                it.contains("opt3") || it.contains("opt 3") || it.contains("option 3") || it.contains("choice 3") || it == "c" || it == "opt_3"
            }
            var opt4Idx = headers.indexOfFirst {
                it.contains("opt4") || it.contains("opt 4") || it.contains("option 4") || it.contains("choice 4") || it == "d" || it == "opt_4"
            }
            var opt5Idx = headers.indexOfFirst {
                it.contains("opt5") || it.contains("opt 5") || it.contains("option 5") || it.contains("choice 5") || it == "e" || it == "opt_5"
            }
            var ansIdx = headers.indexOfFirst {
                it.contains("ans") || it.contains("answer") || it.contains("correct") || it.contains("solution") || it.contains("key")
            }
            val expIdx = headers.indexOfFirst {
                it.contains("expl") || it.contains("hint") || it.contains("note") || it.contains("reason")
            }
            val idIdx = headers.indexOfFirst { it == "id" || it == "id*" || it.contains("qid") }
            val typeIdx = headers.indexOfFirst { it.contains("sheettype") || it.contains("gametype") || it == "type" || it == "mode" }

            // Intelligent fallbacks if column headers are non-standard
            if (qIdx == -1 && headers.isNotEmpty()) {
                qIdx = if (headers.size >= 2 && (headers[0].contains("id") || headers[0] == "#")) 1 else 0
            }
            if (opt1Idx == -1 && headers.size >= 5) {
                opt1Idx = qIdx + 1
                opt2Idx = qIdx + 2
                opt3Idx = qIdx + 3
                opt4Idx = qIdx + 4
                if (headers.size >= 6) opt5Idx = qIdx + 5
            }

            var sheetValidCount = 0

            for (r in 1 until sheet.rows.size) {
                val row = sheet.rows[r]
                if (row.all { it.isBlank() }) continue

                totalFound++
                val rowNum = r + 1

                val id = if (idIdx in row.indices && row[idIdx].isNotBlank()) row[idIdx].trim() else "game_${sheet.sheetName.lowercase()}_$r"
                val question = if (qIdx in row.indices) row[qIdx].trim() else ""
                var opt1 = if (opt1Idx in row.indices) row[opt1Idx].trim() else ""
                var opt2 = if (opt2Idx in row.indices) row[opt2Idx].trim() else ""
                var opt3 = if (opt3Idx in row.indices) row[opt3Idx].trim() else ""
                var opt4 = if (opt4Idx in row.indices) row[opt4Idx].trim() else ""
                var opt5 = if (opt5Idx in row.indices) row[opt5Idx].trim() else ""
                var answer = if (ansIdx in row.indices) row[ansIdx].trim() else ""
                val explanation = if (expIdx in row.indices && row[expIdx].isNotBlank()) row[expIdx].trim() else null
                val sheetType = if (typeIdx in row.indices && row[typeIdx].isNotBlank()) row[typeIdx].trim() else sheet.sheetName

                val resolved = QuestionAnswerResolver.resolve(opt1, opt2, opt3, opt4, opt5, answer)
                opt1 = resolved.cleanOpt1
                opt2 = resolved.cleanOpt2
                opt3 = resolved.cleanOpt3
                opt4 = resolved.cleanOpt4
                opt5 = resolved.cleanOpt5
                answer = resolved.correctAnswer

                // Anomaly Detection
                val rowAnomalies = mutableListOf<String>()
                if (question.isBlank()) {
                    rowAnomalies.add("Row $rowNum [${sheet.sheetName}]: Question is empty")
                }
                val validOptions = listOf(opt1, opt2, opt3, opt4, opt5).filter { it.isNotBlank() }
                if (validOptions.size < 2) {
                    rowAnomalies.add("Row $rowNum [${sheet.sheetName}]: At least 2 options required (found ${validOptions.size})")
                }
                if (answer.isBlank()) {
                    val promptPreview = if (question.length > 22) question.take(22) + "..." else question
                    rowAnomalies.add("Row $rowNum [${sheet.sheetName}] ('$promptPreview'): Missing Answer (no Ans column or '#' in option)")
                }

                if (rowAnomalies.isNotEmpty()) {
                    anomalies.addAll(rowAnomalies)
                } else {
                    sheetValidCount++
                    validGames.add(
                        GamePracticeEntity(
                            id = id,
                            sheetType = sheetType.lowercase().trim(),
                            question = question,
                            opt1 = opt1,
                            opt2 = opt2,
                            opt3 = opt3,
                            opt4 = opt4,
                            opt5 = opt5,
                            answer = answer,
                            explanation = explanation
                        )
                    )
                }
            }

            if (sheetValidCount > 0) {
                sheetBreakdown[sheet.sheetName] = sheetValidCount
            }
        }

        return GameValidationSummary(
            totalQuestionsFound = totalFound,
            validCount = validGames.size,
            anomalyCount = anomalies.size,
            sheetBreakdown = sheetBreakdown,
            anomalies = anomalies,
            validGames = validGames
        )
    }

    /**
     * Parses Question Bank from 2D rows (CSV or Excel sheets).
     * Columns: Id*, Question*, Opt1-4*, Ans*, Explanation, Filter1:label, Filter2:label, Filter3:label
     */
    fun parseQuestionBankRows(rows: List<List<String>>, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        if (rows.size < 2) return emptyList()

        val rawHeaders = rows[0].map { it.trim().replace("\uFEFF", "").replace("\u200B", "") }
        val headers = rawHeaders.map { it.lowercase() }

        val idIdx = headers.indexOfFirst { it == "id" || it == "id*" || it == "#" || it.contains("qid") }
        val qIdx = headers.indexOfFirst { it.contains("question") || it.contains("ques") || it.contains("prompt") || it.contains("sentence") }
        fun findOptionIdx(optNum: Int, letter: Char): Int {
            return headers.indexOfFirst { h ->
                val norm = h.replace(" ", "").replace("_", "").replace("-", "")
                norm == "opt$optNum" ||
                        norm == "option$optNum" ||
                        norm == "choice$optNum" ||
                        norm == "opt($optNum)" ||
                        norm == letter.toString() ||
                        norm == "($letter)" ||
                        norm == "$letter." ||
                        h.contains("opt$optNum") ||
                        h.contains("option $optNum") ||
                        h.contains("option_$optNum") ||
                        h.contains("choice $optNum") ||
                        h.contains("choice_$optNum")
            }
        }
        var opt1Idx = findOptionIdx(1, 'a')
        var opt2Idx = findOptionIdx(2, 'b')
        var opt3Idx = findOptionIdx(3, 'c')
        var opt4Idx = findOptionIdx(4, 'd')
        var opt5Idx = findOptionIdx(5, 'e')

        if (opt1Idx == -1 && qIdx != -1 && headers.size >= qIdx + 5) {
            opt1Idx = qIdx + 1
            opt2Idx = qIdx + 2
            opt3Idx = qIdx + 3
            opt4Idx = qIdx + 4
            if (headers.size >= qIdx + 6) opt5Idx = qIdx + 5
        }
        val ansIdx = headers.indexOfFirst { it.contains("ans") || it.contains("correct") }
        val expIdx = headers.indexOfFirst { it.contains("explanation") || it.contains("explain") }
        val bankIdx = headers.indexOfFirst { it == "bank" || it == "bankname" || it == "bank name" || it == "bank_name" || it == "qb" || it == "qb name" || it == "qb_name" }
        val statusIdx = headers.indexOfFirst { it == "status" || it == "state" }
        val stemIdx = headers.indexOfFirst {
            it == "stem" || it.startsWith("stem") || it == "passage" || it.startsWith("passage") ||
                    it == "scenario" || it.startsWith("scenario") || it == "case" || it.startsWith("case") ||
                    it.contains("stem") || it.contains("passage") || it.contains("scenario") || it.contains("stimulus")
        }

        // Filter columns with labels
        var f1Idx = -1
        var f2Idx = -1
        var f3Idx = -1
        var f1Label = "Course"
        var f2Label = "Q.type"
        var f3Label = "Session"
        var f1Explicit = false
        var f2Explicit = false
        var f3Explicit = false

        rawHeaders.forEachIndexed { index, rawH ->
            val clean = rawH.trim().replace("\uFEFF", "").replace("\u00A0", " ")
            val lower = clean.lowercase()
            val norm = lower.replace(" ", "").replace("_", "").replace("-", "")

            // 1. Check Filter 1 (Course / Category / Topic / Subject)
            val isExplicitF1 = norm.startsWith("filter1") || norm.startsWith("fiter1") ||
                    norm.startsWith("f1:") || norm.startsWith("f1：") || norm == "f1" ||
                    norm.startsWith("filter(1)") || norm.startsWith("filter_1") || norm.startsWith("filter-1")

            val isGeneralF1 = isExplicitF1 ||
                    norm.startsWith("course") || norm.startsWith("category") ||
                    norm.startsWith("topic") || norm.startsWith("subject")

            // 2. Check Filter 2 (Q.type / Difficulty / Type)
            val isExplicitF2 = norm.startsWith("filter2") || norm.startsWith("fiter2") ||
                    norm.startsWith("f2:") || norm.startsWith("f2：") || norm == "f2" ||
                    norm.startsWith("filter(2)") || norm.startsWith("filter_2") || norm.startsWith("filter-2")

            val isGeneralF2 = isExplicitF2 ||
                    norm.startsWith("q.type") || norm.startsWith("qtype") ||
                    norm.startsWith("difficulty") || norm == "type" || norm.startsWith("questiontype")

            // 3. Check Filter 3 (Session / Source / Exam / Year)
            val isExplicitF3 = norm.startsWith("filter3") || norm.startsWith("fiter3") ||
                    norm.startsWith("f3:") || norm.startsWith("f3：") || norm == "f3" ||
                    norm.startsWith("filter(3)") || norm.startsWith("filter_3") || norm.startsWith("filter-3")

            val isGeneralF3 = isExplicitF3 ||
                    norm.startsWith("session") || norm.startsWith("source") ||
                    norm.startsWith("exam") || norm.startsWith("year")

            if (isExplicitF1 || (!f1Explicit && isGeneralF1 && f1Idx == -1)) {
                f1Idx = index
                if (isExplicitF1) f1Explicit = true

                val extracted = when {
                    clean.contains(":") -> clean.substringAfter(":").trim()
                    clean.contains("：") -> clean.substringAfter("：").trim()
                    clean.contains("(") && clean.contains(")") -> clean.substringAfter("(").substringBefore(")").trim()
                    clean.contains("[") && clean.contains("]") -> clean.substringAfter("[").substringBefore("]").trim()
                    clean.contains(" - ") -> clean.substringAfter(" - ").trim()
                    norm.startsWith("course") -> "Course"
                    norm.startsWith("topic") -> "Topic"
                    norm.startsWith("subject") -> "Subject"
                    norm.startsWith("category") -> "Category"
                    else -> ""
                }
                if (extracted.isNotBlank()) {
                    f1Label = extracted
                }
            } else if (isExplicitF2 || (!f2Explicit && isGeneralF2 && f2Idx == -1)) {
                f2Idx = index
                if (isExplicitF2) f2Explicit = true

                val extracted = when {
                    clean.contains(":") -> clean.substringAfter(":").trim()
                    clean.contains("：") -> clean.substringAfter("：").trim()
                    clean.contains("(") && clean.contains(")") -> clean.substringAfter("(").substringBefore(")").trim()
                    clean.contains("[") && clean.contains("]") -> clean.substringAfter("[").substringBefore("]").trim()
                    clean.contains(" - ") -> clean.substringAfter(" - ").trim()
                    norm.startsWith("q.type") || norm.startsWith("qtype") -> "Q.type"
                    norm.startsWith("difficulty") -> "Difficulty"
                    else -> ""
                }
                if (extracted.isNotBlank()) {
                    f2Label = extracted
                }
            } else if (isExplicitF3 || (!f3Explicit && isGeneralF3 && f3Idx == -1)) {
                f3Idx = index
                if (isExplicitF3) f3Explicit = true

                val extracted = when {
                    clean.contains(":") -> clean.substringAfter(":").trim()
                    clean.contains("：") -> clean.substringAfter("：").trim()
                    clean.contains("(") && clean.contains(")") -> clean.substringAfter("(").substringBefore(")").trim()
                    clean.contains("[") && clean.contains("]") -> clean.substringAfter("[").substringBefore("]").trim()
                    clean.contains(" - ") -> clean.substringAfter(" - ").trim()
                    norm.startsWith("session") -> "Session"
                    norm.startsWith("source") -> "Source"
                    norm.startsWith("exam") -> "Exam"
                    norm.startsWith("year") -> "Year"
                    else -> ""
                }
                if (extracted.isNotBlank()) {
                    f3Label = extracted
                }
            }
        }

        val result = mutableListOf<QuestionBankEntity>()
        val stemCache = mutableMapOf<String, String>()

        for (i in 1 until rows.size) {
            val values = rows[i]
            if (values.size <= maxOf(opt1Idx, qIdx)) continue

            val question = if (qIdx in values.indices) values[qIdx].trim() else ""
            val stableSlug = question.lowercase().trim().replace(Regex("[^a-z0-9]"), "_").take(24).trim('_')
            val id = if (idIdx in values.indices && values[idIdx].isNotBlank()) {
                values[idIdx].trim()
            } else {
                "qb_${defaultBankName.lowercase().trim().replace(Regex("[^a-z0-9]"), "_").take(16)}_r${i}_${stableSlug.ifEmpty { i.toString() }}"
            }
            var opt1 = if (opt1Idx in values.indices) values[opt1Idx].trim() else ""
            var opt2 = if (opt2Idx in values.indices) values[opt2Idx].trim() else ""
            var opt3 = if (opt3Idx in values.indices) values[opt3Idx].trim() else ""
            var opt4 = if (opt4Idx in values.indices) values[opt4Idx].trim() else ""
            var opt5 = if (opt5Idx in values.indices) values[opt5Idx].trim() else ""
            var answer = if (ansIdx in values.indices) values[ansIdx].trim() else ""
            val explanation = if (expIdx in values.indices) values[expIdx].trim().ifEmpty { null } else null

            // Resolve Stem & Stem ID (e.g. "stem1: Details..." or "stem1" reuse)
            var resolvedStem: String? = null
            var resolvedStemId: String? = null

            val rawStemCell = if (stemIdx in values.indices) values[stemIdx].trim() else ""
            if (rawStemCell.isNotBlank()) {
                val colonIdx = rawStemCell.indexOfAny(charArrayOf(':', '：'))
                if (colonIdx > 0 && colonIdx < 30) {
                    val possibleId = rawStemCell.substring(0, colonIdx).trim()
                    val textAfter = rawStemCell.substring(colonIdx + 1).trim()
                    val cleanedId = possibleId.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                    if (textAfter.isNotBlank()) {
                        resolvedStemId = possibleId
                        resolvedStem = textAfter
                        stemCache[possibleId.lowercase().trim()] = textAfter
                        stemCache[cleanedId] = textAfter
                    } else {
                        resolvedStemId = possibleId
                        resolvedStem = stemCache[cleanedId] ?: stemCache[possibleId.lowercase().trim()] ?: rawStemCell
                    }
                } else {
                    val key = rawStemCell.lowercase().trim()
                    val keyClean = key.replace(" ", "").replace("_", "").replace("-", "")
                    val cached = stemCache[key] ?: stemCache[keyClean]
                    if (cached != null) {
                        resolvedStemId = rawStemCell
                        resolvedStem = cached
                    } else {
                        resolvedStem = rawStemCell
                        resolvedStemId = null
                    }
                }
            }

            // Resolve answer according to the 3 rules (exact match, '#', A/B/C/D)
            val resolved = QuestionAnswerResolver.resolve(opt1, opt2, opt3, opt4, opt5, answer)
            opt1 = resolved.cleanOpt1
            opt2 = resolved.cleanOpt2
            opt3 = resolved.cleanOpt3
            opt4 = resolved.cleanOpt4
            opt5 = resolved.cleanOpt5
            answer = resolved.correctAnswer

            val filter1 = if (f1Idx in values.indices) values[f1Idx].trim().ifEmpty { null } else null
            val filter2 = if (f2Idx in values.indices) values[f2Idx].trim().ifEmpty { null } else null
            val filter3 = if (f3Idx in values.indices) values[f3Idx].trim().ifEmpty { null } else null

            val rowBankName = if (bankIdx in values.indices && values[bankIdx].isNotBlank()) values[bankIdx].trim() else defaultBankName
            val rowStatus = if (statusIdx in values.indices && values[statusIdx].isNotBlank()) values[statusIdx].trim().lowercase() else "unrated"

            if (question.isNotBlank()) {
                result.add(
                    QuestionBankEntity(
                        id = id,
                        question = question,
                        opt1 = opt1,
                        opt2 = opt2,
                        opt3 = opt3,
                        opt4 = opt4,
                        opt5 = opt5,
                        answer = answer,
                        explanation = explanation,
                        stem = resolvedStem,
                        stemId = resolvedStemId,
                        filter1 = filter1,
                        filter2 = filter2,
                        filter3 = filter3,
                        filter1Label = f1Label,
                        filter2Label = f2Label,
                        filter3Label = f3Label,
                        bankName = rowBankName,
                        status = rowStatus
                    )
                )
            }
        }
        return result
    }

    /**
     * Parses Question Bank CSV.
     * Columns: Id*, Question*, Opt1-4*, Ans*, Explanation, Filter1:label, Filter2:label, Filter3:label
     */
    fun parseQuestionBankCsv(content: String, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        val rows = parseCsv(content)
        if (rows.size < 2) return emptyList()
        return parseQuestionBankRows(rows, defaultBankName)
    }

    /**
     * Parses Question Bank JSON (Array of objects or Object with questions/questionBank key).
     */
    fun parseQuestionBankJson(jsonStr: String, defaultBankName: String = "General QB"): List<QuestionBankEntity> {
        val result = mutableListOf<QuestionBankEntity>()
        try {
            val trimmed = jsonStr.trim()
            val arr: org.json.JSONArray = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val root = org.json.JSONObject(trimmed)
                root.optJSONArray("questionBank")
                    ?: root.optJSONArray("questions")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("qb")
                    ?: org.json.JSONArray().put(root)
            } else {
                return emptyList()
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val qText = obj.optString("question", obj.optString("Question", obj.optString("q", obj.optString("prompt", "")))).trim()
                if (qText.isBlank()) continue

                var o1 = obj.optString("opt1", obj.optString("Opt1", obj.optString("option1", obj.optString("a", "")))).trim()
                var o2 = obj.optString("opt2", obj.optString("Opt2", obj.optString("option2", obj.optString("b", "")))).trim()
                var o3 = obj.optString("opt3", obj.optString("Opt3", obj.optString("option3", obj.optString("c", "")))).trim()
                var o4 = obj.optString("opt4", obj.optString("Opt4", obj.optString("option4", obj.optString("d", "")))).trim()
                var o5 = obj.optString("opt5", obj.optString("Opt5", obj.optString("option5", obj.optString("e", "")))).trim()
                var ans = obj.optString("answer", obj.optString("Answer", obj.optString("ans", obj.optString("correctAnswer", "")))).trim()

                val resolved = QuestionAnswerResolver.resolve(o1, o2, o3, o4, o5, ans)
                o1 = resolved.cleanOpt1
                o2 = resolved.cleanOpt2
                o3 = resolved.cleanOpt3
                o4 = resolved.cleanOpt4
                o5 = resolved.cleanOpt5
                ans = resolved.correctAnswer

                val exp = obj.optString("explanation", obj.optString("Explanation", obj.optString("exp", ""))).trim().ifEmpty { null }
                var f1: String? = null
                var f1Label = "Course"
                var f2: String? = null
                var f2Label = "Q.type"
                var f3: String? = null
                var f3Label = "Session"

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kClean = k.trim().replace("\uFEFF", "").replace("\u00A0", " ")
                    val kNorm = kClean.lowercase().replace(" ", "").replace("_", "").replace("-", "")

                    if (f1 == null && (kNorm.startsWith("filter1") || kNorm.startsWith("f1:") || kNorm == "f1" || kNorm.startsWith("course") || kNorm.startsWith("category") || kNorm.startsWith("topic") || kNorm.startsWith("subject"))) {
                        val v = obj.optString(k, "").trim()
                        if (v.isNotBlank()) f1 = v
                        val lbl = when {
                            kClean.contains(":") -> kClean.substringAfter(":").trim()
                            kClean.contains("：") -> kClean.substringAfter("：").trim()
                            kClean.contains("(") && kClean.contains(")") -> kClean.substringAfter("(").substringBefore(")").trim()
                            kNorm.startsWith("course") -> "Course"
                            kNorm.startsWith("topic") -> "Topic"
                            kNorm.startsWith("subject") -> "Subject"
                            kNorm.startsWith("category") -> "Category"
                            else -> "Course"
                        }
                        if (lbl.isNotBlank()) f1Label = lbl
                    } else if (f2 == null && (kNorm.startsWith("filter2") || kNorm.startsWith("f2:") || kNorm == "f2" || kNorm.startsWith("q.type") || kNorm.startsWith("qtype") || kNorm.startsWith("difficulty") || kNorm == "type")) {
                        val v = obj.optString(k, "").trim()
                        if (v.isNotBlank()) f2 = v
                        val lbl = when {
                            kClean.contains(":") -> kClean.substringAfter(":").trim()
                            kClean.contains("：") -> kClean.substringAfter("：").trim()
                            kClean.contains("(") && kClean.contains(")") -> kClean.substringAfter("(").substringBefore(")").trim()
                            kNorm.startsWith("q.type") || kNorm.startsWith("qtype") -> "Q.type"
                            kNorm.startsWith("difficulty") -> "Difficulty"
                            else -> "Q.type"
                        }
                        if (lbl.isNotBlank()) f2Label = lbl
                    } else if (f3 == null && (kNorm.startsWith("filter3") || kNorm.startsWith("f3:") || kNorm == "f3" || kNorm.startsWith("session") || kNorm.startsWith("source") || kNorm.startsWith("exam") || kNorm.startsWith("year"))) {
                        val v = obj.optString(k, "").trim()
                        if (v.isNotBlank()) f3 = v
                        val lbl = when {
                            kClean.contains(":") -> kClean.substringAfter(":").trim()
                            kClean.contains("：") -> kClean.substringAfter("：").trim()
                            kClean.contains("(") && kClean.contains(")") -> kClean.substringAfter("(").substringBefore(")").trim()
                            kNorm.startsWith("session") -> "Session"
                            kNorm.startsWith("source") -> "Source"
                            kNorm.startsWith("exam") -> "Exam"
                            kNorm.startsWith("year") -> "Year"
                            else -> "Session"
                        }
                        if (lbl.isNotBlank()) f3Label = lbl
                    }
                }
                if (obj.has("filter1Label") && obj.optString("filter1Label").isNotBlank()) f1Label = obj.optString("filter1Label")
                if (obj.has("filter2Label") && obj.optString("filter2Label").isNotBlank()) f2Label = obj.optString("filter2Label")
                if (obj.has("filter3Label") && obj.optString("filter3Label").isNotBlank()) f3Label = obj.optString("filter3Label")

                val bankName = obj.optString("bankName", obj.optString("bank_name", obj.optString("bank", defaultBankName))).ifBlank { defaultBankName }
                val status = obj.optString("status", "unrated").ifBlank { "unrated" }

                val id = obj.optString("id", obj.optString("qbId", "qb_${System.currentTimeMillis()}_$i"))
                val stemText = obj.optString("stem", obj.optString("Stem", obj.optString("passage", obj.optString("scenario", "")))).trim().ifEmpty { null }
                val stemId = obj.optString("stemId", obj.optString("stem_id", obj.optString("StemId", ""))).trim().ifEmpty { null }

                result.add(
                    QuestionBankEntity(
                        id = id,
                        question = qText,
                        opt1 = o1,
                        opt2 = o2,
                        opt3 = o3,
                        opt4 = o4,
                        opt5 = o5,
                        answer = ans,
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
     * Parses Question Bank from any content (auto-detecting JSON vs CSV).
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
     * Parses Question Bank from raw bytes, auto-detecting Excel (.xlsx), JSON, or CSV.
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
            try {
                val rows = parseXlsx(java.io.ByteArrayInputStream(bytes))
                val items = parseQuestionBankRows(rows, defaultBankName)
                if (items.isNotEmpty()) return items
            } catch (_: Exception) {}
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
     * Parses a structured JSON string containing courses, games, or question bank.
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
        } catch (e: Exception) {
            return emptyList()
        }

        for (i in 0 until root.length()) {
            val item = root.getJSONObject(i)
            val rowCourseId = item.optString("courseId", courseId).ifBlank { courseId }
            val id = item.optString("id", "word_${rowCourseId}_$i")
            val word = item.optString("word", item.optString("Word", ""))
            val meaning = item.optString("meaning", item.optString("Meaning", ""))
            val rawGroup = item.optString("group", item.optString("Group", "")).trim()
            val group = if (rawGroup.isNotBlank()) rawGroup else "1"
            val synonyms = item.optString("synonyms", item.optString("Synonyms", null))
            val extraWord = item.optString("extraWord", item.optString("ExtraWord", null))
            val example = item.optString("example", item.optString("Example", null))
            val mnemonic = item.optString("mnemonic", item.optString("Mnemonic", null))
            val status = item.optString("status", "unrated")

            if (word.isNotBlank()) {
                list.add(
                    VocabularyWordEntity(
                        id = id,
                        word = word,
                        meaning = meaning,
                        group = group,
                        synonyms = synonyms,
                        extraWord = extraWord,
                        example = example,
                        mnemonic = mnemonic,
                        status = status,
                        courseId = rowCourseId
                    )
                )
            }
        }
        return list
    }

    /**
     * RFC-4180 compliant CSV/TSV parser:
     * - Multi-line fields inside quotes (stems, passages, explanations)
     * - Escaped quotes ("")
     * - Automatic delimiter detection (comma ',', semicolon ';', tab '\t')
     * - Stripping UTF-8 BOM (\uFEFF) and zero-width spaces (\u200B)
     * - Handling \r\n and \n line endings
     */
    fun parseCsv(content: String): List<List<String>> {
        val cleanContent = content.replace("\uFEFF", "").replace("\u200B", "")
        if (cleanContent.isBlank()) return emptyList()

        // Auto-detect delimiter from the first row outside quotes
        val firstLine = cleanContent.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        var inQ = false
        var commas = 0
        var semicolons = 0
        var tabs = 0
        for (ch in firstLine) {
            if (ch == '\"') inQ = !inQ
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

        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0
        val len = cleanContent.length

        while (i < len) {
            val c = cleanContent[i]
            if (insideQuotes) {
                if (c == '\"') {
                    if (i + 1 < len && cleanContent[i + 1] == '\"') {
                        currentField.append('\"')
                        i++
                    } else {
                        insideQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '\"' -> {
                        insideQuotes = true
                    }
                    delimiter -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                    }
                    '\r' -> {
                        if (i + 1 < len && cleanContent[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    else -> {
                        currentField.append(c)
                    }
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(ArrayList(currentRow))
            }
        }

        return rows
    }

    /**
     * Standard CSV line parser handling quotes and commas
     */
    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                c == '\t' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }

    /**
     * Parses an OpenXML Excel file (.xlsx) from an InputStream into a list of row values.
     * Returns rows from the first sheet or combines rows.
     */
    fun parseXlsx(inputStream: InputStream): List<List<String>> {
        val sheets = parseMultiSheetXlsx(inputStream)
        return sheets.firstOrNull()?.rows ?: emptyList()
    }

    /**
     * Parses all sheets in an OpenXML Excel file (.xlsx) preserving sheet names.
     */
    fun parseMultiSheetXlsx(inputStream: InputStream): List<ParsedSheet> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith("sharedstrings.xml") ||
                    name.endsWith("workbook.xml") ||
                    name.endsWith("workbook.xml.rels") ||
                    (name.contains("worksheets/sheet") && name.endsWith(".xml"))
                ) {
                    entries[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries.entries.find { it.key.endsWith("sharedstrings.xml") }?.let {
            parseSharedStrings(ByteArrayInputStream(it.value))
        } ?: emptyList()

        val sheetList = parseWorkbookSheetNames(entries)
        val result = mutableListOf<ParsedSheet>()

        if (sheetList.isNotEmpty()) {
            for (sheetInfo in sheetList) {
                val targetClean = sheetInfo.filePath.lowercase().trimStart('/')
                val bytes = entries[targetClean]
                    ?: entries["xl/$targetClean"]
                    ?: entries.entries.find { it.key.endsWith(targetClean) }?.value
                if (bytes != null) {
                    val rows = parseSheetXml(ByteArrayInputStream(bytes), sharedStrings)
                    if (rows.isNotEmpty()) {
                        result.add(ParsedSheet(sheetInfo.name, rows))
                    }
                }
            }
        }

        // Fallback: If no sheets matched via workbook.xml, parse any sheet*.xml
        if (result.isEmpty()) {
            val sheetEntries = entries.entries
                .filter { it.key.contains("worksheets/sheet") && it.key.endsWith(".xml") }
                .sortedBy { it.key }
            for (e in sheetEntries) {
                val rows = parseSheetXml(ByteArrayInputStream(e.value), sharedStrings)
                if (rows.isNotEmpty()) {
                    val defaultName = e.key.substringAfterLast("/").removeSuffix(".xml")
                        .replaceFirstChar { it.uppercase() }
                    result.add(ParsedSheet(defaultName, rows))
                }
            }
        }

        return result
    }

    private data class SheetMeta(val name: String, val filePath: String)

    private fun parseWorkbookSheetNames(entries: Map<String, ByteArray>): List<SheetMeta> {
        val wbBytes = entries.entries.find { it.key.endsWith("workbook.xml") }?.value ?: return emptyList()
        val relsBytes = entries.entries.find { it.key.endsWith("workbook.xml.rels") }?.value

        val rIdToTarget = mutableMapOf<String, String>()
        if (relsBytes != null) {
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(ByteArrayInputStream(relsBytes), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.equals("relationship", ignoreCase = true)) {
                        val id = parser.getAttributeValue(null, "Id")
                        val target = parser.getAttributeValue(null, "Target")
                        if (id != null && target != null) {
                            rIdToTarget[id] = target
                        }
                    }
                    event = parser.next()
                }
            } catch (_: Exception) {}
        }

        val result = mutableListOf<SheetMeta>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(wbBytes), "UTF-8")
            var event = parser.eventType
            var sheetIndex = 1
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("sheet", ignoreCase = true)) {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet$sheetIndex"
                    val rId = parser.getAttributeValue(null, "r:id")
                        ?: parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: "rId$sheetIndex"
                    val target = rIdToTarget[rId] ?: "worksheets/sheet$sheetIndex.xml"
                    result.add(SheetMeta(name, target))
                    sheetIndex++
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val strings = mutableListOf<String>()
        var inSi = false
        var inT = false
        val currentSi = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("si", ignoreCase = true)) {
                        inSi = true
                        currentSi.clear()
                    } else if (name.equals("t", ignoreCase = true) && inSi) {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inT) {
                        currentSi.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("t", ignoreCase = true)) {
                        inT = false
                    } else if (name.equals("si", ignoreCase = true)) {
                        inSi = false
                        strings.add(currentSi.toString())
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheetXml(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val allRows = mutableListOf<List<String>>()
        val currentRow = mutableMapOf<Int, String>()
        var currentCol = 0
        var cellType: String? = null
        var inV = false
        var inT = false
        val vContent = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("row", ignoreCase = true)) {
                        currentRow.clear()
                        currentCol = 0
                    } else if (name.equals("c", ignoreCase = true)) {
                        cellType = parser.getAttributeValue(null, "t")
                        val ref = parser.getAttributeValue(null, "r")
                        if (ref != null) {
                            currentCol = columnRefToIndex(ref)
                        }
                        vContent.clear()
                    } else if (name.equals("v", ignoreCase = true)) {
                        inV = true
                        vContent.clear()
                    } else if (name.equals("t", ignoreCase = true)) {
                        inT = true
                        vContent.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inT) {
                        vContent.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("v", ignoreCase = true)) {
                        inV = false
                    } else if (name.equals("t", ignoreCase = true)) {
                        inT = false
                    } else if (name.equals("c", ignoreCase = true)) {
                        val rawVal = vContent.toString().trim()
                        val finalVal = when (cellType) {
                            "s" -> {
                                val idx = rawVal.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                            }
                            "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                            else -> rawVal
                        }
                        currentRow[currentCol] = finalVal
                        currentCol++
                    } else if (name.equals("row", ignoreCase = true)) {
                        if (currentRow.isNotEmpty()) {
                            val maxCol = currentRow.keys.maxOrNull() ?: -1
                            if (maxCol >= 0) {
                                val row = (0..maxCol).map { col -> currentRow[col] ?: "" }
                                if (row.any { it.isNotBlank() }) {
                                    allRows.add(row)
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return allRows
    }

    private fun columnRefToIndex(ref: String): Int {
        var col = 0
        for (c in ref.uppercase()) {
            if (c in 'A'..'Z') {
                col = col * 26 + (c - 'A' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }

    /**
     * Converts a table of rows into CSV format.
     */
    fun rowsToCsv(rows: List<List<String>>): String {
        return rows.joinToString("\n") { row ->
            row.joinToString(",") { cell ->
                if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else {
                    cell
                }
            }
        }
    }
}
