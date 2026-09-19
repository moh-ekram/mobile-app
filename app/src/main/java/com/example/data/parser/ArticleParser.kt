package com.example.data.parser

import kotlinx.coroutines.Dispatchers
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
}
