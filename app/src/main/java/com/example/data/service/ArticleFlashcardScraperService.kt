package com.example.data.service

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.Flashcard
import com.example.data.model.VocabularyWordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Result data class containing scraped article content.
 */
data class ScrapedArticleContent(
    val url: String,
    val title: String,
    val author: String,
    val mainBodyText: String,
    val paragraphs: List<String>,
    val wordCount: Int,
    val readingTimeMinutes: Int,
    val domain: String
)

/**
 * Types of flashcards generated from article content.
 */
enum class FlashcardType {
    VOCABULARY_CONTEXT,
    TERM_DEFINITION,
    CLOZE_DELETION,
    KEY_SENTENCE
}

/**
 * Represents a flashcard generated from scraped article text.
 */
data class GeneratedFlashcard(
    val id: String = UUID.randomUUID().toString(),
    val front: String,
    val back: String,
    val word: String,
    val definition: String,
    val type: FlashcardType = FlashcardType.VOCABULARY_CONTEXT,
    val contextSentence: String? = null,
    val sourceUrl: String? = null,
    val articleTitle: String? = null
)

/**
 * Helper service using Jsoup to scrape article content from provided URLs
 * and extract the main body text for flashcard generation.
 */
class ArticleFlashcardScraperService(
    private val httpClient: OkHttpClient = defaultHttpClient
) {

    companion object {
        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private val SHARED_INSTANCE by lazy { ArticleFlashcardScraperService() }

        fun getInstance(): ArticleFlashcardScraperService = SHARED_INSTANCE

        // Elements that contain noise, navigation, advertising, or boilerplate
        private const val NOISE_SELECTORS =
            "script, style, noscript, nav, header, footer, aside, form, svg, iframe, " +
            ".ad, .ads, .advertisement, .cookie, .cookie-banner, .banner, .social-share, " +
            ".social-buttons, .share-buttons, .comments, #comments, .menu, .sidebar, " +
            ".author-bio, [role=navigation], [role=banner], [role=complementary], " +
            ".newsletter-signup, .related-posts, .promo, .pop-up, .modal"

        // Priority selectors for main article body container
        private val ARTICLE_CONTAINER_SELECTORS = listOf(
            "article",
            "[role=main]",
            "main",
            ".article-body",
            ".article-content",
            ".article__body",
            ".article__content",
            ".story-body",
            ".story-content",
            ".entry-content",
            ".post-content",
            ".mw-parser-output", // Wikipedia
            "#article-body",
            "#content",
            ".content"
        )
    }

    /**
     * Extracts the pristine main body text from a provided URL.
     * Useful for passing directly to flashcard generation or summarization pipelines.
     */
    suspend fun extractMainBodyTextFromUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        scrapeArticle(url).map { it.mainBodyText }
    }

    /**
     * Scrapes a single article URL with Jsoup and extracts structured content
     * including title, author, paragraphs, and main body text.
     */
    suspend fun scrapeArticle(urlInput: String): Result<ScrapedArticleContent> = withContext(Dispatchers.IO) {
        try {
            var url = urlInput.trim()
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: Could not fetch webpage."))
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return@withContext Result.failure(Exception("The fetched webpage content is empty."))
            }

            val doc = Jsoup.parse(html, url)
            val scraped = parseDocumentContent(doc, url)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to scrape article from URL: $urlInput"))
        }
    }

    /**
     * Batch scrapes multiple article URLs in parallel with concurrency limiting.
     */
    suspend fun scrapeMultipleArticles(
        urls: List<String>,
        maxConcurrency: Int = 3
    ): List<Result<ScrapedArticleContent>> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val tasks = urls.filter { it.isNotBlank() }.map { url ->
            async {
                semaphore.withPermit {
                    scrapeArticle(url)
                }
            }
        }
        tasks.awaitAll()
    }

    /**
     * Cleans the Jsoup document and extracts clean article body text and metadata.
     */
    private fun parseDocumentContent(doc: Document, sourceUrl: String): ScrapedArticleContent {
        // 1. Remove noise elements (ads, banners, scripts, navigation)
        doc.select(NOISE_SELECTORS).remove()

        // 2. Extract Title
        val rawTitle = doc.select("meta[property=og:title]").attr("content").ifBlank {
            doc.select("meta[name=twitter:title]").attr("content").ifBlank {
                doc.select("h1").firstOrNull()?.text()?.ifBlank { null } ?: doc.title()
            }
        }
        val cleanTitle = rawTitle
            .replace(Regex("""\s*[-|–—•]\s*[^|–—•]+$"""), "") // Remove " | SourceName"
            .trim()
            .ifBlank { "Article" }

        // 3. Extract Author
        val rawAuthor = doc.select("meta[name=author]").attr("content").ifBlank {
            doc.select("meta[property=article:author]").attr("content").ifBlank {
                doc.select("meta[name=twitter:creator]").attr("content").ifBlank {
                    doc.select("[rel=author], .author, .byline, .author-name").firstOrNull()?.text()
                }
            }
        }
        val domain = try {
            URI(sourceUrl).host?.removePrefix("www.") ?: "web"
        } catch (_: Exception) {
            "web"
        }
        val cleanAuthor = rawAuthor?.trim()?.takeIf { it.isNotBlank() && it.length < 60 } ?: domain

        // 4. Locate Main Article Container
        var container: Element? = null
        for (selector in ARTICLE_CONTAINER_SELECTORS) {
            val found = doc.select(selector).firstOrNull()
            if (found != null && found.text().length > 150) {
                container = found
                break
            }
        }
        val targetElement = container ?: doc.body() ?: doc

        // 5. Extract readable paragraphs
        val rawParagraphs = targetElement.select("p, h2, h3, blockquote")
            .map { it.text().trim() }
            .filter { isValidParagraph(it) }

        val paragraphs = if (rawParagraphs.isNotEmpty()) {
            rawParagraphs
        } else {
            // Fallback: chunk full body text by double line breaks
            targetElement.text()
                .split(Regex("""\n{2,}"""))
                .map { it.trim() }
                .filter { isValidParagraph(it) }
        }

        val mainBodyText = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            targetElement.text().trim()
        }

        val words = mainBodyText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val wordCount = words.size
        val readingTimeMinutes = maxOf(1, (wordCount / 200))

        return ScrapedArticleContent(
            url = sourceUrl,
            title = cleanTitle,
            author = cleanAuthor,
            mainBodyText = mainBodyText,
            paragraphs = paragraphs,
            wordCount = wordCount,
            readingTimeMinutes = readingTimeMinutes,
            domain = domain
        )
    }

    private fun isValidParagraph(text: String): Boolean {
        if (text.length < 35) return false
        val lower = text.lowercase()
        // Filter common boilerplate phrases
        if (lower.startsWith("photo by") || lower.startsWith("image via") ||
            lower.contains("all rights reserved") || lower.contains("cookie policy") ||
            lower.contains("subscribe now") || lower.contains("advertisement") ||
            lower.contains("follow us on") || lower.contains("click here to")) {
            return false
        }
        return true
    }

    /**
     * Extracts flashcard candidates from scraped article content.
     * Generates:
     * 1. Vocabulary in Context cards (matching against known target vocabulary words)
     * 2. Definition / Term extraction cards (e.g. "X is defined as Y", "X refers to Y")
     * 3. Cloze Deletion cards for high-yield sentences
     */
    fun extractFlashcards(
        article: ScrapedArticleContent,
        targetVocabulary: List<VocabularyWordEntity> = emptyList(),
        maxCards: Int = 20
    ): List<GeneratedFlashcard> {
        val result = mutableListOf<GeneratedFlashcard>()
        val seenWords = mutableSetOf<String>()

        // Split text into distinct sentences
        val sentences = article.paragraphs
            .flatMap { p -> p.split(Regex("""(?<=[.!?])\s+(?=[A-Z0-9"])""")) }
            .map { it.trim() }
            .filter { it.length in 35..350 }

        // Strategy 1: Match against target vocabulary list (e.g. GRE/IELTS/Course words)
        if (targetVocabulary.isNotEmpty()) {
            val vocabMap = targetVocabulary.associateBy { it.word.lowercase() }
            for (sentence in sentences) {
                if (result.size >= maxCards) break
                val tokens = sentence.split(Regex("""[\s,.;:!?()"'“”]+""")).map { it.lowercase() }
                for (token in tokens) {
                    val match = vocabMap[token]
                    if (match != null && !seenWords.contains(token)) {
                        seenWords.add(token)
                        result.add(
                            GeneratedFlashcard(
                                front = match.word,
                                back = "${match.meaning}\n\nExample from article:\n\"$sentence\"",
                                word = match.word,
                                definition = match.meaning,
                                type = FlashcardType.VOCABULARY_CONTEXT,
                                contextSentence = sentence,
                                sourceUrl = article.url,
                                articleTitle = article.title
                            )
                        )
                        break
                    }
                }
            }
        }

        // Strategy 2: Pattern-based Definition Extraction
        // Matches patterns like "X is defined as Y", "X refers to Y", "X: Y"
        val definitionPatterns = listOf(
            Regex("""\b([A-Z][a-zA-Z\s]{2,28})\s+(?:is defined as|is described as|refers to|is known as)\s+([^.!?]{15,200})[.!?]""", RegexOption.IGNORE_CASE),
            Regex("""\b([A-Z][a-zA-Z\s]{2,28})\s+(?:means|signifies)\s+([^.!?]{15,200})[.!?]""", RegexOption.IGNORE_CASE),
            Regex("""^([A-Z][a-zA-Z\s]{2,28}):\s+([^.!?\n]{15,200})[.!?]""")
        )

        for (sentence in sentences) {
            if (result.size >= maxCards) break
            for (pattern in definitionPatterns) {
                val match = pattern.find(sentence)
                if (match != null) {
                    val term = match.groupValues[1].trim()
                    val def = match.groupValues[2].trim()
                    val termKey = term.lowercase()
                    if (term.isNotBlank() && def.isNotBlank() && !seenWords.contains(termKey) && term.length <= 30) {
                        seenWords.add(termKey)
                        result.add(
                            GeneratedFlashcard(
                                front = term,
                                back = "$def\n\nContext:\n\"$sentence\"",
                                word = term,
                                definition = def,
                                type = FlashcardType.TERM_DEFINITION,
                                contextSentence = sentence,
                                sourceUrl = article.url,
                                articleTitle = article.title
                            )
                        )
                        break
                    }
                }
            }
        }

        // Strategy 3: Cloze Deletion / Fill-in-the-blank for informative sentences
        for (sentence in sentences) {
            if (result.size >= maxCards) break
            // Find interesting academic or sophisticated words (length >= 7 characters, alphabetic)
            val candidateWords = sentence.split(Regex("""[\s,.;:!?()"'“”]+"""))
                .filter { it.length in 7..16 && it.all { c -> c.isLetter() } }
                .filter { !isStopWord(it) }

            val chosenWord = candidateWords.maxByOrNull { it.length }
            if (chosenWord != null && !seenWords.contains(chosenWord.lowercase())) {
                seenWords.add(chosenWord.lowercase())
                val clozeSentence = sentence.replace(chosenWord, "[ ... ]", ignoreCase = false)
                result.add(
                    GeneratedFlashcard(
                        front = "Fill in the blank:\n\n\"$clozeSentence\"",
                        back = "Missing Word: $chosenWord\n\nFull Sentence:\n\"$sentence\"",
                        word = chosenWord,
                        definition = "Contextual keyword in ${article.title}",
                        type = FlashcardType.CLOZE_DELETION,
                        contextSentence = sentence,
                        sourceUrl = article.url,
                        articleTitle = article.title
                    )
                )
            }
        }

        return result
    }

    /**
     * High-level convenience method: scrapes URLs and generates flashcards in one step.
     */
    suspend fun generateFlashcardsFromUrls(
        urls: List<String>,
        targetVocabulary: List<VocabularyWordEntity> = emptyList(),
        maxCardsPerArticle: Int = 15
    ): List<GeneratedFlashcard> = withContext(Dispatchers.IO) {
        val scrapeResults = scrapeMultipleArticles(urls)
        val flashcards = mutableListOf<GeneratedFlashcard>()
        for (res in scrapeResults) {
            res.onSuccess { article ->
                val cards = extractFlashcards(article, targetVocabulary, maxCardsPerArticle)
                flashcards.addAll(cards)
            }
        }
        flashcards
    }

    /**
     * Persists generated flashcards to Room's FlashcardDao.
     */
    suspend fun saveToFlashcardDatabase(
        context: Context,
        flashcards: List<GeneratedFlashcard>
    ): Int = withContext(Dispatchers.IO) {
        if (flashcards.isEmpty()) return@withContext 0
        val db = AppDatabase.getDatabase(context)
        val entities = flashcards.map { card ->
            Flashcard(
                word = card.word,
                definition = card.back,
                masteryLevel = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        db.flashcardDao().insertAll(entities)
        entities.size
    }

    /**
     * Persists generated flashcards to Room's VocabularyDao under a course,
     * enabling immediate review across FlashcardScreen, Archer Aim, and Column Match games.
     */
    suspend fun saveToVocabularyDatabase(
        context: Context,
        flashcards: List<GeneratedFlashcard>,
        courseId: String = "course_scraped_articles",
        courseTitle: String = "Scraped Articles"
    ): Int = withContext(Dispatchers.IO) {
        if (flashcards.isEmpty()) return@withContext 0
        val db = AppDatabase.getDatabase(context)

        // Ensure course exists
        val existingCourses = db.courseDao().getAllCoursesList()
        if (existingCourses.none { it.id == courseId }) {
            db.courseDao().insertCourses(
                listOf(
                    com.example.data.model.CourseEntity(
                        id = courseId,
                        title = courseTitle,
                        description = "Flashcards automatically generated from scraped web articles",
                        createdAt = System.currentTimeMillis()
                    )
                )
            )
        }

        val entities = flashcards.mapIndexed { index, card ->
            VocabularyWordEntity(
                id = "scraped_${System.currentTimeMillis()}_$index",
                word = card.word,
                meaning = card.definition,
                example = card.contextSentence,
                status = "unrated",
                courseId = courseId,
                group = "1"
            )
        }
        db.vocabularyDao().insertWords(entities)
        entities.size
    }

    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf(
            "however", "therefore", "furthermore", "moreover", "although",
            "meanwhile", "nevertheless", "according", "including", "between",
            "through", "without", "against", "because", "another", "several",
            "different", "important", "following", "possible", "something",
            "everyone", "everything", "anything", "nothing", "usually",
            "already", "together", "probably", "certainly", "actually",
            "yesterday", "tomorrow", "tonight", "perhaps", "instead"
        )
        return stopWords.contains(word.lowercase())
    }
}
