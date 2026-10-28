package app.malino.core

/**
 * Balances are computed, never stored. The bank-reported balance is the source of truth:
 * a mismatch becomes a [Gap] (a missed transaction just before that tx) and the running balance is synced
 * to the bank figure so one gap never cascades.
 */
object Ledger {
    /** [from]..[at] is the period the missing money disappeared in (previous bank-confirmed message .. the message that revealed it). */
    data class Gap(val txId: String, val cardId: String, val diff: Long, val from: Long = 0, val at: Long = 0, val span: List<String> = emptyList())
    data class Result(
        /** Running balance after each counted tx. */
        val after: Map<String, Long>,
        val current: Map<String, Long>,
        /** Tx dated before their card's opening: kept but ignored. */
        val before: Set<String>,
        val gaps: List<Gap>,
        /** Tx inside a period whose bank balance does not agree yet (shown faded until the gap is settled). */
        val unsettled: Set<String> = emptySet(),
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
            var anchorAt = c.openingAt
            val window = ArrayList<String>()
            for (t in (byCard[c.id] ?: emptyList()).sortedWith(order)) {
                if (t.at < c.openingAt) { before.add(t.id); continue }
                run += effect(t)
                window.add(t.id)
                val bank = t.balanceAfter
                if (bank != null) {
                    if (bank != run) {
                        gaps.add(Gap(t.id, c.id, bank - run, anchorAt, t.at, window.toList()))
                        run = bank
                    }
                    anchorAt = t.at
                    window.clear()
                }
                after[t.id] = run
            }
            current[c.id] = run
        }
        return Result(after, current, before, gaps, gaps.flatMap { it.span }.toSet())
    }
}
