package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Picks the largest font size, from [maxFontSize] down to [minFontSize] in [stepSize] steps, at which the text
 * fits its box **and wraps only between words** — a word is never split across lines.
 *
 * So a multi-word label keeps its full size and wraps at a space, while a long single word shrinks until it
 * fits instead of breaking in the middle (long words are common in translations). At [minFontSize] the text
 * is laid out as-is and ellipsizes, like any `maxLines`-bound text. `maxLines` stays the caller's.
 */
internal data class WordSafeTextAutoSize(
    private val maxFontSize: TextUnit,
    private val minFontSize: TextUnit,
    private val stepSize: TextUnit = 0.5.sp,
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = maxFontSize.value
        while (size > minFontSize.value) {
            val candidate = size.sp
            if (performLayout(constraints, text, candidate).fitsWholeWords(text)) return candidate
            size -= stepSize.value
        }
        return minFontSize
    }
}

private fun TextLayoutResult.fitsWholeWords(text: CharSequence): Boolean =
    !hasVisualOverflow &&
        breaksOnlyBetweenWords(text, (0 until lineCount - 1).map { getLineEnd(it, visibleEnd = false) })

/**
 * True when every soft line break at [lineEnds] (the end offset of each line but the last) falls between
 * words: on whitespace or right after a hyphen. A break with a letter on both sides splits a word.
 */
internal fun breaksOnlyBetweenWords(text: CharSequence, lineEnds: List<Int>): Boolean =
    lineEnds.all { end ->
        end <= 0 || end >= text.length ||
            text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] == '-'
    }
