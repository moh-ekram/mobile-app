package com.example.data.service

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.ArticleEntity
import com.example.data.parser.ArticleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Data model representing an article URL extracted from sitemap.xml.
 */
data class SitemapArticleItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val lastMod: String? = null,
    val priority: String? = null,
    val changeFreq: String? = null,
    val section: String? = null,
    val isLikelyArticle: Boolean = true
)

/**
 * Represents a child sitemap in a <sitemapindex>.
 */
data class SitemapChildIndex(
    val url: String,
    val name: String,
    val lastMod: String? = null
)

/**
 * Result returned after fetching and parsing a sitemap.
 */
data class SitemapFetchResult(
    val sitemapUrl: String,
    val websiteDomain: String,
    val totalUrlsFound: Int,
    val articles: List<SitemapArticleItem>,
    val subSitemaps: List<SitemapChildIndex> = emptyList(),
    val isIndexSitemap: Boolean = false
)

/**
 * Service that fetches website sitemaps (sitemap.xml, sitemap_index.xml, robots.txt)
 * to extract and list all available article URLs, and provides batch processing capabilities
 * (batch import to reader and batch flashcard generation).
 */
class ArticleSitemapService private constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        @Volatile
        private var instance: ArticleSitemapService? = null

        fun getInstance(): ArticleSitemapService {
            return instance ?: synchronized(this) {
                instance ?: ArticleSitemapService().also { instance = it }
            }
        }
    }

    /**
     * Resolves the best sitemap URL and extracts article URLs.
     * Accepts either a full sitemap URL or a bare website domain.
     */
    suspend fun fetchSitemap(urlOrDomain: String): Result<SitemapFetchResult> = withContext(Dispatchers.IO) {
        try {
            var rawInput = urlOrDomain.trim()
            if (rawInput.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid website or sitemap URL."))
            }

            if (!rawInput.startsWith("http://", ignoreCase = true) && !rawInput.startsWith("https://", ignoreCase = true)) {
                rawInput = "https://$rawInput"
            }

            val targetUri = try {
                URI(rawInput)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Invalid URL format: ${e.message}"))
            }

            val domain = targetUri.host?.removePrefix("www.") ?: rawInput
            val path = targetUri.path?.lowercase(Locale.ROOT) ?: ""

            // If user supplied a direct XML or sitemap URL
            if (path.endsWith(".xml", ignoreCase = true) || path.contains("sitemap", ignoreCase = true)) {
                return@withContext parseSitemapFromDirectUrl(rawInput, domain)
            }

            // Otherwise, attempt common sitemap locations for this domain
            val baseScheme = targetUri.scheme ?: "https"
            val baseHost = targetUri.host ?: domain
            val rootUrl = "$baseScheme://$baseHost"

            val candidateLocations = listOf(
                "$rootUrl/sitemap.xml",
                "$rootUrl/sitemap_index.xml",
                "$rootUrl/sitemaps.xml",
                "$rootUrl/post-sitemap.xml",
                "$rootUrl/news-sitemap.xml",
                "$rootUrl/article-sitemap.xml"
            )

            // Try the candidate sitemaps
            for (candidateUrl in candidateLocations) {
                val candidateResult = parseSitemapFromDirectUrl(candidateUrl, domain)
                if (candidateResult.isSuccess && candidateResult.getOrNull()?.let { it.articles.isNotEmpty() || it.subSitemaps.isNotEmpty() } == true) {
                    return@withContext candidateResult
                }
            }

            // Fallback: check robots.txt for Sitemap: directives
            val robotsSitemaps = fetchSitemapsFromRobotsTxt(rootUrl)
            for (robotSitemapUrl in robotsSitemaps) {
                val candidateResult = parseSitemapFromDirectUrl(robotSitemapUrl, domain)
                if (candidateResult.isSuccess && candidateResult.getOrNull()?.let { it.articles.isNotEmpty() || it.subSitemaps.isNotEmpty() } == true) {
                    return@withContext candidateResult
                }
            }

            Result.failure(Exception("Could not find a valid sitemap at standard locations for $domain. Try entering the direct sitemap URL (e.g. $rootUrl/sitemap.xml)."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to fetch sitemap."))
        }
    }

    /**
     * Parses a specific sitemap URL. If it is a sitemapindex, extracts child sitemaps
     * and automatically expands the most article-relevant child sitemap.
     */
    suspend fun parseSitemapFromDirectUrl(sitemapUrl: String, domain: String): Result<SitemapFetchResult> = withContext(Dispatchers.IO) {
        try {
            val xmlContent = fetchXmlContent(sitemapUrl)
            if (xmlContent.isBlank()) {
                return@withContext Result.failure(Exception("Empty sitemap response."))
            }

            val doc = Jsoup.parse(xmlContent, sitemapUrl, Parser.xmlParser())

            // Check if this is a <sitemapindex>
            val sitemapNodes = doc.select("sitemapindex > sitemap, sitemap")
            if (sitemapNodes.isNotEmpty()) {
                val subSitemaps = mutableListOf<SitemapChildIndex>()
                for (sitemapEl in sitemapNodes) {
                    val loc = sitemapEl.select("loc").firstOrNull()?.text()?.trim() ?: continue
                    val lastMod = sitemapEl.select("lastmod").firstOrNull()?.text()?.trim()
                    val sitemapName = loc.substringAfterLast("/").ifBlank { loc }
                    subSitemaps.add(
                        SitemapChildIndex(
                            url = loc,
                            name = sitemapName,
                            lastMod = formatFriendlyDate(lastMod)
                        )
                    )
                }

                // Find the best child sitemap to expand (e.g. post-sitemap, article, news, etc.)
                val preferredChild = subSitemaps.firstOrNull { child ->
                    val lower = child.name.lowercase(Locale.ROOT)
                    lower.contains("post") || lower.contains("article") || lower.contains("news") || lower.contains("story") || lower.contains("blog")
                } ?: subSitemaps.firstOrNull()

                val extractedArticles = mutableListOf<SitemapArticleItem>()
                if (preferredChild != null) {
                    val childResult = parseUrlSetFromXml(fetchXmlContent(preferredChild.url), preferredChild.url)
                    extractedArticles.addAll(childResult)
                }

                return@withContext Result.success(
                    SitemapFetchResult(
                        sitemapUrl = sitemapUrl,
                        websiteDomain = domain,
                        totalUrlsFound = extractedArticles.size,
                        articles = extractedArticles,
                        subSitemaps = subSitemaps,
                        isIndexSitemap = true
                    )
                )
            }

            // Otherwise, it is a standard <urlset>
            val articles = parseUrlSetFromXml(xmlContent, sitemapUrl)
            Result.success(
                SitemapFetchResult(
                    sitemapUrl = sitemapUrl,
                    websiteDomain = domain,
                    totalUrlsFound = articles.size,
                    articles = articles,
                    subSitemaps = emptyList(),
                    isIndexSitemap = false
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to parse sitemap XML."))
        }
    }

    /**
     * Parses standard <urlset> and filters for article URLs.
     */
    private fun parseUrlSetFromXml(xmlContent: String, sourceUrl: String): List<SitemapArticleItem> {
        val doc = Jsoup.parse(xmlContent, sourceUrl, Parser.xmlParser())
        val urlNodes = doc.select("urlset > url, url")
        val items = mutableListOf<SitemapArticleItem>()
        val seenUrls = mutableSetOf<String>()

        val ignoredUrlPatterns = listOf(
            "/wp-admin", "/wp-content", "/wp-includes", "/tag/", "/tags/",
            "/category/", "/categories/", "/author/", "/users/", "/search",
            "/privacy", "/terms", "/cookie", "/about-us", "/contact",
            "/login", "/signup", "/register", "/subscribe", "/cart", "/checkout",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".pdf", ".mp4",
            ".css", ".js"
        )

        for (urlEl in urlNodes) {
            val rawLoc = urlEl.select("loc").firstOrNull()?.text()?.trim() ?: continue
            if (rawLoc.isBlank() || !rawLoc.startsWith("http", ignoreCase = true)) continue

            val cleanUrl = rawLoc.substringBefore("#").trimEnd('/')
            if (cleanUrl in seenUrls) continue

            val uri = try { URI(cleanUrl) } catch (_: Exception) { null }
            val path = uri?.path?.lowercase(Locale.ROOT) ?: ""

            // Exclude homepages or root paths
            if (path.isBlank() || path == "/") continue

            // Exclude non-article ignored paths
            if (ignoredUrlPatterns.any { path.contains(it) }) continue

            // Extract metadata if available
            val lastModRaw = urlEl.select("lastmod").firstOrNull()?.text()?.trim()
            val priority = urlEl.select("priority").firstOrNull()?.text()?.trim()
            val changeFreq = urlEl.select("changefreq").firstOrNull()?.text()?.trim()

            // News sitemap tags if present
            var title = urlEl.select("news|title, title").firstOrNull()?.text()?.trim()
            if (title.isNullOrBlank()) {
                title = extractTitleFromSlug(cleanUrl)
            }

            val section = extractSectionFromUrl(cleanUrl)

            seenUrls.add(cleanUrl)
            items.add(
                SitemapArticleItem(
                    url = cleanUrl,
                    title = title,
                    lastMod = formatFriendlyDate(lastModRaw),
                    priority = priority,
                    changeFreq = changeFreq,
                    section = section,
                    isLikelyArticle = isLikelyArticleUrl(path)
                )
            )

            if (items.size >= 150) break // Practical limit for smooth UI rendering
        }

        // Sort: articles first, then by lastmod descending
        return items.sortedWith(
            compareByDescending<SitemapArticleItem> { it.isLikelyArticle }
                .thenByDescending { it.lastMod ?: "" }
        )
    }

    /**
     * Checks robots.txt for Sitemap: links.
     */
    private fun fetchSitemapsFromRobotsTxt(rootUrl: String): List<String> {
        val sitemaps = mutableListOf<String>()
        try {
            val request = Request.Builder()
                .url("$rootUrl/robots.txt")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val regex = Regex("""(?i)^Sitemap:\s*(https?://\S+)""", RegexOption.MULTILINE)
                for (match in regex.findAll(body)) {
                    sitemaps.add(match.groupValues[1].trim())
                }
            }
        } catch (_: Exception) {}
        return sitemaps
    }

    private fun fetchXmlContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/xml, text/xml, application/xhtml+xml, text/html;q=0.9, */*;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP Error ${response.code}: Could not fetch sitemap at $url")
        }
        return response.body?.string() ?: ""
    }

    /**
     * Converts a URL slug into a clean, capitalized headline title.
     * E.g. "https://example.com/2024/09/how-to-learn-kotlin-fast/" -> "How To Learn Kotlin Fast"
     */
    fun extractTitleFromSlug(url: String): String {
        return try {
            val path = URI(url).path.trimEnd('/')
            val lastSegment = path.substringAfterLast("/")
                .removeSuffix(".html")
                .removeSuffix(".htm")
                .removeSuffix(".php")

            val decoded = URLDecoder.decode(lastSegment, "UTF-8")
            val words = decoded.split(Regex("[-_+]")).filter { it.isNotBlank() }

            if (words.isNotEmpty()) {
                val cleanedWords = words.map { word ->
                    if (word.length <= 2 && word.lowercase(Locale.ROOT) in listOf("ai", "ui", "ux", "ml", "us", "uk", "eu")) {
                        word.uppercase(Locale.ROOT)
                    } else {
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                }
                cleanedWords.joinToString(" ")
            } else {
                "Article: " + url.substringAfter("://").take(40)
            }
        } catch (_: Exception) {
            url.substringAfterLast("/").take(40)
        }
    }

    private fun extractSectionFromUrl(url: String): String? {
        return try {
            val pathSegments = URI(url).path.trim('/').split('/').filter { it.isNotBlank() }
            if (pathSegments.size > 1) {
                val first = pathSegments[0].lowercase(Locale.ROOT)
                if (first.length > 2 && !first.all { it.isDigit() }) {
                    first.replaceFirstChar { it.uppercase(Locale.ROOT) }
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    private fun isLikelyArticleUrl(path: String): Boolean {
        // True if path contains date structure (e.g. /2024/03/) or hyphenated slug with multiple words
        val hasDate = Regex("""/\d{4}/\d{1,2}/""").containsMatchIn(path)
        val hasLongSlug = path.substringAfterLast("/").count { it == '-' } >= 2
        val hasArticleWord = path.contains("/article/") || path.contains("/post/") || path.contains("/story/") || path.contains("/news/")
        return hasDate || hasLongSlug || hasArticleWord
    }

    private fun formatFriendlyDate(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        return try {
            val cleanDate = rawDate.take(10) // YYYY-MM-DD
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            val parsed = parser.parse(cleanDate)
            if (parsed != null) {
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                formatter.format(parsed)
            } else cleanDate
        } catch (_: Exception) {
            rawDate.take(10)
        }
    }

    /**
     * Batch processes selected articles by scraping their full text and inserting them
     * into the local Room database as ArticleEntity objects.
     */
    suspend fun batchImportArticlesToReader(
        context: Context,
        selectedArticles: List<SitemapArticleItem>,
        courseId: String = "course_default",
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): Result<List<ArticleEntity>> = withContext(Dispatchers.IO) {
        try {
            if (selectedArticles.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val database = AppDatabase.getDatabase(context)
            val savedEntities = mutableListOf<ArticleEntity>()
            val semaphore = Semaphore(3) // Politeness rate limit

            selectedArticles.mapIndexed { index, item ->
                async {
                    semaphore.withPermit {
                        try {
                            val parsedResult = ArticleParser.fetchWebArticle(item.url)
                            val (finalTitle, finalAuthor, finalContent) = if (parsedResult.isSuccess) {
                                val parsed = parsedResult.getOrThrow()
                                Triple(
                                    if (parsed.title.isNotBlank()) parsed.title else item.title,
                                    parsed.author ?: item.section ?: "Web Source",
                                    parsed.content
                                )
                            } else {
                                // Fallback: scrape directly using ArticleFlashcardScraperService
                                val scraperResult = ArticleFlashcardScraperService.getInstance().scrapeArticle(item.url)
                                if (scraperResult.isSuccess) {
                                    val scraped = scraperResult.getOrThrow()
                                    Triple(scraped.title.ifBlank { item.title }, scraped.author ?: "Web Source", scraped.mainBodyText)
                                } else {
                                    Triple(item.title, "Web Source", "Full article content could not be retrieved from ${item.url}.")
                                }
                            }

                            val wordCount = finalContent.split(Regex("""\s+""")).filter { it.isNotBlank() }.size
                            val entity = ArticleEntity(
                                id = UUID.randomUUID().toString(),
                                title = finalTitle,
                                content = finalContent,
                                author = finalAuthor,
                                courseId = courseId,
                                createdAt = System.currentTimeMillis(),
                                wordCount = wordCount
                            )

                            database.articleDao().insertArticle(entity)
                            synchronized(savedEntities) {
                                savedEntities.add(entity)
                            }
                            onProgress(index + 1, selectedArticles.size, finalTitle)
                        } catch (e: Exception) {
                            onProgress(index + 1, selectedArticles.size, "Failed: ${item.title}")
                        }
                    }
                }
            }.awaitAll()

            Result.success(savedEntities)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to batch import articles."))
        }
    }

    /**
     * Batch processes selected articles by scraping main body text and generating flashcards
     * saved directly into the local database (FlashcardDao and VocabularyDao).
     */
    suspend fun batchGenerateFlashcardsFromArticles(
        context: Context,
        selectedArticles: List<SitemapArticleItem>,
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (selectedArticles.isEmpty()) {
                return@withContext Result.success(0)
            }

            val scraperService = ArticleFlashcardScraperService.getInstance()
            val database = AppDatabase.getDatabase(context)
            val vocabularyWords = database.vocabularyDao().getAllWordsList()
            var totalFlashcardsCreated = 0
            val semaphore = Semaphore(2) // Scrape politeness limit

            selectedArticles.mapIndexed { index, item ->
                async {
                    semaphore.withPermit {
                        try {
                            val scrapeResult = scraperService.scrapeArticle(item.url)
                            if (scrapeResult.isSuccess) {
                                val content = scrapeResult.getOrThrow()
                                val cards = scraperService.extractFlashcards(
                                    article = content,
                                    targetVocabulary = vocabularyWords,
                                    maxCards = 15
                                )
                                if (cards.isNotEmpty()) {
                                    scraperService.saveToFlashcardDatabase(context, cards)
                                    scraperService.saveToVocabularyDatabase(context, cards)
                                    synchronized(this@ArticleSitemapService) {
                                        totalFlashcardsCreated += cards.size
                                    }
                                }
                            }
                            onProgress(index + 1, selectedArticles.size, item.title)
                        } catch (_: Exception) {
                            onProgress(index + 1, selectedArticles.size, "Error on: ${item.title}")
                        }
                    }
                }
            }.awaitAll()

            Result.success(totalFlashcardsCreated)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to generate flashcards from sitemap articles."))
        }
    }
}
