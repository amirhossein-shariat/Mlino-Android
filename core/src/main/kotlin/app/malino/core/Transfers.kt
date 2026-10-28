package app.malino.core

import kotlin.math.max
import kotlin.math.min

/**
 * Pairs an `out` on card A with an `in` on card B (different cards, within 10 minutes). The out side may be larger than the in side
 * by a small fee: fee <= min(50,000, max(10,000, 2% of the in amount)). Ambiguous (>= 2 candidates) = no match.
 */
object Transfers {
    const val WINDOW_MS = 10 * 60_000L

    data class Match(val out: Tx, val inn: Tx, val fee: Long)

    private fun plain(t: Tx, type: TxType) = t.type == type && t.transferPairId == null

    private fun fits(out: Tx, inn: Tx): Boolean {
        if (out.cardId == inn.cardId || out.type != TxType.OUT || inn.type != TxType.IN) return false
        if (kotlin.math.abs(out.at - inn.at) > WINDOW_MS) return false
        val fee = out.amount - inn.amount
        return fee >= 0 && fee <= min(50_000L, max(10_000L, inn.amount * 2 / 100))
    }

    /** Looks for the single counterpart of [t] among [others]. */
    fun find(t: Tx, others: List<Tx>): Match? {
        val pool = others.filter { it.id != t.id }
        val cands = when (t.type) {
            TxType.OUT -> pool.filter { plain(it, TxType.IN) && fits(t, it) }.map { Match(t, it, t.amount - it.amount) }
            TxType.IN -> pool.filter { plain(it, TxType.OUT) && fits(it, t) }.map { Match(it, t, it.amount - t.amount) }
            else -> emptyList()
        }
        if (plain(t, t.type).not()) return null
        return cands.singleOrNull()
    }

    /**
     * Turns a match into the txs to save: both sides become TRANSFER (amount = the received amount, so the sum still equals the bank's
     * debit); a non-zero difference is a separate FEE tx on the out card, 1 s before the out side so the bank balance still lines up.
     */
    fun apply(m: Match, feeId: String): List<Tx> {
        val pair = m.out.id
        val out = m.out.copy(type = TxType.TRANSFER, dirIn = false, amount = m.inn.amount, transferPairId = pair)
        val inn = m.inn.copy(type = TxType.TRANSFER, dirIn = true, transferPairId = pair)
        val res = mutableListOf(out, inn)
        if (m.fee > 0) res.add(Tx(id = feeId, cardId = m.out.cardId, at = m.out.at - 1000, type = TxType.FEE, amount = m.fee,
            note = "کارمزد انتقال", source = m.out.source, status = m.out.status, createdAt = m.out.createdAt))
        return res
    }
}
