package app.malino.core

import kotlin.test.*

class TemplatesTest {
    private val recv = Jalali.toMillis(1405, 8, 6, 13, 35) + 20_000
    private val taught = "بانک نمونه\nبرداشت: 1,200,000 ریال\nمانده: 5,000,000 ریال\nحساب 998877\n1405/08/06 12:30"

    private fun template(text: String = taught, out: Boolean = true, word: String = "برداشت"): Template {
        val s = Parsers.normalize(text)
        val toks = Templates.tokens(s)
        // ordinals: 0 = amount, 1 = balance, 2 = account, 3 = date, 4 = time
        return Templates.learn("t1", "SAMPLEBANK", "card1", out, word, s,
            mapOf(Role.AMOUNT to 0, Role.BALANCE to 1, Role.KEY to 2, Role.DATE to 3, Role.TIME to 4).filterValues { it < toks.size }, 1L)
    }

    @Test fun remembersTheWordsBeforeEachNumber() {
        val t = template()
        assertEquals(listOf("برداشت", "مانده", "حساب", "", ""), t.fields.map { it.prefix })
    }

    @Test fun readsAnotherMessageWithTheSameLayout() {
        val p = Templates.apply(template(), "بانک نمونه\nبرداشت: 350,000 ریال\nمانده: 4,650,000 ریال\nحساب 998877\n1405/08/07 09:05", recv)!!
        assertEquals("custom", p.bank); assertEquals("card1", p.cardId); assertEquals("998877", p.key)
        assertTrue(p.out); assertEquals(350_000, p.amount); assertEquals(4_650_000, p.balance)
        assertEquals(Jalali.toMillis(1405, 8, 7, 9, 5), p.at)
    }

    @Test fun usesTheReceiveTimeWhenTheTemplateHasNoDate() {
        val s = Parsers.normalize("برداشت: 10,000 ریال\nمانده: 90,000 ریال")
        val t = Templates.learn("t", "", "c", true, "", s, mapOf(Role.AMOUNT to 0, Role.BALANCE to 1), 1L)
        assertEquals(Jalali.toMillis(1405, 8, 6, 13, 35), Templates.apply(t, "برداشت: 7,000 ریال\nمانده: 83,000 ریال", recv)!!.at)
    }

    @Test fun refusesWhenTheLayoutOrDirectionWordDiffers() {
        val t = template()
        assertNull(Templates.apply(t, "واریز: 350,000 ریال\nمانده: 4,650,000 ریال", recv), "direction word missing")
        assertNull(Templates.apply(t, "برداشت 350,000", recv), "no balance")
    }

    @Test fun ordinalIsUsedWhenThereAreNoWordsBeforeTheNumber() {
        val s = Parsers.normalize("شعبه مرکزی\n500000\n9000000")
        val t = Templates.learn("t", "", "c", false, "", s, mapOf(Role.AMOUNT to 0, Role.BALANCE to 1), 1L)
        val p = Templates.apply(t, "شعبه مرکزی\n120000\n9120000", recv)!!
        assertEquals(120_000, p.amount); assertEquals(9_120_000, p.balance); assertFalse(p.out)
    }

    @Test fun aTaughtTemplateBeatsTheGenericParserButNotTheSender() {
        val t = template()
        val msg = "بانک نمونه\nبرداشت: 350,000 ریال\nمانده: 4,650,000 ریال"
        assertEquals("custom", Parsers.parse(msg, recv, listOf(t), "samplebank")!!.bank)
        assertEquals("generic", Parsers.parse(msg, recv, listOf(t), "OTHER")?.bank, "another sender: the template is skipped")
    }

    @Test fun ingestUsesTheTemplateCard() {
        val card = Card("card1", "x", openingBalance = 5_350_000, openingAt = 0)
        val msg = RawMsg("sms", "SAMPLEBANK", "", "بانک نمونه\nبرداشت: 350,000 ریال\nمانده: 5,000,000 ریال\nحساب 998877", recv)
        val r = Ingest.processWith(msg, listOf(card, Card("other", "y", openingAt = 0)), emptyList(), listOf(template())) { "n${System.nanoTime()}" } as IngestResult.Created
        assertEquals("card1", r.txs.single().cardId)
    }

    @Test fun guessesDirectionFromKnownWords() {
        assertEquals(true to "برداشت", Templates.guessDirection("برداشت 1000"))
        assertEquals(false to "واریز", Templates.guessDirection("واریز 1000"))
        assertNull(Templates.guessDirection("سلام"))
    }

    @Test fun maskingHidesAccountNumbersPhonesAndNames() {
        val m = Masking.mask("امیرحسین عزیز، 1,650,000 ریال از حساب 6118280053795973 پرید. تماس 09123456789\nمانده: 5,697,039")
        assertTrue("6118280053795973" !in m && "09123456789" !in m && "امیرحسین" !in m, m)
        assertTrue("1,650,000" in m && "5,697,039" in m && "5973" in m && "کاربر عزیز" in m, m)
    }
}
