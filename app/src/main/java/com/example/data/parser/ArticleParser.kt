package com.example.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

data class ParsedArticleItem(
    val title: String,
    val author: String,
    val content: String
)

data class ScannedArticleLink(
    val url: String,
    val title: String,
    val snippet: String = ""
)

data class RssFeedItem(
    val title: String,
    val link: String,
    val author: String = "",
    val pubDate: String = "",
    val description: String = ""
)

data class RssFeedResult(
    val feedTitle: String,
    val feedDescription: String,
    val items: List<RssFeedItem>
)

object ArticleParser {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Parses articles using Option 1 Markdown format:
     *
     * # Article Title
     * @ Author Name
     *
     * Content paragraph 1...
     * Content paragraph 2...
     *
     * ---
     *
     * # Second Article Title
     * @ Second Author
     *
     * Content...
     */
    fun parseArticles(
        rawText: String,
        fallbackTitle: String = "Untitled Article",
        fallbackAuthor: String = "Anonymous Author"
    ): List<ParsedArticleItem> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Delimiter: 3 or more hyphens, equals, or underscores on their own line (e.g. "---", "===", "___")
        // Also supports legacy "[ARTICLE]" tags if present
        val sections = trimmed.split(Regex("""(?m)^(?:\s*[-=_]{3,}\s*|\s*\[/?ARTICLE\]\s*)$"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<ParsedArticleItem>()

        for (section in sections) {
            val lines = section.lines()
            var extractedTitle: String? = null
            var extractedAuthor: String? = null
            val contentLines = mutableListOf<String>()

            var isContentStarted = false

            for (line in lines) {
                val lineTrimmed = line.trim()

                // 1. Title Marker: "# Title", "[TITLE]...[/TITLE]", or "Title: ..."
                if (!isContentStarted && (
                        lineTrimmed.startsWith("#") ||
                        lineTrimmed.startsWith("[TITLE]", ignoreCase = true) ||
                        lineTrimmed.startsWith("Title:", ignoreCase = true)
                    )) {
                    val t = lineTrimmed
                        .removePrefix("#")
                        .removePrefix("[TITLE]")
                        .removeSuffix("[/TITLE]")
                        .removePrefix("Title:")
                        .trim()
                    if (t.isNotEmpty()) {
                        extractedTitle = t
                    }
                    continue
                }

                // 2. Author Marker: "@ Author", "[AUTHOR]...[/AUTHOR]", "Author: ...", or "By: ..."
                if (!isContentStarted && (
                        lineTrimmed.startsWith("@") ||
                        lineTrimmed.startsWith("[AUTHOR]", ignoreCase = true) ||
                        lineTrimmed.startsWith("Author:", ignoreCase = true) ||
                        lineTrimmed.startsWith("By:", ignoreCase = true)
                    )) {
                    val a = lineTrimmed
                        .removePrefix("@")
                        .removePrefix("[AUTHOR]")
                        .removeSuffix("[/AUTHOR]")
                        .removePrefix("Author:")
                        .removePrefix("By:")
                        .trim()
                    if (a.isNotEmpty()) {
                        extractedAuthor = a
                    }
                    continue
                }

                // Skip tag lines if user happened to include any legacy tags
                if (lineTrimmed.equals("[CONTENT]", ignoreCase = true) ||
                    lineTrimmed.equals("[/CONTENT]", ignoreCase = true) ||
                    lineTrimmed.startsWith("[TAGS]", ignoreCase = true) ||
                    (!isContentStarted && lineTrimmed.startsWith("+"))
                ) {
                    continue
                }

                // First non-empty, non-marker line marks content start
                if (lineTrimmed.isNotEmpty()) {
                    isContentStarted = true
                }
                contentLines.add(line)
            }

            val rawContent = contentLines.joinToString("\n").trim()
            if (rawContent.isNotEmpty() || extractedTitle != null) {
                val finalTitle = when {
                    !extractedTitle.isNullOrBlank() -> extractedTitle
                    sections.size == 1 && fallbackTitle.isNotBlank() -> fallbackTitle
                    else -> {
                        val firstLine = rawContent.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Article"
                        if (firstLine.length <= 80) firstLine else firstLine.take(77) + "..."
                    }
                }
                val finalAuthor = extractedAuthor?.ifBlank { null } ?: fallbackAuthor

                result.add(
                    ParsedArticleItem(
                        title = finalTitle,
                        author = finalAuthor,
                        content = rawContent.ifBlank { finalTitle }
                    )
                )
            }
        }

        return result
    }

    /**
     * Extracts Google Docs TXT export URL from a Google Docs link or document ID
     */
    fun getGoogleDocsExportUrl(input: String): String {
        val trimmed = input.trim()
        val docIdMatch = Regex("""/document/d/([a-zA-Z0-9_-]+)""").find(trimmed)
        val docId = docIdMatch?.groupValues?.get(1) ?: trimmed
        return "https://docs.google.com/document/d/$docId/export?format=txt"
    }

    /**
     * Downloads the plain text content of a Google Doc using OkHttp.
     * Note: The document must be shared as "Anyone with the link can view".
     */
    suspend fun fetchGoogleDocText(urlOrId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val exportUrl = getGoogleDocsExportUrl(urlOrId)
            val request = Request.Builder()
                .url(exportUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.contains("<!DOCTYPE html", ignoreCase = true) &&
                    (body.contains("Sign in - Google Accounts", ignoreCase = true) || body.contains("accounts.google.com", ignoreCase = true))
                ) {
                    Result.failure(Exception("Document is private. Please set Google Doc link sharing to 'Anyone with the link can view'."))
                } else if (body.isBlank()) {
                    Result.failure(Exception("Document appears to be empty."))
                } else {
                    Result.success(body)
                }
            } else {
                Result.failure(Exception("HTTP Error ${response.code}: Could not fetch Google Doc. Check link permission."))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to connect to Google Doc"))
        }
    }

    /**
     * Extracts article title, author, and readable paragraphs from any web article link or Google Doc URL.
     */
    suspend fun fetchWebArticle(urlInput: String): Result<ParsedArticleItem> = withContext(Dispatchers.IO) {
        try {
            var url = urlInput.trim()
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }

            // Route Google Docs links to Google Docs plain-text handler
            if (url.contains("docs.google.com/document", ignoreCase = true)) {
                val docResult = fetchGoogleDocText(url)
                return@withContext docResult.mapCatching { docText ->
                    val parsed = parseArticles(docText)
                    if (parsed.isNotEmpty()) parsed.first()
                    else throw Exception("No article content found in Google Doc.")
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: Could not load article."))
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return@withContext Result.failure(Exception("The webpage is empty."))
            }

            val doc = Jsoup.parse(html, url)

            // Strip out navigation, scripts, ads, footers, etc.
            doc.select("script, style, noscript, nav, header, footer, aside, form, svg, iframe, .ad, .ads, .advertisement, .cookie, .banner, .social-share, .comments, #comments, .menu, .sidebar, .author-bio, [role=navigation], [role=banner]").remove()

            // 1. Extract Title
            val rawTitle = doc.select("meta[property=og:title]").attr("content").ifBlank {
                doc.select("h1").firstOrNull()?.text()?.ifBlank { null } ?: doc.title()
            }
            val cleanTitle = rawTitle.replace(Regex("""\s*[-|–—•]\s*[^|–—•]+$"""), "").trim().ifBlank { "Imported Article" }

            // 2. Extract Author
            val rawAuthor = doc.select("meta[name=author]").attr("content").ifBlank {
                doc.select("meta[property=article:author]").attr("content").ifBlank {
                    doc.select("meta[name=twitter:creator]").attr("content").ifBlank {
                        doc.select("[rel=author], .author, .byline, .author-name").firstOrNull()?.text()
                    }
                }
            }
            val hostName = try {
                URI(url).host?.removePrefix("www.") ?: "Web Source"
            } catch (_: Exception) {
                "Web Source"
            }
            val cleanAuthor = rawAuthor?.trim()?.takeIf { it.isNotBlank() && it.length < 50 } ?: hostName

            // 3. Extract Main Content
            val wikiContent = doc.select(".mw-parser-output").firstOrNull()
            val container = wikiContent ?: doc.select("article, [role=main], main, .article-body, .post-content, .entry-content, .story-body, #content, .content").firstOrNull() ?: doc.body()

            val paragraphs = container.select("p, h2, h3, blockquote")
                .map { it.text().trim() }
                .filter { it.length >= 25 }

            val content = if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n")
            } else {
                container.text().trim()
            }

            if (content.length < 60) {
                return@withContext Result.failure(Exception("Could not extract readable article text from this page."))
            }

            Result.success(
                ParsedArticleItem(
                    title = cleanTitle,
                    author = cleanAuthor,
                    content = content
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to extract article from link."))
        }
    }

    /**
     * Scans a website homepage, blog, or category listing page for multiple article links.
     */
    suspend fun scanPageForArticles(pageUrlInput: String): Result<List<ScannedArticleLink>> = withContext(Dispatchers.IO) {
        try {
            var pageUrl = pageUrlInput.trim()
            if (!pageUrl.startsWith("http://", ignoreCase = true) && !pageUrl.startsWith("https://", ignoreCase = true)) {
                pageUrl = "https://$pageUrl"
            }

            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: Could not load page."))
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return@withContext Result.failure(Exception("The webpage is empty."))
            }

            val doc = Jsoup.parse(html, pageUrl)

            // Remove headers, navs, footers, sidebars, ads, scripts
            doc.select("script, style, noscript, nav, header, footer, aside, form, svg, iframe, .ad, .ads, .advertisement, .cookie, .banner, .social-share, .comments, #comments, .menu, .sidebar, [role=navigation], [role=banner]").remove()

            val baseUri = try { URI(pageUrl) } catch (_: Exception) { null }
            val baseHost = baseUri?.host?.removePrefix("www.")?.lowercase() ?: ""

            val ignoredKeywords = setOf(
                "read more", "click here", "learn more", "continue reading", "sign in", "log in",
                "subscribe", "privacy", "terms", "cookies", "about us", "contact", "home",
                "next", "previous", "share", "view all", "load more", "comments"
            )

            val ignoredUrlPatterns = listOf(
                "/tag/", "/tags/", "/category/", "/categories/", "/author/", "/users/",
                "/search", "/privacy", "/terms", "/about", "/contact", "/login", "/signup",
                "/register", "/subscribe", "/feed", "/rss", ".jpg", ".png", ".pdf", ".mp4",
                "/wp-login", "/wp-admin", "/cart", "/checkout"
            )

            val candidateLinks = doc.select("article a[href], h1 a[href], h2 a[href], h3 a[href], h4 a[href], [class*=story] a[href], [class*=post] a[href], [class*=article] a[href], [class*=card] a[href], main a[href], body a[href]")

            val seenUrls = mutableSetOf<String>()
            val articles = mutableListOf<ScannedArticleLink>()

            for (linkEl in candidateLinks) {
                val rawHref = linkEl.attr("href").trim()
                if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:") || rawHref.startsWith("mailto:")) {
                    continue
                }

                val absUrl = try {
                    linkEl.absUrl("href").trim()
                } catch (_: Exception) { "" }

                if (absUrl.isBlank() || !absUrl.startsWith("http", ignoreCase = true)) continue

                // Clean URL by stripping fragments and tracking query params
                val cleanUrl = absUrl.substringBefore("#").replace(Regex("""[?&](utm_[^&]+|ref=[^&]+|source=[^&]+)"""), "")

                // Domain check: ensure it matches host or subdomain
                val linkUri = try { URI(cleanUrl) } catch (_: Exception) { null }
                val linkHost = linkUri?.host?.removePrefix("www.")?.lowercase() ?: ""
                if (baseHost.isNotBlank() && !linkHost.contains(baseHost) && !baseHost.contains(linkHost)) {
                    continue
                }

                // Check URL path against ignored patterns
                val path = linkUri?.path?.lowercase() ?: ""
                if (path.length <= 1 || ignoredUrlPatterns.any { path.contains(it) }) {
                    continue
                }

                // Extract and clean headline title
                var title = linkEl.text().trim()
                if (title.isBlank() || title.length < 12) {
                    val childHeading = linkEl.select("h1, h2, h3, h4, [class*=title], [class*=headline]").firstOrNull()
                    if (childHeading != null) {
                        title = childHeading.text().trim()
                    }
                }
                if (title.isBlank()) {
                    title = linkEl.attr("title").trim()
                }

                val lowerTitle = title.lowercase()
                if (title.length < 12 || title.length > 250 || ignoredKeywords.contains(lowerTitle)) {
                    continue
                }

                // Normalize title
                val cleanTitle = title.replace(Regex("""\s*[-|–—•]\s*[^|–—•]+$"""), "").trim()

                if (seenUrls.add(cleanUrl)) {
                    articles.add(ScannedArticleLink(url = cleanUrl, title = cleanTitle))
                    if (articles.size >= 40) break
                }
            }

            if (articles.isEmpty()) {
                Result.failure(Exception("No individual article links found on this page. Try an RSS feed or direct links."))
            } else {
                Result.success(articles)
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to scan page."))
        }
    }

    /**
     * Parses an RSS (2.0) or Atom (1.0) feed and returns article items.
     */
    suspend fun fetchRssFeed(feedUrlInput: String): Result<RssFeedResult> = withContext(Dispatchers.IO) {
        try {
            var feedUrl = feedUrlInput.trim()
            if (!feedUrl.startsWith("http://", ignoreCase = true) && !feedUrl.startsWith("https://", ignoreCase = true)) {
                feedUrl = "https://$feedUrl"
            }

            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: Could not load RSS feed."))
            }

            val rawBody = response.body?.string() ?: ""
            if (rawBody.isBlank()) {
                return@withContext Result.failure(Exception("The RSS feed response is empty."))
            }

            // Check if user entered a regular HTML webpage that links to an RSS feed
            if (rawBody.contains("<html", ignoreCase = true) && !rawBody.contains("<rss", ignoreCase = true) && !rawBody.contains("<feed", ignoreCase = true)) {
                val htmlDoc = Jsoup.parse(rawBody, feedUrl)
                val rssLinkEl = htmlDoc.select("link[type=application/rss+xml], link[type=application/atom+xml]").firstOrNull()
                if (rssLinkEl != null) {
                    val linkedRss = rssLinkEl.absUrl("href")
                    if (linkedRss.isNotBlank() && linkedRss != feedUrl) {
                        return@withContext fetchRssFeed(linkedRss)
                    }
                }
            }

            val xmlDoc = Jsoup.parse(rawBody, feedUrl, org.jsoup.parser.Parser.xmlParser())

            val items = mutableListOf<RssFeedItem>()

            // Case 1: RSS 2.0 (<rss><channel><item>...)
            val rssItems = xmlDoc.select("channel > item, item")
            if (rssItems.isNotEmpty()) {
                val feedTitle = xmlDoc.select("channel > title").firstOrNull()?.text()?.trim() ?: "RSS Feed"
                val feedDesc = xmlDoc.select("channel > description").firstOrNull()?.text()?.trim() ?: ""

                for (item in rssItems.take(30)) {
                    val title = item.select("title").firstOrNull()?.text()?.trim() ?: ""
                    val link = item.select("link").firstOrNull()?.text()?.trim()
                        ?: item.select("guid[isPermaLink=true]").firstOrNull()?.text()?.trim()
                        ?: ""
                    val author = item.select("dc|creator, author").firstOrNull()?.text()?.trim() ?: ""
                    val pubDate = item.select("pubDate").firstOrNull()?.text()?.trim() ?: ""
                    val rawDesc = item.select("description").firstOrNull()?.text() ?: ""
                    val cleanDesc = Jsoup.parse(rawDesc).text().trim()

                    if (title.isNotBlank() && link.isNotBlank()) {
                        items.add(
                            RssFeedItem(
                                title = title,
                                link = link,
                                author = author,
                                pubDate = pubDate,
                                description = cleanDesc.take(240)
                            )
                        )
                    }
                }
                return@withContext Result.success(RssFeedResult(feedTitle, feedDesc, items))
            }

            // Case 2: Atom 1.0 (<feed><entry>...)
            val atomEntries = xmlDoc.select("feed > entry, entry")
            if (atomEntries.isNotEmpty()) {
                val feedTitle = xmlDoc.select("feed > title").firstOrNull()?.text()?.trim() ?: "Atom Feed"
                val feedDesc = xmlDoc.select("feed > subtitle").firstOrNull()?.text()?.trim() ?: ""

                for (entry in atomEntries.take(30)) {
                    val title = entry.select("title").firstOrNull()?.text()?.trim() ?: ""
                    val link = entry.select("link[rel=alternate]").attr("href").ifBlank {
                        entry.select("link").attr("href")
                    }.trim()
                    val author = entry.select("author > name").firstOrNull()?.text()?.trim() ?: ""
                    val pubDate = entry.select("published, updated").firstOrNull()?.text()?.trim() ?: ""
                    val rawSummary = entry.select("summary, content").firstOrNull()?.text() ?: ""
                    val cleanSummary = Jsoup.parse(rawSummary).text().trim()

                    if (title.isNotBlank() && link.isNotBlank()) {
                        items.add(
                            RssFeedItem(
                                title = title,
                                link = link,
                                author = author,
                                pubDate = pubDate,
                                description = cleanSummary.take(240)
                            )
                        )
                    }
                }
                return@withContext Result.success(RssFeedResult(feedTitle, feedDesc, items))
            }

            Result.failure(Exception("Could not find standard RSS or Atom articles in this feed."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load RSS feed."))
        }
    }

    /**
     * Concurrently extracts full article contents from a list of URLs with progress callback.
     */
    suspend fun fetchBulkWebArticles(
        urls: List<String>,
        onProgress: ((current: Int, total: Int, currentTitle: String) -> Unit)? = null
    ): List<ParsedArticleItem> = withContext(Dispatchers.IO) {
        val cleanUrls = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanUrls.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<ParsedArticleItem>()
        val semaphore = Semaphore(3) // Politeness limit: 3 concurrent requests

        cleanUrls.mapIndexed { idx, url ->
            async {
                semaphore.withPermit {
                    val fetchResult = fetchWebArticle(url)
                    fetchResult.onSuccess { item ->
                        synchronized(results) {
                            results.add(item)
                        }
                        onProgress?.invoke(idx + 1, cleanUrls.size, item.title)
                    }.onFailure {
                        onProgress?.invoke(idx + 1, cleanUrls.size, "Failed: $url")
                    }
                }
            }
        }.awaitAll()

        results
    }
}
