package app.malino.core

data class CardReport(val cardId: String, val name: String, val opening: Long, val closing: Long, val inn: Long, val out: Long)

/** One Jalali month. Transfers and balance adjustments are never income or expense. Category "" = uncategorised. */
data class Report(
    val key: String, val y: Int, val m: Int, val txCount: Int,
    val income: Long, val expense: Long,
    val incomeByCat: Map<String, Long>, val expenseByCat: Map<String, Long>,
    val personalIncome: Long, val personalExpense: Long, val businessIncome: Long, val businessExpense: Long,
    /** Net movement of cards whose purpose contains «پس‌انداز»; null when there is no such card. */
    val savings: Long?,
    val cards: List<CardReport>,
    /** Sum of all card balances at month end. */
    val wealth: Long, val wealthChange: Long?,
    val top: List<Tx>,
) {
    val net get() = income - expense
}

object Reports {
    private val SAVING = Regex("پس.?انداز")

    fun build(y: Int, m: Int, cards: List<Card>, txs: List<Tx>, prev: Report? = null): Report {
        val range = Jalali.monthRange(y, m)
        val start = range.first
        val end = range.last + 1
        val before = Ledger.compute(cards, txs.filter { it.at < start }).current
        val after = Ledger.compute(cards, txs.filter { it.at < end }).current
        val opening = cards.associate { it.id to it.openingAt }
        val inMonth = txs.filter { it.at in start until end && it.at >= (opening[it.cardId] ?: Long.MAX_VALUE) }

        val inc = HashMap<String, Long>(); val exp = HashMap<String, Long>()
        var pi = 0L; var pe = 0L; var bi = 0L; var be = 0L
        val perCard = cards.associate { c -> c.id to longArrayOf(0, 0) }
        for (t in inMonth) {
            val e = Ledger.effect(t)
            perCard[t.cardId]?.let { if (e > 0) it[0] += e else it[1] += -e }
            val cat = t.categoryId ?: ""
            when (t.type) {
                TxType.IN -> { inc.merge(cat, t.amount, Long::plus); if (t.business) bi += t.amount else pi += t.amount }
                TxType.OUT, TxType.FEE -> { exp.merge(cat, t.amount, Long::plus); if (t.business) be += t.amount else pe += t.amount }
                else -> {}
            }
        }
        val saving = cards.filter { SAVING.containsMatchIn(it.purpose) }
        val wealth = cards.sumOf { after[it.id] ?: 0L }
        return Report(
            key = Jalali.monthKey(y, m), y = y, m = m, txCount = inMonth.size,
            income = inc.values.sum(), expense = exp.values.sum(), incomeByCat = inc, expenseByCat = exp,
            personalIncome = pi, personalExpense = pe, businessIncome = bi, businessExpense = be,
            savings = if (saving.isEmpty()) null else saving.sumOf { (after[it.id] ?: 0L) - (before[it.id] ?: 0L) },
            cards = cards.map { c -> CardReport(c.id, c.name, before[c.id] ?: 0, after[c.id] ?: 0, perCard[c.id]!![0], perCard[c.id]!![1]) },
            wealth = wealth, wealthChange = prev?.let { wealth - it.wealth },
            top = inMonth.filter { it.type == TxType.OUT }.sortedByDescending { it.amount }.take(5),
        )
    }

    private fun idx(y: Int, m: Int) = y * 12 + m - 1
    private fun ym(i: Int) = (i / 12) to (i % 12 + 1)

    /** Reports for the [months] months ending at (y, m), oldest first, each chained to the one before it. */
    fun series(y: Int, m: Int, months: Int, cards: List<Card>, txs: List<Tx>): List<Report> {
        val last = idx(y, m)
        var prev: Report? = ym(last - months).let { (py, pm) -> build(py, pm, cards, txs, null) }
        val out = ArrayList<Report>()
        for (i in last - months + 1..last) {
            val (iy, im) = ym(i)
            prev = build(iy, im, cards, txs, prev)
            out.add(prev)
        }
        return out
    }

    /** Percentage change from [prev] to [cur]; null when there is nothing to compare with. */
    fun delta(cur: Long, prev: Long): Int? = if (prev == 0L) null else (((cur - prev) * 100.0) / kotlin.math.abs(prev)).toInt()
}
