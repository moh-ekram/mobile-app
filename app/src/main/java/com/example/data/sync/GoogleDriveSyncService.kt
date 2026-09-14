package com.example.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class DownloadedCourseFile(
    val fileName: String,
    val bytes: ByteArray,
    val isExcel: Boolean,
    val isCsv: Boolean,
    val isJson: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DownloadedCourseFile
        return fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class DiscoveredDriveFile(
    val fileId: String,
    val title: String,
    val isGoogleSheet: Boolean = false
)

object GoogleDriveSyncService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Resolves and downloads all course files from a Google Drive Folder URL,
     * Google Sheet URL, Google Drive File URL, or direct data URLs.
     */
    suspend fun fetchFilesFromInput(inputUrl: String): List<DownloadedCourseFile> = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Please provide a valid Google Drive link or URL.")
        }

        // Check if multiple URLs were provided (e.g. split by newline or comma)
        val candidateUrls = trimmed.split(Regex("[\r\n,]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (candidateUrls.size > 1) {
            val combinedList = mutableListOf<DownloadedCourseFile>()
            for (url in candidateUrls) {
                try {
                    val downloaded = fetchSingleUrlOrFolder(url)
                    combinedList.addAll(downloaded)
                } catch (_: Exception) {}
            }
            if (combinedList.isEmpty()) {
                throw IOException("Could not download files from the provided links. Ensure links have public access ('Anyone with link can view').")
            }
            return@withContext combinedList
        } else {
            return@withContext fetchSingleUrlOrFolder(candidateUrls.first())
        }
    }

    private suspend fun fetchSingleUrlOrFolder(url: String): List<DownloadedCourseFile> {
        val trimmed = url.trim()

        // 1. Google Drive Folder Link
        val folderId = extractDriveFolderId(trimmed)
        if (folderId != null) {
            return fetchFilesFromDriveFolder(folderId)
        }

        // 2. Google Sheets Link
        val sheetId = extractGoogleSheetId(trimmed)
        if (sheetId != null) {
            val file = downloadGoogleSheetAsXlsx(sheetId)
            return listOf(file)
        }

        // 3. Google Drive Single File Link
        val fileId = extractDriveFileId(trimmed)
        if (fileId != null) {
            val file = downloadDriveFile(fileId)
            return listOf(file)
        }

        // 4. Direct CSV/XLSX/JSON link
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val file = downloadDirectUrl(trimmed)
            return listOf(file)
        }

        throw IllegalArgumentException("Unrecognized link format. Please provide a Google Drive folder link, file link, or Google Sheets link.")
    }

    /**
     * Extracts folder ID from URLs like:
     * - https://drive.google.com/drive/folders/1abc1234XYZ...
     * - https://drive.google.com/drive/u/0/folders/1abc1234XYZ...
     * - https://drive.google.com/open?id=1abc1234XYZ...
     */
    fun extractDriveFolderId(url: String): String? {
        val pattern1 = Pattern.compile("""folders/([a-zA-Z0-9_-]{15,})""")
        val matcher1 = pattern1.matcher(url)
        if (matcher1.find()) return matcher1.group(1)

        val pattern2 = Pattern.compile("""[?&]id=([a-zA-Z0-9_-]{15,})""")
        val matcher2 = pattern2.matcher(url)
        if (matcher2.find() && url.contains("folder", ignoreCase = true)) return matcher2.group(1)

        return null
    }

    /**
     * Extracts spreadsheet ID from Google Sheets URLs:
     * - https://docs.google.com/spreadsheets/d/1abc1234XYZ.../edit
     */
    fun extractGoogleSheetId(url: String): String? {
        if (!url.contains("spreadsheets", ignoreCase = true)) return null
        val pattern = Pattern.compile("""spreadsheets/d/([a-zA-Z0-9_-]{15,})""")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Extracts file ID from Google Drive file URLs:
     * - https://drive.google.com/file/d/1abc1234XYZ.../view
     * - https://drive.google.com/open?id=1abc1234XYZ...
     */
    fun extractDriveFileId(url: String): String? {
        val pattern1 = Pattern.compile("""/file/d/([a-zA-Z0-9_-]{15,})""")
        val matcher1 = pattern1.matcher(url)
        if (matcher1.find()) return matcher1.group(1)

        val pattern2 = Pattern.compile("""[?&]id=([a-zA-Z0-9_-]{15,})""")
        val matcher2 = pattern2.matcher(url)
        if (matcher2.find() && !url.contains("folder", ignoreCase = true)) return matcher2.group(1)

        return null
    }

    /**
     * Crawls a public Google Drive folder HTML to find files.
     */
    private suspend fun fetchFilesFromDriveFolder(folderId: String): List<DownloadedCourseFile> = withContext(Dispatchers.IO) {
        // Fetch embedded folder view which provides a cleaner listing
        val folderListUrl = "https://drive.google.com/embeddedfolderview?id=$folderId#list"
        val request = Request.Builder()
            .url(folderListUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to open Google Drive folder (HTTP ${response.code}). Ensure it is set to 'Anyone with link can view'.")
            }
            response.body?.string() ?: ""
        }

        val discoveredFiles = parseFolderHtml(html, folderId)

        if (discoveredFiles.isEmpty()) {
            // Fallback: Try the main drive folder URL
            val fallbackUrl = "https://drive.google.com/drive/folders/$folderId"
            val fbRequest = Request.Builder()
                .url(fallbackUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val fbHtml = client.newCall(fbRequest).execute().use { response ->
                response.body?.string() ?: ""
            }
            val fbDiscovered = parseFolderHtml(fbHtml, folderId)
            if (fbDiscovered.isEmpty()) {
                throw IOException("No files found in Google Drive folder. Please verify the folder contains .xlsx, .csv, or Google Sheets files and that sharing is set to 'Anyone with the link'.")
            }
            return@withContext downloadAllDiscovered(fbDiscovered)
        }

        return@withContext downloadAllDiscovered(discoveredFiles)
    }

    private fun parseFolderHtml(html: String, folderId: String): List<DiscoveredDriveFile> {
        val list = mutableListOf<DiscoveredDriveFile>()
        val seenIds = mutableSetOf<String>()

        // Pattern 1: <div class="flip-entry" id="entry-(ID)">...<div class="flip-entry-title">(TITLE)</div>
        val p1 = Pattern.compile("""id=["']entry-([a-zA-Z0-9_-]{15,})["'][^>]*>[\s\S]*?<div[^>]*class=["'][^"']*flip-entry-title[^"']*["'][^>]*>([^<]+)</div>""", Pattern.CASE_INSENSITIVE)
        val m1 = p1.matcher(html)
        while (m1.find()) {
            val id = m1.group(1) ?: continue
            val title = (m1.group(2) ?: "Course_${id.take(6)}").trim()
            if (id != folderId && seenIds.add(id)) {
                list.add(DiscoveredDriveFile(id, title))
            }
        }

        // Pattern 2: href=".../file/d/(ID)/..." with title or doc-title attribute
        val p2 = Pattern.compile("""(?:file/d/|id=)([a-zA-Z0-9_-]{15,})[^"'>]*["'][^>]*>[\s\S]*?(?:class=["'][^"']*doc-title[^"']*["'][^>]*>|aria-label=["'])([^<"']+)""", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(html)
        while (m2.find()) {
            val id = m2.group(1) ?: continue
            val rawTitle = (m2.group(2) ?: "Course_${id.take(6)}").trim()
            val cleanTitle = rawTitle.removePrefix("File: ").removePrefix("Spreadsheet: ").trim()
            if (id != folderId && seenIds.add(id)) {
                list.add(DiscoveredDriveFile(id, cleanTitle))
            }
        }

        // Pattern 3: JSON-like items inside embedded Javascript
        // e.g. ["ID","TITLE",...
        val p3 = Pattern.compile("""\["([a-zA-Z0-9_-]{25,})"\s*,\s*"([^"]+\.(?:xlsx|csv|tsv|json))"""", Pattern.CASE_INSENSITIVE)
        val m3 = p3.matcher(html)
        while (m3.find()) {
            val id = m3.group(1) ?: continue
            val title = (m3.group(2) ?: "Course_${id.take(6)}").trim()
            if (id != folderId && seenIds.add(id)) {
                list.add(DiscoveredDriveFile(id, title))
            }
        }

        // Filter to supported formats or extensionless titles (which might be Google Sheets)
        return list.filter {
            val lower = it.title.lowercase()
            lower.endsWith(".xlsx") || lower.endsWith(".csv") || lower.endsWith(".tsv") ||
            lower.endsWith(".json") || !lower.contains(".")
        }
    }

    private suspend fun downloadAllDiscovered(files: List<DiscoveredDriveFile>): List<DownloadedCourseFile> {
        val downloaded = mutableListOf<DownloadedCourseFile>()
        for (item in files) {
            try {
                val lower = item.title.lowercase()
                val file = if (lower.endsWith(".csv") || lower.endsWith(".tsv")) {
                    downloadDriveFile(item.fileId, item.title)
                } else if (lower.endsWith(".json")) {
                    downloadDriveFile(item.fileId, item.title)
                } else {
                    // Try downloading as XLSX or Google Sheet Export
                    try {
                        downloadGoogleSheetAsXlsx(item.fileId, item.title)
                    } catch (_: Exception) {
                        downloadDriveFile(item.fileId, item.title)
                    }
                }
                downloaded.add(file)
            } catch (e: Exception) {
                // Ignore single file failures and continue downloading others
            }
        }
        if (downloaded.isEmpty()) {
            throw IOException("Failed to download files from Drive folder. Please verify files are accessible.")
        }
        return downloaded
    }

    /**
     * Downloads a file from Google Drive by its file ID.
     */
    private fun downloadDriveFile(fileId: String, preferredTitle: String? = null): DownloadedCourseFile {
        // Direct download URL
        val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download file from Google Drive (HTTP ${response.code}).")
            }

            val bodyBytes = response.body?.bytes() ?: throw IOException("Empty file received from Google Drive.")

            // Determine filename from Content-Disposition header if available
            val disposition = response.header("Content-Disposition")
            var resolvedName = preferredTitle
            if (resolvedName.isNullOrBlank() && disposition != null) {
                val fnMatch = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";]+)""").find(disposition)
                if (fnMatch != null) {
                    resolvedName = fnMatch.groupValues[1].trim()
                }
            }

            if (resolvedName.isNullOrBlank()) {
                // Inspect header bytes to detect zip/xlsx
                val isZip = bodyBytes.size > 4 && bodyBytes[0] == 0x50.toByte() && bodyBytes[1] == 0x4B.toByte()
                resolvedName = if (isZip) "Course_$fileId.xlsx" else "Course_$fileId.csv"
            }

            val isXlsx = resolvedName.endsWith(".xlsx", ignoreCase = true) ||
                (bodyBytes.size > 4 && bodyBytes[0] == 0x50.toByte() && bodyBytes[1] == 0x4B.toByte())
            val isCsv = resolvedName.endsWith(".csv", ignoreCase = true) || resolvedName.endsWith(".tsv", ignoreCase = true)
            val isJson = resolvedName.endsWith(".json", ignoreCase = true)

            return DownloadedCourseFile(
                fileName = resolvedName,
                bytes = bodyBytes,
                isExcel = isXlsx,
                isCsv = isCsv,
                isJson = isJson
            )
        }
    }

    /**
     * Exports a Google Sheet directly as XLSX.
     */
    private fun downloadGoogleSheetAsXlsx(sheetId: String, preferredTitle: String? = null): DownloadedCourseFile {
        val exportUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=xlsx"
        val request = Request.Builder()
            .url(exportUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // Try CSV fallback if xlsx export fails
                return downloadGoogleSheetAsCsv(sheetId, preferredTitle)
            }
            val bodyBytes = response.body?.bytes() ?: throw IOException("Empty spreadsheet received from Google Sheets.")
            val finalName = if (!preferredTitle.isNullOrBlank()) {
                if (preferredTitle.endsWith(".xlsx", ignoreCase = true)) preferredTitle else "$preferredTitle.xlsx"
            } else {
                "Spreadsheet_$sheetId.xlsx"
            }

            return DownloadedCourseFile(
                fileName = finalName,
                bytes = bodyBytes,
                isExcel = true,
                isCsv = false,
                isJson = false
            )
        }
    }

    /**
     * Exports a Google Sheet directly as CSV.
     */
    private fun downloadGoogleSheetAsCsv(sheetId: String, preferredTitle: String? = null): DownloadedCourseFile {
        val exportUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
        val request = Request.Builder()
            .url(exportUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to export Google Sheet (HTTP ${response.code}). Ensure it is shared with 'Anyone with the link'.")
            }
            val bodyBytes = response.body?.bytes() ?: throw IOException("Empty response from Google Sheets.")
            val finalName = if (!preferredTitle.isNullOrBlank()) {
                if (preferredTitle.endsWith(".csv", ignoreCase = true)) preferredTitle else "$preferredTitle.csv"
            } else {
                "Spreadsheet_$sheetId.csv"
            }

            return DownloadedCourseFile(
                fileName = finalName,
                bytes = bodyBytes,
                isExcel = false,
                isCsv = true,
                isJson = false
            )
        }
    }

    /**
     * Downloads a direct web link (e.g. Published Google Sheet CSV or raw file).
     */
    private fun downloadDirectUrl(url: String): DownloadedCourseFile {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download link (HTTP ${response.code}).")
            }
            val bytes = response.body?.bytes() ?: throw IOException("Empty response from URL.")
            val disposition = response.header("Content-Disposition")
            var filename = "Downloaded_Course.csv"
            if (disposition != null) {
                val fnMatch = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";]+)""").find(disposition)
                if (fnMatch != null) {
                    filename = fnMatch.groupValues[1].trim()
                }
            } else {
                val pathSegment = url.substringBefore("?").substringAfterLast("/")
                if (pathSegment.isNotBlank() && pathSegment.contains(".")) {
                    filename = pathSegment
                }
            }

            val isXlsx = filename.endsWith(".xlsx", ignoreCase = true) ||
                (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
            val isCsv = filename.endsWith(".csv", ignoreCase = true) || filename.endsWith(".tsv", ignoreCase = true) || !isXlsx
            val isJson = filename.endsWith(".json", ignoreCase = true)

            return DownloadedCourseFile(
                fileName = filename,
                bytes = bytes,
                isExcel = isXlsx,
                isCsv = isCsv,
                isJson = isJson
            )
        }
    }
}
