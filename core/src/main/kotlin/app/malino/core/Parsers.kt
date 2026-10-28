package app.malino.core

/** Result of parsing one bank message. [key] routes it to a card through Card.smsKey. */
/**
 * [extraOut]: an extra amount the bank moved out in the same event (Blu's «رُند» round-up to savings): it also lowered the balance,
 * so it becomes its own non-expense entry and the bank balance still lines up.
 */
data class Parsed(val bank: String, val key: String, val out: Boolean, val amount: Long, val balance: Long, val at: Long, val extraOut: Long = 0, val cardId: String? = null)

interface BankParser {
    val bank: String
    fun parse(text: String, receivedMs: Long): Parsed?
}

object Parsers {
    private val BIDI = Regex("[‎‏‪-‮⁦-⁩]")

    private val DIACRITICS = Regex("[\u064B-\u065F\u0670\u0640]")

    internal fun norm(s: String) = Jalali.toEn(s)
        .replace(BIDI, "").replace(DIACRITICS, "").replace('٬', ',').replace('،', ',')
        .replace('−', '-').replace('–', '-').replace('—', '-').replace("\r", "")

    private fun fixArabic(s: String) = s.replace('ي', 'ی').replace('ك', 'ک')
    private fun num(s: String) = s.replace(",", "").toLong()
    private val M = RegexOption.MULTILINE

    /** The SMS has day/month and time but no year: use the receive year, step back a year if that lands in the future. */
    internal fun resolveAt(jm: Int, jd: Int, h: Int, mi: Int, receivedMs: Long): Long? {
        val y = Jalali.fromMillis(receivedMs).date.y
        for (jy in listOf(y, y - 1)) {
            if (!Jalali.isValid(jy, jm, jd)) continue
            val ms = Jalali.toMillis(jy, jm, jd, h, mi)
            if (ms <= receivedMs + 86_400_000L) return ms
        }
        return null
    }

