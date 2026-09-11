package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.data.local.AppDatabase
import com.example.data.model.UserProgressEntity
import com.example.data.model.VocabularyWordEntity
import com.example.data.model.CourseEntity
import com.example.data.model.ArticleEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.parser.FileParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupManager(private val context: Context, private val database: AppDatabase) {

    private val prefs = context.getSharedPreferences("memorizer_prefs", Context.MODE_PRIVATE)

    /**
     * Gets the custom folder URI if user has selected a custom folder via SAF.
     */
    fun getCustomTreeUri(): String? {
        return prefs.getString("custom_backup_tree_uri", null)
    }

    fun setCustomTreeUri(uriString: String) {
        prefs.edit().putString("custom_backup_tree_uri", uriString).apply()
    }

    /**
     * App-specific external storage directory where backup files are saved automatically.
     * Safe on all Android versions (Android 10+ scoped storage compliant) with zero permission issues (no EACCES).
     */
    fun getBackupDirectory(): File {
        val externalDir = context.getExternalFilesDir("backup")
        val dir = externalDir ?: File(context.filesDir, "backup")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getJsonBackupFile(): File = File(getBackupDirectory(), "memorizer_progress.json")
    fun getCsvBackupFile(): File = File(getBackupDirectory(), "memorizer_vocabulary.csv")

    fun getBackupPathString(): String {
        val treeUri = getCustomTreeUri()
        if (treeUri != null) {
            return "SAF Linked Folder"
        }
        return getBackupDirectory().absolutePath
    }

    /**
     * Generates the complete JSON backup string containing courses, flashcards, articles, games, and questions.
     */
    suspend fun generateBackupJsonString(userId: String = "1235"): String = withContext(Dispatchers.IO) {
        val courses = database.courseDao().getAllCoursesList()
        val words = database.vocabularyDao().getAllWordsList()
        val articles = database.articleDao().getAllArticlesList()
        val games = database.gamePracticeDao().getAllItemsList()
        val questions = database.questionBankDao().getAllQuestionsList()
        val progress = database.userProgressDao().getProgressOnce(userId)

        val courseMap = courses.associateBy { it.id }

        val jsonRoot = JSONObject()
        jsonRoot.put("app", "Memorizer")
        jsonRoot.put("version", "2.0")
        jsonRoot.put("userId", userId)
        jsonRoot.put("timestamp", System.currentTimeMillis())

        val coursesArray = JSONArray()
        courses.forEach { c ->
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("title", c.title)
            cObj.put("description", c.description ?: "")
            cObj.put("createdAt", c.createdAt)
            cObj.put("columnHeadersJson", c.columnHeadersJson ?: "")
            coursesArray.put(cObj)
        }
        jsonRoot.put("courses", coursesArray)

        val statsObj = JSONObject()
        statsObj.put("totalWords", words.size)
        statsObj.put("totalCourses", courses.size)
        statsObj.put("knowCount", words.count { it.status == "know" })
        statsObj.put("confusionCount", words.count { it.status == "confusion" })
        statsObj.put("dontKnowCount", words.count { it.status == "dont_know" })
        statsObj.put("unratedCount", words.count { it.status == "unrated" })
        jsonRoot.put("statistics", statsObj)

        // User Profile & Photo serialization
        val profilePrefs = context.getSharedPreferences("memorizer_user_profile", Context.MODE_PRIVATE)
        val profileObj = JSONObject()
        val displayName = profilePrefs.getString("display_name", "User #1235") ?: "User #1235"
        val avatarUri = profilePrefs.getString("avatar_uri", null)
        val targetExam = profilePrefs.getString("target_exam", "GRE / IELTS") ?: "GRE / IELTS"
        val dailyGoal = profilePrefs.getInt("daily_goal", 20)
        val bio = profilePrefs.getString("bio", "Aiming for GRE 330+ and IELTS 8.0") ?: "Aiming for GRE 330+ and IELTS 8.0"
        profileObj.put("displayName", displayName)
        profileObj.put("avatarUri", avatarUri ?: "")
        profileObj.put("targetExam", targetExam)
        profileObj.put("dailyGoal", dailyGoal)
        profileObj.put("bio", bio)

        val avatarFile = File(context.filesDir, "profile_avatar.jpg")
        if (avatarFile.exists() && avatarFile.length() > 0) {
            try {
                val bytes = avatarFile.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                profileObj.put("avatarBase64", base64)
            } catch (e: Exception) {
                android.util.Log.e("BackupManager", "Failed to encode avatarBase64: ${e.message}")
            }
        }
        jsonRoot.put("profile", profileObj)

        val wordsArray = JSONArray()
        words.forEach { w ->
            val wObj = JSONObject()
            wObj.put("id", w.id)
            wObj.put("word", w.word)
            wObj.put("meaning", w.meaning)
            wObj.put("group", w.group)
            wObj.put("synonyms", w.synonyms ?: "")
            wObj.put("extraWord", w.extraWord ?: "")
            wObj.put("extraMeaning", w.extraMeaning ?: "")
            wObj.put("example", w.example ?: "")
            wObj.put("mnemonic", w.mnemonic ?: "")
            wObj.put("status", w.status)
            wObj.put("courseId", w.courseId)
            wObj.put("courseTitle", courseMap[w.courseId]?.title ?: "General Course")
            wObj.put("customPlacesJson", w.customPlacesJson ?: "")
            wordsArray.put(wObj)
        }
        jsonRoot.put("words", wordsArray)

        val articlesArray = JSONArray()
        articles.forEach { a ->
            val aObj = JSONObject()
            aObj.put("id", a.id)
            aObj.put("title", a.title)
            aObj.put("content", a.content)
            aObj.put("author", a.author)
            aObj.put("createdAt", a.createdAt)
            articlesArray.put(aObj)
        }
        jsonRoot.put("articles", articlesArray)

        val gamesArray = JSONArray()
        games.forEach { g ->
            val gObj = JSONObject()
            gObj.put("id", g.id)
            gObj.put("sheetType", g.sheetType)
            gObj.put("question", g.question)
            gObj.put("opt1", g.opt1)
            gObj.put("opt2", g.opt2)
            gObj.put("opt3", g.opt3)
            gObj.put("opt4", g.opt4)
            gObj.put("answer", g.answer)
            gObj.put("explanation", g.explanation ?: "")
            gamesArray.put(gObj)
        }
        jsonRoot.put("games", gamesArray)

        val qbArray = JSONArray()
        questions.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("question", q.question)
            qObj.put("opt1", q.opt1)
            qObj.put("opt2", q.opt2)
            qObj.put("opt3", q.opt3)
            qObj.put("opt4", q.opt4)
            qObj.put("answer", q.answer)
            qObj.put("explanation", q.explanation ?: "")
            qObj.put("filter1", q.filter1 ?: "")
            qObj.put("filter2", q.filter2 ?: "")
            qObj.put("filter3", q.filter3 ?: "")
            qbArray.put(qObj)
        }
        jsonRoot.put("questionBank", qbArray)

        progress?.let { p ->
            val pObj = JSONObject()
            pObj.put("userId", p.userId)
            pObj.put("totalWords", p.totalWords)
            pObj.put("knowCount", p.knowCount)
            pObj.put("confusionCount", p.confusionCount)
            pObj.put("dontKnowCount", p.dontKnowCount)
            pObj.put("unratedCount", p.unratedCount)
            pObj.put("streakDays", p.streakDays)
            pObj.put("quizCompleted", p.quizCompleted)
            pObj.put("quizTotalScore", p.quizTotalScore)
            jsonRoot.put("progress", pObj)
        }

        jsonRoot.toString(2)
    }

    /**
     * Exports backup data directly to a user-chosen SAF file URI.
     */
    suspend fun exportBackupToUri(uri: Uri, userId: String = "1235"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonText = generateBackupJsonString(userId)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(jsonText.toByteArray())
            } ?: return@withContext Result.failure(Exception("Failed to open output stream for selected file"))
            Result.success("Backup successfully exported to device!")
        } catch (e: Exception) {
            val friendlyError = "Export failed: ${e.localizedMessage ?: e.message}\n" +
                "Possible solution: Ensure sufficient storage space and select a writable folder like Downloads."
            Result.failure(Exception(friendlyError, e))
        }
    }

    /**
     * Generates or updates both JSON and CSV files on the device automatically.
     * Writes to the public Documents/MemorizerBackup folder and also to custom SAF folder if granted.
     * All courses, vocabulary words (course-separated), articles, games, and questions are backed up.
     */
    suspend fun saveBackupFiles(userId: String = "1235"): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        try {
            val courses = database.courseDao().getAllCoursesList()
            val words = database.vocabularyDao().getAllWordsList()
            val articles = database.articleDao().getAllArticlesList()
            val games = database.gamePracticeDao().getAllItemsList()
            val questions = database.questionBankDao().getAllQuestionsList()
            val progress = database.userProgressDao().getProgressOnce(userId)

            val courseMap = courses.associateBy { it.id }

            // 1. JSON Backup content - Full Course Hierarchy & Associated Entities
            val jsonRoot = JSONObject()
            jsonRoot.put("app", "Memorizer")
            jsonRoot.put("version", "2.0")
            jsonRoot.put("userId", userId)
            jsonRoot.put("timestamp", System.currentTimeMillis())

            // Courses Array
            val coursesArray = JSONArray()
            courses.forEach { c ->
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("title", c.title)
                cObj.put("description", c.description ?: "")
                cObj.put("createdAt", c.createdAt)
                cObj.put("columnHeadersJson", c.columnHeadersJson ?: "")
                coursesArray.put(cObj)
            }
            jsonRoot.put("courses", coursesArray)

            // Statistics
            val statsObj = JSONObject()
            statsObj.put("totalWords", words.size)
            statsObj.put("totalCourses", courses.size)
            statsObj.put("knowCount", words.count { it.status == "know" })
            statsObj.put("confusionCount", words.count { it.status == "confusion" })
            statsObj.put("dontKnowCount", words.count { it.status == "dont_know" })
            statsObj.put("unratedCount", words.count { it.status == "unrated" })
            jsonRoot.put("statistics", statsObj)

            // User Profile & Photo serialization
            val profilePrefs = context.getSharedPreferences("memorizer_user_profile", Context.MODE_PRIVATE)
            val profileObj = JSONObject()
            val displayName = profilePrefs.getString("display_name", "User #1235") ?: "User #1235"
            val avatarUri = profilePrefs.getString("avatar_uri", null)
            val targetExam = profilePrefs.getString("target_exam", "GRE / IELTS") ?: "GRE / IELTS"
            val dailyGoal = profilePrefs.getInt("daily_goal", 20)
            val bio = profilePrefs.getString("bio", "Aiming for GRE 330+ and IELTS 8.0") ?: "Aiming for GRE 330+ and IELTS 8.0"
            profileObj.put("displayName", displayName)
            profileObj.put("avatarUri", avatarUri ?: "")
            profileObj.put("targetExam", targetExam)
            profileObj.put("dailyGoal", dailyGoal)
            profileObj.put("bio", bio)

            val avatarFile = File(context.filesDir, "profile_avatar.jpg")
            if (avatarFile.exists() && avatarFile.length() > 0) {
                try {
                    val bytes = avatarFile.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    profileObj.put("avatarBase64", base64)
                } catch (e: Exception) {
                    android.util.Log.e("BackupManager", "Failed to encode avatarBase64: ${e.message}")
                }
            }
            jsonRoot.put("profile", profileObj)

            // Words Array with course association
            val wordsArray = JSONArray()
            words.forEach { w ->
                val wObj = JSONObject()
                wObj.put("id", w.id)
                wObj.put("word", w.word)
                wObj.put("meaning", w.meaning)
                wObj.put("group", w.group)
                wObj.put("synonyms", w.synonyms ?: "")
                wObj.put("extraWord", w.extraWord ?: "")
                wObj.put("extraMeaning", w.extraMeaning ?: "")
                wObj.put("example", w.example ?: "")
                wObj.put("mnemonic", w.mnemonic ?: "")
                wObj.put("status", w.status)
                wObj.put("courseId", w.courseId)
                wObj.put("courseTitle", courseMap[w.courseId]?.title ?: "General Course")
                wObj.put("customPlacesJson", w.customPlacesJson ?: "")
                wObj.put("timesReviewed", w.timesReviewed)
                wObj.put("lastReviewedAt", w.lastReviewedAt)
                wordsArray.put(wObj)
            }
            jsonRoot.put("words", wordsArray)

            // Articles Array
            val articlesArray = JSONArray()
            articles.forEach { a ->
                val aObj = JSONObject()
                aObj.put("id", a.id)
                aObj.put("title", a.title)
                aObj.put("content", a.content)
                aObj.put("author", a.author)
                aObj.put("courseId", a.courseId)
                aObj.put("createdAt", a.createdAt)
                aObj.put("wordCount", a.wordCount)
                articlesArray.put(aObj)
            }
            jsonRoot.put("articles", articlesArray)

            // Games Array
            val gamesArray = JSONArray()
            games.forEach { g ->
                val gObj = JSONObject()
                gObj.put("id", g.id)
                gObj.put("sheetType", g.sheetType)
                gObj.put("question", g.question)
                gObj.put("opt1", g.opt1)
                gObj.put("opt2", g.opt2)
                gObj.put("opt3", g.opt3)
                gObj.put("opt4", g.opt4)
                gObj.put("answer", g.answer)
                gObj.put("explanation", g.explanation ?: "")
                gamesArray.put(gObj)
            }
            jsonRoot.put("games", gamesArray)

            // Question Bank Array
            val qbArray = JSONArray()
            questions.forEach { q ->
                val qObj = JSONObject()
                qObj.put("id", q.id)
                qObj.put("question", q.question)
                qObj.put("opt1", q.opt1)
                qObj.put("opt2", q.opt2)
                qObj.put("opt3", q.opt3)
                qObj.put("opt4", q.opt4)
                qObj.put("answer", q.answer)
                qObj.put("explanation", q.explanation ?: "")
                qObj.put("filter1", q.filter1 ?: "")
                qObj.put("filter2", q.filter2 ?: "")
                qObj.put("filter3", q.filter3 ?: "")
                qbArray.put(qObj)
            }
            jsonRoot.put("questionBank", qbArray)

            // User Progress
            progress?.let { p ->
                val pObj = JSONObject()
                pObj.put("userId", p.userId)
                pObj.put("totalWords", p.totalWords)
                pObj.put("knowCount", p.knowCount)
                pObj.put("confusionCount", p.confusionCount)
                pObj.put("dontKnowCount", p.dontKnowCount)
                pObj.put("unratedCount", p.unratedCount)
                pObj.put("streakDays", p.streakDays)
                pObj.put("quizCompleted", p.quizCompleted)
                pObj.put("quizTotalScore", p.quizTotalScore)
                jsonRoot.put("progress", pObj)
            }

            val jsonString = jsonRoot.toString(2)
            val jsonFile = getJsonBackupFile()
            jsonFile.writeText(jsonString)

            // 2. CSV / Excel format backup content - includes courseId & courseTitle
            val csvSb = java.lang.StringBuilder()
            csvSb.append("id,courseId,courseTitle,group,Place1: Word,Place2: Meaning,Place3: Example,Place4: Synonyms,Place5: Extra,Place6: Mnemonic,status\n")
            words.forEach { w ->
                val cTitle = courseMap[w.courseId]?.title ?: "General Course"
                csvSb.append("\"${w.id}\",")
                csvSb.append("\"${w.courseId}\",")
                csvSb.append("\"${escapeCsv(cTitle)}\",")
                csvSb.append("\"${escapeCsv(w.group)}\",")
                csvSb.append("\"${escapeCsv(w.word)}\",")
                csvSb.append("\"${escapeCsv(w.meaning)}\",")
                csvSb.append("\"${escapeCsv(w.example ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.synonyms ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.extraWord ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.mnemonic ?: "")}\",")
                csvSb.append("\"${w.status}\"\n")
            }
            val csvString = csvSb.toString()
            val csvFile = getCsvBackupFile()
            csvFile.writeText(csvString)

            // If user granted permission to a custom SAF folder, write to it as well
            val customTreeUriStr = getCustomTreeUri()
            if (customTreeUriStr != null) {
                try {
                    val treeUri = Uri.parse(customTreeUriStr)
                    writeToSafTree(treeUri, "memorizer_backup.json", "application/json", jsonString.toByteArray())
                    writeToSafTree(treeUri, "memorizer_progress.json", "application/json", jsonString.toByteArray())
                    writeToSafTree(treeUri, "memorizer_vocabulary.csv", "text/csv", csvString.toByteArray())
                } catch (_: Exception) {}
            }

            // Update user progress timestamp
            val updatedProgress = (progress ?: UserProgressEntity(userId)).copy(
                totalWords = words.size,
                knowCount = words.count { it.status == "know" },
                confusionCount = words.count { it.status == "confusion" },
                dontKnowCount = words.count { it.status == "dont_know" },
                unratedCount = words.count { it.status == "unrated" },
                lastBackupTimestamp = System.currentTimeMillis()
            )
            database.userProgressDao().insertOrUpdate(updatedProgress)

            Result.success(Pair(jsonFile, csvFile))
        } catch (e: Exception) {
            val rootCause = e.localizedMessage ?: e.message ?: "Storage operation error"
            val message = "Backup Failed: $rootCause\n\nPossible Solutions:\n• Tap 'Export Backup File' to save directly using the system file picker to Downloads or Google Drive.\n• Ensure device internal storage has available space.\n• Select a custom folder using 'Choose Folder'."
            Result.failure(Exception(message, e))
        }
    }

    /**
     * Directly backups and replaces memorizer_backup.json inside the user's linked Google Drive / SAF folder.
     * Prevents duplicate files by overwriting in-place.
     */
    suspend fun backupDirectlyToLinkedFolder(userId: String = "1235"): Result<String> = withContext(Dispatchers.IO) {
        val treeUriStr = getCustomTreeUri()
            ?: return@withContext Result.failure(Exception("No Google Drive folder linked yet. Tap 'Select or create Drive folder' first."))

        try {
            val treeUri = Uri.parse(treeUriStr)
            val jsonText = generateBackupJsonString(userId)
            val jsonBytes = jsonText.toByteArray()

            writeToSafTree(treeUri, "memorizer_backup.json", "application/json", jsonBytes)
            writeToSafTree(treeUri, "memorizer_progress.json", "application/json", jsonBytes)

            val words = database.vocabularyDao().getAllWordsList()
            val courses = database.courseDao().getAllCoursesList()
            val courseMap = courses.associateBy { it.id }
            val csvSb = java.lang.StringBuilder()
            csvSb.append("id,courseId,courseTitle,group,Place1: Word,Place2: Meaning,Place3: Example,Place4: Synonyms,Place5: Extra,Place6: Mnemonic,status\n")
            words.forEach { w ->
                val cTitle = courseMap[w.courseId]?.title ?: "General Course"
                csvSb.append("\"${w.id}\",\"${w.courseId}\",\"${escapeCsv(cTitle)}\",\"${escapeCsv(w.group)}\",\"${escapeCsv(w.word)}\",\"${escapeCsv(w.meaning)}\",\"${escapeCsv(w.example ?: "")}\",\"${escapeCsv(w.synonyms ?: "")}\",\"${escapeCsv(w.extraWord ?: "")}\",\"${escapeCsv(w.mnemonic ?: "")}\",\"${w.status}\"\n")
            }
            writeToSafTree(treeUri, "memorizer_vocabulary.csv", "text/csv", csvSb.toString().toByteArray())

            // Update timestamp
            val progress = database.userProgressDao().getProgressOnce(userId)
            val updatedProgress = (progress ?: UserProgressEntity(userId)).copy(
                totalWords = words.size,
                knowCount = words.count { it.status == "know" },
                confusionCount = words.count { it.status == "confusion" },
                dontKnowCount = words.count { it.status == "dont_know" },
                unratedCount = words.count { it.status == "unrated" },
                lastBackupTimestamp = System.currentTimeMillis()
            )
            database.userProgressDao().insertOrUpdate(updatedProgress)

            Result.success("Backup overwritten in-place in your linked Drive folder (memorizer_backup.json, memorizer_progress.json, memorizer_vocabulary.csv)!")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save to Drive folder: ${e.message}", e))
        }
    }

    /**
     * Directly restores from the linked Google Drive folder by reading memorizer_backup.json or memorizer_progress.json.
     */
    suspend fun restoreDirectlyFromLinkedFolder(userId: String = "1235"): Result<Int> = withContext(Dispatchers.IO) {
        val treeUriStr = getCustomTreeUri()
            ?: return@withContext Result.failure(Exception("No Google Drive folder linked yet. Please select a Drive folder first."))

        try {
            val treeUri = Uri.parse(treeUriStr)
            val parentDocId = try {
                if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            var targetDocUri: Uri? = null
            var isJson = true

            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: ""
                        val childId = cursor.getString(idIdx)
                        if (isTargetFileMatch(name, "memorizer_backup.json") || isTargetFileMatch(name, "memorizer_progress.json")) {
                            targetDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            isJson = true
                            break
                        } else if (isTargetFileMatch(name, "memorizer_vocabulary.csv") && targetDocUri == null) {
                            targetDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            isJson = false
                        }
                    }
                }
            }

            if (targetDocUri == null) {
                return@withContext Result.failure(Exception("No memorizer_backup.json found in your linked Drive folder. Please tap 'Backup to Drive' first."))
            }

            val content = context.contentResolver.openInputStream(targetDocUri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(Exception("Could not read backup file from linked folder."))

            restoreFromFileContent(content, isJson, userId)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to restore from linked folder: ${e.message}", e))
        }
    }

    /**
     * Checks if a display name in the SAF cursor matches the target file name.
     * Handles:
     * - Exact case-insensitive match (e.g. "memorizer_backup.json")
     * - Extension stripped by Google Drive (e.g. "memorizer_backup")
     * - Double extensions (e.g. "memorizer_backup.json.json")
     * - System duplicate suffixes created by earlier runs or pickers (e.g. "memorizer_backup (1).json", "memorizer_backup (1)")
     */
    private fun isTargetFileMatch(displayName: String, targetFileName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.equals(targetFileName, ignoreCase = true)) return true

        val targetBase = targetFileName.substringBeforeLast('.')
        val targetExt = targetFileName.substringAfterLast('.', "")

        if (trimmed.equals(targetBase, ignoreCase = true)) return true
        if (targetExt.isNotEmpty() && trimmed.equals("$targetFileName.$targetExt", ignoreCase = true)) return true

        if (targetExt.isNotEmpty()) {
            val pattern = Regex("""^${Regex.escape(targetBase)}\s*\(\d+\)(\.${Regex.escape(targetExt)})?$""", RegexOption.IGNORE_CASE)
            if (pattern.matches(trimmed)) return true
        } else {
            val pattern = Regex("""^${Regex.escape(targetBase)}\s*\(\d+\)$""", RegexOption.IGNORE_CASE)
            if (pattern.matches(trimmed)) return true
        }
        return false
    }

    /**
     * Overwrites target file in-place inside the SAF / Google Drive tree folder.
     * Prevents duplicate files by finding any existing file or variation, deleting redundant duplicates,
     * and performing an in-place truncation and write.
     */
    private fun writeToSafTree(treeUri: Uri, fileName: String, mimeType: String, data: ByteArray) {
        val parentDocId = try {
            if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
        } catch (_: Exception) {
            DocumentsContract.getTreeDocumentId(treeUri)
        }

        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        val matchingDocEntries = mutableListOf<Pair<Uri, String>>()
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx) ?: ""
                        if (isTargetFileMatch(name, fileName)) {
                            val childDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            matchingDocEntries.add(Pair(childDocUri, name))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Query children failed for $childrenUri: ${e.message}")
        }

        // Pick primary target URI: exact match first, then base match, then any duplicate match
        val exactMatch = matchingDocEntries.firstOrNull { it.second.trim().equals(fileName, ignoreCase = true) }
        val baseMatch = matchingDocEntries.firstOrNull { it.second.trim().equals(fileName.substringBeforeLast('.'), ignoreCase = true) }
        val targetEntry = exactMatch ?: baseMatch ?: matchingDocEntries.firstOrNull()

        val targetDocUri: Uri = if (targetEntry != null) {
            targetEntry.first
        } else {
            DocumentsContract.createDocument(context.contentResolver, dirUri, mimeType, fileName)
                ?: throw IllegalStateException("Unable to create document for $fileName in linked folder")
        }

        // Clean up redundant duplicate files (e.g. memorizer_backup (1).json or duplicate Google Drive entries)
        for (entry in matchingDocEntries) {
            if (entry.first != targetDocUri) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, entry.first)
                } catch (_: Exception) {
                    // Non-fatal if provider doesn't permit deleting
                }
            }
        }

        // Overwrite in-place with truncation
        var written = false

        // Attempt 1: File descriptor with "rwt" + truncate (clears any old trailing bytes)
        try {
            context.contentResolver.openFileDescriptor(targetDocUri, "rwt")?.use { pfd ->
                try {
                    android.system.Os.ftruncate(pfd.fileDescriptor, 0)
                } catch (_: Throwable) {}
                java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                    fos.write(data)
                    fos.flush()
                }
                written = true
            }
        } catch (_: Throwable) {}

        // Attempt 2: File descriptor with "wt"
        if (!written) {
            try {
                context.contentResolver.openFileDescriptor(targetDocUri, "wt")?.use { pfd ->
                    try {
                        android.system.Os.ftruncate(pfd.fileDescriptor, 0)
                    } catch (_: Throwable) {}
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(data)
                        fos.flush()
                    }
                    written = true
                }
            } catch (_: Throwable) {}
        }

        // Attempt 3: openOutputStream with "wt"
        if (!written) {
            try {
                context.contentResolver.openOutputStream(targetDocUri, "wt")?.use { os ->
                    os.write(data)
                    os.flush()
                    written = true
                }
            } catch (_: Throwable) {}
        }

        // Attempt 4: Universal fallback openOutputStream with "w"
        if (!written) {
            context.contentResolver.openOutputStream(targetDocUri, "w")?.use { os ->
                os.write(data)
                os.flush()
                written = true
            } ?: throw IllegalStateException("Failed to open output stream to overwrite $fileName")
        }
    }

    /**
     * Restores state from JSON or CSV text.
     * Re-creates the complete course hierarchy so each word is strictly mapped to its course.
     * All items (courses, words, articles, games, question bank) are restored.
     */
    suspend fun restoreFromFileContent(content: String, isJson: Boolean, userId: String = "1235"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val coursesToInsert = mutableListOf<CourseEntity>()
            val wordsToInsert = mutableListOf<VocabularyWordEntity>()
            val articlesToInsert = mutableListOf<ArticleEntity>()
            val gamesToInsert = mutableListOf<GamePracticeEntity>()
            val questionsToInsert = mutableListOf<QuestionBankEntity>()

            val trimmed = content.trim()
            if (isJson || trimmed.startsWith("{") || trimmed.startsWith("[")) {
                if (trimmed.startsWith("{")) {
                    val root = JSONObject(trimmed)

                    // 0. User Profile & Photo Restore
                    if (root.has("profile")) {
                        val pObj = root.getJSONObject("profile")
                        val pName = pObj.optString("displayName", "User #1235")
                        val pExam = pObj.optString("targetExam", "GRE / IELTS")
                        val pGoal = pObj.optInt("dailyGoal", 20)
                        val pBio = pObj.optString("bio", "")
                        var restoredAvatarUri: String? = null

                        if (pObj.has("avatarBase64")) {
                            try {
                                val b64 = pObj.getString("avatarBase64")
                                if (b64.isNotBlank()) {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val avatarFile = File(context.filesDir, "profile_avatar.jpg")
                                    avatarFile.writeBytes(bytes)
                                    restoredAvatarUri = Uri.fromFile(avatarFile).toString()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("BackupManager", "Failed to decode avatarBase64: ${e.message}")
                            }
                        } else {
                            val rawUri = pObj.optString("avatarUri", "")
                            if (rawUri.isNotBlank()) {
                                restoredAvatarUri = rawUri
                            }
                        }

                        val profilePrefs = context.getSharedPreferences("memorizer_user_profile", Context.MODE_PRIVATE)
                        profilePrefs.edit()
                            .putString("display_name", pName)
                            .putString("avatar_uri", restoredAvatarUri)
                            .putString("target_exam", pExam)
                            .putInt("daily_goal", pGoal)
                            .putString("bio", pBio)
                            .apply()
                    }

                    // 1. Courses
                    if (root.has("courses")) {
                        val cArr = root.getJSONArray("courses")
                        for (i in 0 until cArr.length()) {
                            val cObj = cArr.getJSONObject(i)
                            coursesToInsert.add(
                                CourseEntity(
                                    id = cObj.optString("id", "course_${System.currentTimeMillis()}_$i"),
                                    title = cObj.optString("title", "Course ${i + 1}"),
                                    description = cObj.optString("description", "").ifEmpty { null },
                                    createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                                    columnHeadersJson = cObj.optString("columnHeadersJson", "").ifEmpty { null }
                                )
                            )
                        }
                    }

                    // 2. Words
                    if (root.has("words")) {
                        val wArr = root.getJSONArray("words")
                        for (i in 0 until wArr.length()) {
                            val wObj = wArr.getJSONObject(i)
                            val wId = wObj.optString("id", "word_${System.currentTimeMillis()}_$i")
                            val word = wObj.optString("word", "")
                            val meaning = wObj.optString("meaning", "")
                            val cId = wObj.optString("courseId", "").ifEmpty { "course_default" }
                            val cTitle = wObj.optString("courseTitle", "").ifEmpty { "Restored Course" }

                            if (word.isNotBlank()) {
                                if (coursesToInsert.none { it.id == cId }) {
                                    coursesToInsert.add(CourseEntity(id = cId, title = cTitle))
                                }
                                val rawGrp = wObj.optString("group", "").trim()
                                val wordGroup = if (rawGrp.isNotBlank()) rawGrp else "1"
                                wordsToInsert.add(
                                    VocabularyWordEntity(
                                        id = wId,
                                        word = word,
                                        meaning = meaning,
                                        group = wordGroup,
                                        synonyms = wObj.optString("synonyms", "").ifEmpty { null },
                                        extraWord = wObj.optString("extraWord", "").ifEmpty { null },
                                        extraMeaning = wObj.optString("extraMeaning", "").ifEmpty { null },
                                        example = wObj.optString("example", "").ifEmpty { null },
                                        mnemonic = wObj.optString("mnemonic", "").ifEmpty { null },
                                        status = wObj.optString("status", "unrated"),
                                        courseId = cId,
                                        customPlacesJson = wObj.optString("customPlacesJson", "").ifEmpty { null },
                                        timesReviewed = wObj.optInt("timesReviewed", 0),
                                        lastReviewedAt = wObj.optLong("lastReviewedAt", System.currentTimeMillis())
                                    )
                                )
                            }
                        }
                    }

                    // 3. Articles
                    if (root.has("articles")) {
                        val aArr = root.getJSONArray("articles")
                        for (i in 0 until aArr.length()) {
                            val aObj = aArr.getJSONObject(i)
                            articlesToInsert.add(
                                ArticleEntity(
                                    id = aObj.optString("id", "art_$i"),
                                    title = aObj.optString("title", "Article"),
                                    content = aObj.optString("content", ""),
                                    author = aObj.optString("author", "Unknown Author"),
                                    courseId = aObj.optString("courseId", "course_default"),
                                    createdAt = aObj.optLong("createdAt", System.currentTimeMillis()),
                                    wordCount = aObj.optInt("wordCount", 0)
                                )
                            )
                        }
                    }

                    // 4. Games
                    if (root.has("games")) {
                        val gArr = root.getJSONArray("games")
                        for (i in 0 until gArr.length()) {
                            val gObj = gArr.getJSONObject(i)
                            gamesToInsert.add(
                                GamePracticeEntity(
                                    id = gObj.optString("id", "game_$i"),
                                    sheetType = gObj.optString("sheetType", "practice"),
                                    question = gObj.optString("question", ""),
                                    opt1 = gObj.optString("opt1", ""),
                                    opt2 = gObj.optString("opt2", ""),
                                    opt3 = gObj.optString("opt3", ""),
                                    opt4 = gObj.optString("opt4", ""),
                                    answer = gObj.optString("answer", ""),
                                    explanation = gObj.optString("explanation", "").ifEmpty { null }
                                )
                            )
                        }
                    }

                    // 5. Question Bank
                    if (root.has("questionBank")) {
                        val qArr = root.getJSONArray("questionBank")
                        for (i in 0 until qArr.length()) {
                            val qObj = qArr.getJSONObject(i)
                            questionsToInsert.add(
                                QuestionBankEntity(
                                    id = qObj.optString("id", "qb_$i"),
                                    question = qObj.optString("question", ""),
                                    opt1 = qObj.optString("opt1", ""),
                                    opt2 = qObj.optString("opt2", ""),
                                    opt3 = qObj.optString("opt3", ""),
                                    opt4 = qObj.optString("opt4", ""),
                                    answer = qObj.optString("answer", ""),
                                    explanation = qObj.optString("explanation", "").ifEmpty { null },
                                    filter1 = qObj.optString("filter1", "").ifEmpty { null },
                                    filter2 = qObj.optString("filter2", "").ifEmpty { null },
                                    filter3 = qObj.optString("filter3", "").ifEmpty { null }
                                )
                            )
                        }
                    }

                    // 6. Progress
                    if (root.has("progress")) {
                        val pObj = root.getJSONObject("progress")
                        val restoredProg = UserProgressEntity(
                            userId = pObj.optString("userId", userId),
                            totalWords = pObj.optInt("totalWords", wordsToInsert.size),
                            knowCount = pObj.optInt("knowCount", 0),
                            confusionCount = pObj.optInt("confusionCount", 0),
                            dontKnowCount = pObj.optInt("dontKnowCount", 0),
                            unratedCount = pObj.optInt("unratedCount", 0),
                            streakDays = pObj.optInt("streakDays", 1),
                            quizCompleted = pObj.optInt("quizCompleted", 0),
                            quizTotalScore = pObj.optInt("quizTotalScore", 0)
                        )
                        database.userProgressDao().insertOrUpdate(restoredProg)
                    }
                } else {
                    // Legacy JSON array of words directly
                    val rawWords = FileParsers.parseJsonCourse(trimmed)
                    wordsToInsert.addAll(rawWords)
                    val distinctCIds = rawWords.map { it.courseId }.distinct()
                    distinctCIds.forEach { cId ->
                        coursesToInsert.add(CourseEntity(id = cId, title = "Course $cId"))
                    }
                }
            } else {
                // CSV Backup Restore
                val rawWords = FileParsers.parseCourseCsv(trimmed)
                wordsToInsert.addAll(rawWords)

                // Detect courses from CSV lines
                val lines = trimmed.lines().filter { it.isNotBlank() }
                if (lines.size >= 2) {
                    val headerLine = lines[0]
                    val headers = FileParsers.extractHeaders(headerLine).map { it.lowercase() }
                    val cIdIdx = headers.indexOfFirst { it == "courseid" || it == "course_id" }
                    val cTitleIdx = headers.indexOfFirst { it == "coursetitle" || it == "course_title" || it == "coursename" }

                    val courseMap = mutableMapOf<String, String>()
                    if (cIdIdx != -1) {
                        for (i in 1 until lines.size) {
                            val parts = FileParsers.extractHeaders(lines[i])
                            if (cIdIdx in parts.indices && parts[cIdIdx].isNotBlank()) {
                                val cId = parts[cIdIdx].trim()
                                val cTitle = if (cTitleIdx != -1 && cTitleIdx in parts.indices && parts[cTitleIdx].isNotBlank()) {
                                    parts[cTitleIdx].trim()
                                } else {
                                    "Course $cId"
                                }
                                courseMap[cId] = cTitle
                            }
                        }
                    }
                    courseMap.forEach { (id, title) ->
                        coursesToInsert.add(CourseEntity(id = id, title = title))
                    }
                }

                // Ensure every word has a matching course
                wordsToInsert.map { it.courseId }.distinct().forEach { cId ->
                    if (coursesToInsert.none { it.id == cId }) {
                        coursesToInsert.add(CourseEntity(id = cId, title = "Course $cId"))
                    }
                }
            }

            val totalRestoredCount = wordsToInsert.size + coursesToInsert.size + articlesToInsert.size + gamesToInsert.size + questionsToInsert.size
            if (totalRestoredCount > 0) {
                if (coursesToInsert.isNotEmpty()) {
                    database.courseDao().insertCourses(coursesToInsert)
                }
                if (wordsToInsert.isNotEmpty()) {
                    database.vocabularyDao().insertWords(wordsToInsert)
                }
                if (articlesToInsert.isNotEmpty()) {
                    database.articleDao().insertArticles(articlesToInsert)
                }
                if (gamesToInsert.isNotEmpty()) {
                    database.gamePracticeDao().insertItems(gamesToInsert)
                }
                if (questionsToInsert.isNotEmpty()) {
                    database.questionBankDao().insertQuestions(questionsToInsert)
                }

                // Re-sync backup files on device
                saveBackupFiles(userId)
                Result.success(totalRestoredCount)
            } else {
                Result.failure(Exception("No valid data (courses, words, articles, games, or questions) found in file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads and restores backup content directly from a SAF or Google Drive file Uri.
     */
    suspend fun restoreFromUri(uri: Uri, userId: String = "1235"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Failed to read selected file from Google Drive / Storage"))
            val isJson = content.trim().startsWith("{") || content.trim().startsWith("[")
            restoreFromFileContent(content, isJson, userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(s: String): String {
        return s.replace("\"", "\"\"")
    }
}

