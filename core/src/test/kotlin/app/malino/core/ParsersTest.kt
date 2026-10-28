package app.malino.core

import kotlin.test.*

/** Synthetic samples in the real formats (no real account numbers). */
class ParsersTest {
    private val recv = Jalali.toMillis(1405, 8, 4, 7, 10)

    @Test fun parsianWithdrawalAndDeposit() {
        val out = Parsers.parse("12345678901234\nمبلغ:500,000-\nمانده:69,521,224\n08/04\n07:06", recv)!!
        assertEquals(Parsed("parsian", "12345678901234", true, 500_000, 69_521_224, Jalali.toMillis(1405, 8, 4, 7, 6)), out)
        val inn = Parsers.parse("12345678901234\nمبلغ:1,200,000+\nمانده:70,721,224\n08/04\n07:09", recv)!!
        assertFalse(inn.out); assertEquals(1_200_000, inn.amount)
        // persian digits and bidi marks
        assertNotNull(Parsers.parse("‏۱۲۳۴۵۶۷۸۹۰۱۲۳۴\nمبلغ:۵۰۰,۰۰۰-\nمانده:۶۹,۵۲۱,۲۲۴\n۰۸/۰۴\n۰۷:۰۶", recv))
    }

    @Test fun parsianYearStepsBack() {
        // an SMS dated 12/20 received in early 1405 belongs to 1404
        val r = Parsers.parse("12345678901234\nمبلغ:10,000-\nمانده:5,000\n12/20\n10:00", Jalali.toMillis(1405, 1, 5, 9, 0))!!
        assertEquals(Jalali.toMillis(1404, 12, 20, 10, 0), r.at)
    }

    @Test fun tejarat() {
        val r = Parsers.parse("*بانک تجارت*\nحساب: 0123456789012\nبرداشت: 7,436,600 ریال\nاز طريق: شتاب\nمانده: 294,363,972 ریال\n1405/07/30\n19:46", recv)!!
        assertEquals("tejarat", r.bank); assertTrue(r.out); assertEquals(7_436_600, r.amount); assertEquals(294_363_972, r.balance)
        assertEquals(Jalali.toMillis(1405, 7, 30, 19, 46), r.at)
    }

    @Test fun blu() {
        val r = Parsers.parse("بلو\nبرداشت پول\nعلی عزیز، 260,000ریال از حساب شما پرید.\nموجودی: 537,950,039 ریال\n7:35\n۱۴۰۵.۰۸.۰۴", recv)!!
        assertEquals("blu", r.key); assertTrue(r.out); assertEquals(260_000, r.amount); assertEquals(537_950_039, r.balance)
        assertEquals(Jalali.toMillis(1405, 8, 4, 7, 35), r.at)
        val dep = Parsers.parse("بلو\nواریز پول\nعلی عزیز، 1,000,000ریال به حساب شما نشست.\nموجودی: 538,950,039 ریال\n9:05\n۱۴۰۵.۰۸.۰۴", recv)!!
        assertFalse(dep.out)
    }

    @Test fun unknownAndIgnorable() {
        assertNull(Parsers.parse("سلام، فردا میای؟", recv))
        assertEquals("otp", Parsers.ignorable("رمز یکبار مصرف شما 123456 است"))
        assertEquals("otp", Parsers.ignorable("کد تایید: 4821"))
        assertEquals("promo", Parsers.ignorable("بسته پیامکی شما تمدید شد"))
        assertNull(Parsers.ignorable("12345678901234\nمبلغ:500,000-\nمانده:1\n08/04\n07:06"))
    }
}
