package com.example.data.engine

import com.example.data.model.VocabularyWordEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Modular similarity engine for identifying confusion sets in vocabulary courses.
 */
object ConfusionSimilarityEngine {

    enum class ComparisonField(val id: String, val label: String, val placeNumber: Int) {
        PLACE1("place1", "Place 1 (Word)", 1),
        PLACE2("place2", "Place 2 (Meaning)", 2),
        PLACE3("place3", "Place 3 (Synonym / Extra)", 3)
    }

    enum class MatchingMode(val id: String, val label: String) {
        SPELLING("spelling", "Spelling"),
        PRONUNCIATION("pronunciation", "Pronunciation (Phonetic)"),
        COMBINED("combined", "Combined (Spelling + Phonetic)"),
        MEANING("meaning", "Text / Meaning Similarity")
    }

    enum class SimilarityAlgorithm(val id: String, val label: String) {
        LEVENSHTEIN("levenshtein", "Normalized Levenshtein"),
        JARO_WINKLER("jaro_winkler", "Jaro-Winkler")
    }

    enum class SortOption(val id: String, val label: String) {
        RANDOM("random", "Random Shuffle"),
        SIMILARITY_DESC("sim_desc", "Highest Similarity"),
        SIZE_DESC("size_desc", "Largest Group"),
        ALPHABETICAL("alpha", "Alphabetical (A-Z)")
    }

    data class ConfusionConfig(
        val field: ComparisonField = ComparisonField.PLACE1,
        val mode: MatchingMode = MatchingMode.SPELLING,
        val algorithm: SimilarityAlgorithm = SimilarityAlgorithm.LEVENSHTEIN,
        val threshold: Double = 75.0,
        val spellingWeight: Double = 0.70,
        val pronunciationWeight: Double = 0.30,
        val maxClusterSize: Int = 5,
        val sortBy: SortOption = SortOption.RANDOM
    )

    data class ConfusionWordItem(
        val wordEntity: VocabularyWordEntity,
        val place1: String,
        val place2: String,
        val place3: String?,
        val comparedRawValue: String,
        val comparedNormalized: String,
        val phoneticPrimary: String? = null,
        val phoneticSecondary: String? = null
    )

    data class ConfusionPair(
        val word1: ConfusionWordItem,
        val word2: ConfusionWordItem,
        val score: Double,
        val spellingScore: Double,
        val pronunciationScore: Double? = null,
        val meaningScore: Double? = null,
        val matchReason: String
    )

    data class ConfusionGroup(
        val id: String,
        val words: List<ConfusionWordItem>,
        val avgSimilarity: Double,
        val maxSimilarity: Double,
        val representativeReason: String,
        val pairs: List<ConfusionPair>
    )

    // ==========================================
    // 1. Text Normalization
    // ==========================================

