package com.example.data.engine

import android.content.Context
import android.util.Log
import com.example.data.engine.ConfusionSimilarityEngine.ComparisonField
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionConfig
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionGroup
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionPair
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionWordItem
import com.example.data.engine.ConfusionSimilarityEngine.MatchingMode
import com.example.data.engine.ConfusionSimilarityEngine.SimilarityAlgorithm
import com.example.data.engine.ConfusionSimilarityEngine.SortOption
import com.example.data.model.VocabularyWordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent local storage for Confusion Set matched groups and settings.
 * Saves matched groups into internal app storage (JSON) and configuration in SharedPreferences.
 * Prevents unnecessary re-computation and resets across app sessions and screen navigations.
 */
object ConfusionSetCacheManager {

    private const val TAG = "ConfusionCache"
    private const val PREFS_NAME = "confusion_set_storage_prefs"
    private const val KEY_LAST_COURSE_ID = "last_course_id"
    private const val KEY_CONFIG_PREFIX = "config_saved"
    private const val CACHE_DIR_NAME = "confusion_cache"

    data class CacheResult(
        val groups: List<ConfusionGroup>,
        val timestamp: Long,
        val isExactMatch: Boolean
    )

    // ==========================================
    // 1. SharedPreferences: Course & Config
    // ==========================================

