package app.malino.core

import kotlin.test.*

class ReportsTest {
    private val open = Jalali.toMillis(1405, 6, 1)
    private val cards = listOf(
        Card("c1", "روزمره", purpose = "روزمره", openingBalance = 1_000_000, openingAt = open),
        Card("c2", "پس‌انداز", purpose = "پس‌انداز", openingBalance = 0, openingAt = open),
    )
    private var n = 0
    private fun tx(card: String, m: Int, d: Int, type: TxType, amount: Long, cat: String? = null, business: Boolean = false, dirIn: Boolean = false, at: Long? = null) =
        Tx("t${++n}", card, at ?: Jalali.toMillis(1405, m, d, 10, 0), type, amount, dirIn, categoryId = cat, business = business, createdAt = n.toLong())

    private val txs = listOf(
        tx("c1", 6, 10, TxType.IN, 5_000_000, "other_in"),
        tx("c1", 7, 2, TxType.OUT, 400_000, "food"), tx("c1", 7, 3, TxType.FEE, 6_000, "fee"),
        tx("c1", 7, 5, TxType.IN, 3_000_000, "invoice", business = true),
        tx("c1", 7, 8, TxType.TRANSFER, 1_000_000), tx("c2", 7, 8, TxType.TRANSFER, 1_000_000, dirIn = true),
        tx("c1", 7, 9, TxType.ADJUST, -500),
        tx("c1", 7, 10, TxType.OUT, 900_000, "food"), tx("c1", 7, 11, TxType.OUT, 250_000),
        tx("c1", 8, 1, TxType.OUT, 111, "food"),
        tx("c1", 5, 20, TxType.OUT, 99_999_999, at = Jalali.toMillis(1405, 5, 20)), // before the card opened: ignored
    )

    @Test fun firstMonth() {
        val r = Reports.build(1405, 6, cards, txs)
        assertEquals("1405-06", r.key)
        assertEquals(5_000_000, r.income); assertEquals(0, r.expense); assertEquals(5_000_000, r.net)
        assertEquals(1_000_000, r.cards[0].opening); assertEquals(6_000_000, r.cards[0].closing)
        assertEquals(6_000_000, r.wealth); assertNull(r.wealthChange)
    }

    @Test fun transfersAndAdjustsAreNeitherIncomeNorExpense() {
        val r6 = Reports.build(1405, 6, cards, txs)
        val r = Reports.build(1405, 7, cards, txs, r6)
        assertEquals(mapOf("invoice" to 3_000_000L), r.incomeByCat)
        assertEquals(mapOf("food" to 1_300_000L, "fee" to 6_000L, "" to 250_000L), r.expenseByCat)
        assertEquals(3_000_000 - 1_556_000, r.net)
        assertEquals(3_000_000, r.businessIncome); assertEquals(1_556_000, r.personalExpense)
        assertEquals(6_000_000 + 3_000_000 - 1_556_000 - 1_000_000 - 500, r.cards[0].closing)
        assertEquals(1_000_000, r.cards[1].closing)
        assertEquals(3_000_000, r.cards[0].inn); assertEquals(1_556_000 + 1_000_000 + 500, r.cards[0].out)
        assertEquals(1_000_000, r.savings)
        assertEquals(r.wealth - r6.wealth, r.wealthChange)
        assertEquals(listOf(900_000L, 400_000L, 250_000L), r.top.map { it.amount }, "top withdrawals: OUT only")
        assertEquals(8, r.txCount)
    }

    @Test fun seriesIsChainedAndOrdered() {
        val s = Reports.series(1405, 8, 3, cards, txs)
        assertEquals(listOf("1405-06", "1405-07", "1405-08"), s.map { it.key })
        assertEquals(s[1].wealth - s[0].wealth, s[1].wealthChange)
        assertNotNull(s[0].wealthChange, "first item is chained to the month before the window")
    }

    @Test fun deltaPercent() {
        assertEquals(50, Reports.delta(150, 100)); assertEquals(-25, Reports.delta(75, 100)); assertNull(Reports.delta(5, 0))
    }

    @Test fun noSavingsCard() {
        assertNull(Reports.build(1405, 7, cards.take(1), txs).savings)
    }
}
