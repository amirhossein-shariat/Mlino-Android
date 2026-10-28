package app.mlino.core

/**
 * Balances are computed, never stored. The bank-reported balance is the source of truth:
 * a mismatch becomes a [Gap] (a missed transaction just before that tx) and the running balance is synced
 * to the bank figure so one gap never cascades.
 */
object Ledger {
    data class Gap(val txId: String, val cardId: String, val diff: Long)
    data class Result(
        /** Running balance after each counted tx. */
        val after: Map<String, Long>,
        val current: Map<String, Long>,
        /** Tx dated before their card's opening: kept but ignored. */
        val before: Set<String>,
        val gaps: List<Gap>,
    )

    fun effect(t: Tx): Long = when (t.type) {
        TxType.IN -> t.amount
        TxType.OUT, TxType.FEE -> -t.amount
        TxType.ADJUST -> t.amount
        TxType.TRANSFER -> if (t.dirIn) t.amount else -t.amount
    }

    val order: Comparator<Tx> = compareBy<Tx>({ it.at }, { it.createdAt }, { it.id })

    fun compute(cards: List<Card>, txs: List<Tx>): Result {
        val after = HashMap<String, Long>()
        val current = HashMap<String, Long>()
        val before = HashSet<String>()
        val gaps = ArrayList<Gap>()
        val byCard = txs.groupBy { it.cardId }
        for (c in cards) {
            var run = c.openingBalance
            for (t in (byCard[c.id] ?: emptyList()).sortedWith(order)) {
                if (t.at < c.openingAt) { before.add(t.id); continue }
                run += effect(t)
                val bank = t.balanceAfter
                if (bank != null && bank != run) {
                    gaps.add(Gap(t.id, c.id, bank - run))
                    run = bank
                }
                after[t.id] = run
            }
            current[c.id] = run
        }
        return Result(after, current, before, gaps)
    }
}
