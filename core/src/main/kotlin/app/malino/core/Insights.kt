package app.malino.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/** Budgets, recurring expenses, suspicious transactions, goals, installments, asset values. "Spend" = OUT and FEE only. */
object Insights {
    private const val DAY = 86_400_000L

    private fun isSpend(t: Tx) = t.type == TxType.OUT || t.type == TxType.FEE
    private fun median(a: List<Double>): Double {
        if (a.isEmpty()) return 0.0
        val s = a.sorted(); val n = s.size
        return if (n % 2 == 1) s[(n - 1) / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }
    private fun quantile(sorted: List<Double>, q: Double): Double {
        val p = (sorted.size - 1) * q
        val lo = floor(p).toInt(); val hi = ceil(p).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (p - lo)
    }
    private fun monthIndex(y: Int, m: Int) = y * 12 + (m - 1)
    private fun fromIndex(i: Int) = (i / 12) to (i % 12 + 1)
    private fun clampDay(y: Int, m: Int, d: Int) = minOf(d, Jalali.monthLength(y, m))

    // ---------- budgets ----------
    /** Rial spent per category ("" = uncategorised) in one Jalali month; tx before a card's opening are ignored. */
    fun spendByCategory(txs: List<Tx>, cards: List<Card>, y: Int, m: Int): Map<String, Long> {
        val r = Jalali.monthRange(y, m)
        val opening = cards.associate { it.id to it.openingAt }
        val out = HashMap<String, Long>()
        for (t in txs) {
            if (!isSpend(t) || t.at < r.first || t.at > r.last || t.at < (opening[t.cardId] ?: Long.MAX_VALUE)) continue
            out.merge(t.categoryId ?: "", t.amount, Long::plus)
        }
        return out
    }

    enum class Level { OK, WARN, OVER }
    data class BudgetStatus(val categoryId: String, val limit: Long, val spent: Long, val pct: Double, val level: Level)

    /** ok < 80% <= warn < 100% <= over */
    fun budgetStatus(budgets: List<Budget>, spend: Map<String, Long>): List<BudgetStatus> = budgets.map { b ->
        val spent = spend[b.categoryId] ?: 0L
        val pct = if (b.monthlyLimit > 0) spent.toDouble() / b.monthlyLimit else 0.0
        BudgetStatus(b.categoryId, b.monthlyLimit, spent, pct, if (pct >= 1) Level.OVER else if (pct >= 0.8) Level.WARN else Level.OK)
    }

    // ---------- recurring expenses ----------
    data class Recurring(
        val cardId: String, val categoryId: String?, val amount: Long, val day: Int, val months: Int, val txIds: List<String>,
        val lastMonth: Pair<Int, Int>, val next: Jalali.Date,
    )

    private class Cluster(var amount: Double, var day: Int, val txs: MutableList<Pair<Tx, Jalali.DateTime>>)

    /** Same card + category, amounts within 5% and day-of-month within 3 days, in >= 3 different Jalali months; only active series. */
    fun detectRecurring(txs: List<Tx>, now: Long): List<Recurring> {
        val cur = Jalali.fromMillis(now).date.let { monthIndex(it.y, it.m) }
        val series = ArrayList<Recurring>()
        for (list in txs.filter(::isSpend).groupBy { it.cardId + "|" + (it.categoryId ?: "") }.values) {
            val clusters = ArrayList<Cluster>()
            for (t in list.sortedWith(Ledger.order)) {
                val j = Jalali.fromMillis(t.at)
                val c = clusters.firstOrNull { abs(t.amount - it.amount) <= max(it.amount * 0.05, 2000.0) && abs(j.date.d - it.day) <= 3 }
                if (c != null) {
                    c.txs.add(t to j)
                    c.amount = median(c.txs.map { it.first.amount.toDouble() })
                    c.day = median(c.txs.map { it.second.date.d.toDouble() }).roundToLong().toInt()
                } else clusters.add(Cluster(t.amount.toDouble(), j.date.d, mutableListOf(t to j)))
            }
            for (c in clusters) {
                val months = c.txs.map { monthIndex(it.second.date.y, it.second.date.m) }.distinct().sorted()
                if (months.size < 3) continue
                val last = months.last()
                if (last < cur - 1) continue
                val (ny, nm) = fromIndex(last + 1)
                series.add(Recurring(c.txs[0].first.cardId, c.txs[0].first.categoryId, c.amount.roundToLong(), c.day, months.size,
                    c.txs.map { it.first.id }, fromIndex(last), Jalali.Date(ny, nm, clampDay(ny, nm, c.day))))
            }
        }
        return series.sortedBy { it.next.y * 10_000 + it.next.m * 100 + it.next.d }
    }

    data class Forecast(val total: Long, val items: List<Recurring>, val month: Pair<Int, Int>)

    fun forecastNextMonth(series: List<Recurring>, now: Long): Forecast {
        val target = Jalali.fromMillis(now).date.let { monthIndex(it.y, it.m) } + 1
        val hit = series.filter { monthIndex(it.lastMonth.first, it.lastMonth.second) >= target - 2 }
        return Forecast(hit.sumOf { it.amount }, hit, fromIndex(target))
    }

    // ---------- suspicious transactions ----------
    enum class Reason { AMOUNT, HOUR }
    data class Suspicious(val txId: String, val cardId: String, val reasons: List<Reason>)

    /** Recent withdrawals far above the card's usual range (> Q3 + 3*IQR and > 3x median) or at 00-05 Tehran when the card is almost never used then. */
    fun suspicious(txs: List<Tx>, cards: List<Card>, now: Long, days: Int = 7): List<Suspicious> {
        val out = ArrayList<Suspicious>()
        for (c in cards) {
            val mine = txs.filter { it.cardId == c.id && it.type == TxType.OUT && it.at >= c.openingAt }.sortedWith(Ledger.order)
            mine.forEachIndexed { i, t ->
                if (now - t.at > days * DAY) return@forEachIndexed
                val hist = mine.subList(0, i)
                val reasons = ArrayList<Reason>()
                if (hist.size >= 8) {
                    val s = hist.map { it.amount.toDouble() }.sorted()
                    val q1 = quantile(s, 0.25); val q3 = quantile(s, 0.75)
                    if (t.amount > q3 + 3 * (q3 - q1) && t.amount > 3 * median(s)) reasons.add(Reason.AMOUNT)
                }
                if (hist.size >= 10) {
                    val night = { ms: Long -> Jalali.fromMillis(ms).h < 5 }
                    val share = hist.count { night(it.at) }.toDouble() / hist.size
                    if (night(t.at) && share < 0.05) reasons.add(Reason.HOUR)
                }
                if (reasons.isNotEmpty()) out.add(Suspicious(t.id, c.id, reasons))
            }
        }
        return out
    }

    // ---------- assets ----------
    /** Rial value, or null when the needed price has not been entered yet. */
    fun assetValue(a: Asset, p: Prices): Long? = when (a.type) {
        AssetType.GOLD -> if (p.gold18 > 0) (a.qty * (a.karat / 18.0) * p.gold18).roundToLong() else null
        AssetType.USD -> if (p.usd > 0) (a.qty * p.usd).roundToLong() else null
        AssetType.COIN -> if (p.coin > 0) (a.qty * p.coin).roundToLong() else null
        AssetType.OTHER -> a.value
    }

    // ---------- goals ----------
    data class GoalProgress(val current: Long, val remaining: Long, val pct: Double, val monthlyRate: Long?, val doneAt: Jalali.Date?, val done: Boolean)

    /** Current = linked cards' balances + linked assets' values. Monthly rate = net movement of the linked cards over the last 90 days (needs >= 14 days of data). */
    fun goalProgress(goal: Goal, cards: List<Card>, balances: Map<String, Long>, assets: List<Asset>, prices: Prices, txs: List<Tx>, now: Long): GoalProgress {
        val myCards = cards.filter { it.id in goal.cardIds }
        val myAssets = assets.filter { it.id in goal.assetIds }
        val current = myCards.sumOf { balances[it.id] ?: 0L } + myAssets.sumOf { assetValue(it, prices) ?: 0L }
        val remaining = max(0L, goal.target - current)
        var rate: Long? = null
        if (myCards.isNotEmpty()) {
            val since = max(now - 90 * DAY, myCards.maxOf { it.openingAt })
            val observed = (now - since).toDouble() / DAY
            if (observed >= 14) {
                val ids = myCards.map { it.id }.toSet()
                val net = txs.filter { it.cardId in ids && it.at in since..now }.sumOf { Ledger.effect(it) }
                rate = (net / (observed / 30)).roundToLong()
            }
        }
        val doneAt = if (remaining > 0 && rate != null && rate > 0)
            Jalali.fromMillis(now + ceil(remaining.toDouble() / rate).toLong() * 30 * DAY).date else null
        return GoalProgress(current, remaining, if (goal.target > 0) minOf(1.0, current.toDouble() / goal.target) else 0.0, rate, doneAt, remaining == 0L)
    }

    // ---------- debts / installments ----------
    data class Due(val debtId: String, val index: Int, val person: String, val owedToMe: Boolean, val amount: Long, val due: Long, val daysLeft: Int)

    /** Unpaid installments due within [withinDays] (overdue ones included, daysLeft < 0), soonest first. */
    fun dueInstallments(debts: List<Debt>, now: Long, withinDays: Int = 7): List<Due> {
        val t = Jalali.today(now)
        val today = Jalali.toDays(t.y, t.m, t.d)
        val out = ArrayList<Due>()
        for (d in debts) d.installments.forEachIndexed { idx, inst ->
            if (inst.paid) return@forEachIndexed
            val j = Jalali.fromMillis(inst.due).date
            val left = (Jalali.toDays(j.y, j.m, j.d) - today).toInt()
            if (left <= withinDays) out.add(Due(d.id, idx, d.person, d.owedToMe, inst.amount, inst.due, left))
        }
        return out.sortedBy { it.daysLeft }
    }

    /** Equal installments (the last takes the remainder), [every] Jalali months apart, day clamped to the month length. */
    fun splitInstallments(total: Long, count: Int, first: Jalali.Date, every: Int = 1): List<Installment> {
        val base = total / count
        return (0 until count).map { i ->
            val (y, m) = fromIndex(monthIndex(first.y, first.m) + i * every)
            Installment(Jalali.toMillis(y, m, clampDay(y, m, first.d)), if (i == count - 1) total - base * (count - 1) else base)
        }
    }
}
