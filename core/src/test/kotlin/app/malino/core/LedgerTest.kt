package app.malino.core

import kotlin.test.*

class LedgerTest {
    private val open = Jalali.toMillis(1405, 7, 1)
    private val card = Card("c1", "main", openingBalance = 1000, openingAt = open)
    private fun t(id: String, d: Int, h: Int, type: TxType, amount: Long, bal: Long? = null, dirIn: Boolean = false, created: Long = 0, at: Long? = null) =
        Tx(id, "c1", at ?: Jalali.toMillis(1405, 7, d, h, 0), type, amount, dirIn, bal, createdAt = created)

    @Test fun orderAndBackdating() {
        val txs = mutableListOf(t("a", 2, 10, TxType.OUT, 300), t("b", 3, 9, TxType.IN, 5000), t("c", 3, 12, TxType.FEE, 20))
        var r = Ledger.compute(listOf(card), txs)
        assertEquals(mapOf("a" to 700L, "b" to 5700L, "c" to 5680L), r.after)
        txs.add(t("d", 2, 8, TxType.OUT, 100))
        r = Ledger.compute(listOf(card), txs)
        assertEquals(mapOf("d" to 900L, "a" to 600L, "b" to 5600L, "c" to 5580L), r.after)
        assertEquals(5580L, r.current["c1"])
    }

    @Test fun beforeOpeningIgnored() {
        val old = t("old", 0, 0, TxType.IN, 999999, at = Jalali.toMillis(1405, 6, 30, 23, 0))
        val r = Ledger.compute(listOf(card), listOf(old, t("a", 2, 10, TxType.OUT, 300)))
        assertEquals(700L, r.current["c1"]); assertTrue("old" in r.before); assertFalse("old" in r.after)
    }

    @Test fun adjustTransferAndTies() {
        var r = Ledger.compute(listOf(card), listOf(t("x", 2, 1, TxType.ADJUST, -50), t("y", 2, 2, TxType.TRANSFER, 200), t("z", 2, 3, TxType.TRANSFER, 40, dirIn = true)))
        assertEquals(mapOf("x" to 950L, "y" to 750L, "z" to 790L), r.after)
        r = Ledger.compute(listOf(card), listOf(t("p", 2, 5, TxType.OUT, 1, created = 2), t("q", 2, 5, TxType.IN, 10, created = 1)))
        assertEquals(mapOf("q" to 1010L, "p" to 1009L), r.after)
        val c2 = Card("c2", "other", openingBalance = 7, openingAt = open)
        assertEquals(7L, Ledger.compute(listOf(card, c2), emptyList()).current["c2"])
    }

    @Test fun continuityGap() {
        assertEquals(emptyList(), Ledger.compute(listOf(card), listOf(t("s1", 2, 1, TxType.OUT, 100, 900), t("s2", 2, 2, TxType.OUT, 50, 850))).gaps)
        val r = Ledger.compute(listOf(card), listOf(t("s1", 2, 1, TxType.OUT, 100, 900), t("s2", 2, 2, TxType.OUT, 50, 850),
            t("s3", 2, 3, TxType.OUT, 50, 600), t("s4", 2, 4, TxType.IN, 10, 610)))
        assertEquals(listOf(Triple("s3", "c1", -200L)), r.gaps.map { Triple(it.txId, it.cardId, it.diff) })
        assertEquals(mapOf("s1" to 900L, "s2" to 850L, "s3" to 600L, "s4" to 610L), r.after)
        val fixed = Ledger.compute(listOf(card), listOf(t("s1", 2, 1, TxType.OUT, 100, 900), t("s2", 2, 2, TxType.OUT, 50, 850),
            t("fix", 2, 2, TxType.FEE, 200, at = Jalali.toMillis(1405, 7, 2, 2, 30)), t("s3", 2, 3, TxType.OUT, 50, 600)))
        assertEquals(emptyList(), fixed.gaps)
        // wrong opening balance shows up on the first bank-reported tx
        val g = Ledger.compute(listOf(card.copy(openingBalance = 0)), listOf(t("s1", 2, 1, TxType.OUT, 100, 900)))
        assertEquals(listOf(Triple("s1", "c1", 1000L)), g.gaps.map { Triple(it.txId, it.cardId, it.diff) })
    }

    @Test fun gapPeriodAndUnsettled() {
        val r = Ledger.compute(listOf(card), listOf(t("s1", 2, 1, TxType.OUT, 100, 900), t("n", 2, 2, TxType.OUT, 10),
            t("s3", 2, 3, TxType.OUT, 50, 600), t("s4", 2, 4, TxType.IN, 10, 610)))
        val g = r.gaps.single()
        assertEquals(Jalali.toMillis(1405, 7, 2, 1, 0), g.from); assertEquals(Jalali.toMillis(1405, 7, 2, 3, 0), g.at)
        assertEquals(setOf("n", "s3"), r.unsettled)
    }
}
