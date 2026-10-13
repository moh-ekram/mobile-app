package com.example

import com.example.data.engine.ConfusionSimilarityEngine
import com.example.data.engine.ConfusionSimilarityEngine.ComparisonField
import com.example.data.engine.ConfusionSimilarityEngine.ConfusionConfig
import com.example.data.engine.ConfusionSimilarityEngine.MatchingMode
import com.example.data.engine.ConfusionSimilarityEngine.SimilarityAlgorithm
import com.example.data.model.VocabularyWordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfusionSimilarityEngineTest {

    @Test
    fun testTextNormalization() {
        val raw = "   Affect,  "
        val norm = ConfusionSimilarityEngine.normalizeText(raw)
        assertEquals("affect,", norm)

        val clean = ConfusionSimilarityEngine.cleanForComparison(raw)
        assertEquals("affect", clean)
    }

    @Test
    fun testLevenshteinSimilarity() {
        // "affect" vs "effect"
        val sim = ConfusionSimilarityEngine.normalizedLevenshtein("affect", "effect")
        // 5 characters match out of 6 -> ~83.3%
        assertTrue("Expected similarity > 80%", sim > 80.0)

        // "allusion" vs "illusion"
        val sim2 = ConfusionSimilarityEngine.normalizedLevenshtein("allusion", "illusion")
        assertTrue("Expected similarity > 80%", sim2 > 80.0)
    }

    @Test
    fun testJaroWinklerSimilarity() {
        val jw = ConfusionSimilarityEngine.jaroWinklerSimilarity("principal", "principle")
        assertTrue("Expected Jaro-Winkler > 85%", jw > 85.0)
    }

    @Test
    fun testDoubleMetaphonePhonetic() {
        val m1 = ConfusionSimilarityEngine.doubleMetaphone("knight")
        val m2 = ConfusionSimilarityEngine.doubleMetaphone("night")
        assertNotNull(m1)
        assertNotNull(m2)
        assertEquals(m1!!.first, m2!!.first) // both are "NT"
    }

    @Test
    fun testFindConfusionGroups() {
        val testWords = listOf(
            VocabularyWordEntity(
                id = "w1",
                word = "Affect",
                meaning = "To influence",
                courseId = "test_course"
            ),
            VocabularyWordEntity(
                id = "w2",
                word = "Effect",
                meaning = "A result",
                courseId = "test_course"
            ),
            VocabularyWordEntity(
                id = "w3",
                word = "Allusion",
                meaning = "An indirect reference",
                courseId = "test_course"
            ),
            VocabularyWordEntity(
                id = "w4",
                word = "Illusion",
                meaning = "A deceptive appearance",
                courseId = "test_course"
            ),
            VocabularyWordEntity(
                id = "w5",
                word = "CompletelyUnrelatedElephant",
                meaning = "An animal",
                courseId = "test_course"
            )
        )

        val config = ConfusionConfig(
            field = ComparisonField.PLACE1,
            mode = MatchingMode.SPELLING,
            threshold = 75.0
        )

        val groups = ConfusionSimilarityEngine.findConfusionGroups(
            words = testWords,
            config = config,
            getPlaceValue = { w, place ->
                when (place) {
                    1 -> w.word
                    2 -> w.meaning
                    else -> ""
                }
            }
        )

        assertNotNull(groups)
        assertTrue("Should have identified confusion groups", groups.isNotEmpty())

        val affectEffectGroup = groups.find { g ->
            g.words.any { it.place1 == "Affect" } && g.words.any { it.place1 == "Effect" }
        }
        assertNotNull("Should contain Affect and Effect grouped together", affectEffectGroup)

        val allusionIllusionGroup = groups.find { g ->
            g.words.any { it.place1 == "Allusion" } && g.words.any { it.place1 == "Illusion" }
        }
        assertNotNull("Should contain Allusion and Illusion grouped together", allusionIllusionGroup)

        // Elephant should NOT be in any group with Affect/Effect
        val elephantInGroup = groups.any { g ->
            g.words.any { it.place1 == "CompletelyUnrelatedElephant" }
        }
        assertFalse("Unrelated word should not be grouped", elephantInGroup)
    }
}