    fun saveLastCourseId(context: Context, courseId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_COURSE_ID, courseId).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last course ID: ${e.message}")
        }
    }

    fun getLastCourseId(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_COURSE_ID, null)
        } catch (e: Exception) {
            null
        }
    }

    fun saveConfig(context: Context, config: ConfusionConfig) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("field", config.field.id)
                put("mode", config.mode.id)
                put("algorithm", config.algorithm.id)
                put("threshold", config.threshold)
                put("spellingWeight", config.spellingWeight)
                put("pronunciationWeight", config.pronunciationWeight)
                put("maxClusterSize", config.maxClusterSize)
                put("sortBy", config.sortBy.id)
            }
            prefs.edit().putString(KEY_CONFIG_PREFIX, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${e.message}")
        }
    }

    fun loadConfig(context: Context): ConfusionConfig {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CONFIG_PREFIX, null) ?: return ConfusionConfig()
            val obj = JSONObject(jsonStr)

            val field = ComparisonField.entries.find { it.id == obj.optString("field") } ?: ComparisonField.PLACE1
            val mode = MatchingMode.entries.find { it.id == obj.optString("mode") } ?: MatchingMode.SPELLING
            val algorithm = SimilarityAlgorithm.entries.find { it.id == obj.optString("algorithm") } ?: SimilarityAlgorithm.LEVENSHTEIN
            val threshold = obj.optDouble("threshold", 75.0)
            val spellingWeight = obj.optDouble("spellingWeight", 0.70)
            val pronunciationWeight = obj.optDouble("pronunciationWeight", 0.30)
            val maxClusterSize = obj.optInt("maxClusterSize", 5)
            val sortBy = SortOption.entries.find { it.id == obj.optString("sortBy") } ?: SortOption.SIMILARITY_DESC

            ConfusionConfig(
                field = field,
                mode = mode,
                algorithm = algorithm,
                threshold = threshold,
                spellingWeight = spellingWeight,
                pronunciationWeight = pronunciationWeight,
                maxClusterSize = maxClusterSize,
                sortBy = sortBy
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config, using default: ${e.message}")
            ConfusionConfig()
        }
    }

    // ==========================================
    // 2. Fingerprinting
    // ==========================================

    fun computeFingerprint(
        courseId: String,
        words: List<VocabularyWordEntity>,
        config: ConfusionConfig
    ): String {
        // Fast hash fingerprint: courseId + count + ids/words checksum + config params
        var wordsHash = 0
        for (w in words) {
            wordsHash = 31 * wordsHash + w.id.hashCode() + w.word.hashCode()
        }
        return "${courseId}_${words.size}_${wordsHash}_${config.field.id}_${config.mode.id}_${config.algorithm.id}_${config.threshold.toInt()}_${config.maxClusterSize}_${config.sortBy.id}"
    }

    // ==========================================
    // 3. Cache Storage (Files in internal cache)
    // ==========================================

    private fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getCacheFile(context: Context, courseId: String): File {
        val safeId = courseId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        return File(getCacheDir(context), "cache_$safeId.json")
    }

    /**
     * Saves computed confusion groups to local storage.
     */
    fun saveConfusionGroups(
        context: Context,
        courseId: String,
        words: List<VocabularyWordEntity>,
        config: ConfusionConfig,
        groups: List<ConfusionGroup>
    ) {
        try {
            val file = getCacheFile(context, courseId)
            val root = JSONObject()
            val timestamp = System.currentTimeMillis()
            val fingerprint = computeFingerprint(courseId, words, config)

            root.put("version", 1)
            root.put("courseId", courseId)
            root.put("timestamp", timestamp)
            root.put("fingerprint", fingerprint)
            root.put("groupCount", groups.size)

            val groupsArray = JSONArray()
            for (group in groups) {
                val groupObj = JSONObject()
                groupObj.put("id", group.id)
                groupObj.put("avgSimilarity", group.avgSimilarity)
                groupObj.put("maxSimilarity", group.maxSimilarity)
                groupObj.put("representativeReason", group.representativeReason)

                // Serialized words in this group
                val wordsArray = JSONArray()
                for (item in group.words) {
                    val wObj = JSONObject()
                    wObj.put("wordId", item.wordEntity.id)
                    wObj.put("word", item.wordEntity.word)
                    wObj.put("meaning", item.wordEntity.meaning)
                    wObj.put("synonyms", item.wordEntity.synonyms)
                    wObj.put("courseId", item.wordEntity.courseId)
                    wObj.put("status", item.wordEntity.status)
                    wObj.put("place1", item.place1)
                    wObj.put("place2", item.place2)
                    wObj.put("place3", item.place3 ?: "")
                    wObj.put("comparedRawValue", item.comparedRawValue)
                    wObj.put("comparedNormalized", item.comparedNormalized)
                    wObj.put("phoneticPrimary", item.phoneticPrimary ?: "")
                    wObj.put("phoneticSecondary", item.phoneticSecondary ?: "")
                    wordsArray.put(wObj)
                }
                groupObj.put("words", wordsArray)

                // Serialized pairs in this group
                val pairsArray = JSONArray()
                for (pair in group.pairs) {
                    val pObj = JSONObject()
                    pObj.put("word1Id", pair.word1.wordEntity.id)
                    pObj.put("word2Id", pair.word2.wordEntity.id)
                    pObj.put("score", pair.score)
                    pObj.put("spellingScore", pair.spellingScore)
                    pObj.put("pronunciationScore", pair.pronunciationScore ?: -1.0)
                    pObj.put("meaningScore", pair.meaningScore ?: -1.0)
                    pObj.put("matchReason", pair.matchReason)
                    pairsArray.put(pObj)
                }
                groupObj.put("pairs", pairsArray)

                groupsArray.put(groupObj)
            }

            root.put("groups", groupsArray)

            // Atomic file write
            val tempFile = File(getCacheDir(context), "cache_${courseId}_tmp.json")
            tempFile.writeText(root.toString())
            if (tempFile.renameTo(file).not()) {
                file.writeText(root.toString())
                tempFile.delete()
            }
            Log.d(TAG, "Successfully cached ${groups.size} confusion groups for course $courseId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save confusion groups to local storage: ${e.message}", e)
        }
    }

    /**
     * Loads confusion groups from local storage.
     * Updates each word with its current DB state (e.g., status: know/dont_know) from [currentWords].
     */
    fun loadConfusionGroups(
        context: Context,
        courseId: String,
        currentWords: List<VocabularyWordEntity>,
        config: ConfusionConfig
    ): CacheResult? {
        try {
            val file = getCacheFile(context, courseId)
            if (!file.exists()) return null

            val text = file.readText()
            if (text.isBlank()) return null

            val root = JSONObject(text)
            val cachedFingerprint = root.optString("fingerprint", "")
            val timestamp = root.optLong("timestamp", 0L)
            val currentFingerprint = computeFingerprint(courseId, currentWords, config)
            val isExactMatch = (cachedFingerprint == currentFingerprint)

            val groupsArray = root.getJSONArray("groups")
            val currentWordsMap = currentWords.associateBy { it.id }

            val groups = mutableListOf<ConfusionGroup>()

            for (i in 0 until groupsArray.length()) {
                val groupObj = groupsArray.getJSONObject(i)
                val groupId = groupObj.getString("id")
                val avgSimilarity = groupObj.getDouble("avgSimilarity")
                val maxSimilarity = groupObj.getDouble("maxSimilarity")
                val representativeReason = groupObj.optString("representativeReason", "")

                val wordsArray = groupObj.getJSONArray("words")
                val wordItemsMap = mutableMapOf<String, ConfusionWordItem>()
                val wordItemsList = mutableListOf<ConfusionWordItem>()

                for (j in 0 until wordsArray.length()) {
                    val wObj = wordsArray.getJSONObject(j)
                    val wordId = wObj.getString("wordId")

                    // Refresh latest entity state from DB if available, keeping user rating up-to-date
                    val entity = currentWordsMap[wordId] ?: VocabularyWordEntity(
                        id = wordId,
                        word = wObj.optString("word", ""),
                        meaning = wObj.optString("meaning", ""),
                        synonyms = wObj.optString("synonyms", ""),
                        courseId = wObj.optString("courseId", courseId),
                        status = wObj.optString("status", "unrated")
                    )

                    val place3Str = wObj.optString("place3").takeIf { it.isNotBlank() }
                    val pPrimary = wObj.optString("phoneticPrimary").takeIf { it.isNotBlank() }
                    val pSecondary = wObj.optString("phoneticSecondary").takeIf { it.isNotBlank() }

                    val item = ConfusionWordItem(
                        wordEntity = entity,
                        place1 = wObj.optString("place1", entity.word),
                        place2 = wObj.optString("place2", entity.meaning),
                        place3 = place3Str,
                        comparedRawValue = wObj.optString("comparedRawValue", ""),
                        comparedNormalized = wObj.optString("comparedNormalized", ""),
                        phoneticPrimary = pPrimary,
                        phoneticSecondary = pSecondary
                    )

                    wordItemsMap[wordId] = item
                    wordItemsList.add(item)
                }

                // Reconstruct pairs
                val pairsArray = groupObj.getJSONArray("pairs")
                val pairsList = mutableListOf<ConfusionPair>()

                for (k in 0 until pairsArray.length()) {
                    val pObj = pairsArray.getJSONObject(k)
                    val w1Id = pObj.getString("word1Id")
                    val w2Id = pObj.getString("word2Id")
                    val w1 = wordItemsMap[w1Id]
                    val w2 = wordItemsMap[w2Id]

                    if (w1 != null && w2 != null) {
                        val pronScore = pObj.optDouble("pronunciationScore", -1.0).takeIf { it >= 0 }
                        val meanScore = pObj.optDouble("meaningScore", -1.0).takeIf { it >= 0 }
                        pairsList.add(
                            ConfusionPair(
                                word1 = w1,
                                word2 = w2,
                                score = pObj.getDouble("score"),
                                spellingScore = pObj.getDouble("spellingScore"),
                                pronunciationScore = pronScore,
                                meaningScore = meanScore,
                                matchReason = pObj.optString("matchReason", "")
                            )
                        )
                    }
                }

                groups.add(
                    ConfusionGroup(
                        id = groupId,
                        words = wordItemsList,
                        avgSimilarity = avgSimilarity,
                        maxSimilarity = maxSimilarity,
                        representativeReason = representativeReason,
                        pairs = pairsList
                    )
                )
            }

            return CacheResult(
                groups = groups,
                timestamp = timestamp,
                isExactMatch = isExactMatch
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading confusion cache for $courseId: ${e.message}", e)
            return null
        }
    }

    /**
     * Clears cached matches for a given course.
     */
    fun clearCacheForCourse(context: Context, courseId: String) {
        try {
            val file = getCacheFile(context, courseId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for $courseId: ${e.message}")
        }
    }

    /**
     * Clears all cached confusion sets.
     */
    fun clearAllCache(context: Context) {
        try {
            val dir = getCacheDir(context)
            dir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache: ${e.message}")
        }
    }
}
