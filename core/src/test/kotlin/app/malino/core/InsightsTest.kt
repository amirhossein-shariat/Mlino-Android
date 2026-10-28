package app.malino.core

import kotlin.test.*

class InsightsTest {
    private val card = Card("c1", "main", openingBalance = 0, openingAt = Jalali.toMillis(1404, 1, 1))
    private var n = 0
    private fun tx(m: Int, d: Int, h: Int, type: TxType, amount: Long, cat: String? = null, dirIn: Boolean = false, card: String = "c1") =
        Tx("t${++n}", card, Jalali.toMillis(1405, m, d, h, 0), type, amount, dirIn, categoryId = cat, createdAt = n.toLong())
    private val now = Jalali.toMillis(1405, 8, 10, 12, 0)

    private val txs = listOf(
        tx(8, 2, 10, TxType.OUT, 400_000, "food"), tx(8, 5, 10, TxType.FEE, 6_000, "bank-fee"), tx(8, 6, 10, TxType.OUT, 100_000),
        tx(8, 7, 10, TxType.IN, 9_000_000), tx(8, 8, 10, TxType.TRANSFER, 5_000_000), tx(8, 9, 10, TxType.ADJUST, -1_000),
        tx(7, 29, 23, TxType.OUT, 777, "food"), tx(8, 1, 0, TxType.OUT, 50_000, "food"),
    )

    @Test fun budgets() {
        val sp = Insights.spendByCategory(txs, listOf(card), 1405, 8)
        assertEquals(mapOf("food" to 450_000L, "bank-fee" to 6_000L, "" to 100_000L), sp)
        assertEquals(mapOf("" to 100_000L, "bank-fee" to 6_000L),
            Insights.spendByCategory(txs, listOf(card.copy(openingAt = Jalali.toMillis(1405, 8, 3))), 1405, 8), "pre-opening ignored")
        val st = Insights.budgetStatus(listOf(Budget("food", 500_000), Budget("x", 100), Budget("bank-fee", 6_000)), sp)
        assertEquals(listOf(Insights.Level.WARN, Insights.Level.OK, Insights.Level.OVER), st.map { it.level })
        assertEquals(0.9, st[0].pct, 1e-9)
    }

    @Test fun recurring() {
        val r = ArrayList<Tx>()
        for ((m, d) in listOf(5 to 4, 6 to 5, 7 to 6, 8 to 5)) r.add(tx(m, d, 9, TxType.OUT, 5_000_000L + if (m == 7) 40_000 else 0, "home"))
        r.add(tx(8, 20, 9, TxType.OUT, 123_456, "food")); r.add(tx(7, 3, 9, TxType.OUT, 987_654, "food"))
        val rec = Insights.detectRecurring(r, now)
        assertEquals(1, rec.size)
        assertEquals("home", rec[0].categoryId); assertEquals(4, rec[0].months); assertEquals(5_000_000, rec[0].amount); assertEquals(5, rec[0].day)
        assertEquals(Jalali.Date(1405, 9, 5), rec[0].next); assertEquals(1405 to 8, rec[0].lastMonth)
        val fc = Insights.forecastNextMonth(rec, now)
        assertEquals(5_000_000, fc.total); assertEquals(1405 to 9, fc.month)
        assertTrue(Insights.detectRecurring(listOf(tx(7, 5, 9, TxType.OUT, 100), tx(8, 5, 9, TxType.OUT, 100)), now).isEmpty(), "two months only")
        assertTrue(Insights.detectRecurring(listOf(tx(6, 5, 9, TxType.OUT, 1_000_000), tx(7, 5, 9, TxType.OUT, 1_000_000), tx(8, 5, 9, TxType.OUT, 1_200_000)), now).isEmpty(), "amount off by more than 5%")
        assertTrue(Insights.detectRecurring(listOf(tx(1, 5, 9, TxType.OUT, 1_000_000), tx(2, 5, 9, TxType.OUT, 1_000_000), tx(3, 5, 9, TxType.OUT, 1_000_000)), now).isEmpty(), "stopped")
        val r31 = listOf(tx(4, 31, 9, TxType.OUT, 1_000_000), tx(5, 31, 9, TxType.OUT, 1_000_000), tx(6, 31, 9, TxType.OUT, 1_000_000), tx(7, 30, 9, TxType.OUT, 1_000_000))
        assertEquals(30, Insights.detectRecurring(r31, now)[0].next.d)
    }

