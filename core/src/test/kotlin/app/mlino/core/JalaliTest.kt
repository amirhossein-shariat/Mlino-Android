package app.mlino.core

import java.time.LocalDate
import java.time.Instant
import kotlin.test.*

class JalaliTest {
    @Test fun knownNowruzDates() {
        val nowruz = mapOf(1399 to "2020-03-20", 1400 to "2021-03-21", 1401 to "2022-03-21", 1402 to "2023-03-21",
            1403 to "2024-03-20", 1404 to "2025-03-21", 1405 to "2026-03-21")
        for ((jy, g) in nowruz) assertEquals(LocalDate.parse(g), Jalali.toGregorian(jy, 1, 1), "nowruz $jy")
    }

    @Test fun leapYears() {
        assertTrue(Jalali.isLeap(1399)); assertTrue(Jalali.isLeap(1403)); assertFalse(Jalali.isLeap(1404))
        assertEquals(30, Jalali.monthLength(1403, 12)); assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test fun roundTripEveryDay() {
        var d = Jalali.toDays(1300, 1, 1)
        val end = Jalali.toDays(1450, 12, 29)
        var prev: Jalali.Date? = null
        while (d <= end) {
            val j = Jalali.fromDays(d)
            assertTrue(Jalali.isValid(j.y, j.m, j.d), "invalid $j")
            assertEquals(d, Jalali.toDays(j.y, j.m, j.d))
            if (prev != null) {
                // consecutive days: day+1, or first of next month/year
                val ok = (j.y == prev.y && j.m == prev.m && j.d == prev.d + 1) || (j.d == 1 && (j.m == prev.m + 1 && j.y == prev.y || j.m == 1 && j.y == prev.y + 1))
                assertTrue(ok, "$prev -> $j")
            }
            prev = j; d++
        }
    }

    @Test fun yearLengthMatchesLeap() {
        for (y in 1300..1450) {
            val len = Jalali.toDays(y + 1, 1, 1) - Jalali.toDays(y, 1, 1)
            assertEquals(if (Jalali.isLeap(y)) 366L else 365L, len, "year $y")
        }
    }

    @Test fun weekday() {
        // 2026-03-21 was a Saturday
        assertEquals(0, Jalali.weekday(1405, 1, 1))
        assertEquals(6, Jalali.weekday(1405, 1, 7))
    }

    @Test fun parseTyping() {
        assertEquals(Jalali.Date(1405, 7, 2), Jalali.parse("۱۴۰۵/۷/۲"))
        assertEquals(Jalali.Date(1405, 7, 2), Jalali.parse("1405-7-2"))
        assertEquals(Jalali.Date(1405, 7, 2), Jalali.parse("1405.7.2"))
        assertEquals(Jalali.Date(1405, 7, 2), Jalali.parse("05/7/2"))
        assertNull(Jalali.parse("1404/12/30")); assertNull(Jalali.parse("1405/13/1")); assertNull(Jalali.parse("abc"))
        assertEquals("۱۴۰۵/۰۷/۰۲", Jalali.format(Jalali.Date(1405, 7, 2)))
    }

    @Test fun tehranTimeIncludingOldDst() {
        // 1399 was in DST (+04:30): 12:00 Tehran on 1399/05/01 = 07:30Z
        assertEquals(Instant.parse("2020-07-22T07:30:00Z").toEpochMilli(), Jalali.toMillis(1399, 5, 1, 12, 0))
        // after the 2022 abolition: always +03:30 (1405/05/31 = 2026-08-22)
        assertEquals(Instant.parse("2026-08-22T08:30:00Z").toEpochMilli(), Jalali.toMillis(1405, 5, 31, 12, 0))
        val ms = Jalali.toMillis(1405, 7, 20, 23, 59, 30)
        val dt = Jalali.fromMillis(ms)
        assertEquals(Jalali.Date(1405, 7, 20), dt.date); assertEquals(23, dt.h); assertEquals(59, dt.mi); assertEquals(30, dt.s)
    }

    @Test fun monthRangeAndDigits() {
        val r = Jalali.monthRange(1405, 12)
        assertEquals(Jalali.toMillis(1406, 1, 1), r.last + 1)
        assertEquals("1405-07", Jalali.monthKey(1405, 7))
        assertEquals("۱۲۳", Jalali.toFa(123)); assertEquals("1230", Jalali.toEn("۱۲۳٠"))
    }
}
