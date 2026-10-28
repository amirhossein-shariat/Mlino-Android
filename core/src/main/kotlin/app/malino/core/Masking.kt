package app.malino.core

/** Removes personal details from a message before the user shares it as a sample (keeps amounts, balances, dates and words). */
object Masking {
    private val LONG_DIGITS = Regex("""(?<![\d,])\d{9,}(?![\d,])""")
    private val NAME = Regex("""[\p{L}‌]+(\s+[\p{L}‌]+)?\s*(عزیز)""")

    fun mask(text: String): String = Parsers.normalize(text)
        .replace(LONG_DIGITS) { m -> "*".repeat(m.value.length - 4) + m.value.takeLast(4) }
        .replace(Regex("""^.*\sعزیز""", RegexOption.MULTILINE)) { m ->
            val line = m.value
            val i = line.lastIndexOf("عزیز")
            // "name عزیز، 1,650,000 ..." -> "کاربر عزیز، ..."; only the leading words before «عزیز» are replaced
            "کاربر " + line.substring(i)
        }
}