    @Test fun suspicious() {
        val hist = (1..12).map { tx(7, it, 14, TxType.OUT, 100_000L + it * 1_000) }
        val big = tx(8, 9, 14, TxType.OUT, 9_000_000); val night = tx(8, 9, 2, TxType.OUT, 120_000)
        val normal = tx(8, 9, 15, TxType.OUT, 105_000); val old = tx(5, 1, 14, TxType.OUT, 9_000_000_000)
        val sus = Insights.suspicious(hist + listOf(big, night, normal, old), listOf(card), now)
        assertEquals(setOf(big.id to listOf(Insights.Reason.AMOUNT), night.id to listOf(Insights.Reason.HOUR)), sus.map { it.txId to it.reasons }.toSet())
        assertTrue(Insights.suspicious(listOf(big), listOf(card), now).isEmpty(), "not enough history")
    }

    @Test fun assets() {
        val p = Prices(30_000_000, 1_000_000, 400_000_000)
        assertEquals(300_000_000L, Insights.assetValue(Asset("a", AssetType.GOLD, qty = 10.0, karat = 18), p))
        assertEquals(240_000_000L, Insights.assetValue(Asset("a", AssetType.GOLD, qty = 6.0, karat = 24), p))
        assertEquals(250_000_000L, Insights.assetValue(Asset("a", AssetType.USD, qty = 250.0), p))
        assertEquals(800_000_000L, Insights.assetValue(Asset("a", AssetType.COIN, qty = 2.0), p))
        assertEquals(12_345L, Insights.assetValue(Asset("a", AssetType.OTHER, value = 12_345), Prices()))
        assertNull(Insights.assetValue(Asset("a", AssetType.USD, qty = 1.0), Prices()), "no price yet")
    }

    @Test fun goals() {
        val day = 86_400_000L
        val sav = Card("s1", "sav", openingAt = now - 100 * day)
        val stx = (0 until 3).map { k -> Tx("g$k", "s1", now - (80 - k * 30) * day, TxType.TRANSFER, 3_000_000, dirIn = true) }
        val p = Prices(30_000_000, 1_000_000, 400_000_000)
        val g = Insights.goalProgress(Goal("g", "g", 20_000_000, listOf("s1"), listOf("a1")), listOf(sav), mapOf("s1" to 9_000_000L), listOf(Asset("a1", AssetType.OTHER, value = 1_000_000)), p, stx, now)
        assertEquals(10_000_000, g.current); assertEquals(10_000_000, g.remaining); assertEquals(0.5, g.pct, 1e-9)
        assertEquals(3_000_000L, g.monthlyRate)
        assertNotNull(g.doneAt); assertTrue(g.doneAt!!.y >= 1405)
        assertTrue(Insights.goalProgress(Goal("g", "g", 5_000_000, listOf("s1")), listOf(sav), mapOf("s1" to 9_000_000L), emptyList(), p, stx, now).done)
        assertNull(Insights.goalProgress(Goal("g", "g", 50_000_000, listOf("s1")), listOf(sav.copy(openingAt = now - 3 * day)), mapOf("s1" to 1L), emptyList(), p, stx, now).monthlyRate, "< 14 days of data")
    }

    @Test fun installments() {
        val parts = Insights.splitInstallments(1_000_001, 3, Jalali.Date(1405, 11, 30), 1)
        assertEquals(listOf(333_333L, 333_333L, 333_335L), parts.map { it.amount })
        assertEquals(listOf(Jalali.Date(1405, 11, 30), Jalali.Date(1405, 12, Jalali.monthLength(1405, 12)), Jalali.Date(1406, 1, 30)), parts.map { Jalali.fromMillis(it.due).date })
        val debts = listOf(Debt("d1", "علی", true, 15, listOf(
            Installment(Jalali.toMillis(1405, 8, 8), 1), Installment(Jalali.toMillis(1405, 8, 14), 2), Installment(Jalali.toMillis(1405, 8, 30), 3),
            Installment(Jalali.toMillis(1405, 8, 9), 4, paidTxId = "x"), Installment(Jalali.toMillis(1405, 8, 7), 5, paidAt = 1L))))
        assertEquals(listOf(1L to -2, 2L to 4), Insights.dueInstallments(debts, now, 7).map { it.amount to it.daysLeft })
        assertEquals(listOf(1L, 2L, 3L), Insights.dueInstallments(debts, now, 30).map { it.amount })
    }
}
