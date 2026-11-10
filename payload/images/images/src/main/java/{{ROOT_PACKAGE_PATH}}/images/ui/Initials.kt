package {{ROOT_PACKAGE}}.images.ui

/**
 * The fallback an avatar shows while its image loads, when it fails, or when there is none: up to two initials —
 * first letters of the first and last words — upper-cased. Pure, so it is unit-tested for the cases that break
 * naive versions (extra whitespace, one word, empty, letters outside ASCII, surrogate pairs).
 */
object Initials {

    fun of(name: String?, maxLetters: Int = 2): String {
        val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || maxLetters <= 0) return ""
        val picked = if (words.size == 1 || maxLetters == 1) listOf(words.first()) else listOf(words.first(), words.last())
        return picked.joinToString("") { word -> firstCodePoint(word).uppercase() }
    }

    private fun firstCodePoint(word: String): String = String(Character.toChars(word.codePointAt(0)))
}
