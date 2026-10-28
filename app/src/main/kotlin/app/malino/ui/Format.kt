package app.malino.ui

import app.malino.core.Jalali

/** 1234567 -> ۱٬۲۳۴٬۵۶۷ */
fun money(n: Long): String = Jalali.toFa(kotlin.math.abs(n).toString().reversed().chunked(3).joinToString("٬").reversed())

/** The sign goes after the digits in logical order, which is the LEFT side of the number in RTL. */
fun signedMoney(n: Long, plus: Boolean): String = money(n) + "\u200F" + (if (plus) "+" else "−")

fun fa(n: Int) = Jalali.toFa(n)

fun two(n: Int) = n.toString().padStart(2, '0')

fun clock(ms: Long): String = Jalali.fromMillis(ms).let { Jalali.toFa("${two(it.h)}:${two(it.mi)}") }

fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = Jalali.fromMillis(ms).date
    val today = Jalali.today(now)
    return when (Jalali.toDays(d.y, d.m, d.d) - Jalali.toDays(today.y, today.m, today.d)) {
        0L -> "امروز"
        -1L -> "دیروز"
        else -> Jalali.toFa("${d.d}") + " " + Jalali.MONTHS[d.m - 1] + if (d.y != today.y) " " + Jalali.toFa(d.y) else ""
    }
}

fun fullDate(ms: Long): String = Jalali.fromMillis(ms).date.let { Jalali.format(it) }

/** Latin digits only (accepts Persian/Arabic digits typed by the user). */
fun digitsOnly(s: String) = Jalali.toEn(s).filter { it.isDigit() }

fun newId() = java.util.UUID.randomUUID().toString()
