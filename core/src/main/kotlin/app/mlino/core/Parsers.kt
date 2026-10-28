package app.mlino.core

/** Result of parsing one bank message. [key] routes it to a card through Card.smsKey. */
data class Parsed(val bank: String, val key: String, val out: Boolean, val amount: Long, val balance: Long, val at: Long)

interface BankParser {
    val bank: String
    fun parse(text: String, receivedMs: Long): Parsed?
}

object Parsers {
    private val BIDI = Regex("[‎‏‪-‮⁦-⁩]")

    internal fun norm(s: String) = Jalali.toEn(s)
        .replace(BIDI, "").replace('٬', ',').replace('،', ',')
        .replace('−', '-').replace('–', '-').replace('—', '-').replace("\r", "")

    private fun fixArabic(s: String) = s.replace('ي', 'ی').replace('ك', 'ک')
    private fun num(s: String) = s.replace(",", "").toLong()
    private val M = RegexOption.MULTILINE

    /** The SMS has day/month and time but no year: use the receive year, step back a year if that lands in the future. */
    private fun resolveAt(jm: Int, jd: Int, h: Int, mi: Int, receivedMs: Long): Long? {
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
     * Blu (Saman) app notification, no account number so the card key is the fixed word "blu":
     * `بلو` / `برداشت پول` / `… 260,000ریال از حساب شما پرید.` / `موجودی: …` / `7:35` / `۱۴۰۵.۰۸.۰۴`.
     * Deposit wording is unverified: «واریز / دریافت / نشست» count as deposit.
     */
    val blu = object : BankParser {
        override val bank = "blu"
        private val amt = Regex("""([\d,]+)\s*ریال""")
        private val bal = Regex("""موجودی\s*[:：]\s*(-?)\s*([\d,]+)""")
        private val date = Regex("""(\d{4})[./-](\d{1,2})[./-](\d{1,2})""")
        private val time = Regex("""(?:^|\s)(\d{1,2}):(\d{2})(?:\s|$)""", M)
        private val title = Regex("""^\s*بلو\s*$""", M)
        override fun parse(text: String, receivedMs: Long): Parsed? {
            val t = fixArabic(norm(text))
            val a = amt.find(t) ?: return null
            val b = bal.find(t) ?: return null
            val d = date.find(t) ?: return null
            val tm = time.find(t) ?: return null
            val out = Regex("برداشت|پرید").containsMatchIn(t)
            val inc = Regex("واریز|دریافت|نشست").containsMatchIn(t)
            if (!title.containsMatchIn(t) || out == inc) return null
            val y = d.groupValues[1].toInt(); val m = d.groupValues[2].toInt(); val dd = d.groupValues[3].toInt()
            if (!Jalali.isValid(y, m, dd)) return null
            return Parsed(bank, "blu", out, num(a.groupValues[1]),
                (if (b.groupValues[1].isEmpty()) 1 else -1) * num(b.groupValues[2]),
                Jalali.toMillis(y, m, dd, tm.groupValues[1].toInt(), tm.groupValues[2].toInt()))
        }
    }

    val all: List<BankParser> = listOf(parsian, tejarat, blu)

    /** Non-transaction messages. "otp" ones must never be stored; "promo" ones are ignored. */
    fun ignorable(text: String): String? {
        val t = norm(text)
        if (Regex("""رمز\s*(یکبار|یک\s*بار|پویا|دوم)|کد\s*(تایید|تأیید|فعال|ورود)|\bOTP\b|\bcvv2\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return "otp"
        if (Regex("""بسته\s*پیامکی|تمدید\s*شد""").containsMatchIn(t)) return "promo"
        return null
    }

    /** First parser that understands the message wins; null = unknown format. */
    fun parse(text: String, receivedMs: Long = System.currentTimeMillis()): Parsed? {
        for (p in all) {
            val r = p.parse(text, receivedMs)
            if (r != null && r.amount > 0) return r
        }
        return null
    }
}
