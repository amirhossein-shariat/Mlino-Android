package app.malino.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Jalali calendar (jalaali-js 33-year-cycle algorithm). Tehran time comes from tz data, never a fixed offset. */
object Jalali {
    data class Date(val y: Int, val m: Int, val d: Int)
    data class DateTime(val date: Date, val h: Int, val mi: Int, val s: Int)

    val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    /** Index 0 = Saturday. */
    val WEEKDAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    private val BREAKS = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    fun toFa(v: Any?): String = buildString { v?.toString()?.forEach { append(if (it in '0'..'9') FA[it - '0'] else it) } }
    fun toEn(v: Any?): String = buildString {
        v?.toString()?.forEach {
            val f = FA.indexOf(it)
            val a = AR.indexOf(it)
            append(if (f >= 0) '0' + f else if (a >= 0) '0' + a else it)
        }
    }

    private fun mod(a: Int, b: Int) = a - (a / b) * b
    private fun pad(n: Int) = n.toString().padStart(2, '0')

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): Cal {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        if (jy < jp || jy >= BREAKS[bl - 1]) throw IllegalArgumentException("Jalali year out of range: $jy")
        for (i in 1 until bl) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + mod(jump, 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + (mod(n, 33) + 3) / 4
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    fun isLeap(jy: Int) = jalCal(jy).leap == 0
    fun monthLength(jy: Int, jm: Int) = if (jm <= 6) 31 else if (jm <= 11) 30 else if (isLeap(jy)) 30 else 29
    fun isValid(jy: Int, jm: Int, jd: Int) =
        jy >= -61 && jy < 3177 && jm in 1..12 && jd >= 1 && jd <= monthLength(jy, jm)

    /** Days since 1970-01-01. */
    fun toDays(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return LocalDate.of(r.gy, 3, r.march).toEpochDay() + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    fun fromDays(days: Long): Date {
        val gy = LocalDate.ofEpochDay(days).year
        var jy = gy - 621
        val r = jalCal(jy)
        val first = LocalDate.of(gy, 3, r.march).toEpochDay()
        var k = (days - first).toInt()
        if (k >= 0) {
            if (k <= 185) return Date(jy, 1 + k / 31, mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return Date(jy, 7 + k / 30, mod(k, 30) + 1)
    }

    fun toJalali(gy: Int, gm: Int, gd: Int) = fromDays(LocalDate.of(gy, gm, gd).toEpochDay())
    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate = LocalDate.ofEpochDay(toDays(jy, jm, jd))

    /** 0 = Saturday … 6 = Friday. */
    fun weekday(jy: Int, jm: Int, jd: Int) = mod((toDays(jy, jm, jd) + 5).toInt(), 7)
    fun addDays(d: Date, n: Int) = fromDays(toDays(d.y, d.m, d.d) + n)
    fun format(d: Date, fa: Boolean = true): String {
        val s = "${d.y}/${pad(d.m)}/${pad(d.d)}"
        return if (fa) toFa(s) else s
    }

    private val PARSE = Regex("""^(\d{2}|\d{4})\s*[/\-.\s]\s*(\d{1,2})\s*[/\-.\s]\s*(\d{1,2})$""")

    /** Quick typing: "۱۴۰۵/۷/۲", "1405-7-2", "1405.7.2", "05/7/2" (2-digit year = 14xx). Null when invalid. */
    fun parse(str: String): Date? {
        val m = PARSE.matchEntire(toEn(str).trim()) ?: return null
        var y = m.groupValues[1].toInt()
        if (m.groupValues[1].length == 2) y += 1400
        val d = Date(y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        return if (isValid(d.y, d.m, d.d)) d else null
    }

    fun fromMillis(ms: Long): DateTime {
        val z = Instant.ofEpochMilli(ms).atZone(TEHRAN)
        return DateTime(toJalali(z.year, z.monthValue, z.dayOfMonth), z.hour, z.minute, z.second)
    }

    /** Jalali date + Tehran wall-clock time -> epoch millis (old DST periods handled by tz data). */
    fun toMillis(jy: Int, jm: Int, jd: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long {
        val g = toGregorian(jy, jm, jd)
        return LocalDateTime.of(g.year, g.monthValue, g.dayOfMonth, h, mi, s).atZone(TEHRAN).toInstant().toEpochMilli()
    }

    fun today(nowMs: Long = System.currentTimeMillis()) = fromMillis(nowMs).date

    /** [start, end) of a Jalali month in Tehran time. */
    fun monthRange(jy: Int, jm: Int): LongRange {
        val ny = if (jm == 12) jy + 1 else jy
        val nm = if (jm == 12) 1 else jm + 1
        return toMillis(jy, jm, 1) until toMillis(ny, nm, 1)
    }

    fun monthKey(jy: Int, jm: Int) = "$jy-${pad(jm)}"
}
