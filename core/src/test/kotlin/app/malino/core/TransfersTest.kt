package app.malino.core

import kotlin.test.*

class TransfersTest {
    private val base = Jalali.toMillis(1405, 7, 10, 12, 0)
    private fun tx(id: String, card: String, type: TxType, amount: Long, at: Long) = Tx(id, card, at, type, amount, status = TxStatus.INBOX, createdAt = at)

    @Test fun matchesWithFee() {
        val out = tx("o", "A", TxType.OUT, 1_005_000, base)
        val inn = tx("i", "B", TxType.IN, 1_000_000, base + 90_000)
        val m = Transfers.find(inn, listOf(out))!!
        assertEquals(5_000, m.fee)
        assertEquals(m.out, Transfers.find(out, listOf(inn))!!.out)
        val res = Transfers.apply(m, "fee1")
        assertEquals(3, res.size)
        val (o, i, f) = res
        assertEquals(TxType.TRANSFER, o.type); assertFalse(o.dirIn); assertEquals(1_000_000, o.amount)
        assertTrue(i.dirIn); assertEquals(o.transferPairId, i.transferPairId)
        assertEquals(TxType.FEE, f.type); assertEquals(5_000, f.amount); assertEquals(base - 1000, f.at); assertEquals("A", f.cardId)
        // the debit on card A is unchanged: 1,000,000 + 5,000
        val card = Card("A", "a", openingBalance = 10_000_000, openingAt = 0)
        assertEquals(8_995_000, Ledger.compute(listOf(card), listOf(o, f)).current["A"])
    }

    @Test fun rejects() {
        val out = tx("o", "A", TxType.OUT, 1_000_000, base)
        assertNull(Transfers.find(tx("i", "A", TxType.IN, 1_000_000, base), listOf(out)), "same card")
        assertNull(Transfers.find(tx("i", "B", TxType.IN, 1_000_000, base + 11 * 60_000), listOf(out)), "too far apart")
        assertNull(Transfers.find(tx("i", "B", TxType.IN, 1_200_000, base), listOf(out)), "in larger than out")
        assertNull(Transfers.find(tx("i", "B", TxType.IN, 800_000, base), listOf(out)), "fee too large")
        val two = listOf(out, tx("o2", "C", TxType.OUT, 1_000_000, base + 1000))
        assertNull(Transfers.find(tx("i", "B", TxType.IN, 1_000_000, base), two), "ambiguous")
    }
}
