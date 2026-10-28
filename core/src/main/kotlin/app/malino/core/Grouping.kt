package app.malino.core

/** Live thousands grouping for amount fields: the typed text stays plain digits, only the display (and cursor mapping) changes. */
object Grouping {
    private fun sepBefore(k: Int, n: Int) = k > 0 && (n - k) % 3 == 0

    fun group(digits: String, sep: Char): String {
        val n = digits.length
        return buildString { digits.forEachIndexed { k, c -> if (sepBefore(k, n)) append(sep); append(c) } }
    }

    /** Cursor offset in the plain text -> offset in the grouped text. */
    fun toTransformed(n: Int, offset: Int): Int {
        val i = offset.coerceIn(0, n)
        return i + (1 until i).count { sepBefore(it, n) }
    }

    /** Cursor offset in the grouped text -> offset in the plain text. */
    fun toOriginal(n: Int, offset: Int): Int =
        (0 until n).count { k -> k + (1..k).count { sepBefore(it, n) } < offset }
}
