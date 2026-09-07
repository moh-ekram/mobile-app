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
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val headerLine = lines[0]
        val headers = parseCsvLine(headerLine).map { it.trim() }

        // Map column indices
        var idIndex = -1
        var groupIndex = -1
        var wordIndex = -1
        var meaningIndex = -1
        var exampleIndex = -1
        var synonymsIndex = -1
        var extraIndex = -1
        var mnemonicIndex = -1

        val customPlacesMap = mutableMapOf<Int, String>()

        headers.forEachIndexed { index, rawHeader ->
            val lower = rawHeader.lowercase()
            val cleanLabel = if (rawHeader.contains(":")) rawHeader.substringAfter(":").trim() else rawHeader.trim()

            when {
                lower == "id" -> idIndex = index
                lower == "group" -> groupIndex = index
                lower.startsWith("place1") || (wordIndex == -1 && (lower.contains("word") || lower == "term" || lower == "vocabulary")) -> {
                    wordIndex = index
                }
                lower.startsWith("place2") || (meaningIndex == -1 && (lower.contains("meaning") || lower.contains("definition") || lower.contains("bangla") || lower.contains("অর্থ"))) -> {
                    meaningIndex = index
                    customPlacesMap[index] = cleanLabel
                }
                else -> {
                    customPlacesMap[index] = cleanLabel
                    val labelLower = cleanLabel.lowercase()
                    when {
                        labelLower.contains("word") && wordIndex == -1 -> wordIndex = index
                        labelLower.contains("meaning") || labelLower.contains("bangla") || labelLower.contains("অর্থ") -> {
                            if (meaningIndex == -1) meaningIndex = index
                        }
                        labelLower.contains("example") || labelLower.contains("sentence") -> exampleIndex = index
                        labelLower.contains("synonym") -> synonymsIndex = index
                        labelLower.contains("extra") || labelLower.contains("derivative") || labelLower.contains("form") -> extraIndex = index
                        labelLower.contains("mnemonic") || labelLower.contains("trick") -> mnemonicIndex = index
                    }
                }
            }
        }

        // Fallbacks if not recognized
        if (wordIndex == -1 && headers.isNotEmpty()) {
            wordIndex = if (headers.size > 2 && (idIndex != -1 || groupIndex != -1)) 2 else 0
        }
        if (meaningIndex == -1 && headers.size > 1) {
            meaningIndex = if (wordIndex == 0) 1 else if (wordIndex + 1 < headers.size) wordIndex + 1 else -1
            if (meaningIndex != -1 && !customPlacesMap.containsKey(meaningIndex)) {
                customPlacesMap[meaningIndex] = headers[meaningIndex]
            }
        }

        val result = mutableListOf<VocabularyWordEntity>()
        for (i in 1 until lines.size) {
            val values = parseCsvLine(lines[i])
            if (values.isEmpty()) continue

            val id = if (idIndex in values.indices && values[idIndex].isNotBlank()) values[idIndex].trim() else "word_${courseId}_$i"
            val group = if (groupIndex in values.indices) values[groupIndex].trim().toIntOrNull() ?: 1 else 1
            val word = if (wordIndex in values.indices) values[wordIndex].trim() else "Word $i"
            val meaning = if (meaningIndex in values.indices) values[meaningIndex].trim() else ""
            val example = if (exampleIndex in values.indices && values[exampleIndex].isNotBlank()) values[exampleIndex].trim() else null
            val synonyms = if (synonymsIndex in values.indices && values[synonymsIndex].isNotBlank()) values[synonymsIndex].trim() else null
            val extraWord = if (extraIndex in values.indices && values[extraIndex].isNotBlank()) values[extraIndex].trim() else null
            val mnemonic = if (mnemonicIndex in values.indices && values[mnemonicIndex].isNotBlank()) values[mnemonicIndex].trim() else null

            // Record custom places exactly as present in this row
            val placeJsonObj = JSONObject()
            customPlacesMap.forEach { (colIdx, label) ->
                if (colIdx in values.indices && values[colIdx].isNotBlank()) {
                    placeJsonObj.put(label, values[colIdx].trim())
                }
            }

            if (word.isNotBlank()) {
                result.add(
                    VocabularyWordEntity(
                        id = id,
                        word = word,
                        meaning = meaning,
                        group = group,
                        synonyms = synonyms,
                        extraWord = extraWord,
                        example = example,
                        mnemonic = mnemonic,
                        customPlacesJson = if (placeJsonObj.length() > 0) placeJsonObj.toString() else null,
                        courseId = courseId
                    )
                )
            }
        }
        return result
    }

    /**
     * Parses Game/Practice CSV/Excel.
     * Columns: Id*, Question*, Opt1*, Opt2*, Opt3*, Opt4*, Ans*, Explanation
     * If Ans is missing or empty, detects which Opt has '#'
     */
    fun parseGameCsv(content: String, defaultSheetType: String = "practice"): List<GamePracticeEntity> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val headers = parseCsvLine(lines[0]).map { it.trim().lowercase() }
        val idIdx = headers.indexOfFirst { it == "id" || it == "id*" }
        val qIdx = headers.indexOfFirst { it.contains("question") }
        val opt1Idx = headers.indexOfFirst { it.contains("opt1") }
        val opt2Idx = headers.indexOfFirst { it.contains("opt2") }
        val opt3Idx = headers.indexOfFirst { it.contains("opt3") }
        val opt4Idx = headers.indexOfFirst { it.contains("opt4") }
        val ansIdx = headers.indexOfFirst { it.contains("ans") }
        val expIdx = headers.indexOfFirst { it.contains("explanation") }

        val result = mutableListOf<GamePracticeEntity>()

        for (i in 1 until lines.size) {
            val values = parseCsvLine(lines[i])
            if (values.size <= maxOf(opt1Idx, qIdx)) continue

            val id = if (idIdx in values.indices && values[idIdx].isNotBlank()) values[idIdx].trim() else "game_$i"
            val question = if (qIdx in values.indices) values[qIdx].trim() else ""
            var opt1 = if (opt1Idx in values.indices) values[opt1Idx].trim() else ""
            var opt2 = if (opt2Idx in values.indices) values[opt2Idx].trim() else ""
            var opt3 = if (opt3Idx in values.indices) values[opt3Idx].trim() else ""
            var opt4 = if (opt4Idx in values.indices) values[opt4Idx].trim() else ""
            var answer = if (ansIdx in values.indices) values[ansIdx].trim() else ""
            val explanation = if (expIdx in values.indices) values[expIdx].trim() else null

            // If answer is empty, check which option has '#'
            if (answer.isBlank()) {
                when {
                    opt1.contains("#") -> {
                        opt1 = opt1.replace("#", "").trim()
                        answer = opt1
                    }
                    opt2.contains("#") -> {
                        opt2 = opt2.replace("#", "").trim()
                        answer = opt2
                    }
                    opt3.contains("#") -> {
                        opt3 = opt3.replace("#", "").trim()
                        answer = opt3
                    }
                    opt4.contains("#") -> {
                        opt4 = opt4.replace("#", "").trim()
                        answer = opt4
                    }
                    else -> answer = opt1
                }
            } else {
                // If answer was specified e.g. "Opt1" or "1" or exact text
                if (answer.equals("Opt1", true) || answer == "1") answer = opt1
                else if (answer.equals("Opt2", true) || answer == "2") answer = opt2
                else if (answer.equals("Opt3", true) || answer == "3") answer = opt3
                else if (answer.equals("Opt4", true) || answer == "4") answer = opt4
            }

            if (question.isNotBlank()) {
                result.add(
                    GamePracticeEntity(
                        id = id,
                        sheetType = defaultSheetType,
                        question = question,
                        opt1 = opt1,
                        opt2 = opt2,
                        opt3 = opt3,
                        opt4 = opt4,
                        answer = answer,
                        explanation = explanation
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
    fun parseQuestionBankCsv(content: String): List<QuestionBankEntity> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val rawHeaders = parseCsvLine(lines[0]).map { it.trim() }
        val headers = rawHeaders.map { it.lowercase() }

        val idIdx = headers.indexOfFirst { it == "id" || it == "id*" }
        val qIdx = headers.indexOfFirst { it.contains("question") }
        val opt1Idx = headers.indexOfFirst { it.contains("opt1") }
        val opt2Idx = headers.indexOfFirst { it.contains("opt2") }
        val opt3Idx = headers.indexOfFirst { it.contains("opt3") }
        val opt4Idx = headers.indexOfFirst { it.contains("opt4") }
        val ansIdx = headers.indexOfFirst { it.contains("ans") }
        val expIdx = headers.indexOfFirst { it.contains("explanation") }

        // Filter columns with labels
        var f1Idx = -1
        var f2Idx = -1
        var f3Idx = -1
        var f1Label = "Category"
        var f2Label = "Difficulty"
        var f3Label = "Source"

        rawHeaders.forEachIndexed { index, header ->
            val lower = header.lowercase()
            if (lower.startsWith("filter1") || lower.startsWith("fiter1")) {
                f1Idx = index
                val p = header.split(":", limit = 2)
                if (p.size > 1) f1Label = p[1].trim()
            } else if (lower.startsWith("filter2") || lower.startsWith("fiter2")) {
                f2Idx = index
                val p = header.split(":", limit = 2)
                if (p.size > 1) f2Label = p[1].trim()
            } else if (lower.startsWith("filter3") || lower.startsWith("fiter3")) {
                f3Idx = index
                val p = header.split(":", limit = 2)
                if (p.size > 1) f3Label = p[1].trim()
            }
        }

        val result = mutableListOf<QuestionBankEntity>()
        for (i in 1 until lines.size) {
            val values = parseCsvLine(lines[i])
            if (values.size <= maxOf(opt1Idx, qIdx)) continue

            val id = if (idIdx in values.indices && values[idIdx].isNotBlank()) values[idIdx].trim() else "qb_$i"
            val question = if (qIdx in values.indices) values[qIdx].trim() else ""
            var opt1 = if (opt1Idx in values.indices) values[opt1Idx].trim() else ""
            var opt2 = if (opt2Idx in values.indices) values[opt2Idx].trim() else ""
            var opt3 = if (opt3Idx in values.indices) values[opt3Idx].trim() else ""
            var opt4 = if (opt4Idx in values.indices) values[opt4Idx].trim() else ""
            var answer = if (ansIdx in values.indices) values[ansIdx].trim() else ""
            val explanation = if (expIdx in values.indices) values[expIdx].trim() else null

            // Check for # in options
            if (answer.isBlank()) {
                when {
                    opt1.contains("#") -> { opt1 = opt1.replace("#", "").trim(); answer = opt1 }
                    opt2.contains("#") -> { opt2 = opt2.replace("#", "").trim(); answer = opt2 }
                    opt3.contains("#") -> { opt3 = opt3.replace("#", "").trim(); answer = opt3 }
                    opt4.contains("#") -> { opt4 = opt4.replace("#", "").trim(); answer = opt4 }
                    else -> answer = opt1
                }
            }

            val filter1 = if (f1Idx in values.indices) values[f1Idx].trim() else null
            val filter2 = if (f2Idx in values.indices) values[f2Idx].trim() else null
            val filter3 = if (f3Idx in values.indices) values[f3Idx].trim() else null

            if (question.isNotBlank()) {
                result.add(
                    QuestionBankEntity(
                        id = id,
                        question = question,
                        opt1 = opt1,
                        opt2 = opt2,
                        opt3 = opt3,
                        opt4 = opt4,
                        answer = answer,
                        explanation = explanation,
                        filter1 = filter1,
                        filter2 = filter2,
                        filter3 = filter3,
                        filter1Label = f1Label,
                        filter2Label = f2Label,
                        filter3Label = f3Label
                    )
                )
            }
        }
        return result
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
            val id = item.optString("id", "word_${courseId}_$i")
            val word = item.optString("word", item.optString("Word", ""))
            val meaning = item.optString("meaning", item.optString("Meaning", ""))
            val group = item.optInt("group", item.optInt("Group", 1))
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
                        courseId = courseId
                    )
                )
            }
        }
        return list
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
     */
    fun parseXlsx(inputStream: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith("sharedstrings.xml") || (name.contains("worksheets/sheet") && name.endsWith(".xml"))) {
                    entries[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries.entries.find { it.key.endsWith("sharedstrings.xml") }?.let {
            parseSharedStrings(ByteArrayInputStream(it.value))
        } ?: emptyList()

        // Prioritize sheet1.xml or first found worksheet
        val sheetBytes = entries.entries.find { it.key.endsWith("sheet1.xml") }?.value
            ?: entries.entries.find { it.key.contains("worksheets/sheet") && it.key.endsWith(".xml") }?.value
            ?: return emptyList()

        return parseSheetXml(ByteArrayInputStream(sheetBytes), sharedStrings)
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
