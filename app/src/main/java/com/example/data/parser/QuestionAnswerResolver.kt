package com.example.data.parser

data class ResolvedQuizQuestion(
    val cleanOpt1: String,
    val cleanOpt2: String,
    val cleanOpt3: String,
    val cleanOpt4: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val correctAnswer: String
)

object QuestionAnswerResolver {

    /**
     * Identifies the correct option following 3 canonical rules:
     * 1. Exact match with any of the 4 options;
     * 2. If Ans column does not have exact matching data or is empty,
     *    whichever option among Opt1, Opt2, Opt3, Opt4 contains '#' is the answer (and '#' is stripped);
     * 3. If A, B, C, D is written in the Ans column, then A=Opt1, B=Opt2, C=Opt3, D=Opt4.
     */
    fun resolve(
        rawOpt1: String,
        rawOpt2: String,
        rawOpt3: String,
        rawOpt4: String,
        rawAnswer: String
    ): ResolvedQuizQuestion {
        val o1 = rawOpt1.trim()
        val o2 = rawOpt2.trim()
        val o3 = rawOpt3.trim()
        val o4 = rawOpt4.trim()

        val rawOpts = listOf(o1, o2, o3, o4)
        val cleanOpts = rawOpts.map { it.replace("#", "").trim() }
        val hashIndex = rawOpts.indexOfFirst { it.contains("#") }
        val trimmedAns = rawAnswer.trim()

        var matchedIndex = -1

        // Rule 1: Exact match with any of the options (checked against clean options and raw options)
        if (trimmedAns.isNotBlank()) {
            for (i in cleanOpts.indices) {
                if (cleanOpts[i].isNotBlank() && cleanOpts[i].equals(trimmedAns, ignoreCase = true)) {
                    matchedIndex = i
                    break
                }
            }
            if (matchedIndex == -1) {
                for (i in rawOpts.indices) {
                    if (rawOpts[i].isNotBlank() && rawOpts[i].equals(trimmedAns, ignoreCase = true)) {
                        matchedIndex = i
                        break
                    }
                }
            }
        }

        // Rule 3: If written as A, B, C, D (or variants like "A.", "(B)", "opt1", "1", etc.), map to Opt1..Opt4
        if (matchedIndex == -1 && trimmedAns.isNotBlank()) {
            val norm = trimmedAns.lowercase()
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
                .replace("option", "opt")
                .trim()

            matchedIndex = when (norm) {
                "a", "opt1", "opt 1", "1" -> 0
                "b", "opt2", "opt 2", "2" -> 1
                "c", "opt3", "opt 3", "3" -> 2
                "d", "opt4", "opt 4", "4" -> 3
                else -> -1
            }
        }

        // Rule 2: If Ans column does not have exact matching data or is empty, whichever contains '#' is the answer
        if (matchedIndex == -1 && hashIndex != -1) {
            matchedIndex = hashIndex
        }

        // Fallback: If still unmatched, fallback to 0 or first available option
        if (matchedIndex == -1 || matchedIndex >= cleanOpts.size) {
            matchedIndex = cleanOpts.indexOfFirst { it.isNotBlank() }.coerceAtLeast(0)
        }

        val nonBlankOpts = cleanOpts.filter { it.isNotBlank() }
        val finalAnswer = if (matchedIndex in cleanOpts.indices && cleanOpts[matchedIndex].isNotBlank()) {
            cleanOpts[matchedIndex]
        } else {
            nonBlankOpts.firstOrNull() ?: ""
        }

        return ResolvedQuizQuestion(
            cleanOpt1 = cleanOpts.getOrElse(0) { "" },
            cleanOpt2 = cleanOpts.getOrElse(1) { "" },
            cleanOpt3 = cleanOpts.getOrElse(2) { "" },
            cleanOpt4 = cleanOpts.getOrElse(3) { "" },
            options = nonBlankOpts,
            correctOptionIndex = matchedIndex,
            correctAnswer = finalAnswer
        )
    }
}
