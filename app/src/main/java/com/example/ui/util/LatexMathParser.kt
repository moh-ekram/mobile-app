package com.example.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily

/**
 * High-performance, robust LaTeX equation and math parser for Course & Question Bank items.
 * Converts LaTeX formulas, fractions, radicals, Greek alphabet, superscripts, subscripts,
 * and mathematical operators into beautifully formatted mathematical typography.
 */
object LatexMathParser {

    private val GREEK_MAP = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω"
    )

    private val OPERATOR_MAP = mapOf(
        "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷", "cdot" to "·",
        "ast" to "∗", "star" to "★", "circ" to "∘", "bullet" to "•",
        "cap" to "∩", "cup" to "∪", "vee" to "∨", "wedge" to "∧",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
        "supset" to "⊃", "supseteq" to "⊇", "forall" to "∀", "exists" to "∃",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "hbar" to "ℏ",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "sum" to "∑", "prod" to "∏",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓",
        "angle" to "∠", "perp" to "⊥", "degree" to "°"
    )

    private val SUPERSCRIPT_MAP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
    )

    private val SUBSCRIPT_MAP = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    /**
     * Quickly determines if a text contains LaTeX math commands or delimiters.
     */
    fun hasLatex(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.contains("$") || text.contains("\\[") || text.contains("\\(")) return true
        val commonKeywords = listOf(
            "\\frac", "\\sqrt", "\\alpha", "\\beta", "\\gamma", "\\delta",
            "\\theta", "\\pi", "\\lambda", "\\sigma", "\\omega", "\\pm",
            "\\times", "\\div", "\\le", "\\ge", "\\ne", "\\approx", "\\sum",
            "\\int", "\\prod", "\\infty", "\\text{", "\\mathbf{", "\\mathit{"
        )
        return commonKeywords.any { text.contains(it) }
    }

    /**
     * Parses LaTeX and converts it into a clean, human-readable math string with unicode characters.
     */
    fun cleanLatexString(raw: String): String {
        if (raw.isBlank()) return raw
        var s = raw.trim()

        // Strip dollar delimiters
        s = s.replace(Regex("""^\$\$|\$\$$"""), "")
        s = s.replace(Regex("""^\$|\$$"""), "")
        s = s.replace(Regex("""^\\\[|\\\]$"""), "")
        s = s.replace(Regex("""^\\\(|\\\)$"""), "")

        // Replace \frac{a}{b} or \dfrac{a}{b} -> (a)/(b)
        val fracRegex = Regex("""\\(?:d)?frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}""")
        var iter = 0
        while (fracRegex.containsMatchIn(s) && iter < 10) {
            s = fracRegex.replace(s) { match ->
                val num = match.groupValues[1].trim()
                val den = match.groupValues[2].trim()
                if (num.length == 1 && den.length == 1 && num.all { it.isDigit() } && den.all { it.isDigit() }) {
                    when ("$num/$den") {
                        "1/2" -> "½"
                        "1/3" -> "⅓"
                        "2/3" -> "⅔"
                        "1/4" -> "¼"
                        "3/4" -> "¾"
                        "1/5" -> "⅕"
                        else -> "$num/$den"
                    }
                } else {
                    if (num.contains(" ") || num.contains("+") || num.contains("-")) {
                        "($num) / ($den)"
                    } else {
                        "$num / $den"
                    }
                }
            }
            iter++
        }

        // Replace \sqrt[n]{x} -> ⁿ√(x)
        s = s.replace(Regex("""\\sqrt\s*\[([^{}]+)\]\s*\{([^{}]+)\}""")) { match ->
            val n = match.groupValues[1]
            val x = match.groupValues[2]
            val supN = n.map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
            "$supN√($x)"
        }

        // Replace \sqrt{x} -> √(x)
        s = s.replace(Regex("""\\sqrt\s*\{([^{}]+)\}""")) { match ->
            val x = match.groupValues[1]
            if (x.length == 1) "√$x" else "√($x)"
        }

        // Replace Greek letters
        GREEK_MAP.forEach { (cmd, sym) ->
            s = s.replace(Regex("""\\$cmd(?![a-zA-Z])"""), sym)
        }

        // Replace Operators
        OPERATOR_MAP.forEach { (cmd, sym) ->
            s = s.replace(Regex("""\\$cmd(?![a-zA-Z])"""), " $sym ")
        }

        // Text formatting wrappers
        s = s.replace(Regex("""\\(?:text|mathrm|mathbf|mathit)\s*\{([^{}]+)\}""")) { it.groupValues[1] }

        // Common functions \sin, \cos, etc.
        val funcs = listOf("sin", "cos", "tan", "sec", "csc", "cot", "ln", "log", "exp", "lim", "det", "max", "min")
        funcs.forEach { f ->
            s = s.replace(Regex("""\\$f(?![a-zA-Z])"""), f)
        }

        // Clean extra backslashes
        s = s.replace("\\,", " ").replace("\\;", " ").replace("\\quad", "   ").replace("\\!", "")
        s = s.replace(Regex("""\s+"""), " ").trim()

        return s
    }

    /**
     * Converts text containing LaTeX equations and standard text into a rich AnnotatedString
     * with Mathematical Serif styling for equations and variables.
     */
    fun formatToAnnotatedString(
        rawText: String,
        baseFontFamily: FontFamily? = null,
        baseFontSize: TextUnit = 16.sp,
        highlightWord: String? = null,
        highlightColor: Color? = null
    ): AnnotatedString {
        if (!hasLatex(rawText)) {
            return buildAnnotatedString {
                appendPlainTextWithHighlight(rawText, highlightWord, highlightColor)
            }
        }

        // Text has LaTeX! Parse segments: plain text vs inline math ($...$) vs block math ($$...$$)
        return buildAnnotatedString {
            val mathSegmentRegex = Regex("""(\$\$.*?\$\$|\$.*?\$|\\\[.*?\\\]|\\\(.*?\\\))""", RegexOption.DOT_MATCHES_ALL)
            var lastIndex = 0
            val matches = mathSegmentRegex.findAll(rawText).toList()

            if (matches.isEmpty()) {
                // Standalone LaTeX without delimiters
                appendMathSegment(rawText, baseFontSize)
            } else {
                for (m in matches) {
                    val plainPart = rawText.substring(lastIndex, m.range.first)
                    if (plainPart.isNotEmpty()) {
                        appendPlainTextWithHighlight(plainPart, highlightWord, highlightColor)
                    }
                    val mathRaw = m.value
                    appendMathSegment(mathRaw, baseFontSize)
                    lastIndex = m.range.last + 1
                }
                if (lastIndex < rawText.length) {
                    appendPlainTextWithHighlight(rawText.substring(lastIndex), highlightWord, highlightColor)
                }
            }
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendPlainTextWithHighlight(
        text: String,
        highlightWord: String?,
        highlightColor: Color?
    ) {
        if (text.isEmpty()) return
        val bracketRegex = Regex("""\[([^\]]+)\]""")
        if (highlightColor != null && bracketRegex.containsMatchIn(text)) {
            var lastIdx = 0
            for (m in bracketRegex.findAll(text)) {
                val range = m.range
                if (range.first > lastIdx) {
                    append(text.substring(lastIdx, range.first))
                }
                val word = m.groupValues[1]
                pushStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                append(word)
                pop()
                lastIdx = range.last + 1
            }
            if (lastIdx < text.length) {
                append(text.substring(lastIdx))
            }
        } else if (!highlightWord.isNullOrBlank() && highlightColor != null) {
            val regex = Regex("(?i)\\b" + Regex.escape(highlightWord) + "\\b")
            var lastIdx = 0
            val matches = regex.findAll(text).toList()
            if (matches.isEmpty()) {
                append(text)
            } else {
                for (match in matches) {
                    if (match.range.first > lastIdx) {
                        append(text.substring(lastIdx, match.range.first))
                    }
                    pushStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(match.value)
                    pop()
                    lastIdx = match.range.last + 1
                }
                if (lastIdx < text.length) {
                    append(text.substring(lastIdx))
                }
            }
        } else {
            append(text)
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMathSegment(
        mathRaw: String,
        baseFontSize: TextUnit
    ) {
        val cleaned = cleanLatexString(mathRaw)

        // Parse math string for superscripts (^), subscripts (_), and variables
        var i = 0
        val len = cleaned.length

        pushStyle(
            SpanStyle(
                fontFamily = FontFamily.Serif,
                letterSpacing = 0.5.sp
            )
        )

        while (i < len) {
            val ch = cleaned[i]
            when (ch) {
                '^' -> {
                    i++
                    if (i < len) {
                        if (cleaned[i] == '{') {
                            val end = cleaned.indexOf('}', i)
                            if (end != -1) {
                                val supText = cleaned.substring(i + 1, end)
                                pushStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Superscript,
                                        fontSize = if (baseFontSize.isSp) (baseFontSize.value * 0.72f).sp else 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                append(supText)
                                pop()
                                i = end + 1
                                continue
                            }
                        } else {
                            val supChar = cleaned[i]
                            val unicodeSup = SUPERSCRIPT_MAP[supChar]
                            if (unicodeSup != null) {
                                append(unicodeSup)
                            } else {
                                pushStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Superscript,
                                        fontSize = if (baseFontSize.isSp) (baseFontSize.value * 0.72f).sp else 11.sp
                                    )
                                )
                                append(supChar)
                                pop()
                            }
                            i++
                            continue
                        }
                    }
                }
                '_' -> {
                    i++
                    if (i < len) {
                        if (cleaned[i] == '{') {
                            val end = cleaned.indexOf('}', i)
                            if (end != -1) {
                                val subText = cleaned.substring(i + 1, end)
                                pushStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Subscript,
                                        fontSize = if (baseFontSize.isSp) (baseFontSize.value * 0.72f).sp else 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                append(subText)
                                pop()
                                i = end + 1
                                continue
                            }
                        } else {
                            val subChar = cleaned[i]
                            val unicodeSub = SUBSCRIPT_MAP[subChar]
                            if (unicodeSub != null) {
                                append(unicodeSub)
                            } else {
                                pushStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Subscript,
                                        fontSize = if (baseFontSize.isSp) (baseFontSize.value * 0.72f).sp else 11.sp
                                    )
                                )
                                append(subChar)
                                pop()
                            }
                            i++
                            continue
                        }
                    }
                }
                else -> {
                    // Check if character is a standalone variable (e.g. x, y, z, a, b, c) -> Italicize
                    if (ch in 'a'..'z' || ch in 'A'..'Z') {
                        val prevIsLetter = i > 0 && cleaned[i - 1].isLetter()
                        val nextIsLetter = i + 1 < len && cleaned[i + 1].isLetter()
                        if (!prevIsLetter && !nextIsLetter) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal))
                            append(ch)
                            pop()
                        } else {
                            append(ch)
                        }
                    } else {
                        append(ch)
                    }
                    i++
                }
            }
        }

        pop() // Pop Serif wrapper
    }
}

