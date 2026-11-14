package com.example

import com.example.data.model.BookEntity
import com.example.data.model.BookPageEntity
import com.example.data.search.BookSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchEngineTest {

    private fun sampleBook(id: String = "book_1", title: String = "Bangladesh Affairs & Science") = BookEntity(
        id = id,
        title = title,
        author = "Editorial Board",
        subject = "BCS Preparation",
        description = "Comprehensive offline reference",
        format = "PDF",
        fileName = "bcs_guide.pdf",
        localFilePath = "/tmp/bcs_guide.pdf",
        totalPages = 3
    )

    @Test
    fun `mcq profile resolves option letter and ranks exact concept plus answer page first`() {
        val book = sampleBook()
        val booksById = mapOf(book.id to book)
        val pages = listOf(
            BookPageEntity(
                id = "${book.id}_p1",
                bookId = book.id,
                pageNumber = 1,
                chapterTitle = "Chapter 1: Geography",
                textContent = "The Padma is one of the major rivers flowing through western Bangladesh and merging with the Jamuna.",
                wordCount = 17
            ),
            BookPageEntity(
                id = "${book.id}_p2",
                bookId = book.id,
                pageNumber = 2,
                chapterTitle = "Chapter 2: Constitutional Amendments",
                textContent = "The Thirteenth Amendment to the Constitution of Bangladesh introduced the Caretaker Government system in 1996 to oversee parliamentary elections.",
                wordCount = 20
            ),
            BookPageEntity(
                id = "${book.id}_p3",
                bookId = book.id,
                pageNumber = 3,
                chapterTitle = "Chapter 3: Optics",
                textContent = "Refraction occurs when light waves change speed and bend upon entering a denser optical medium such as glass.",
                wordCount = 19
            )
        )

        val profile = BookSearchEngine.buildMcqSearchProfile(
            question = "Which constitutional amendment introduced the Caretaker Government system in Bangladesh?",
            options = listOf("Twelfth Amendment", "Thirteenth Amendment", "Fifteenth Amendment", "Eighth Amendment"),
            rawAnswer = "B"
        )

        assertEquals("Thirteenth Amendment", profile.resolvedAnswerText)

        val results = BookSearchEngine.rankPagesForMcq(
            pages = pages,
            booksById = booksById,
            profile = profile
        )

        assertTrue("Expected relevant page to match", results.isNotEmpty())
        assertEquals(2, results.first().pageNumber)
        assertEquals("Chapter 2: Constitutional Amendments", results.first().chapterTitle)
        assertTrue(results.first().passageSnippet.contains("Caretaker Government"))
    }

    @Test
    fun `mcq search supports bengali text inflections and excludes unrelated books`() {
        val book = sampleBook(id = "bn_book", title = "বাংলা ব্যাকরণ ও সাহিত্য")
        val booksById = mapOf(book.id to book)
        val pages = listOf(
            BookPageEntity(
                id = "${book.id}_p10",
                bookId = book.id,
                pageNumber = 10,
                chapterTitle = "সমাস",
                textContent = "বহুব্রীহি সমাসে পূর্বপদ ও পরপদ কোনোটির অর্থ প্রাধান্য না পেয়ে অন্য কোনো নতুন অর্থ প্রকাশ পায়। যেমন: নীলকণ্ঠ।",
                wordCount = 20
            ),
            BookPageEntity(
                id = "${book.id}_p11",
                bookId = book.id,
                pageNumber = 11,
                chapterTitle = "সন্ধি",
                textContent = "স্বরসন্ধি হলো দুটি স্বরধ্বনির মিলন। যেমন: বিদ্যা + আলয় = বিদ্যালয়।",
                wordCount = 12
            )
        )

        val profile = BookSearchEngine.buildMcqSearchProfile(
            question = "কোন সমাসে পূর্বপদ ও পরপদের অর্থ প্রাধান্য না পেয়ে নতুন অর্থ প্রকাশ পায়?",
            options = listOf("দ্বন্দ্ব সমাস", "বহুব্রীহি সমাস", "তৎপুরুষ সমাস", "কর্মধারয় সমাস"),
            rawAnswer = "খ"
        )

        assertEquals("বহুব্রীহি সমাস", profile.resolvedAnswerText)

        val results = BookSearchEngine.rankPagesForMcq(
            pages = pages,
            booksById = booksById,
            profile = profile
        )

        assertTrue(results.isNotEmpty())
        assertEquals(10, results.first().pageNumber)
        assertTrue(results.none { it.pageNumber == 11 })
    }

    @Test
    fun `unrelated books return empty matches instead of fabricating explanations`() {
        val book = sampleBook()
        val booksById = mapOf(book.id to book)
        val pages = listOf(
            BookPageEntity(
                id = "${book.id}_p1",
                bookId = book.id,
                pageNumber = 1,
                chapterTitle = "Agriculture",
                textContent = "Rice cultivation requires alluvial soil and seasonal monsoon rainfall across river deltas.",
                wordCount = 13
            )
        )

        val profile = BookSearchEngine.buildMcqSearchProfile(
            question = "What is the powerhouse organelle responsible for ATP synthesis in eukaryotic cells?",
            options = listOf("Ribosome", "Mitochondria", "Golgi Apparatus", "Lysosome"),
            rawAnswer = "Mitochondria"
        )

        val results = BookSearchEngine.rankPagesForMcq(
            pages = pages,
            booksById = booksById,
            profile = profile
        )

        assertTrue("Unrelated pages must not produce hallucinated matches", results.isEmpty())
    }

    @Test
    fun `extractBookLinkIds parses id-xxx tags cleanly and ignores false substrings`() {
        val tagged1 = sampleBook(id = "b1").copy(fileName = "id-akr_Bangla_Notes.docx")
        val tagged2 = sampleBook(id = "b2").copy(fileName = "General Knowledge [ID-BCS46].pdf")
        val tagged3 = sampleBook(id = "b3").copy(fileName = "Physics_id-phy2.pdf")
        val untaggedWord = sampleBook(id = "b4").copy(fileName = "android-developer-guide.pdf")
        val untaggedPlain = sampleBook(id = "b5").copy(fileName = "PlainNotes.docx")

        assertEquals(setOf("akr"), BookSearchEngine.extractBookLinkIds(tagged1))
        assertEquals(setOf("bcs46"), BookSearchEngine.extractBookLinkIds(tagged2))
        assertEquals(setOf("phy2"), BookSearchEngine.extractBookLinkIds(tagged3))
        assertTrue(BookSearchEngine.extractBookLinkIds(untaggedWord).isEmpty())
        assertTrue(BookSearchEngine.extractBookLinkIds(untaggedPlain).isEmpty())
    }

    @Test
    fun `filterBooksForQuestion strictly links only matching id-xxx books to QB id prefix`() {
        val akrBook = sampleBook(id = "b_akr").copy(fileName = "Bangladesh_id-akr.pdf")
        val shortTagBook = sampleBook(id = "b_ak").copy(fileName = "Short_id-ak.pdf")
        val phyBook = sampleBook(id = "b_phy").copy(fileName = "Physics_id-phy.docx")
        val untaggedBook = sampleBook(id = "b_none").copy(fileName = "Untagged_Reference.pdf")
        val library = listOf(akrBook, shortTagBook, phyBook, untaggedBook)

        val matchedForAkr1 = BookSearchEngine.filterBooksForQuestion(library, "akr_1")
        assertEquals(listOf("b_akr"), matchedForAkr1.map { it.id })

        val matchedForAkr2 = BookSearchEngine.filterBooksForQuestion(library, "AKR_2")
        assertEquals(listOf("b_akr"), matchedForAkr2.map { it.id })

        val matchedForAkrNum = BookSearchEngine.filterBooksForQuestion(library, "akr12")
        assertEquals(listOf("b_akr"), matchedForAkrNum.map { it.id })

        // Question with non-matching prefix or generated id must match zero books
        assertTrue(BookSearchEngine.filterBooksForQuestion(library, "bio_1").isEmpty())
        assertTrue(BookSearchEngine.filterBooksForQuestion(library, "akram_1").isEmpty())
        assertTrue(BookSearchEngine.filterBooksForQuestion(library, "qb_general_r1").isEmpty())
    }

    @Test
    fun `extracts winning paragraph and single highest matching bestLine inside it`() {
        val book = sampleBook(id = "multi_para", title = "Constitutional History")
        val booksById = mapOf(book.id to book)
        val multiParaText = """
            The Constituent Assembly began drafting national charters immediately after independence. Many committees reviewed administrative frameworks across districts.

            Constitutional evolution reshaped parliamentary governance over multiple decades. The Thirteenth Amendment introduced the Caretaker Government system in 1996 to oversee national elections. Subsequent debates focused on judicial review and transparency.

            Local municipal councils oversee urban drainage and road maintenance budgets every fiscal year.
        """.trimIndent()

        val pages = listOf(
            BookPageEntity(
                id = "${book.id}_p5",
                bookId = book.id,
                pageNumber = 5,
                chapterTitle = "Amendments",
                textContent = multiParaText,
                wordCount = 65
            )
        )

        val profile = BookSearchEngine.buildMcqSearchProfile(
            question = "Which amendment introduced the Caretaker Government system in 1996?",
            options = listOf("Twelfth", "Thirteenth Amendment", "Fourteenth", "Fifteenth"),
            rawAnswer = "B"
        )

        val results = BookSearchEngine.rankPagesForMcq(pages, booksById, profile)
        assertTrue(results.isNotEmpty())
        val top = results.first()
        assertEquals(
            "The Thirteenth Amendment introduced the Caretaker Government system in 1996 to oversee national elections.",
            top.bestLine
        )
        assertTrue(top.passageSnippet.contains("Constitutional evolution reshaped"))
        assertTrue(top.passageSnippet.contains(top.bestLine))
        assertTrue(!top.passageSnippet.contains("municipal councils"))
    }

    @Test
    fun `findBestMatchingLine picks strongest bengali sentence inside paragraph`() {
        val para = "বাংলা সাহিত্যের প্রাচীনতম নিদর্শন হলো চর্যাপদ। এটি ১৯০৭ সালে নেপালের রাজদরবার থেকে হরপ্রসাদ শাস্ত্রী আবিষ্কার করেন। পরবর্তীতে বাংলা গদ্যের বিকাশ ঘটে।"
        val line = BookSearchEngine.findBestMatchingLine(para, "হরপ্রসাদ শাস্ত্রী নেপাল আবিষ্কার")
        assertEquals("এটি ১৯০৭ সালে নেপালের রাজদরবার থেকে হরপ্রসাদ শাস্ত্রী আবিষ্কার করেন।", line)
    }
}
