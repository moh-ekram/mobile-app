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
     * Retrieves the set of courses selected by user for study and backup.
     */
    fun getSelectedCourseIds(): Set<String>? {
        val coursePrefs = context.getSharedPreferences("memorizer_course_prefs", Context.MODE_PRIVATE)
        return coursePrefs.getStringSet("selected_course_ids", null)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Generates the complete JSON backup string containing courses, flashcards, articles, games, and questions.
     * Only includes data belonging to selectedCourseIds if specified.
     */
    suspend fun generateBackupJsonString(userId: String = "1235", selectedCourseIds: Set<String>? = null): String = withContext(Dispatchers.IO) {
        val targetCourseIds = selectedCourseIds ?: getSelectedCourseIds()
        val allCourses = database.courseDao().getAllCoursesList()
        val courses = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
            allCourses.filter { it.id in targetCourseIds }
        } else {
            allCourses
        }

        val allWords = database.vocabularyDao().getAllWordsList()
        val words = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
            allWords.filter { it.courseId in targetCourseIds }
        } else {
            allWords
        }

        val allArticles = database.articleDao().getAllArticlesList()
        val articles = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
            allArticles.filter { it.courseId == null || it.courseId in targetCourseIds }
        } else {
            allArticles
        }

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
            wObj.put("lastQuizStatus", w.lastQuizStatus ?: "not_studied")
            wObj.put("quizCorrectCount", w.quizCorrectCount)
            wObj.put("quizIncorrectCount", w.quizIncorrectCount)
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
            gObj.put("lastAttemptStatus", g.lastAttemptStatus ?: "not_studied")
            gObj.put("correctCount", g.correctCount)
            gObj.put("incorrectCount", g.incorrectCount)
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

        val pToSave = progress ?: UserProgressEntity(
            userId = userId,
            totalWords = words.size,
            knowCount = words.count { it.status.equals("know", ignoreCase = true) },
            confusionCount = words.count { it.status.equals("confusion", ignoreCase = true) },
            dontKnowCount = words.count { it.status.equals("dont_know", ignoreCase = true) },
            unratedCount = words.count { it.status.equals("unrated", ignoreCase = true) },
            streakDays = 1,
            quizCompleted = words.count { it.quizCorrectCount > 0 || (it.lastQuizStatus != null && it.lastQuizStatus != "not_studied") },
            quizTotalScore = words.sumOf { it.quizCorrectCount }
        )
        val pObj = JSONObject()
        pObj.put("userId", pToSave.userId)
        pObj.put("totalWords", pToSave.totalWords)
        pObj.put("knowCount", pToSave.knowCount)
        pObj.put("confusionCount", pToSave.confusionCount)
        pObj.put("dontKnowCount", pToSave.dontKnowCount)
        pObj.put("unratedCount", pToSave.unratedCount)
        pObj.put("streakDays", pToSave.streakDays)
        pObj.put("quizCompleted", pToSave.quizCompleted)
        pObj.put("quizTotalScore", pToSave.quizTotalScore)
        jsonRoot.put("progress", pObj)

        jsonRoot.toString(2)
    }

    /**
     * Exports backup data directly to a user-chosen SAF file URI.
     */
    suspend fun exportBackupToUri(uri: Uri, userId: String = "1235", selectedCourseIds: Set<String>? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonText = generateBackupJsonString(userId, selectedCourseIds)
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
     * Only courses, vocabulary words, and articles belonging to selectedCourseIds are included.
     */
    suspend fun saveBackupFiles(userId: String = "1235", selectedCourseIds: Set<String>? = null): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        try {
            val targetCourseIds = selectedCourseIds ?: getSelectedCourseIds()
            val allCourses = database.courseDao().getAllCoursesList()
            val courses = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
                allCourses.filter { it.id in targetCourseIds }
            } else {
                allCourses
            }

            val allWords = database.vocabularyDao().getAllWordsList()
            val words = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
                allWords.filter { it.courseId in targetCourseIds }
            } else {
                allWords
            }

            val allArticles = database.articleDao().getAllArticlesList()
            val articles = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
                allArticles.filter { it.courseId == null || it.courseId in targetCourseIds }
            } else {
                allArticles
            }

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
                wObj.put("lastQuizStatus", w.lastQuizStatus ?: "not_studied")
                wObj.put("quizCorrectCount", w.quizCorrectCount)
                wObj.put("quizIncorrectCount", w.quizIncorrectCount)
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
                gObj.put("lastAttemptStatus", g.lastAttemptStatus ?: "not_studied")
                gObj.put("correctCount", g.correctCount)
                gObj.put("incorrectCount", g.incorrectCount)
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

            // User Progress - always serialized, fallback calculated from words
            val pToSave = progress ?: UserProgressEntity(
                userId = userId,
                totalWords = words.size,
                knowCount = words.count { it.status.equals("know", ignoreCase = true) },
                confusionCount = words.count { it.status.equals("confusion", ignoreCase = true) },
                dontKnowCount = words.count { it.status.equals("dont_know", ignoreCase = true) },
                unratedCount = words.count { it.status.equals("unrated", ignoreCase = true) },
                streakDays = 1,
                quizCompleted = words.count { it.quizCorrectCount > 0 || (it.lastQuizStatus != null && it.lastQuizStatus != "not_studied") },
                quizTotalScore = words.sumOf { it.quizCorrectCount }
            )
            val pObj = JSONObject()
            pObj.put("userId", pToSave.userId)
            pObj.put("totalWords", pToSave.totalWords)
            pObj.put("knowCount", pToSave.knowCount)
            pObj.put("confusionCount", pToSave.confusionCount)
            pObj.put("dontKnowCount", pToSave.dontKnowCount)
            pObj.put("unratedCount", pToSave.unratedCount)
            pObj.put("streakDays", pToSave.streakDays)
            pObj.put("quizCompleted", pToSave.quizCompleted)
            pObj.put("quizTotalScore", pToSave.quizTotalScore)
            jsonRoot.put("progress", pObj)

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

            val targetCourseIds = getSelectedCourseIds()
            val allCourses = database.courseDao().getAllCoursesList()
            val courses = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
                allCourses.filter { it.id in targetCourseIds }
            } else {
                allCourses
            }
            val allWords = database.vocabularyDao().getAllWordsList()
            val words = if (targetCourseIds != null && targetCourseIds.isNotEmpty()) {
                allWords.filter { it.courseId in targetCourseIds }
            } else {
                allWords
            }
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
            var backupJsonUri: Uri? = null
            var progressJsonUri: Uri? = null
            var anyJsonUri: Uri? = null
            var backupCsvUri: Uri? = null
            var anyCsvUri: Uri? = null

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
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val lower = name.lowercase()
                        when {
                            isTargetFileMatch(name, "memorizer_backup.json") -> backupJsonUri = docUri
                            isTargetFileMatch(name, "memorizer_progress.json") -> progressJsonUri = docUri
                            lower.endsWith(".json") && anyJsonUri == null -> anyJsonUri = docUri
                            isTargetFileMatch(name, "memorizer_vocabulary.csv") -> backupCsvUri = docUri
                            lower.endsWith(".csv") && anyCsvUri == null -> anyCsvUri = docUri
                        }
                    }
                }
            }

            val targetDocUri = backupJsonUri ?: progressJsonUri ?: anyJsonUri ?: backupCsvUri ?: anyCsvUri
            val isJson = targetDocUri == backupJsonUri || targetDocUri == progressJsonUri || targetDocUri == anyJsonUri

            if (targetDocUri == null) {
                return@withContext Result.failure(Exception("No backup file (.json or .csv) found in your linked Drive folder. Please tap 'Backup to Drive' first."))
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

    private fun findStr(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = obj.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun findIntVal(obj: JSONObject, defaultValue: Int, vararg keys: String): Int {
        for (k in keys) {
            if (obj.has(k)) {
                return obj.optInt(k, defaultValue)
            }
        }
        return defaultValue
    }

    /**
     * Restores state from JSON or CSV text.
     * Re-creates the complete course hierarchy so each word is strictly mapped to its course.
     * All items (courses, words, articles, games, question bank, progress) are restored.
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
                    val profObj = root.optJSONObject("profile") ?: root.optJSONObject("userProfile") ?: root.optJSONObject("user")
                    if (profObj != null) {
                        val pName = findStr(profObj, "displayName", "display_name", "name", "Name").ifEmpty { "User #1235" }
                        val pExam = findStr(profObj, "targetExam", "target_exam", "exam", "Exam").ifEmpty { "GRE / IELTS" }
                        val pGoal = findIntVal(profObj, 20, "dailyGoal", "daily_goal", "goal")
                        val pBio = findStr(profObj, "bio", "Bio", "about")
                        var restoredAvatarUri: String? = null

                        val b64 = findStr(profObj, "avatarBase64", "avatar_base64")
                        if (b64.isNotBlank()) {
                            try {
                                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                val avatarFile = File(context.filesDir, "profile_avatar.jpg")
                                avatarFile.writeBytes(bytes)
                                restoredAvatarUri = Uri.fromFile(avatarFile).toString()
                            } catch (e: Exception) {
                                android.util.Log.e("BackupManager", "Failed to decode avatarBase64: ${e.message}")
                            }
                        } else {
                            val rawUri = findStr(profObj, "avatarUri", "avatar_uri", "avatar")
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

                    // Helper lambdas to parse a word JSON object:
                    fun parseWordObj(wObj: JSONObject, fallbackCourseId: String, fallbackCourseTitle: String) {
                        val wId = findStr(wObj, "id", "wordId", "word_id").ifEmpty {
                            "word_${System.currentTimeMillis()}_${wordsToInsert.size}"
                        }
                        var word = findStr(wObj, "word", "Word", "place1", "Place1", "Place 1", "Place1: Word", "Place 1: Word", "term", "Term", "vocabulary", "Vocabulary", "name")
                        if (word.isBlank()) {
                            val keys = wObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val lk = k.lowercase()
                                if (lk.contains("word") || lk.startsWith("place1") || lk.contains("term")) {
                                    val v = wObj.optString(k, "").trim()
                                    if (v.isNotBlank()) { word = v; break }
                                }
                            }
                        }
                        var meaning = findStr(wObj, "meaning", "Meaning", "place2", "Place2", "Place 2", "Place2: Meaning", "Place 2: Meaning", "definition", "Definition", "translation", "Translation", "desc", "description")
                        if (meaning.isBlank()) {
                            val keys = wObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val lk = k.lowercase()
                                if (lk.contains("meaning") || lk.startsWith("place2") || lk.contains("definition")) {
                                    val v = wObj.optString(k, "").trim()
                                    if (v.isNotBlank()) { meaning = v; break }
                                }
                            }
                        }

                        if (word.isNotBlank()) {
                            val rawGrp = findStr(wObj, "group", "Group", "grp", "category", "Category", "groupNumber", "group_number")
                            val wordGroup = if (rawGrp.isNotBlank()) rawGrp else "1"
                            val wordCourseId = findStr(wObj, "courseId", "course_id", "courseID", "course", "CourseId").ifEmpty {
                                fallbackCourseId
                            }
                            val wordCourseTitle = findStr(wObj, "courseTitle", "course_title", "courseName", "course_name", "Course").ifEmpty {
                                fallbackCourseTitle
                            }

                            if (wordCourseId.isNotBlank() && coursesToInsert.none { it.id == wordCourseId }) {
                                coursesToInsert.add(CourseEntity(id = wordCourseId, title = wordCourseTitle.ifEmpty { "Restored Course" }))
                            }

                            wordsToInsert.add(
                                VocabularyWordEntity(
                                    id = wId,
                                    word = word,
                                    meaning = meaning,
                                    group = wordGroup,
                                    synonyms = findStr(wObj, "synonyms", "Synonyms", "synonym", "Synonym", "place4", "Place4", "Place 4").ifEmpty { null },
                                    extraWord = findStr(wObj, "extraWord", "ExtraWord", "extra", "Extra", "forms", "Forms", "place5", "Place5", "Place 5").ifEmpty { null },
                                    extraMeaning = findStr(wObj, "extraMeaning", "ExtraMeaning").ifEmpty { null },
                                    example = findStr(wObj, "example", "Example", "place3", "Place3", "Place 3", "sentence", "Sentence").ifEmpty { null },
                                    mnemonic = findStr(wObj, "mnemonic", "Mnemonic", "notes", "Notes", "hint", "Hint", "place6", "Place6", "Place 6").ifEmpty { null },
                                    status = findStr(wObj, "status", "Status", "rating", "Rating").ifEmpty { "unrated" },
                                    courseId = wordCourseId,
                                    customPlacesJson = findStr(wObj, "customPlacesJson", "placesJson").ifEmpty { null },
                                    timesReviewed = findIntVal(wObj, 0, "timesReviewed", "times_reviewed"),
                                    lastReviewedAt = if (wObj.has("lastReviewedAt")) wObj.optLong("lastReviewedAt") else System.currentTimeMillis(),
                                    lastQuizStatus = findStr(wObj, "lastQuizStatus", "last_quiz_status", "quizStatus").ifEmpty { "not_studied" },
                                    quizCorrectCount = findIntVal(wObj, 0, "quizCorrectCount", "quiz_correct_count", "correctCount", "correct"),
                                    quizIncorrectCount = findIntVal(wObj, 0, "quizIncorrectCount", "quiz_incorrect_count", "incorrectCount", "incorrect")
                                )
                            )
                        }
                    }

                    // Helper to parse game item:
                    fun parseGameObj(gObj: JSONObject, defaultType: String) {
                        val q = findStr(gObj, "question", "Question", "q", "Q", "prompt", "Prompt", "title")
                        val o1 = findStr(gObj, "opt1", "Opt1", "option1", "Option1", "a", "A", "choice1", "Choice1")
                        val o2 = findStr(gObj, "opt2", "Opt2", "option2", "Option2", "b", "B", "choice2", "Choice2")
                        val o3 = findStr(gObj, "opt3", "Opt3", "option3", "Option3", "c", "C", "choice3", "Choice3")
                        val o4 = findStr(gObj, "opt4", "Opt4", "option4", "Option4", "d", "D", "choice4", "Choice4")
                        val ans = findStr(gObj, "answer", "Answer", "ans", "Ans", "correctAnswer", "correct_answer", "solution")

                        if (q.isNotBlank() && (ans.isNotBlank() || o1.isNotBlank())) {
                            val gId = findStr(gObj, "id", "gameId", "game_id").ifEmpty {
                                "game_${System.currentTimeMillis()}_${gamesToInsert.size}"
                            }
                            val sType = findStr(gObj, "sheetType", "sheet_type", "type", "Type").ifEmpty { defaultType }
                            gamesToInsert.add(
                                GamePracticeEntity(
                                    id = gId,
                                    sheetType = sType,
                                    question = q,
                                    opt1 = o1,
                                    opt2 = o2,
                                    opt3 = o3,
                                    opt4 = o4,
                                    answer = ans,
                                    explanation = findStr(gObj, "explanation", "Explanation", "exp", "Exp", "reason").ifEmpty { null },
                                    lastAttemptStatus = findStr(gObj, "lastAttemptStatus", "last_attempt_status", "status").ifEmpty { "not_studied" },
                                    correctCount = findIntVal(gObj, 0, "correctCount", "correct_count", "correct"),
                                    incorrectCount = findIntVal(gObj, 0, "incorrectCount", "incorrect_count", "incorrect")
                                )
                            )
                        }
                    }

                    // 1. Courses (and any nested words / games inside course objects)
                    val cArr = root.optJSONArray("courses") ?: root.optJSONArray("courseList")
                    if (cArr != null) {
                        for (i in 0 until cArr.length()) {
                            val cObj = cArr.getJSONObject(i)
                            val cId = findStr(cObj, "id", "courseId", "course_id").ifEmpty { "course_${System.currentTimeMillis()}_$i" }
                            val cTitle = findStr(cObj, "title", "name", "courseTitle", "courseName").ifEmpty { "Course ${i + 1}" }
                            val cDesc = findStr(cObj, "description", "desc").ifEmpty { null }
                            val cCreated = if (cObj.has("createdAt")) cObj.optLong("createdAt") else System.currentTimeMillis()
                            val cHeaders = findStr(cObj, "columnHeadersJson", "headers").ifEmpty { null }

                            if (coursesToInsert.none { it.id == cId }) {
                                coursesToInsert.add(
                                    CourseEntity(
                                        id = cId,
                                        title = cTitle,
                                        description = cDesc,
                                        createdAt = cCreated,
                                        columnHeadersJson = cHeaders
                                    )
                                )
                            }

                            // Nested words inside course
                            val nestedWords = cObj.optJSONArray("words") ?: cObj.optJSONArray("vocabulary") ?: cObj.optJSONArray("items") ?: cObj.optJSONArray("flashcards")
                            if (nestedWords != null) {
                                for (wIdx in 0 until nestedWords.length()) {
                                    val wObj = nestedWords.getJSONObject(wIdx)
                                    parseWordObj(wObj, fallbackCourseId = cId, fallbackCourseTitle = cTitle)
                                }
                            }

                            // Nested games inside course
                            val nestedGames = cObj.optJSONArray("games") ?: cObj.optJSONArray("gamePractice") ?: cObj.optJSONArray("quizzes")
                            if (nestedGames != null) {
                                for (gIdx in 0 until nestedGames.length()) {
                                    val gObj = nestedGames.getJSONObject(gIdx)
                                    parseGameObj(gObj, defaultType = "practice")
                                }
                            }

                            // Nested articles inside course
                            val nestedArticles = cObj.optJSONArray("articles")
                            if (nestedArticles != null) {
                                for (aIdx in 0 until nestedArticles.length()) {
                                    val aObj = nestedArticles.getJSONObject(aIdx)
                                    val aTitle = findStr(aObj, "title", "Title").ifEmpty { "Article" }
                                    val aContent = findStr(aObj, "content", "Content", "body", "text")
                                    if (aContent.isNotBlank()) {
                                        articlesToInsert.add(
                                            ArticleEntity(
                                                id = findStr(aObj, "id").ifEmpty { "art_${System.currentTimeMillis()}_$aIdx" },
                                                title = aTitle,
                                                content = aContent,
                                                author = findStr(aObj, "author").ifEmpty { "Author" },
                                                courseId = cId,
                                                createdAt = if (aObj.has("createdAt")) aObj.optLong("createdAt") else System.currentTimeMillis(),
                                                wordCount = findIntVal(aObj, aContent.split("\\s+".toRegex()).size, "wordCount", "word_count")
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Root-level Words
                    val wArr = root.optJSONArray("words") ?: root.optJSONArray("vocabulary") ?: root.optJSONArray("items") ?: root.optJSONArray("flashcards") ?: root.optJSONArray("cards")
                    if (wArr != null) {
                        val fallbackCId = if (coursesToInsert.isNotEmpty()) coursesToInsert.first().id else "course_restored_${System.currentTimeMillis()}"
                        val fallbackCTitle = if (coursesToInsert.isNotEmpty()) coursesToInsert.first().title else "Restored Course"
                        for (i in 0 until wArr.length()) {
                            val wObj = wArr.getJSONObject(i)
                            parseWordObj(wObj, fallbackCourseId = fallbackCId, fallbackCourseTitle = fallbackCTitle)
                        }
                    }

                    // 3. Root-level Articles
                    val aArr = root.optJSONArray("articles")
                    if (aArr != null) {
                        for (i in 0 until aArr.length()) {
                            val aObj = aArr.getJSONObject(i)
                            val aTitle = findStr(aObj, "title", "Title").ifEmpty { "Article ${i + 1}" }
                            val aContent = findStr(aObj, "content", "Content", "body", "text")
                            val aCourseId = findStr(aObj, "courseId", "course_id").ifEmpty {
                                if (coursesToInsert.isNotEmpty()) coursesToInsert.first().id else "course_restored_1"
                            }
                            if (aContent.isNotBlank()) {
                                articlesToInsert.add(
                                    ArticleEntity(
                                        id = findStr(aObj, "id").ifEmpty { "art_$i" },
                                        title = aTitle,
                                        content = aContent,
                                        author = findStr(aObj, "author").ifEmpty { "Unknown Author" },
                                        courseId = aCourseId,
                                        createdAt = if (aObj.has("createdAt")) aObj.optLong("createdAt") else System.currentTimeMillis(),
                                        wordCount = findIntVal(aObj, aContent.split("\\s+".toRegex()).size, "wordCount")
                                    )
                                )
                            }
                        }
                    }

                    // 4. Root-level Games (check all variations and sheet types)
                    val gamesArr = root.optJSONArray("games")
                        ?: root.optJSONArray("gamePractice")
                        ?: root.optJSONArray("game_practice")
                        ?: root.optJSONArray("gamesPractice")
                        ?: root.optJSONArray("practiceGames")
                        ?: root.optJSONArray("quizzes")
                        ?: root.optJSONArray("quiz")
                        ?: root.optJSONArray("gameData")
                        ?: root.optJSONArray("practice")
                    if (gamesArr != null) {
                        for (i in 0 until gamesArr.length()) {
                            val gObj = gamesArr.getJSONObject(i)
                            parseGameObj(gObj, defaultType = "practice")
                        }
                    }

                    // Specific game sheets
                    val oooArr = root.optJSONArray("oddOneOut") ?: root.optJSONArray("odd_one_out")
                    if (oooArr != null) {
                        for (i in 0 until oooArr.length()) {
                            parseGameObj(oooArr.getJSONObject(i), defaultType = "odd_one_out")
                        }
                    }
                    val anaArr = root.optJSONArray("analogy") ?: root.optJSONArray("analogies")
                    if (anaArr != null) {
                        for (i in 0 until anaArr.length()) {
                            parseGameObj(anaArr.getJSONObject(i), defaultType = "analogy")
                        }
                    }
                    val matchArr = root.optJSONArray("matching") ?: root.optJSONArray("match")
                    if (matchArr != null) {
                        for (i in 0 until matchArr.length()) {
                            parseGameObj(matchArr.getJSONObject(i), defaultType = "match")
                        }
                    }

                    // 5. Root-level Question Bank
                    val qArr = root.optJSONArray("questionBank")
                        ?: root.optJSONArray("question_bank")
                        ?: root.optJSONArray("questions")
                        ?: root.optJSONArray("qb")
                    if (qArr != null) {
                        for (i in 0 until qArr.length()) {
                            val qObj = qArr.getJSONObject(i)
                            val qText = findStr(qObj, "question", "Question", "q", "Q", "prompt")
                            val qAns = findStr(qObj, "answer", "Answer", "ans", "Ans", "correctAnswer")
                            val o1 = findStr(qObj, "opt1", "Opt1", "option1", "Option1", "a", "A")
                            val o2 = findStr(qObj, "opt2", "Opt2", "option2", "Option2", "b", "B")
                            val o3 = findStr(qObj, "opt3", "Opt3", "option3", "Option3", "c", "C")
                            val o4 = findStr(qObj, "opt4", "Opt4", "option4", "Option4", "d", "D")

                            if (qText.isNotBlank() && (qAns.isNotBlank() || o1.isNotBlank())) {
                                questionsToInsert.add(
                                    QuestionBankEntity(
                                        id = findStr(qObj, "id", "qbId").ifEmpty { "qb_$i" },
                                        question = qText,
                                        opt1 = o1,
                                        opt2 = o2,
                                        opt3 = o3,
                                        opt4 = o4,
                                        answer = qAns,
                                        explanation = findStr(qObj, "explanation", "Explanation", "exp").ifEmpty { null },
                                        filter1 = findStr(qObj, "filter1", "filter1Label").ifEmpty { null },
                                        filter2 = findStr(qObj, "filter2", "filter2Label").ifEmpty { null },
                                        filter3 = findStr(qObj, "filter3", "filter3Label").ifEmpty { null }
                                    )
                                )
                            }
                        }
                    }

                    // 6. Progress Restoration (with smart derivation from restored words)
                    val pObj = root.optJSONObject("progress")
                        ?: root.optJSONObject("userProgress")
                        ?: root.optJSONObject("user_progress")
                        ?: root.optJSONObject("statistics")
                        ?: root.optJSONObject("stats")

                    val knowFromWords = wordsToInsert.count { it.status.equals("know", ignoreCase = true) }
                    val confFromWords = wordsToInsert.count { it.status.equals("confusion", ignoreCase = true) }
                    val dontKnowFromWords = wordsToInsert.count { it.status.equals("dont_know", ignoreCase = true) }
                    val unratedFromWords = wordsToInsert.count { it.status.equals("unrated", ignoreCase = true) }
                    val quizFromWords = wordsToInsert.count { it.quizCorrectCount > 0 || (it.lastQuizStatus != null && it.lastQuizStatus != "not_studied") }
                    val quizScoreFromWords = wordsToInsert.sumOf { it.quizCorrectCount }

                    val pKnow = pObj?.optInt("knowCount", 0) ?: 0
                    val pConf = pObj?.optInt("confusionCount", 0) ?: 0
                    val pDontKnow = pObj?.optInt("dontKnowCount", 0) ?: 0
                    val pUnrated = pObj?.optInt("unratedCount", 0) ?: 0
                    val pTotal = pObj?.optInt("totalWords", 0) ?: 0
                    val pStreak = pObj?.optInt("streakDays", 1) ?: 1
                    val pQuiz = pObj?.optInt("quizCompleted", 0) ?: 0
                    val pQuizScore = pObj?.optInt("quizTotalScore", 0) ?: 0

                    val finalKnow = if (pKnow > 0) pKnow else knowFromWords
                    val finalConf = if (pConf > 0) pConf else confFromWords
                    val finalDontKnow = if (pDontKnow > 0) pDontKnow else dontKnowFromWords
                    val finalUnrated = if (pUnrated > 0) pUnrated else unratedFromWords
                    val finalTotal = if (pTotal > 0) pTotal else wordsToInsert.size
                    val finalStreak = if (pStreak > 0) pStreak else 1
                    val finalQuiz = if (pQuiz > 0) pQuiz else quizFromWords
                    val finalQuizScore = if (pQuizScore > 0) pQuizScore else quizScoreFromWords

                    val targetUids = setOf(userId, "1235")
                    targetUids.forEach { uid ->
                        val restoredProg = UserProgressEntity(
                            userId = uid,
                            totalWords = finalTotal,
                            knowCount = finalKnow,
                            confusionCount = finalConf,
                            dontKnowCount = finalDontKnow,
                            unratedCount = finalUnrated,
                            streakDays = finalStreak,
                            quizCompleted = finalQuiz,
                            quizTotalScore = finalQuizScore
                        )
                        database.userProgressDao().insertOrUpdate(restoredProg)
                    }
                } else {
                    // JSON array [...] at root
                    val rootArr = JSONArray(trimmed)
                    var hasCoursesOrWords = false
                    for (i in 0 until rootArr.length()) {
                        val item = rootArr.optJSONObject(i) ?: continue
                        if (item.has("title") || item.has("courseTitle") || item.has("name")) {
                            val cId = findStr(item, "id", "courseId").ifEmpty { "course_${System.currentTimeMillis()}_$i" }
                            val cTitle = findStr(item, "title", "courseTitle", "name").ifEmpty { "Course ${i + 1}" }
                            if (coursesToInsert.none { it.id == cId }) {
                                coursesToInsert.add(CourseEntity(id = cId, title = cTitle))
                            }
                            val nestedW = item.optJSONArray("words") ?: item.optJSONArray("vocabulary")
                            if (nestedW != null) {
                                for (j in 0 until nestedW.length()) {
                                    val wObj = nestedW.getJSONObject(j)
                                    val word = findStr(wObj, "word", "Word", "place1", "Place1")
                                    val meaning = findStr(wObj, "meaning", "Meaning", "place2", "Place2")
                                    if (word.isNotBlank()) {
                                        wordsToInsert.add(
                                            VocabularyWordEntity(
                                                id = findStr(wObj, "id").ifEmpty { "word_${System.currentTimeMillis()}_${wordsToInsert.size}" },
                                                word = word,
                                                meaning = meaning,
                                                group = findStr(wObj, "group").ifEmpty { "1" },
                                                courseId = cId
                                            )
                                        )
                                    }
                                }
                            }
                            hasCoursesOrWords = true
                        } else if (item.has("question") || item.has("Question")) {
                            val q = findStr(item, "question", "Question")
                            val ans = findStr(item, "answer", "Answer", "ans")
                            if (q.isNotBlank()) {
                                gamesToInsert.add(
                                    GamePracticeEntity(
                                        id = "game_${System.currentTimeMillis()}_$i",
                                        question = q,
                                        opt1 = findStr(item, "opt1", "option1"),
                                        opt2 = findStr(item, "opt2", "option2"),
                                        opt3 = findStr(item, "opt3", "option3"),
                                        opt4 = findStr(item, "opt4", "option4"),
                                        answer = ans,
                                        sheetType = findStr(item, "sheetType").ifEmpty { "practice" }
                                    )
                                )
                            }
                        } else if (item.has("word") || item.has("Word") || item.has("place1")) {
                            val word = findStr(item, "word", "Word", "place1", "Place1")
                            val meaning = findStr(item, "meaning", "Meaning", "place2", "Place2")
                            val cId = findStr(item, "courseId", "course_id").ifEmpty { "course_restored_1" }
                            if (word.isNotBlank()) {
                                if (coursesToInsert.none { it.id == cId }) {
                                    coursesToInsert.add(CourseEntity(id = cId, title = findStr(item, "courseTitle").ifEmpty { "Restored Course" }))
                                }
                                wordsToInsert.add(
                                    VocabularyWordEntity(
                                        id = findStr(item, "id").ifEmpty { "word_${System.currentTimeMillis()}_$i" },
                                        word = word,
                                        meaning = meaning,
                                        group = findStr(item, "group").ifEmpty { "1" },
                                        courseId = cId
                                    )
                                )
                            }
                            hasCoursesOrWords = true
                        }
                    }

                    if (!hasCoursesOrWords) {
                        val rawWords = FileParsers.parseJsonCourse(trimmed)
                        wordsToInsert.addAll(rawWords)
                        val distinctCIds = rawWords.map { it.courseId }.distinct()
                        distinctCIds.forEach { cId ->
                            coursesToInsert.add(CourseEntity(id = cId, title = "Course $cId"))
                        }
                    }
                }
            } else {
                // CSV Backup Restore
                val rawWords = FileParsers.parseCourseCsv(trimmed)
                wordsToInsert.addAll(rawWords)

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

            // Clean up any old sample course or Barron's references
            database.courseDao().deleteCourseById("course_default")
            database.vocabularyDao().deleteWordsByCourse("course_default")
            val existing = database.courseDao().getAllCoursesList()
            existing.filter { it.title.contains("Barron", ignoreCase = true) }.forEach {
                database.courseDao().deleteCourseById(it.id)
                database.vocabularyDao().deleteWordsByCourse(it.id)
            }

            // If words exist but courseId is pointing to course_default or missing, remap to the first restored course
            if (coursesToInsert.isNotEmpty()) {
                val validCourseId = coursesToInsert.first().id
                val remappedWords = wordsToInsert.map { w ->
                    if (w.courseId == "course_default" || coursesToInsert.none { it.id == w.courseId }) {
                        w.copy(courseId = validCourseId)
                    } else {
                        w
                    }
                }
                wordsToInsert.clear()
                wordsToInsert.addAll(remappedWords)
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