/**
 * Universal Composable for seamlessly rendering standard text, Bengali text, and LaTeX equations.
 * If text contains LaTeX, it styles equations with professional mathematical typography.
 */
@Composable
fun LatexMathText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    highlightWord: String? = null,
    highlightColor: Color? = null
) {
    val isBlockEquation = remember(text) {
        text.startsWith("$$") || text.startsWith("\\[") || (LatexMathParser.hasLatex(text) && text.lines().size == 1 && text.trim().startsWith("\\begin"))
    }

    val annotated = remember(text, fontFamily, fontSize, highlightWord, highlightColor) {
        LatexMathParser.formatToAnnotatedString(
            rawText = text,
            baseFontFamily = fontFamily,
            baseFontSize = fontSize,
            highlightWord = highlightWord,
            highlightColor = highlightColor
        )
    }

    if (isBlockEquation) {
        // Render block equation in a styled equation card with smooth horizontal scroll if needed
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = annotated,
                    color = if (color != Color.Unspecified) color else Color(0xFF0F172A),
                    fontSize = fontSize,
                    fontStyle = fontStyle,
                    fontWeight = fontWeight ?: FontWeight.Medium,
                    fontFamily = fontFamily ?: FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else (fontSize.value * 1.4f).sp
                )
            }
        }
    } else {
        Text(
            text = annotated,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily ?: PoppinsFontFamily,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines
        )
    }
}
