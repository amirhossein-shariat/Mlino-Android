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

/** Synthetic versions of real notification layouts (no real names or numbers). */
class RealWorldParsersTest {
    private val recv = Jalali.toMillis(1405, 8, 6, 13, 35) + 20_000

    @Test fun bluNotificationWithoutDateAndWithRoundUp() {
        val text = "بلو\nبرداشت رُند\nعلی عزیز، 1,650,000 ریال از حساب شما پرید.\nو 350,000 ریال هم رند شد.\nموجودی: 5,697,039 ریال"
        val p = Parsers.parse(text, recv)!!
        assertEquals("blu", p.bank); assertTrue(p.out)
        assertEquals(1_650_000, p.amount); assertEquals(5_697_039, p.balance); assertEquals(350_000, p.extraOut)
        assertEquals(Jalali.toMillis(1405, 8, 6, 13, 35), p.at, "receive time, to the minute")
    }

    @Test fun bluRoundUpKeepsTheBalanceLinedUp() {
        val text = "بلو\nبرداشت رُند\nعلی عزیز، 1,650,000 ریال از حساب شما پرید.\nو 350,000 ریال هم رند شد.\nموجودی: 5,697,039 ریال"
        val card = Card("b1", "blu", bank = "blu", openingBalance = 7_697_039, openingAt = 0)
        val r = Ingest.process(RawMsg("notif", "com.example.blu", "", text, recv), listOf(card), emptyList()) { "x${System.nanoTime()}" } as IngestResult.Created
        assertEquals(2, r.txs.size)
        assertEquals(setOf(TxType.OUT, TxType.ADJUST), r.txs.map { it.type }.toSet())
        val res = Ledger.compute(listOf(card), r.txs)
        assertTrue(res.gaps.isEmpty(), "gaps: ${res.gaps}")
        assertEquals(5_697_039L, res.current["b1"])
        // the round-up is not an expense
        assertEquals(1_650_000L, Insights.spendByCategory(r.txs, listOf(card), 1405, 8)[""])
    }

    @Test fun bluOlderLayoutWithDateAndTimeStillWorks() {
        val p = Parsers.parse("بلو\nبرداشت پول\nعلی عزیز، 260,000ریال از حساب شما پرید.\nموجودی: 537,950,039 ریال\n7:35\n۱۴۰۵.۰۸.۰۴", recv)!!
        assertEquals(Jalali.toMillis(1405, 8, 4, 7, 35), p.at); assertEquals(0, p.extraOut)
    }

    @Test fun genericBankWithAccountDateAndTime() {
        val p = Parsers.parse("بانک ملت\nحساب 1234567890\nواریز 2,500,000 ریال\nمانده 8,000,000 ریال\n1405/08/05-14:22", recv)!!
        assertEquals("generic", p.bank); assertFalse(p.out)
        assertEquals(2_500_000, p.amount); assertEquals(8_000_000, p.balance); assertEquals("1234567890", p.key)
        assertEquals(Jalali.toMillis(1405, 8, 5, 14, 22), p.at)
    }

    @Test fun genericMaskedCardShortDateAndToman() {
        val p = Parsers.parse("کارت **4321\nخرید 15,000 تومان\nمانده: 300,000 تومان\n08/05 10:11", recv)!!
        assertTrue(p.out); assertEquals("4321", p.key)
        assertEquals(150_000, p.amount); assertEquals(3_000_000, p.balance)
        assertEquals(Jalali.toMillis(1405, 8, 5, 10, 11), p.at)
    }

    @Test fun genericNeedsBalanceAndDirection() {
        assertNull(Parsers.parse("واریز 2,500,000 ریال به حساب شما", recv), "no balance")
        assertNull(Parsers.parse("مبلغ 2,500,000 ریال\nمانده 8,000,000 ریال", recv), "no direction")
        assertNull(Parsers.parse("امشب ساعت 8 شام بیا، مانده بودم", recv))
    }

    @Test fun genericWithoutAnyKeyBelongsToTheOnlyCard() {
        val text = "برداشت 500,000 ریال\nمانده 1,000,000 ریال"
        val one = listOf(Card("c", "x", openingBalance = 1_500_000, openingAt = 0))
        assertIs<IngestResult.Created>(Ingest.process(RawMsg("sms", "SOMEBANK", "", text, recv), one, emptyList()) { "i" })
        val two = one + Card("d", "y", openingAt = 0)
        assertIs<IngestResult.Unmapped>(Ingest.process(RawMsg("sms", "SOMEBANK", "", text, recv), two, emptyList()) { "i" })
        // ...unless a card lists the sender id as one of its keys
        val keyed = one + Card("d", "y", smsKey = "SOMEBANK", openingAt = 0)
        assertEquals("d", (Ingest.process(RawMsg("sms", "SOMEBANK", "", text, recv), keyed, emptyList()) { "i${System.nanoTime()}" } as IngestResult.Created).txs.first().cardId)
    }
}
