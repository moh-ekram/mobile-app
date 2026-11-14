package com.example.data.search

import com.example.data.local.AppDatabase
import com.example.data.local.BookFtsHelper
import com.example.data.model.BookEntity
import com.example.data.model.BookPageEntity
import com.example.data.model.BookPassageMatch
import com.example.ui.screens.QuizQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Pure offline generalized search & ranking engine over imported books.
 * Combines SQLite FTS5/BM25 candidate retrieval with Okapi BM25, exact/sub-phrase matching,
 * proximity clustering (question + options + correct answer), and fuzzy Levenshtein/stem matching.
 */
object BookSearchEngine {

    private val STOP_WORDS = setOf(
        // English stop words & generic MCQ boilerplate
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "shall", "can", "must", "to", "of", "in",
        "on", "at", "by", "for", "with", "about", "against", "between", "into",
        "through", "during", "before", "after", "above", "below", "from", "up",
        "down", "out", "off", "over", "under", "again", "further", "then", "once",
        "here", "there", "when", "where", "why", "how", "all", "any", "both",
        "each", "few", "more", "most", "other", "some", "such", "no", "nor",
        "not", "only", "own", "same", "so", "than", "too", "very", "just",
        "and", "but", "if", "or", "because", "as", "until", "while", "what",
        "which", "who", "whom", "this", "that", "these", "those", "it", "its",
        "they", "them", "their", "he", "she", "his", "her", "we", "our", "you",
        "your", "following", "correct", "incorrect", "statement", "statements",
        "true", "false", "none", "option", "question", "choose", "select",
        "meaning", "means", "called", "known", "refer", "refers", "best",
        "suitable", "example", "blank", "fill", "given", "below",
        // Bengali stop words & generic MCQ boilerplate
        "ও", "এবং", "আর", "বা", "অথবা", "কিন্তু", "তবে", "যদি", "তাহলে",
        "যে", "যা", "যার", "যাকে", "যাদের", "এই", "সেই", "এটি", "এটা",
        "ওটি", "তা", "তার", "তাদের", "তিনি", "তাঁর", "সে", "আমি", "আমরা",
        "তুমি", "তোমরা", "আপনি", "কি", "কী", "কোন", "কোনটি", "কোনগুলো",
        "কে", "কাকে", "কখন", "কোথায়", "কোথায়", "কেন", "কীভাবে", "কিভাবে",
        "নিচের", "নিম্নের", "উপরের", "মধ্যে", "থেকে", "হতে", "দ্বারা", "দিয়ে",
        "দিয়ে", "জন্য", "জন্যে", "সাথে", "সঙ্গে", "করে", "করা", "হয়", "হয়",
        "হয়েছে", "হয়েছে", "হবে", "ছিল", "আছে", "নেই", "নয়", "নয়", "না",
        "সঠিক", "ভুল", "উত্তর", "প্রশ্ন", "বলা", "বলে", "বলেন", "বোঝায়",
        "বোঝায়", "নামে", "পরিচিত", "সকল", "সব", "সবগুলো", "একটি", "এক",
        "দুটি", "দুই", "তিন", "চার", "খুব", "অনেক", "প্রতি", "অনুযায়ী",
        "অনুযায়ী", "মতো", "মত", "নয়টি"
    )

    private val BENGALI_SUFFIXES = listOf(
        "গুলোর", "গুলোকে", "গুলো", "গুলির", "গুলি", "দেরকে", "দের", "গুলোতে",
        "তে", "কে", "রে", "ের", "র", "য়", "য়", "ে", "টি", "টা", "খানা", "খানি"
    )

    data class WeightedSearchProfile(
        val resolvedAnswerText: String,
        val questionPhrases: List<String>,
        val answerPhrases: List<String>,
        val termWeights: Map<String, Double>,
        val questionTokens: Set<String>,
        val answerTokens: Set<String>,
        val optionTokens: Set<String>,
        val ftsMatchExpression: String
    )

