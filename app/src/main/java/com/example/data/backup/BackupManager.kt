package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.data.local.AppDatabase
import com.example.data.model.UserProgressEntity
import com.example.data.model.VocabularyWordEntity
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
     * Public Documents directory on device (outside Android/data) where backup files are saved.
     * Accessible directly via Files / Documents / MemorizerBackup.
     */
    fun getBackupDirectory(): File {
        val customPath = prefs.getString("custom_backup_dir", null)
        if (!customPath.isNullOrBlank()) {
            val customDir = File(customPath)
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir
            }
        }

        // Public Documents directory: /storage/emulated/0/Documents/MemorizerBackup
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val memorizerDir = File(publicDocs, "MemorizerBackup")
        if (memorizerDir.exists() || memorizerDir.mkdirs()) {
            return memorizerDir
        }

        // Fallback to external files dir if Documents cannot be created
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
            val uri = Uri.parse(treeUri)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            return "Custom Folder: $docId"
        }
        return getBackupDirectory().absolutePath
    }

    /**
     * Generates or updates both JSON and CSV files on the device automatically.
     * Writes to the public Documents/MemorizerBackup folder and also to custom SAF folder if granted.
     */
    suspend fun saveBackupFiles(userId: String = "1235"): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        try {
            val words = database.vocabularyDao().getAllWordsList()
            val progress = database.userProgressDao().getProgressOnce(userId)

            // 1. JSON Backup content
            val jsonRoot = JSONObject()
            jsonRoot.put("app", "Memorizer")
            jsonRoot.put("version", "1.0")
            jsonRoot.put("userId", userId)
            jsonRoot.put("timestamp", System.currentTimeMillis())

            val statsObj = JSONObject()
            statsObj.put("totalWords", words.size)
            statsObj.put("knowCount", words.count { it.status == "know" })
            statsObj.put("confusionCount", words.count { it.status == "confusion" })
            statsObj.put("dontKnowCount", words.count { it.status == "dont_know" })
            statsObj.put("unratedCount", words.count { it.status == "unrated" })
            jsonRoot.put("statistics", statsObj)

            val wordsArray = JSONArray()
            words.forEach { w ->
                val wObj = JSONObject()
                wObj.put("id", w.id)
                wObj.put("word", w.word)
                wObj.put("meaning", w.meaning)
                wObj.put("group", w.group)
                wObj.put("synonyms", w.synonyms ?: "")
                wObj.put("extraWord", w.extraWord ?: "")
                wObj.put("example", w.example ?: "")
                wObj.put("mnemonic", w.mnemonic ?: "")
                wObj.put("status", w.status)
                wObj.put("courseId", w.courseId)
                wObj.put("customPlacesJson", w.customPlacesJson ?: "")
                wObj.put("timesReviewed", w.timesReviewed)
                wObj.put("lastReviewedAt", w.lastReviewedAt)
                wordsArray.put(wObj)
            }
            jsonRoot.put("words", wordsArray)

            val jsonString = jsonRoot.toString(2)
            val jsonFile = getJsonBackupFile()
            jsonFile.writeText(jsonString)

            // 2. CSV / Excel format backup content
            val csvSb = java.lang.StringBuilder()
            csvSb.append("id,group,Place1: Word,Place2: Meaning,Place3: Example,Place4: Synonyms,Place5: Extra,Place6: Mnemonic,status,courseId\n")
            words.forEach { w ->
                csvSb.append("\"${w.id}\",")
                csvSb.append("${w.group},")
                csvSb.append("\"${escapeCsv(w.word)}\",")
                csvSb.append("\"${escapeCsv(w.meaning)}\",")
                csvSb.append("\"${escapeCsv(w.example ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.synonyms ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.extraWord ?: "")}\",")
                csvSb.append("\"${escapeCsv(w.mnemonic ?: "")}\",")
                csvSb.append("\"${w.status}\",")
                csvSb.append("\"${w.courseId}\"\n")
            }
            val csvString = csvSb.toString()
            val csvFile = getCsvBackupFile()
            csvFile.writeText(csvString)

            // If user granted permission to a custom SAF folder, write to it as well
            val customTreeUriStr = getCustomTreeUri()
            if (customTreeUriStr != null) {
                try {
                    writeToSafTree(Uri.parse(customTreeUriStr), "memorizer_progress.json", "application/json", jsonString.toByteArray())
                    writeToSafTree(Uri.parse(customTreeUriStr), "memorizer_vocabulary.csv", "text/csv", csvString.toByteArray())
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
            Result.failure(e)
        }
    }

    private fun writeToSafTree(treeUri: Uri, fileName: String, mimeType: String, data: ByteArray) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val newDocUri = DocumentsContract.createDocument(context.contentResolver, dirUri, mimeType, fileName)
        if (newDocUri != null) {
            context.contentResolver.openOutputStream(newDocUri)?.use { os ->
                os.write(data)
            }
        }
    }

    /**
     * Restores state from JSON or CSV text.
     */
    suspend fun restoreFromFileContent(content: String, isJson: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val words = if (isJson) {
                FileParsers.parseJsonCourse(content)
            } else {
                FileParsers.parseCourseCsv(content)
            }

            if (words.isNotEmpty()) {
                database.vocabularyDao().insertWords(words)
                // Save updated backup files
                saveBackupFiles()
                Result.success(words.size)
            } else {
                Result.failure(Exception("No valid vocabulary words found in file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(s: String): String {
        return s.replace("\"", "\"\"")
    }
}

