package app.malino.core

/**
 * Templates the user teaches the app from one real message: the user marks which number is the amount, the balance, and so on;
 * the app remembers the words just before each number («مانده:», «مبلغ»…) and reads the same places in later messages.
 */
enum class Role { AMOUNT, BALANCE, KEY, DATE, TIME }

/** [prefix] = the words right before the number on its line; when there are none, [ordinal] (the n-th number of the message) is used. */
data class Field(val role: Role, val prefix: String, val ordinal: Int)

/** [sender] = SMS sender id or app package the template belongs to (blank = any); [dirWord] must appear in the text (blank = no check). */
data class Template(
    val id: String, val sender: String, val cardId: String, val out: Boolean, val dirWord: String,
    val fields: List<Field>, val createdAt: Long,
)

object Templates {
    class Tok(val text: String, val start: Int, val end: Int, val ordinal: Int)

    private val TOKEN = Regex("""\d[\d,./:\-]*\d|\d""")
    private val MONEY = Regex("""\d[\d,]*""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val FULL_DATE = Regex("""(1[34]\d{2})[/.-](\d{1,2})[/.-](\d{1,2})""")
    private val SHORT_DATE = Regex("""(\d{1,2})/(\d{1,2})""")

    /** Numbers (also dates and times as one piece) of an already normalised text, in reading order. */
    fun tokens(text: String): List<Tok> = TOKEN.findAll(text).mapIndexed { i, m -> Tok(m.value, m.range.first, m.range.last + 1, i) }.toList()

    fun isMoney(tok: String) = MONEY.matches(tok)
    fun isTime(tok: String) = TIME.matches(tok)
    fun isDate(tok: String) = FULL_DATE.matches(tok) || SHORT_DATE.matches(tok)

    /** Words before the number on its own line (up to 16 characters), without a trailing colon. */
    fun prefixOf(text: String, tok: Tok): String {
        val lineStart = text.lastIndexOf('\n', tok.start - 1) + 1
        var before = text.substring(lineStart, tok.start)
        val lastDigit = before.indexOfLast { it.isDigit() }
        if (lastDigit >= 0) before = before.substring(lastDigit + 1)
        before = before.trim().trimEnd(':', '：', '-', '+').trim()
        return if (before.length > 16) before.takeLast(16).trim() else before
    }

    /** Builds a template from [text] (normalised) and the numbers the user picked ([picks]: role -> ordinal). */
    fun learn(id: String, sender: String, cardId: String, out: Boolean, dirWord: String, text: String, picks: Map<Role, Int>, now: Long): Template {
        val toks = tokens(text)
        val fields = picks.mapNotNull { (role, ord) -> toks.getOrNull(ord)?.let { Field(role, prefixOf(text, it), ord) } }
        return Template(id, sender, cardId, out, dirWord, fields, now)
    }

    private fun read(text: String, toks: List<Tok>, f: Field): Pair<String, String>? {
        if (f.prefix.isNotEmpty()) {
            val m = Regex(Regex.escape(f.prefix) + """\s*[:：]?\s*(-?)\s*(\d[\d,./:\-]*\d|\d)""").find(text) ?: return null
            return m.groupValues[1] to m.groupValues[2]
        }
        return toks.getOrNull(f.ordinal)?.let { "" to it.text }
    }

    fun apply(t: Template, text: String, receivedMs: Long): Parsed? {
        val s = Parsers.normalize(text)
        if (t.dirWord.isNotEmpty() && !s.contains(t.dirWord)) return null
        val toks = tokens(s)
        val v = t.fields.associate { it.role to read(s, toks, it) }
        val amountTok = v[Role.AMOUNT]?.second ?: return null
        val balanceTok = v[Role.BALANCE]?.second ?: return null
        if (!isMoney(amountTok) || !isMoney(balanceTok)) return null
        val amount = amountTok.replace(",", "").toLongOrNull() ?: return null
        val balance = (balanceTok.replace(",", "").toLongOrNull() ?: return null) * (if (v[Role.BALANCE]!!.first == "-") -1 else 1)
        val key = v[Role.KEY]?.second?.filter { it.isDigit() }.orEmpty()
        val recv = Jalali.fromMillis(receivedMs)
        val tm = v[Role.TIME]?.second?.let { TIME.matchEntire(it) }
        val h = tm?.groupValues?.get(1)?.toInt() ?: recv.h
        val mi = tm?.groupValues?.get(2)?.toInt() ?: recv.mi
        if (h > 23 || mi > 59) return null
        val dateTok = v[Role.DATE]?.second
        val at = when {
            dateTok != null && FULL_DATE.matches(dateTok) -> FULL_DATE.matchEntire(dateTok)!!.groupValues.let { g ->
                val y = g[1].toInt(); val m = g[2].toInt(); val d = g[3].toInt()
                if (!Jalali.isValid(y, m, d)) return null
                Jalali.toMillis(y, m, d, h, mi)
            }
            dateTok != null && SHORT_DATE.matches(dateTok) -> SHORT_DATE.matchEntire(dateTok)!!.groupValues.let { g ->
                Parsers.resolveAt(g[1].toInt(), g[2].toInt(), h, mi, receivedMs) ?: return null
            }
            tm != null -> Jalali.toMillis(recv.date.y, recv.date.m, recv.date.d, h, mi).let { if (it > receivedMs + 5 * 60_000L) it - 86_400_000L else it }
            else -> receivedMs / 60_000 * 60_000
        }
        return Parsed("custom", key, t.out, amount, balance, at, 0, t.cardId)
    }

    /** Direction word found in the text (to pre-fill the teaching screen), or "" when none is known. */
    fun guessDirection(text: String): Pair<Boolean, String>? {
        val s = Parsers.normalize(text)
        val out = Regex("برداشت|خرید|پرداخت|کسر|پرید|انتقال\\s*از|هزینه").find(s)
        val inn = Regex("واریز|دریافت|نشست|افزایش|شارژ").find(s)
        return when {
            out != null && (inn == null || out.range.first < inn.range.first) -> true to out.value
            inn != null -> false to inn.value
            else -> null
        }
    }
}
