package com.example

import com.example.data.parser.FileParsers
import com.example.data.parser.ImportIssueType
import com.example.data.parser.ImportNormalization
import com.example.data.parser.ParsedSheetData
import com.example.data.parser.QuestionAnswerResolver
import com.example.data.parser.StrictAnswerResult
import com.example.data.parser.TabularRow
import com.example.data.parser.WorkbookReadException
import com.example.data.parser.XlsxWorkbookReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileParsersImportTest {

    private fun buildXlsxBytes(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun normalizeId_trimsWhitespaceAndWholeDecimalsWithoutBreakingDecimalsOrPrefixes() {
        assertEquals("1", ImportNormalization.normalizeId(" 1.0 "))
        assertEquals("42", ImportNormalization.normalizeId("42.00"))
        assertEquals("akr_10", ImportNormalization.normalizeId("\uFEFF  akr_10 \u200B"))
        assertEquals("1.5", ImportNormalization.normalizeId("1.5"))
        assertEquals("", ImportNormalization.normalizeId("   "))
    }

    @Test
    fun parseMultiSheetXlsx_skipsHiddenAndVeryHiddenSheetsAndReadsMergedDatesAndRichText() {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="VisibleBank" sheetId="1" r:id="rId1"/>
                <sheet name="SecretSheet" sheetId="2" state="hidden" r:id="rId2"/>
                <sheet name="VerySecret" sheetId="3" state="veryHidden" r:id="rId3"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
            </Relationships>
        """.trimIndent()

        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><r><t xml:space="preserve">Hello </t></r><r><t>World</t></r><rPh sb="0" eb="5"><t>IGNORE_RUBY</t></rPh></si>
              <si><t>MergedGroup</t></si>
            </sst>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <cellXfs count="2">
                <xf numFmtId="0"/>
                <xf numFmtId="14"/>
              </cellXfs>
            </styleSheet>
        """.trimIndent()

        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Title Banner Row</t></is></c></row>
                <row r="2">
                  <c r="A2" t="inlineStr"><is><t>Id</t></is></c>
                  <c r="B2" t="inlineStr"><is><t>place1: Term</t></is></c>
                  <c r="C2" t="inlineStr"><is><t>place3: Date</t></is></c>
                  <c r="D2" t="inlineStr"><is><t>group1: Section</t></is></c>
                </row>
                <row r="3">
                  <c r="A3"><v>1.0</v></c>
                  <c r="B3" t="s"><v>0</v></c>
                  <c r="C3" s="1"><v>43831</v></c>
                  <c r="D3" t="s"><v>1</v></c>
                </row>
                <row r="4">
                  <c r="A4"><v>2</v></c>
                  <c r="B4" t="inlineStr"><is><r><t>Inline </t></r><r><t>Text</t></r></is></c>
                  <c r="C4" s="1"><v>43832</v></c>
                </row>
              </sheetData>
              <mergeCells count="1">
                <mergeCell ref="D3:D4"/>
              </mergeCells>
            </worksheet>
        """.trimIndent()

        val hiddenSheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Id</t></is></c></row></sheetData>
            </worksheet>
        """.trimIndent()

        val bytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to workbookXml,
                "xl/_rels/workbook.xml.rels" to relsXml,
                "xl/sharedStrings.xml" to sharedStringsXml,
                "xl/styles.xml" to stylesXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml,
                "xl/worksheets/sheet2.xml" to hiddenSheetXml,
                "xl/worksheets/sheet3.xml" to hiddenSheetXml
            )
        )

        val sheets = XlsxWorkbookReader.parseMultiSheetXlsx(ByteArrayInputStream(bytes))
        assertEquals(1, sheets.size)
        assertEquals("VisibleBank", sheets[0].sheetName)

        val parsedCourse = FileParsers.parseCourseSheetStrict(sheets[0], "c1")
        assertEquals(2, parsedCourse.headerRowNumber)
        assertEquals(2, parsedCourse.validEntries.size)

        val first = parsedCourse.validEntries[0]
        assertEquals("1", first.word.id)
        assertEquals("Hello World", first.placeValues[1])
        assertFalse(first.placeValues[1]!!.contains("IGNORE_RUBY"))
        assertEquals("2020-01-01", first.placeValues[3])
        assertEquals("MergedGroup", first.groupValues[1])

        val second = parsedCourse.validEntries[1]
        assertEquals("2", second.word.id)
        assertEquals("Inline Text", second.placeValues[1])
        assertEquals("2020-01-02", second.placeValues[3])
        assertEquals("MergedGroup", second.groupValues[1])
        // No missing-column substitution for place2
        assertEquals("", second.word.meaning)
        assertNull(second.placeValues[2])
    }

    @Test(expected = WorkbookReadException::class)
    fun parseMultiSheetXlsx_throwsRealExceptionOnEncryptedOrCorruptWorkbook() {
        val bytes = buildXlsxBytes(
            mapOf("EncryptedPackage" to "encrypted-content")
        )
        XlsxWorkbookReader.parseMultiSheetXlsx(ByteArrayInputStream(bytes))
    }

    @Test
    fun parseCourseSheetStrict_skipsBlankAndDuplicateIdsAndExtractsLabelsWithoutWordGuessing() {
        val rows = listOf(
            TabularRow(1, listOf("Course Export Banner")),
            TabularRow(2, listOf("Id", "place3: Example", "place5: Note", "group1: Unit", "group2: Difficulty")),
            TabularRow(3, listOf(" 10.0 ", "Ex 1", "Note 1", " Unit 1 ", "Hard")),
            TabularRow(4, listOf("   ", "Missing ID", "Should skip", "Unit 1", "Easy")),
            TabularRow(5, listOf("10", "Duplicate ID", "Should skip", "Unit 2", "Easy")),
            TabularRow(6, listOf("11", "Ex 2", "", "", "Medium"))
        )
        val sheet = ParsedSheetData("VocabSheet", rows)
        val result = FileParsers.parseCourseSheetStrict(sheet, "c_vocab")

        assertEquals(2, result.headerRowNumber)
        assertEquals(mapOf(3 to "Example", 5 to "Note"), result.placeLabels)
        assertEquals(mapOf(1 to "Unit", 2 to "Difficulty"), result.groupLabels)

        assertEquals(2, result.validEntries.size)
        assertEquals("10", result.validEntries[0].word.id)
        // Must NOT copy place3 into word or meaning
        assertEquals("", result.validEntries[0].word.word)
        assertEquals("", result.validEntries[0].word.meaning)
        assertEquals("Unit 1", result.validEntries[0].groupValues[1])
        assertEquals("", result.validEntries[1].groupValues[1])

        assertEquals(2, result.skippedIssues.size)
        assertEquals(ImportIssueType.SKIPPED_BLANK_ID, result.skippedIssues[0].type)
        assertEquals(4, result.skippedIssues[0].rowNumber)
        assertEquals(ImportIssueType.SKIPPED_DUPLICATE_ID, result.skippedIssues[1].type)
        assertEquals(5, result.skippedIssues[1].rowNumber)
    }

    @Test
    fun parseQuestionBankSheetStrict_handlesTwoPassStemStrictHeadersAndStrictAE() {
        val rows = listOf(
            TabularRow(1, listOf("Banner Title")),
            TabularRow(
                2,
                listOf(
                    "Id",
                    "Question",
                    "Option A",
                    "Option B",
                    "Choice C",
                    "(D)",
                    "Opt5",
                    "Translation",
                    "Ans",
                    "system",
                    "Stem",
                    "Filter1: Subject",
                    "Filter2: Exam",
                    "Filter3: Year"
                )
            ),
            // Row 3 references stem1 BEFORE stem1 is defined on row 4!
            TabularRow(
                3,
                listOf(
                    "q1",
                    "What is C#?",
                    "A #1 language",
                    "Coffee",
                    "Snake",
                    "Gem",
                    "",
                    "WrongAnsCol",
                    "A",
                    "ShouldIgnoreSystem",
                    "stem1",
                    "CS",
                    "BCS",
                    "2024"
                )
            ),
            // Row 4 defines stem1 and uses '#' inside an option (must NOT mark answer or be stripped!)
            TabularRow(
                4,
                listOf(
                    "q2",
                    "Which is B?",
                    "#First",
                    "Second#",
                    "Third",
                    "Fourth",
                    "Fifth",
                    "IgnoreTranslation",
                    "E",
                    "IgnoreSystem",
                    "stem1: Read this shared passage carefully.",
                    "CS",
                    "BCS",
                    "2024"
                )
            ),
            // Row 5 has Ans=E while Opt5 is blank -> NeedsReview!
            TabularRow(
                5,
                listOf(
                    "q3",
                    "Bad empty option ref",
                    "OptA",
                    "OptB",
                    "OptC",
                    "OptD",
                    "",
                    "B",
                    "E",
                    "",
                    "",
                    "CS",
                    "BCS",
                    "2024"
                )
            ),
            // Row 6 has full text answer instead of A-E -> NeedsReview!
            TabularRow(
                6,
                listOf(
                    "q4",
                    "Text answer rejected",
                    "OptA",
                    "OptB",
                    "OptC",
                    "OptD",
                    "",
                    "",
                    "OptA",
                    "",
                    "",
                    "",
                    "",
                    ""
                )
            ),
            // Row 7 has blank Id -> Skipped
            TabularRow(
                7,
                listOf("", "No ID question", "A1", "B1", "C1", "D1", "", "", "A", "", "", "", "", "")
            )
        )

        val result = FileParsers.parseQuestionBankSheetStrict(ParsedSheetData("Biology 101", rows), "Biology 101")

        assertEquals("Subject", result.filter1Label)
        assertEquals("Exam", result.filter2Label)
        assertEquals("Year", result.filter3Label)

        assertEquals(2, result.validItems.size)
        val q1 = result.validItems[0]
        assertEquals("q1", q1.id)
        assertEquals("Biology 101", q1.bankName)
        // Forward stem reference resolved from row 4!
        assertEquals("stem1", q1.stemId)
        assertEquals("Read this shared passage carefully.", q1.stem)
        // '#' must NOT be stripped from options
        assertEquals("A #1 language", q1.opt1)
        assertEquals("A #1 language", q1.answer)

        val q2 = result.validItems[1]
        assertEquals("q2", q2.id)
        assertEquals("#First", q2.opt1)
        assertEquals("Second#", q2.opt2)
        assertEquals("Fifth", q2.answer)

        assertEquals(2, result.needsReviewIssues.size)
        assertEquals(ImportIssueType.NEEDS_REVIEW_EMPTY_OPTION, result.needsReviewIssues[0].type)
        assertEquals(5, result.needsReviewIssues[0].rowNumber)
        assertEquals(ImportIssueType.NEEDS_REVIEW_INVALID_ANSWER, result.needsReviewIssues[1].type)
        assertEquals(6, result.needsReviewIssues[1].rowNumber)

        assertEquals(1, result.skippedIssues.size)
        assertEquals(ImportIssueType.SKIPPED_BLANK_ID, result.skippedIssues[0].type)
        assertEquals(7, result.skippedIssues[0].rowNumber)
    }

    @Test
    fun questionAnswerResolver_doesNotStripHashAndRejectsInvalidAnswers() {
        val invalidHash = QuestionAnswerResolver.resolveStrictImport("#Alpha", "Beta", "Gamma", "Delta", "", "")
        assertTrue(invalidHash is StrictAnswerResult.NeedsReview)

        val validWithHashText = QuestionAnswerResolver.resolveStrictImport("#Alpha", "Beta", "Gamma", "Delta", "", "A")
        assertTrue(validWithHashText is StrictAnswerResult.Valid)
        assertEquals("#Alpha", (validWithHashText as StrictAnswerResult.Valid).selectedOptionText)

        // Legacy stored resolution must not silently fall back to Opt1 when unmatched
        val unresolvedStored = QuestionAnswerResolver.resolve("One", "Two", "Three", "Four", "", "InvalidStored")
        assertEquals(-1, unresolvedStored.correctOptionIndex)
        assertEquals("One", unresolvedStored.cleanOpt1)
    }
}