    fun isBengaliText(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x0980..0x09FF) return true
        }
        return false
    }

    fun normalizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val nfc = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
        return nfc.replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)
    }

    fun cleanForComparison(input: String?): String {
        val norm = normalizeText(input)
        if (norm.isBlank()) return ""
        // Keep letters (Latin, Bengali, etc.), digits and single spaces, removing punctuation
        val sb = StringBuilder()
        for (ch in norm) {
            if (ch.isLetterOrDigit() || ch == ' ') {
                sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }

    // ==========================================
    // 2. Normalized Levenshtein Similarity
    // ==========================================

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var prev = IntArray(s2.length + 1) { it }
        var curr = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            curr[0] = i
            val c1 = s1[i - 1]
            for (j in 1..s2.length) {
                val cost = if (c1 == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[s2.length]
    }

    fun normalizedLevenshtein(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 0.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 0.0
        val dist = levenshteinDistance(s1, s2)
        val sim = 100.0 * (1.0 - (dist.toDouble() / maxLen.toDouble()))
        return sim.coerceIn(0.0, 100.0)
    }

    // ==========================================
    // 3. Jaro-Winkler Similarity
    // ==========================================

    fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val len1 = s1.length
        val len2 = s2.length
        val matchDistance = maxOf(len1, len2) / 2 - 1

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) {
                k++
            }
            if (s1[i] != s2[k]) {
                transpositions++
            }
            k++
        }

        val jaro = (
            (matches.toDouble() / len1.toDouble()) +
            (matches.toDouble() / len2.toDouble()) +
            ((matches - transpositions / 2.0) / matches.toDouble())
        ) / 3.0

        return jaro
    }

    fun jaroWinklerSimilarity(s1: String, s2: String, prefixScaling: Double = 0.1): Double {
        val jaro = jaroSimilarity(s1, s2)
        var prefix = 0
        val maxPrefix = minOf(4, minOf(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        val jw = jaro + prefix * prefixScaling * (1.0 - jaro)
        return (jw * 100.0).coerceIn(0.0, 100.0)
    }

    // ==========================================
    // 4. Phonetic Encoder (Double Metaphone)
    // ==========================================

    /**
     * Complete Double Metaphone implementation for English pronunciation similarity.
     * Returns Pair(primaryCode, secondaryCode)
     */
    fun doubleMetaphone(input: String): Pair<String, String>? {
        val clean = input.uppercase(Locale.US).filter { it in 'A'..'Z' }
        if (clean.isEmpty()) return null

        val primary = StringBuilder()
        val secondary = StringBuilder()
        val length = clean.length
        val last = length - 1
        var current = 0

        fun charAt(idx: Int): Char = if (idx in 0 until length) clean[idx] else ' '
        fun isVowel(idx: Int): Boolean = charAt(idx) in listOf('A', 'E', 'I', 'O', 'U', 'Y')

        fun stringAt(start: Int, len: Int, vararg targets: String): Boolean {
            if (start < 0 || start + len > length) return false
            val sub = clean.substring(start, start + len)
            return targets.any { it == sub }
        }

        // Handle initial silent letters
        if (stringAt(0, 2, "GN", "KN", "PN", "WR", "PS")) {
            current += 1
        } else if (charAt(0) == 'X') {
            primary.append('S')
            secondary.append('S')
            current += 1
        }

        while (current < length) {
            when (val ch = charAt(current)) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    if (current == 0) {
                        primary.append('A')
                        secondary.append('A')
                    }
                    current += 1
                }
                'B' -> {
                    primary.append('P')
                    secondary.append('P')
                    current += if (charAt(current + 1) == 'B') 2 else 1
                }
                'C' -> {
                    if (stringAt(current, 2, "CH")) {
                        primary.append('X')
                        secondary.append('X')
                        current += 2
                    } else if (stringAt(current, 2, "CZ")) {
                        primary.append('S')
                        secondary.append('S')
                        current += 2
                    } else if (stringAt(current, 2, "CI", "CE", "CY")) {
                        primary.append('S')
                        secondary.append('S')
                        current += 2
                    } else if (stringAt(current, 2, "CK", "CG")) {
                        primary.append('K')
                        secondary.append('K')
                        current += 2
                    } else {
                        primary.append('K')
                        secondary.append('K')
                        current += if (stringAt(current, 2, "CC")) 2 else 1
                    }
                }
                'D' -> {
                    if (stringAt(current, 3, "DGE", "DGI", "DGY")) {
                        primary.append('J')
                        secondary.append('J')
                        current += 3
                    } else {
                        primary.append('T')
                        secondary.append('T')
                        current += if (charAt(current + 1) == 'D') 2 else 1
                    }
                }
                'F' -> {
                    primary.append('F')
                    secondary.append('F')
                    current += if (charAt(current + 1) == 'F') 2 else 1
                }
                'G' -> {
                    if (charAt(current + 1) == 'H') {
                        if (current > 0 && !isVowel(current - 1)) {
                            primary.append('K')
                            secondary.append('K')
                        } else if (current == 0) {
                            if (charAt(current + 2) == 'I') {
                                primary.append('J')
                                secondary.append('J')
                            } else {
                                primary.append('K')
                                secondary.append('K')
                            }
                        }
                        current += 2
                    } else if (charAt(current + 1) == 'N') {
                        if (current == 1 && isVowel(0)) {
                            primary.append("KN")
                            secondary.append('N')
                        } else {
                            primary.append('N')
                            secondary.append('N')
                        }
                        current += 2
                    } else if (stringAt(current + 1, 1, "E", "I", "Y")) {
                        primary.append('J')
                        secondary.append('K')
                        current += 2
                    } else {
                        primary.append('K')
                        secondary.append('K')
                        current += if (charAt(current + 1) == 'G') 2 else 1
                    }
                }
                'H' -> {
                    if ((current == 0 || isVowel(current - 1)) && isVowel(current + 1)) {
                        primary.append('H')
                        secondary.append('H')
                        current += 2
                    } else {
                        current += 1
                    }
                }
                'J' -> {
                    primary.append('J')
                    secondary.append('A')
                    current += if (charAt(current + 1) == 'J') 2 else 1
                }
                'K' -> {
                    primary.append('K')
                    secondary.append('K')
                    current += if (charAt(current + 1) == 'K') 2 else 1
                }
                'L' -> {
                    primary.append('L')
                    secondary.append('L')
                    current += if (charAt(current + 1) == 'L') 2 else 1
                }
                'M' -> {
                    primary.append('M')
                    secondary.append('M')
                    current += if (charAt(current + 1) == 'M') 2 else 1
                }
                'N' -> {
                    primary.append('N')
                    secondary.append('N')
                    current += if (charAt(current + 1) == 'N') 2 else 1
                }
                'P' -> {
                    if (charAt(current + 1) == 'H') {
                        primary.append('F')
                        secondary.append('F')
                        current += 2
                    } else {
                        primary.append('P')
                        secondary.append('P')
                        current += if (stringAt(current + 1, 1, "P", "B")) 2 else 1
                    }
                }
                'Q' -> {
                    primary.append('K')
                    secondary.append('K')
                    current += if (charAt(current + 1) == 'Q') 2 else 1
                }
                'R' -> {
                    primary.append('R')
                    secondary.append('R')
                    current += if (charAt(current + 1) == 'R') 2 else 1
                }
                'S' -> {
                    if (stringAt(current, 2, "SH")) {
                        primary.append('X')
                        secondary.append('X')
                        current += 2
                    } else if (stringAt(current, 3, "SIO", "SIA")) {
                        primary.append('S')
                        secondary.append('X')
                        current += 3
                    } else {
                        primary.append('S')
                        secondary.append('S')
                        current += if (stringAt(current + 1, 1, "S", "Z")) 2 else 1
                    }
                }
                'T' -> {
                    if (stringAt(current, 3, "TIA", "TIO")) {
                        primary.append('X')
                        secondary.append('X')
                        current += 3
                    } else if (stringAt(current, 2, "TH")) {
                        primary.append('0')
                        secondary.append('T')
                        current += 2
                    } else {
                        primary.append('T')
                        secondary.append('T')
                        current += if (stringAt(current + 1, 1, "T", "D")) 2 else 1
                    }
                }
                'V' -> {
                    primary.append('F')
                    secondary.append('F')
                    current += if (charAt(current + 1) == 'V') 2 else 1
                }
                'W' -> {
                    if (isVowel(current + 1)) {
                        primary.append('A')
                        secondary.append('F')
                    }
                    current += 1
                }
                'X' -> {
                    if (current > 0) {
                        primary.append("KS")
                        secondary.append("KS")
                    }
                    current += if (stringAt(current + 1, 1, "C", "X")) 2 else 1
                }
                'Z' -> {
                    primary.append('S')
                    secondary.append('S')
                    current += if (charAt(current + 1) == 'Z') 2 else 1
                }
                else -> current += 1
            }
        }

        val p = primary.toString().take(6)
        val s = secondary.toString().take(6)
        return Pair(p, s)
    }

    fun pronunciationSimilarity(w1: ConfusionWordItem, w2: ConfusionWordItem): Double {
        val p1 = w1.phoneticPrimary
        val p2 = w2.phoneticPrimary
        val s1 = w1.phoneticSecondary
        val s2 = w2.phoneticSecondary

        if (p1 == null || p2 == null) {
            // Fallback to normalized Levenshtein if non-English script
            return normalizedLevenshtein(w1.comparedNormalized, w2.comparedNormalized)
        }

        // Exact primary phonetic code match
        if (p1 == p2 && p1.isNotEmpty()) return 100.0

        // Secondary cross match
        if ((s1 != null && s1 == p2) || (s2 != null && s2 == p1) || (s1 != null && s2 != null && s1 == s2)) {
            return 88.0
        }

        // Levenshtein similarity on phonetic codes
        val codeSim = normalizedLevenshtein(p1, p2)
        return codeSim
    }

    // ==========================================
    // 5. Meaning & Semantic Text Similarity
    // ==========================================

    private val STOP_WORDS = setOf(
        "a", "an", "the", "to", "of", "in", "for", "on", "with", "by", "at", "from",
        "and", "or", "is", "are", "be", "been", "being", "that", "this",
        "করা", "হওয়া", "বা", "এবং", "এর", "একটি", "কোন", "যে", "ও", "থেকে", "দিয়ে"
    )

    fun meaningSimilarity(s1: String, s2: String): Double {
        val clean1 = cleanForComparison(s1)
        val clean2 = cleanForComparison(s2)
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0

        val tokens1 = clean1.split(Regex("""[\s,;/]+""")).filter { it.isNotBlank() && it !in STOP_WORDS }.toSet()
        val tokens2 = clean2.split(Regex("""[\s,;/]+""")).filter { it.isNotBlank() && it !in STOP_WORDS }.toSet()

        val tokenSim: Double = if (tokens1.isNotEmpty() && tokens2.isNotEmpty()) {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            if (union > 0) (intersection.toDouble() / union.toDouble()) * 100.0 else 0.0
        } else 0.0

        val stringSim = normalizedLevenshtein(clean1, clean2)

        // For definitions/meanings: Token overlap carries higher semantic weight, supplemented by string match
        val combined = if (tokens1.size > 1 || tokens2.size > 1) {
            (tokenSim * 0.70) + (stringSim * 0.30)
        } else {
            maxOf(tokenSim, stringSim)
        }
        return combined.coerceIn(0.0, 100.0)
    }

    // ==========================================
    // 6. Pairwise Scoring & Matching Logic
    // ==========================================

    fun scorePair(
        w1: ConfusionWordItem,
        w2: ConfusionWordItem,
        config: ConfusionConfig
    ): ConfusionPair? {
        // Exclude duplicate/identical records per specification:
        // "Exclude null, empty, whitespace-only, and invalid values from comparisons.
        // Do not treat identical values or duplicate records as meaningful confusion pairs."
        if (w1.comparedNormalized.isBlank() || w2.comparedNormalized.isBlank()) return null
        if (w1.comparedNormalized == w2.comparedNormalized) return null
        if (w1.wordEntity.id == w2.wordEntity.id) return null

        val spellingSim = when (config.algorithm) {
            SimilarityAlgorithm.LEVENSHTEIN -> normalizedLevenshtein(w1.comparedNormalized, w2.comparedNormalized)
            SimilarityAlgorithm.JARO_WINKLER -> jaroWinklerSimilarity(w1.comparedNormalized, w2.comparedNormalized)
        }

        var pronunciationSim: Double? = null
        var meaningSim: Double? = null

        val finalScore = when (config.mode) {
            MatchingMode.SPELLING -> spellingSim
            MatchingMode.PRONUNCIATION -> {
                val pSim = pronunciationSimilarity(w1, w2)
                pronunciationSim = pSim
                pSim
            }
            MatchingMode.COMBINED -> {
                val pSim = pronunciationSimilarity(w1, w2)
                pronunciationSim = pSim
                val totalWeight = config.spellingWeight + config.pronunciationWeight
                val sw = if (totalWeight > 0) config.spellingWeight / totalWeight else 0.7
                val pw = if (totalWeight > 0) config.pronunciationWeight / totalWeight else 0.3
                (sw * spellingSim) + (pw * pSim)
            }
            MatchingMode.MEANING -> {
                val mSim = meaningSimilarity(w1.comparedRawValue, w2.comparedRawValue)
                meaningSim = mSim
                mSim
            }
        }

        if (finalScore < config.threshold) return null

        val reason = when (config.mode) {
            MatchingMode.SPELLING -> "Spelling match: %.0f%%".format(Locale.US, spellingSim)
            MatchingMode.PRONUNCIATION -> "Phonetic match: %.0f%%".format(Locale.US, pronunciationSim ?: finalScore)
            MatchingMode.COMBINED -> "Combined: %.0f%% (Spelling %.0f%%, Phonetic %.0f%%)".format(
                Locale.US, finalScore, spellingSim, pronunciationSim ?: 0.0
            )
            MatchingMode.MEANING -> "Meaning overlap: %.0f%%".format(Locale.US, meaningSim ?: finalScore)
        }

        return ConfusionPair(
            word1 = w1,
            word2 = w2,
            score = finalScore,
            spellingScore = spellingSim,
            pronunciationScore = pronunciationSim,
            meaningScore = meaningSim,
            matchReason = reason
        )
    }

    // ==========================================
    // 7. Graph Clustering / Confusion Grouping
    // ==========================================

    private class DisjointSet(n: Int) {
        val parent = IntArray(n) { it }
        val rank = IntArray(n) { 0 }

        fun find(i: Int): Int {
            if (parent[i] != i) {
                parent[i] = find(parent[i])
            }
            return parent[i]
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                if (rank[rootI] < rank[rootJ]) {
                    parent[rootI] = rootJ
                } else if (rank[rootI] > rank[rootJ]) {
                    parent[rootJ] = rootI
                } else {
                    parent[rootJ] = rootI
                    rank[rootI]++
                }
            }
        }
    }

    /**
     * Identifies confusion groups in the provided words according to configuration.
     */
    fun findConfusionGroups(
        words: List<VocabularyWordEntity>,
        config: ConfusionConfig,
        getPlaceValue: (VocabularyWordEntity, Int) -> String
    ): List<ConfusionGroup> {
        if (words.size < 2) return emptyList()

        // 1. Prepare items
        val items = mutableListOf<ConfusionWordItem>()
        for (w in words) {
            val p1 = getPlaceValue(w, 1).trim()
            val p2 = getPlaceValue(w, 2).trim()
            val p3 = getPlaceValue(w, 3).trim().takeIf { it.isNotBlank() }

            val comparedRaw = when (config.field) {
                ComparisonField.PLACE1 -> p1
                ComparisonField.PLACE2 -> p2
                ComparisonField.PLACE3 -> p3 ?: ""
            }

            if (comparedRaw.isBlank()) continue

            val comparedNorm = cleanForComparison(comparedRaw)
            if (comparedNorm.isBlank()) continue

            val phonetics = if (!isBengaliText(comparedNorm)) {
                doubleMetaphone(comparedNorm)
            } else null

            items.add(
                ConfusionWordItem(
                    wordEntity = w,
                    place1 = p1,
                    place2 = p2,
                    place3 = p3,
                    comparedRawValue = comparedRaw,
                    comparedNormalized = comparedNorm,
                    phoneticPrimary = phonetics?.first,
                    phoneticSecondary = phonetics?.second
                )
            )
        }

        if (items.size < 2) return emptyList()

        // 2. Find all pairs meeting threshold
        val pairs = mutableListOf<ConfusionPair>()
        val n = items.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val pair = scorePair(items[i], items[j], config)
                if (pair != null) {
                    pairs.add(pair)
                }
            }
        }

        if (pairs.isEmpty()) return emptyList()

        // 3. Cluster using connected components with size bounding
        // Map word ID to index in items
        val idToIndex = items.mapIndexed { idx, itm -> itm.wordEntity.id to idx }.toMap()
        val dsu = DisjointSet(n)

        // Sort pairs by highest similarity score so strongest connections merge first
        val sortedPairs = pairs.sortedByDescending { it.score }
        for (pair in sortedPairs) {
            val idx1 = idToIndex[pair.word1.wordEntity.id] ?: continue
            val idx2 = idToIndex[pair.word2.wordEntity.id] ?: continue
            dsu.union(idx1, idx2)
        }

        val componentMap = mutableMapOf<Int, MutableList<ConfusionWordItem>>()
        for (i in 0 until n) {
            val root = dsu.find(i)
            componentMap.getOrPut(root) { mutableListOf() }.add(items[i])
        }

        val groups = mutableListOf<ConfusionGroup>()
        var groupIndex = 1

        for ((_, clusterWords) in componentMap) {
            // Exclude singletons
            if (clusterWords.size < 2) continue

            // If cluster is too large (chaining effect), partition into sub-clusters by maxClusterSize
            val chunks = if (config.maxClusterSize in 2..10 && clusterWords.size > config.maxClusterSize) {
                clusterWords.chunked(config.maxClusterSize)
            } else {
                listOf(clusterWords)
            }

            for (chunk in chunks) {
                if (chunk.size < 2) continue

                val chunkWordIds = chunk.map { it.wordEntity.id }.toSet()
                val chunkPairs = pairs.filter {
                    it.word1.wordEntity.id in chunkWordIds && it.word2.wordEntity.id in chunkWordIds
                }

                val avgScore = if (chunkPairs.isNotEmpty()) {
                    chunkPairs.map { it.score }.average()
                } else {
                    config.threshold
                }

                val maxScore = if (chunkPairs.isNotEmpty()) {
                    chunkPairs.maxOf { it.score }
                } else {
                    config.threshold
                }

                val repReason = chunkPairs.maxByOrNull { it.score }?.matchReason
                    ?: "Similarity: %.0f%%".format(Locale.US, avgScore)

                groups.add(
                    ConfusionGroup(
                        id = "group_$groupIndex",
                        words = chunk,
                        avgSimilarity = avgScore,
                        maxSimilarity = maxScore,
                        representativeReason = repReason,
                        pairs = chunkPairs
                    )
                )
                groupIndex++
            }
        }

        // 4. Sort groups according to config
        return when (config.sortBy) {
            SortOption.RANDOM -> groups.shuffled()
            SortOption.SIMILARITY_DESC -> groups.sortedByDescending { it.maxSimilarity }
            SortOption.SIZE_DESC -> groups.sortedWith(compareByDescending<ConfusionGroup> { it.words.size }.thenByDescending { it.maxSimilarity })
            SortOption.ALPHABETICAL -> groups.sortedBy { it.words.firstOrNull()?.place1?.lowercase(Locale.ROOT) ?: "" }
        }
    }
}
