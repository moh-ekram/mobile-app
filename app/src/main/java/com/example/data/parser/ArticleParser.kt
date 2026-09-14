package com.example.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
