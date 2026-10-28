package app.malino.core

/**
 * "Malino statement" CSV: the fixed format an AI assistant (or the user) produces from a bank statement, photo or Excel file.
 * Columns: date,time,type,amount,balance,description  (oldest first).
 */
object Statement {
    data class Row(val at: Long, val out: Boolean, val amount: Long, val balance: Long?, val note: String)
    data class Result(val rows: List<Row>, val errors: List<String>)

    const val HEADER = "date,time,type,amount,balance,description"

    /** Ready-to-paste instructions for ChatGPT or any other assistant (shown and copied by the app). */
    val PROMPT = """
        این صورت‌حساب بانکی (که همراه همین پیام فرستادم؛ عکس، فایل یا متن) را فقط به‌صورت CSV برگردان؛ داخل یک بلوک کد و بدون هیچ توضیح اضافه.
        سطر اول دقیقاً این باشد:
        $HEADER
        قواعد ستون‌ها:
        - date: تاریخ شمسی به شکل 1405/07/29 (اگر تاریخ‌ها میلادی‌اند به شمسی تبدیل کن).
        - time: ساعت ۲۴ ساعته به شکل HH:MM؛ اگر در صورت‌حساب نیست بنویس 12:00.
        - type: فقط یکی از out (برداشت/بدهکار) یا in (واریز/بستانکار).
        - amount: مبلغ به ریال، فقط رقم لاتین، بدون ویرگول و بدون علامت. اگر مبالغ به تومان‌اند در ۱۰ ضرب کن.
        - balance: مانده‌ی بعد از همان تراکنش به ریال (فقط رقم)؛ اگر در صورت‌حساب نیست خالی بگذار.
        - description: شرح کوتاه؛ اگر ویرگول دارد داخل "..." بگذار.
        ردیف‌ها از قدیمی به جدید باشند. هیچ ردیفی را حذف، اضافه یا ادغام نکن. اگر ردیفی خوانا نبود، همان ردیف را با description برابر «نامشخص» بنویس.
    """.trimIndent()

    private val OUT = setOf("out", "debit", "withdraw", "withdrawal", "-", "برداشت", "بدهکار", "خروج", "پرداخت")
    private val IN = setOf("in", "credit", "deposit", "+", "واریز", "بستانکار", "ورود", "دریافت")

    fun parse(text: String): Result {
        val rows = ArrayList<Row>(); val errors = ArrayList<String>()
        var lineNo = 0
        for (raw in text.lines()) {
            lineNo++
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("```")) continue
            val f = split(line)
            if (f.firstOrNull()?.trim()?.lowercase() == "date") continue
            if (f.size < 4) { errors.add("سطر $lineNo: ستون‌ها کم است"); continue }
            val date = parseDate(Jalali.toEn(f[0]).trim()) ?: run { errors.add("سطر $lineNo: تاریخ نامعتبر"); null } ?: continue
            val time = parseTime(Jalali.toEn(f[1]).trim()) ?: run { errors.add("سطر $lineNo: ساعت نامعتبر"); null } ?: continue
            val t = f[2].trim().lowercase()
            val amountRaw = Jalali.toEn(f[3]).trim()
            val out = when {
                t in OUT -> true
                t in IN -> false
                amountRaw.startsWith("-") -> true
                else -> { errors.add("سطر $lineNo: نوع باید out یا in باشد"); continue }
            }
            val amount = digitsToLong(amountRaw)?.takeIf { it > 0 } ?: run { errors.add("سطر $lineNo: مبلغ نامعتبر"); null } ?: continue
            val balance = f.getOrNull(4)?.let { Jalali.toEn(it).trim() }?.takeIf { it.isNotEmpty() }?.let { digitsToLong(it) }
            val note = f.drop(5).joinToString(",").trim().trim('"')
            rows.add(Row(Jalali.toMillis(date.y, date.m, date.d, time.first, time.second), out, amount, balance, note))
        }
        return Result(rows, errors)
    }

    private fun digitsToLong(s: String): Long? {
        val d = s.filter { it.isDigit() }
        return if (d.isEmpty() || d.length > 15) null else d.toLong()
    }

    private fun parseDate(s: String): Jalali.Date? {
        val p = s.split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
        if (p.size != 3) return null
        val (y, m, d) = p
        if (m !in 1..12 || d !in 1..31) return null
        return when {
            y in 1300..1500 -> Jalali.Date(y, m, d)
            y in 1900..2200 -> Jalali.toJalali(y, m, d)
            else -> null
        }
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        if (s.isEmpty()) return 12 to 0
        val p = s.split(':').mapNotNull { it.trim().toIntOrNull() }
        if (p.size < 2 || p[0] !in 0..23 || p[1] !in 0..59) return null
        return p[0] to p[1]
    }

    /** Comma, semicolon or tab separated; double quotes protect commas inside a field. */
    private fun split(line: String): List<String> {
        val sep = when { line.contains('\t') -> '\t'; line.contains(',') -> ','; else -> ';' }
        val out = ArrayList<String>(); val cur = StringBuilder(); var q = false
        for (c in line) when {
            c == '"' -> q = !q
            c == sep && !q -> { out.add(cur.toString()); cur.clear() }
            else -> cur.append(c)
        }
        out.add(cur.toString())
        return out
    }
}