    private val BOOK_ID_TAG_REGEX = Regex(
        """(?:^|[^A-Za-z0-9])id-([A-Za-z0-9]+)(?=$|[^A-Za-z0-9])""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts all `id-###` link tags from a book's filename (e.g. `id-akr_Notes.pdf` -> {"akr"}).
     * Falls back to title only when fileName is blank. Uses non-alphanumeric boundaries so words
     * like `android-guide.pdf` do not falsely match.
     */
    fun extractBookLinkIds(book: BookEntity): Set<String> {
        val source = book.fileName.ifBlank { book.title }
        if (source.isBlank()) return emptySet()
        return BOOK_ID_TAG_REGEX.findAll(source)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .toSet()
    }

    /**
     * Checks whether a Question Bank question `id` (e.g. `akr_1`, `akr_2`) begins with a book's
     * `id-###` tag (e.g. `akr`) at a clean token boundary (`_`, `-`, `.`, space, or digits).
     */
    fun doesQuestionIdMatchBookTag(questionId: String, bookTag: String): Boolean {
        val qId = questionId.trim().lowercase()
        val tag = bookTag.trim().lowercase()
        if (qId.isBlank() || tag.isBlank()) return false
        if (!qId.startsWith(tag)) return false
        val nextChar = qId.getOrNull(tag.length) ?: return true
        if (!nextChar.isLetterOrDigit()) return true
        return tag.last().isLetter() && nextChar.isDigit()
    }

    /**
     * Returns only the books whose filename `id-###` matches the given question's `id` prefix.
     * Books without a matching `id-###` tag are never linked.
     */
    fun filterBooksForQuestion(books: List<BookEntity>, questionId: String): List<BookEntity> {
        if (books.isEmpty() || questionId.isBlank()) return emptyList()
        return books.filter { book ->
            val tags = extractBookLinkIds(book)
            tags.any { tag -> doesQuestionIdMatchBookTag(questionId, tag) }
        }
    }

    suspend fun searchForMcqExplanation(
        db: AppDatabase,
        questionItem: QuizQuestionItem,
        maxResults: Int = 3
    ): List<BookPassageMatch> = withContext(Dispatchers.IO) {
        val readyBooks = db.bookDao().getAllBooksList()
            .filter { it.indexStatus == "ready" || it.totalPages > 0 }
        if (readyBooks.isEmpty()) return@withContext emptyList()

        // Strictly scope search ONLY to books with filename `id-###` matching Question Bank ID prefix
        val eligibleBooks = filterBooksForQuestion(readyBooks, questionItem.id)
        if (eligibleBooks.isEmpty()) return@withContext emptyList()

        val eligibleBookIds = eligibleBooks.mapTo(HashSet()) { it.id }
        val booksById = eligibleBooks.associateBy { it.id }
        val options = listOf(
            questionItem.opt1,
            questionItem.opt2,
            questionItem.opt3,
            questionItem.opt4,
            questionItem.opt5
        ).map { it.trim() }.filter { it.isNotBlank() }

        val profile = buildMcqSearchProfile(
            question = questionItem.question,
            options = options,
            rawAnswer = questionItem.answer
        )
        if (profile.termWeights.isEmpty()) return@withContext emptyList()

        // 1. Gather candidate pages via SQLite FTS5/BM25 scoped to eligible books
        val sqliteDb = db.openHelper.readableDatabase
        val singleBookFilter = if (eligibleBooks.size == 1) eligibleBooks.first().id else null
        val ftsRows = if (profile.ftsMatchExpression.isNotBlank()) {
            BookFtsHelper.queryFtsCandidates(
                sqliteDb,
                profile.ftsMatchExpression,
                bookIdFilter = singleBookFilter,
                limit = 80
            ).filter { it.bookId in eligibleBookIds }
        } else {
            emptyList()
        }

        // Load pages strictly from eligible books so unrelated books never enter scoring or IDF
        val eligiblePages = if (eligibleBooks.size == 1) {
            db.bookDao().getPagesListForBook(eligibleBooks.first().id)
        } else {
            db.bookDao().getAllPagesList().filter { it.bookId in eligibleBookIds }
        }
        if (eligiblePages.isEmpty()) return@withContext emptyList()

        val ftsRankByKey = HashMap<Pair<String, Int>, Double>(ftsRows.size)
        for (row in ftsRows) {
            ftsRankByKey[row.bookId to row.pageNumber] = row.rawRank
        }

        val candidates: List<BookPageEntity> = if (eligiblePages.size <= 3500 || ftsRows.isEmpty()) {
            eligiblePages
        } else {
            val selectedKeys = ftsRankByKey.keys
            val matched = eligiblePages.filter { (it.bookId to it.pageNumber) in selectedKeys }
            if (matched.size >= 15) matched else eligiblePages
        }

        val scoredMatches = rankPagesForMcq(
            pages = candidates,
            booksById = booksById,
            profile = profile,
            ftsRankByKey = ftsRankByKey
        )

        scoredMatches.take(maxResults)
    }

    suspend fun searchInBook(
        db: AppDatabase,
        bookId: String,
        query: String,
        maxResults: Int = 30
    ): List<BookPassageMatch> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val book = db.bookDao().getBookById(bookId) ?: return@withContext emptyList()
        val pages = db.bookDao().getPagesListForBook(bookId)
        if (pages.isEmpty()) return@withContext emptyList()

        val booksById = mapOf(book.id to book)
        val queryTokens = tokenize(cleanQuery).filter { it.length >= 2 }
        val terms = (if (queryTokens.isNotEmpty()) queryTokens else listOf(cleanQuery.lowercase())).distinct()
        val termWeights = terms.associateWith { 2.0 }

        val profile = WeightedSearchProfile(
            resolvedAnswerText = "",
            questionPhrases = listOf(normalizeForPhrase(cleanQuery)),
            answerPhrases = emptyList(),
            termWeights = termWeights,
            questionTokens = terms.toSet(),
            answerTokens = emptySet(),
            optionTokens = emptySet(),
            ftsMatchExpression = buildFtsOrQuery(terms)
        )

        val ftsRows = BookFtsHelper.queryFtsCandidates(
            db.openHelper.readableDatabase,
            profile.ftsMatchExpression,
            bookIdFilter = bookId,
            limit = maxResults * 2
        )
        val ftsMap = ftsRows.associate { (it.bookId to it.pageNumber) to it.rawRank }

        rankPagesForMcq(
            pages = pages,
            booksById = booksById,
            profile = profile,
            ftsRankByKey = ftsMap,
            strictMcqGate = false
        ).take(maxResults)
    }

    /**
     * Builds a weighted query profile using question text + all options + resolved correct answer.
     */
    fun buildMcqSearchProfile(
        question: String,
        options: List<String>,
        rawAnswer: String
    ): WeightedSearchProfile {
        val resolvedAnswer = resolveCorrectOptionText(options, rawAnswer)
        val cleanedQuestion = question
            .replace(Regex("_{2,}"), " ")
            .replace("[", "")
            .replace("]", "")
            .trim()

        val qTokens = tokenizeContent(cleanedQuestion)
        val aTokens = tokenizeContent(resolvedAnswer)
        val allOptTokens = options.flatMap { tokenizeContent(it) }.toSet()

        val weights = LinkedHashMap<String, Double>()
        // 1. Question core keywords carry highest weight
        for (tok in qTokens) {
            weights[tok] = (weights[tok] ?: 0.0) + 3.0
        }
        // 2. Correct answer keywords carry strong weight
        for (tok in aTokens) {
            weights[tok] = (weights[tok] ?: 0.0) + 2.8
        }
        // 3. All distractor options add secondary topical context (helpful when MCQ asks "Which of the following...")
        for (tok in allOptTokens) {
            if (tok !in aTokens) {
                weights[tok] = (weights[tok] ?: 0.0) + 0.75
            }
        }

        // Phrases (normalized multi-word sequences & sliding 2-3 grams)
        val qPhrases = extractSignificantPhrases(cleanedQuestion)
        val aPhrases = if (resolvedAnswer.length >= 3) {
            listOf(normalizeForPhrase(resolvedAnswer)).filter { it.length >= 3 }
        } else emptyList()

        // Top 14 tokens for FTS5 OR matching
        val topFtsTokens = weights.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.length >= 2 }
            .take(14)

        return WeightedSearchProfile(
            resolvedAnswerText = resolvedAnswer,
            questionPhrases = qPhrases,
            answerPhrases = aPhrases,
            termWeights = weights,
            questionTokens = qTokens.toSet(),
            answerTokens = aTokens.toSet(),
            optionTokens = allOptTokens,
            ftsMatchExpression = buildFtsOrQuery(topFtsTokens)
        )
    }

    /**
     * Pure deterministic scorer/ranker (callable directly in JVM unit tests without SQLite).
     */
    fun rankPagesForMcq(
        pages: List<BookPageEntity>,
        booksById: Map<String, BookEntity>,
        profile: WeightedSearchProfile,
        ftsRankByKey: Map<Pair<String, Int>, Double> = emptyMap(),
        strictMcqGate: Boolean = true
    ): List<BookPassageMatch> {
        if (pages.isEmpty() || profile.termWeights.isEmpty()) return emptyList()

        // Precompute corpus DF & avg document length for Okapi BM25
        val docCount = pages.size.toDouble()
        val avgDocLen = pages.sumOf { max(1, it.wordCount) }.toDouble() / docCount
        val queryTerms = profile.termWeights.keys.toList()

        val pageTokenCaches = ArrayList<Pair<BookPageEntity, List<String>>>(pages.size)
        val dfMap = HashMap<String, Int>(queryTerms.size)

        for (page in pages) {
            if (page.textContent.isBlank()) continue
            val tokens = tokenize(page.textContent)
            if (tokens.isEmpty()) continue
            pageTokenCaches.add(page to tokens)
            val tokenSet = tokens.toHashSet()
            for (qt in queryTerms) {
                if (qt in tokenSet || tokenSet.any { tokensStemMatch(qt, it) }) {
                    dfMap[qt] = (dfMap[qt] ?: 0) + 1
                }
            }
        }

        val k1 = 1.5
        val b = 0.75
        val matches = mutableListOf<BookPassageMatch>()

        for ((page, docTokens) in pageTokenCaches) {
            val book = booksById[page.bookId] ?: continue
            val docLen = max(1, docTokens.size).toDouble()
            val normalizedText = normalizeForPhrase(page.textContent)

            // 1. Term Frequency map + Fuzzy / Stem match detection
            val tfMap = HashMap<String, Int>()
            val matchedTermsList = linkedSetOf<String>()
            val uniqueDocTokens = docTokens.toHashSet()

            for (qt in queryTerms) {
                var freq = 0
                for (dt in docTokens) {
                    if (dt == qt) freq++
                }
                var isFuzzyOrStem = false
                if (freq == 0) {
                    for (dt in uniqueDocTokens) {
                        if (tokensStemMatch(qt, dt) || fuzzyTokenMatch(qt, dt)) {
                            freq += 1
                            isFuzzyOrStem = true
                        }
                    }
                }
                if (freq > 0) {
                    tfMap[qt] = freq
                    if (!isFuzzyOrStem) {
                        matchedTermsList.add(qt)
                    } else {
                        // Also capture actual surface word from doc for reader highlighting
                        matchedTermsList.add(qt)
                        uniqueDocTokens.firstOrNull { tokensStemMatch(qt, it) || fuzzyTokenMatch(qt, it) }
                            ?.let { matchedTermsList.add(it) }
                    }
                }
            }

            if (tfMap.isEmpty()) continue

            // 2. Okapi BM25 Score
            var bm25Score = 0.0
            for ((qt, tf) in tfMap) {
                val df = (dfMap[qt] ?: 1).toDouble()
                val idf = ln(1.0 + (docCount - df + 0.5) / (df + 0.5))
                val tfNorm = (tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docLen / avgDocLen)))
                val weight = profile.termWeights[qt] ?: 1.0
                bm25Score += idf * tfNorm * weight
            }

            // 3. Phrase Matching Score
            var phraseScore = 0.0
            var matchedQuestionPhrase = false
            for (qp in profile.questionPhrases) {
                if (qp.length >= 6 && normalizedText.contains(qp)) {
                    phraseScore += 10.0
                    matchedQuestionPhrase = true
                    matchedTermsList.add(qp)
                }
            }
            var matchedAnswerPhrase = false
            for (ap in profile.answerPhrases) {
                if (ap.length >= 3 && normalizedText.contains(ap)) {
                    phraseScore += 9.0
                    matchedAnswerPhrase = true
                    matchedTermsList.add(ap)
                }
            }

            // 4. Question + Answer Coverage & Proximity Window Extraction
            val qHits = profile.questionTokens.count { it in tfMap }
            val aHits = profile.answerTokens.count { it in tfMap }
            val optHits = profile.optionTokens.count { it in tfMap }

            // Strictly validate minimum evidence so unrelated pages aren't falsely shown
            if (strictMcqGate) {
                val hasStrongEvidence = when {
                    matchedQuestionPhrase && (aHits > 0 || matchedAnswerPhrase) -> true
                    qHits >= 2 && (aHits >= 1 || matchedAnswerPhrase) -> true
                    qHits >= 2 -> true
                    profile.questionTokens.size <= 1 && qHits == 1 && (aHits >= 1 || matchedAnswerPhrase) -> true
                    matchedQuestionPhrase -> true
                    matchedAnswerPhrase && optHits >= 2 -> true
                    else -> false
                }
                if (!hasStrongEvidence) continue
            }

            val (bestSnippet, bestLine, windowDensityScore) = extractBestParagraphAndLine(
                pageText = page.textContent,
                queryTerms = queryTerms,
                termWeights = profile.termWeights,
                answerTokens = profile.answerTokens,
                questionPhrases = profile.questionPhrases,
                answerPhrases = profile.answerPhrases
            )

            val cooccurrenceBonus = when {
                qHits >= 1 && aHits >= 1 -> 6.0 + (min(qHits, 4) * 1.5) + (min(aHits, 3) * 1.5)
                qHits >= 2 -> 3.0
                else -> 0.0
            }

            // FTS5 bm25() returns negative values where smaller (more negative) = better match
            val ftsRaw = ftsRankByKey[page.bookId to page.pageNumber]
            val ftsBonus = if (ftsRaw != null && ftsRaw < 0.0) min(4.0, -ftsRaw * 0.4) else 0.0

            val totalScore = bm25Score + phraseScore + windowDensityScore + cooccurrenceBonus + ftsBonus

            if (strictMcqGate && totalScore < 4.0) continue

            matches.add(
                BookPassageMatch(
                    bookId = book.id,
                    bookTitle = book.title,
                    bookFormat = book.format,
                    pageNumber = page.pageNumber,
                    chapterTitle = page.chapterTitle,
                    passageSnippet = bestSnippet,
                    matchedTerms = matchedTermsList.toList().take(12),
                    relevanceScore = totalScore,
                    bestLine = bestLine
                )
            )
        }

        return matches.sortedByDescending { it.relevanceScore }
    }

    private fun resolveCorrectOptionText(options: List<String>, rawAnswer: String): String {
        val ans = rawAnswer.trim()
        if (ans.isBlank()) return ""
        // Direct match with an option
        options.firstOrNull { it.equals(ans, ignoreCase = true) }?.let { return it }

        // Index notation: A/B/C/D/E or 1/2/3/4/5 or Option A
        val cleanKey = ans.lowercase()
            .removePrefix("option")
            .removePrefix("opt")
            .trim()
            .removePrefix("(")
            .removeSuffix(")")
            .removeSuffix(".")
            .trim()

        val letterIdx = when (cleanKey) {
            "a", "1", "ক" -> 0
            "b", "2", "খ" -> 1
            "c", "3", "গ" -> 2
            "d", "4", "ঘ" -> 3
            "e", "5", "ঙ" -> 4
            else -> -1
        }
        if (letterIdx in options.indices) {
            return options[letterIdx]
        }
        return ans
    }

    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}\u0980-\u09FF]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun tokenizeContent(text: String): List<String> {
        return tokenize(text).filter { tok ->
            tok.length >= 2 && tok !in STOP_WORDS
        }
    }

    private fun normalizeForPhrase(text: String): String {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\u0980-\u09FF\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractSignificantPhrases(text: String): List<String> {
        val phrases = mutableListOf<String>()
        // Quoted terms inside questions often hold key subjects/words
        Regex("['\"‘“]([^'\"’”]{3,60})['\"’”]").findAll(text).forEach { m ->
            val inner = normalizeForPhrase(m.groupValues[1])
            if (inner.length >= 3) phrases.add(inner)
        }
        val words = tokenize(text).filter { it.length >= 2 }
        // 3-word & 2-word phrases excluding pure stopwords
        for (n in listOf(3, 2)) {
            if (words.size >= n) {
                for (i in 0..words.size - n) {
                    val slice = words.subList(i, i + n)
                    if (slice.count { it !in STOP_WORDS } >= 2) {
                        phrases.add(slice.joinToString(" "))
                    }
                }
            }
        }
        return phrases.distinct().take(12)
    }

    private fun buildFtsOrQuery(terms: List<String>): String {
        val safeTerms = terms.mapNotNull { t ->
            val cleaned = t.replace(Regex("[^\\p{L}\\p{Nd}\u0980-\u09FF]"), "")
            if (cleaned.length >= 2) "\"$cleaned\"*" else null
        }.distinct().take(12)
        return safeTerms.joinToString(" OR ")
    }

    private fun tokensStemMatch(queryToken: String, docToken: String): Boolean {
        if (queryToken == docToken) return true
        val qStem = stemToken(queryToken)
        val dStem = stemToken(docToken)
        if (qStem.length >= 3 && qStem == dStem) return true
        if (qStem.length >= 4 && dStem.length >= 4 &&
            (dStem.startsWith(qStem) || qStem.startsWith(dStem)) &&
            kotlin.math.abs(qStem.length - dStem.length) <= 3
        ) {
            return true
        }
        return false
    }

    private fun stemToken(token: String): String {
        var s = token
        if (s.any { it in '\u0980'..'\u09FF' }) {
            for (suf in BENGALI_SUFFIXES) {
                if (s.length > suf.length + 2 && s.endsWith(suf)) {
                    s = s.removeSuffix(suf)
                    break
                }
            }
            return s
        }
        // Light English Porter-style suffix stripping
        val engSuffixes = listOf("ation", "ment", "ness", "ing", "ies", "ied", "ed", "es", "ly", "s")
        for (suf in engSuffixes) {
            if (s.length > suf.length + 3 && s.endsWith(suf)) {
                return s.removeSuffix(suf)
            }
        }
        return s
    }

    private fun fuzzyTokenMatch(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4) return false
        if (kotlin.math.abs(a.length - b.length) > 2) return false
        val maxDist = if (max(a.length, b.length) >= 7) 2 else 1
        val dist = levenshtein(a, b)
        return dist <= maxDist
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val n = s1.length
        val m = s2.length
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    /**
     * Splits a paragraph into individual lines/sentences while preserving exact substrings
     * from the source text for crisp best-line highlighting.
     */
    fun splitIntoLinesOrSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val rawLines = text.split("\n")
        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val sentences = line.split(Regex("(?<=[.!?।])\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (sentences.isNotEmpty()) {
                result.addAll(sentences)
            } else {
                result.add(line)
            }
        }
        return result
    }

    /**
     * Finds the single highest-matching line/sentence inside [paragraph] for a free-text [query].
     */
    fun findBestMatchingLine(paragraph: String, query: String): String {
        val trimmedPara = paragraph.trim()
        val trimmedQuery = query.trim()
        if (trimmedPara.isBlank() || trimmedQuery.isBlank()) return ""

        val lines = splitIntoLinesOrSentences(trimmedPara)
        if (lines.isEmpty()) return ""
        if (lines.size == 1) return lines.first()

        // Direct substring/line match takes precedence
        val normQuery = normalizeForPhrase(trimmedQuery)
        lines.firstOrNull { line ->
            line.contains(trimmedQuery, ignoreCase = true) ||
                (normQuery.length >= 6 && normalizeForPhrase(line).contains(normQuery))
        }?.let { return it }

        val queryTokens = tokenizeContent(trimmedQuery).ifEmpty { tokenize(trimmedQuery) }.distinct()
        if (queryTokens.isEmpty()) return ""

        var bestLine = ""
        var bestScore = 0.0
        for (line in lines) {
            val lineTokens = tokenize(line).toHashSet()
            var score = 0.0
            for (qt in queryTokens) {
                if (qt in lineTokens || lineTokens.any { tokensStemMatch(qt, it) }) {
                    score += 1.5
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestLine = line
            }
        }
        return bestLine
    }

    /**
     * Identifies the highest-scoring paragraph on the page and the single highest-matching line/sentence
     * inside that paragraph, returning (paragraphSnippet, bestLine, densityScore).
     */
    private fun extractBestParagraphAndLine(
        pageText: String,
        queryTerms: List<String>,
        termWeights: Map<String, Double>,
        answerTokens: Set<String>,
        questionPhrases: List<String>,
        answerPhrases: List<String>
    ): Triple<String, String, Double> {
        val clean = pageText.trim()
        if (clean.isBlank()) return Triple("", "", 0.0)

        val paragraphs = clean.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(clean) }

        var bestPara = paragraphs.first()
        var bestParaScore = -1.0

        for (para in paragraphs) {
            val paraTokens = tokenize(para).toHashSet()
            val normPara = normalizeForPhrase(para)
            var pScore = 0.0
            var matchedDistinct = 0
            var hasAnswerInPara = false
            for (qt in queryTerms) {
                val matched = qt in paraTokens || paraTokens.any { tokensStemMatch(qt, it) }
                if (matched) {
                    matchedDistinct++
                    pScore += (termWeights[qt] ?: 1.0)
                    if (qt in answerTokens) hasAnswerInPara = true
                }
            }
            if (hasAnswerInPara && matchedDistinct >= 2) {
                pScore += 4.5
            }
            for (qp in questionPhrases) {
                if (qp.length >= 6 && normPara.contains(qp)) {
                    pScore += 5.0
                }
            }
            for (ap in answerPhrases) {
                if (ap.length >= 3 && normPara.contains(ap)) {
                    pScore += 4.5
                }
            }
            if (pScore > bestParaScore) {
                bestParaScore = pScore
                bestPara = para
            }
        }

        // Score every line/sentence inside the winning paragraph to find the single best line
        val lines = splitIntoLinesOrSentences(bestPara).ifEmpty { listOf(bestPara) }
        var bestLineIdx = 0
        var bestLineScore = -1.0

        for ((idx, line) in lines.withIndex()) {
            val lineTokens = tokenize(line).toHashSet()
            val normLine = normalizeForPhrase(line)
            var lScore = 0.0
            var matchedDistinct = 0
            var hasAnswerInLine = false
            for (qt in queryTerms) {
                val matched = qt in lineTokens || lineTokens.any { tokensStemMatch(qt, it) }
                if (matched) {
                    matchedDistinct++
                    lScore += (termWeights[qt] ?: 1.0)
                    if (qt in answerTokens) hasAnswerInLine = true
                }
            }
            if (hasAnswerInLine && matchedDistinct >= 2) {
                lScore += 4.0
            }
            for (qp in questionPhrases) {
                if (qp.length >= 6 && normLine.contains(qp)) {
                    lScore += 4.0
                }
            }
            for (ap in answerPhrases) {
                if (ap.length >= 3 && normLine.contains(ap)) {
                    lScore += 3.5
                }
            }
            lScore += matchedDistinct * 0.4
            if (lScore > bestLineScore) {
                bestLineScore = lScore
                bestLineIdx = idx
            }
        }

        val bestLine = lines.getOrElse(bestLineIdx) { bestPara }

        // Keep full containing paragraph whenever reasonably sized; otherwise window around bestLine
        val snippet = if (bestPara.length <= 680) {
            bestPara
        } else {
            val chosen = ArrayDeque<String>()
            chosen.add(bestLine)
            var totalLen = bestLine.length
            var left = bestLineIdx - 1
            var right = bestLineIdx + 1
            while ((left >= 0 || right < lines.size) && totalLen < 520) {
                if (left >= 0) {
                    val candidate = lines[left]
                    if (totalLen + candidate.length + 1 <= 620) {
                        chosen.addFirst(candidate)
                        totalLen += candidate.length + 1
                    }
                    left--
                }
                if (right < lines.size) {
                    val candidate = lines[right]
                    if (totalLen + candidate.length + 1 <= 620) {
                        chosen.addLast(candidate)
                        totalLen += candidate.length + 1
                    }
                    right++
                }
            }
            val joined = chosen.joinToString(" ")
            val prefix = if (left >= 0) "..." else ""
            val suffix = if (right < lines.size) "..." else ""
            "$prefix$joined$suffix"
        }

        return Triple(snippet, bestLine, max(0.0, max(bestParaScore, bestLineScore) * 0.65))
    }
}
