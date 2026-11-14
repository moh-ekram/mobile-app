package com.example.data.parser

data class ResolvedQuizQuestion(
    val cleanOpt1: String,
    val cleanOpt2: String,
    val cleanOpt3: String,
    val cleanOpt4: String,
    val cleanOpt5: String = "",
    val options: List<String>,
    val correctOptionIndex: Int,
    val correctAnswer: String
)

sealed class StrictAnswerResult {
    data class Valid(
        val optionIndex: Int,
        val letter: String,
        val selectedOptionText: String
    ) : StrictAnswerResult()

    data class NeedsReview(
        val issueType: ImportIssueType,
        val reason: String
    ) : StrictAnswerResult()
}

object QuestionAnswerResolver {

    /**
     * Strict import validation rule:
     * - Ans MUST be strictly A, B, C, D, or E (case-insensitive, trimmed).
     * - '#' is NEVER treated as an answer marker and is NEVER stripped from option text.
     * - If Ans is empty, outside A-E, or points to a blank option (e.g. Ans=E when Opt5 is empty),
     *   returns StrictAnswerResult.NeedsReview instead of silently defaulting to Opt1.
     */
    fun resolveStrictImport(
        rawOpt1: String,
        rawOpt2: String,
        rawOpt3: String,
        rawOpt4: String,
        rawOpt5: String = "",
        rawAnswer: String
    ): StrictAnswerResult {
        val opts = listOf(
            ImportNormalization.cleanInvisibleChars(rawOpt1).trim(),
            ImportNormalization.cleanInvisibleChars(rawOpt2).trim(),
            ImportNormalization.cleanInvisibleChars(rawOpt3).trim(),
            ImportNormalization.cleanInvisibleChars(rawOpt4).trim(),
            ImportNormalization.cleanInvisibleChars(rawOpt5).trim()
        )
        val cleanedAns = ImportNormalization.cleanInvisibleChars(rawAnswer).trim()
        if (cleanedAns.isEmpty()) {
            return StrictAnswerResult.NeedsReview(
                issueType = ImportIssueType.NEEDS_REVIEW_INVALID_ANSWER,
                reason = "Ans কলাম ফাঁকা আছে; উত্তর অবশ্যই A–E হতে হবে।"
            )
        }

        val upper = cleanedAns.uppercase()
        val idx = when (upper) {
            "A" -> 0
            "B" -> 1
            "C" -> 2
            "D" -> 3
            "E" -> 4
            else -> -1
        }
        if (idx == -1) {
            return StrictAnswerResult.NeedsReview(
                issueType = ImportIssueType.NEEDS_REVIEW_INVALID_ANSWER,
                reason = "অবৈধ উত্তর '$cleanedAns' — Ans কলামে শুধু A, B, C, D বা E অনুমোদিত।"
            )
        }

        val targetOpt = opts[idx]
        if (targetOpt.isBlank()) {
            val optLabel = "Opt${idx + 1}"
            return StrictAnswerResult.NeedsReview(
                issueType = ImportIssueType.NEEDS_REVIEW_EMPTY_OPTION,
                reason = "উত্তর '$upper' দেওয়া হয়েছে কিন্তু $optLabel ফাঁকা।"
            )
        }

        return StrictAnswerResult.Valid(
            optionIndex = idx,
            letter = upper,
            selectedOptionText = targetOpt
        )
    }

    /**
     * Resolves an already-stored QuestionBankEntity's options and answer for quiz display.
     * Supports both stored exact option text and A-E letters without '#' stripping or Opt1 fallback.
     */
    fun resolve(
        rawOpt1: String,
        rawOpt2: String,
        rawOpt3: String,
        rawOpt4: String,
        rawOpt5: String = "",
        rawAnswer: String
    ): ResolvedQuizQuestion {
        val o1 = rawOpt1.trim()
        val o2 = rawOpt2.trim()
        val o3 = rawOpt3.trim()
        val o4 = rawOpt4.trim()
        val o5 = rawOpt5.trim()

        val rawOpts = if (o5.isNotBlank()) listOf(o1, o2, o3, o4, o5) else listOf(o1, o2, o3, o4)
        val trimmedAns = rawAnswer.trim()

        var matchedIndex = -1

        // 1. Exact text match with any stored option
        if (trimmedAns.isNotBlank()) {
            for (i in rawOpts.indices) {
                if (rawOpts[i].isNotBlank() && rawOpts[i].equals(trimmedAns, ignoreCase = true)) {
                    matchedIndex = i
                    break
                }
            }
        }

        // 2. Letter A-E match (pointing to a valid non-blank option)
        if (matchedIndex == -1 && trimmedAns.isNotBlank()) {
            val idx = when (trimmedAns.uppercase()) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                "E" -> 4
                else -> -1
            }
            if (idx in rawOpts.indices && rawOpts[idx].isNotBlank()) {
                matchedIndex = idx
            }
        }

        val nonBlankOpts = rawOpts.filter { it.isNotBlank() }
        val finalAnswer = if (matchedIndex in rawOpts.indices && rawOpts[matchedIndex].isNotBlank()) {
            rawOpts[matchedIndex]
        } else {
            trimmedAns
        }

        return ResolvedQuizQuestion(
            cleanOpt1 = rawOpts.getOrElse(0) { "" },
            cleanOpt2 = rawOpts.getOrElse(1) { "" },
            cleanOpt3 = rawOpts.getOrElse(2) { "" },
            cleanOpt4 = rawOpts.getOrElse(3) { "" },
            cleanOpt5 = rawOpts.getOrElse(4) { "" },
            options = nonBlankOpts,
            correctOptionIndex = matchedIndex,
            correctAnswer = finalAnswer
        )
    }

    fun resolve(
        rawOpt1: String,
        rawOpt2: String,
        rawOpt3: String,
        rawOpt4: String,
        rawAnswer: String
    ): ResolvedQuizQuestion = resolve(rawOpt1, rawOpt2, rawOpt3, rawOpt4, "", rawAnswer)
}