    /**
     * Parsian SMS: account number / `مبلغ:500,000-` (trailing - = withdrawal, + = deposit) / `مانده:…` / `MM/DD` / `HH:MM`.
     */
    val parsian = object : BankParser {
        override val bank = "parsian"
        private val key = Regex("""^\s*(\d{6,})\s*$""", M)
        private val amt = Regex("""مبلغ\s*[:：]\s*([+-])?\s*([\d,]+)\s*([+-])?""")
        private val bal = Regex("""مانده\s*[:：]\s*(-?)\s*([\d,]+)""")
        private val date = Regex("""^\s*(\d{1,2})/(\d{1,2})\s*$""", M)
        private val time = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""", M)
        override fun parse(text: String, receivedMs: Long): Parsed? {
            val t = norm(text)
            val k = key.find(t)?.groupValues?.get(1) ?: return null
            val a = amt.find(t) ?: return null
            val b = bal.find(t) ?: return null
            val d = date.find(t) ?: return null
            val tm = time.find(t) ?: return null
            val sign = a.groupValues[3].ifEmpty { a.groupValues[1] }
            if (sign.isEmpty()) return null
            val at = resolveAt(d.groupValues[1].toInt(), d.groupValues[2].toInt(), tm.groupValues[1].toInt(), tm.groupValues[2].toInt(), receivedMs) ?: return null
            return Parsed(bank, k, sign == "-", num(a.groupValues[2]), (if (b.groupValues[1].isEmpty()) 1 else -1) * num(b.groupValues[2]), at)
        }
    }

    /** Tejarat SMS with a full Jalali date: `حساب:` / `برداشت:` or `واریز:` / `مانده:` / `1405/07/30` / `19:46`. */
    val tejarat = object : BankParser {
        override val bank = "tejarat"
        private val key = Regex("""حساب\s*[:：]\s*(\d{6,})""")
        private val amt = Regex("""(برداشت|واریز)\s*[:：]\s*([\d,]+)""")
        private val bal = Regex("""مانده\s*[:：]\s*(-?)\s*([\d,]+)""")
        private val date = Regex("""^\s*(\d{4})/(\d{1,2})/(\d{1,2})\s*$""", M)
        private val time = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""", M)
        override fun parse(text: String, receivedMs: Long): Parsed? {
            val t = fixArabic(norm(text))
            if (!Regex("""بانک\s*تجارت""").containsMatchIn(t)) return null
            val k = key.find(t)?.groupValues?.get(1) ?: return null
            val a = amt.find(t) ?: return null
            val b = bal.find(t) ?: return null
            val d = date.find(t) ?: return null
            val tm = time.find(t) ?: return null
            val y = d.groupValues[1].toInt(); val m = d.groupValues[2].toInt(); val dd = d.groupValues[3].toInt()
            if (!Jalali.isValid(y, m, dd)) return null
            return Parsed(bank, k, a.groupValues[1] == "برداشت", num(a.groupValues[2]),
                (if (b.groupValues[1].isEmpty()) 1 else -1) * num(b.groupValues[2]),
                Jalali.toMillis(y, m, dd, tm.groupValues[1].toInt(), tm.groupValues[2].toInt()))
        }
    }

    /**
     * Blu (Saman) app notification. The real notification has no account number and usually no date/time lines (the system shows its own
     * time), so the receive time is used when the text has none:
     * `بلو` / `برداشت رُند` / `… 1,650,000 ریال از حساب شما پرید.` / `و 350,000 ریال هم رند شد.` / `موجودی: 5,697,039 ریال`
     * An older variant also carried `7:35` and `۱۴۰۵.۰۸.۰۴` lines; both are still understood. Deposit wording is unverified.
     */
    val blu = object : BankParser {
        override val bank = "blu"
        private val amt = Regex("""([\d,]+)\s*ریال""")
        private val round = Regex("""و\s*([\d,]+)\s*ریال\s*هم\s*رند\s*شد""")
        private val bal = Regex("""موجودی\s*[:：]\s*(-?)\s*([\d,]+)""")
        private val date = Regex("""(?<![\d,])(1[34]\d{2})[./-](\d{1,2})[./-](\d{1,2})(?![\d,])""")
        private val time = Regex("""(?:^|\s)(\d{1,2}):(\d{2})(?:\s|$)""", M)
        private val title = Regex("""^\s*بلو\s*$""", M)
        override fun parse(text: String, receivedMs: Long): Parsed? {
            val t = fixArabic(norm(text))
            val b = bal.find(t) ?: return null
            if (!title.containsMatchIn(t)) return null
            val ex = round.find(t)
            val a = amt.find(t) ?: return null
            val out = Regex("برداشت|پرید").containsMatchIn(t)
            val inc = Regex("واریز|دریافت|نشست|اضافه\\s*شد").containsMatchIn(t)
            if (out == inc) return null
            val d = date.find(t)
            val tm = time.find(t)
            val at = if (d != null && tm != null) {
                val y = d.groupValues[1].toInt(); val m = d.groupValues[2].toInt(); val dd = d.groupValues[3].toInt()
                if (!Jalali.isValid(y, m, dd)) return null
                Jalali.toMillis(y, m, dd, tm.groupValues[1].toInt(), tm.groupValues[2].toInt())
            } else receivedMs / 60_000 * 60_000
            return Parsed(bank, "blu", out, num(a.groupValues[1]), (if (b.groupValues[1].isEmpty()) 1 else -1) * num(b.groupValues[2]), at,
                if (out && ex != null) num(ex.groupValues[1]) else 0)
        }
    }

    /**
     * Fallback for banks without a dedicated parser. It needs a labelled balance (مانده / موجودی), an amount and a clear direction word
     * or sign; the account / card key, date and time are read when present (the receive time is used otherwise). Toman amounts become rial.
     */
    val generic = object : BankParser {
        override val bank = "generic"
        private val numRe = """(\d{1,3}(?:,\d{3})+|\d{4,})"""
        private val balRe = Regex("""(?:مانده(?:\s*(?:حساب|قابل\s*برداشت|کل))?|موجودی(?:\s*حساب)?|Balance)\s*[:：]?\s*(-?)\s*(\d{1,3}(?:,\d{3})+|\d+)""", RegexOption.IGNORE_CASE)
        private val signed = Regex("""مبلغ\s*[:：]?\s*([+-])?\s*$numRe\s*([+-])?""")
        private val withUnit = Regex("""$numRe\s*(?:ریال|تومان|IRR|IRT|Rial|Toman)""", RegexOption.IGNORE_CASE)
        private val labelled = Regex("""(?:برداشت|واریز|خرید|پرداخت|کسر|دریافت)\s*[:：]?\s*$numRe""")
        private val outW = Regex("""برداشت|خرید|پرداخت|کسر|پرید|انتقال\s*از|هزینه|Debit|Withdraw|Purchase""", RegexOption.IGNORE_CASE)
        private val inW = Regex("""واریز|دریافت|نشست|افزایش|شارژ|Credit|Deposit""", RegexOption.IGNORE_CASE)
        private val keyRe = Regex("""(?:شماره\s*حساب|حساب|کارت|Acc(?:ount)?|Card)\s*[:：]?\s*\**\s*(\d[\d-]{3,})""", RegexOption.IGNORE_CASE)
        private val maskedRe = Regex("""\*{2,}\s*(\d{4})""")
        private val lineKey = Regex("""^\s*(\d{6,})\s*$""", M)
        private val full = Regex("""(?<![\d,])(1[34]\d{2})[/.-](\d{1,2})[/.-](\d{1,2})(?![\d,])""")
        private val short = Regex("""(?<![\d,/])(\d{1,2})/(\d{1,2})(?![\d/,])""")
        private val time = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")

        override fun parse(text: String, receivedMs: Long): Parsed? {
            val t = fixArabic(norm(text))
            val b = balRe.find(t) ?: return null
            val rest = t.removeRange(b.range)
            var amountStr: String? = null
            var out: Boolean? = null
            signed.find(rest)?.let { m ->
                amountStr = m.groupValues[2]
                when (m.groupValues[3].ifEmpty { m.groupValues[1] }) { "-" -> out = true; "+" -> out = false }
            }
            if (amountStr == null) amountStr = withUnit.find(rest)?.groupValues?.get(1) ?: labelled.find(rest)?.groupValues?.get(1)
            val amount = amountStr?.let { num(it) } ?: return null
            if (out == null) {
                val o = outW.find(rest)?.range?.first
                val i = inW.find(rest)?.range?.first
                out = when { o != null && i == null -> true; i != null && o == null -> false; o != null && i != null -> o < i; else -> null }
            }
            val isOut = out ?: return null
            val mult = if (t.contains("تومان") && !t.contains("ریال")) 10 else 1
            val key = (keyRe.find(rest)?.groupValues?.get(1) ?: maskedRe.find(rest)?.groupValues?.get(1) ?: lineKey.find(rest)?.groupValues?.get(1)).orEmpty().filter { it.isDigit() }
            val recv = Jalali.fromMillis(receivedMs)
            val tm = time.find(rest)
            val h = tm?.groupValues?.get(1)?.toInt() ?: recv.h
            val mi = tm?.groupValues?.get(2)?.toInt() ?: recv.mi
            if (h > 23 || mi > 59) return null
            val f = full.find(rest)
            val s = if (f == null) short.find(rest) else null
            val at: Long = when {
                f != null -> {
                    val y = f.groupValues[1].toInt(); val m = f.groupValues[2].toInt(); val d = f.groupValues[3].toInt()
                    if (!Jalali.isValid(y, m, d)) return null
                    Jalali.toMillis(y, m, d, h, mi)
                }
                s != null -> resolveAt(s.groupValues[1].toInt(), s.groupValues[2].toInt(), h, mi, receivedMs) ?: return null
                tm != null -> Jalali.toMillis(recv.date.y, recv.date.m, recv.date.d, h, mi).let { if (it > receivedMs + 5 * 60_000L) it - 86_400_000L else it }
                else -> receivedMs / 60_000 * 60_000
            }
            return Parsed(bank, key, isOut, amount * mult, (if (b.groupValues[1].isEmpty()) 1 else -1) * num(b.groupValues[2]) * mult, at)
        }
    }

    val all: List<BankParser> = listOf(parsian, tejarat, blu, generic)

    /** Same normalisation the parsers use (Latin digits, no diacritics or bidi marks). Used by the teaching screen. */
    fun normalize(text: String) = fixArabic(norm(text))

    /** Non-transaction messages. "otp" ones must never be stored; "promo" ones are ignored. */
    fun ignorable(text: String): String? {
        val t = norm(text)
        if (Regex("""رمز\s*(یکبار|یک\s*بار|پویا|دوم)|کد\s*(تایید|تأیید|فعال|ورود)|\bOTP\b|\bcvv2\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return "otp"
        if (Regex("""بسته\s*پیامکی|تمدید\s*شد""").containsMatchIn(t)) return "promo"
        return null
    }

    /**
     * First understanding wins: dedicated bank parsers, then templates the user taught ([templates], only those for this [sender]),
     * then the generic fallback. null = unknown format.
     */
    fun parse(text: String, receivedMs: Long = System.currentTimeMillis(), templates: List<Template> = emptyList(), sender: String = ""): Parsed? {
        for (p in all.filter { it.bank != "generic" }) {
            val r = p.parse(text, receivedMs)
            if (r != null && r.amount > 0) return r
        }
        for (t in templates) {
            if (t.sender.isNotBlank() && !t.sender.equals(sender.trim(), ignoreCase = true)) continue
            val r = Templates.apply(t, text, receivedMs)
            if (r != null && r.amount > 0) return r
        }
        val g = generic.parse(text, receivedMs)
        return if (g != null && g.amount > 0) g else null
    }
}
