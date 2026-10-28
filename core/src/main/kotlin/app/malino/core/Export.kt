package app.malino.core

/** Spreadsheet export of transactions for a time range ([from, to) in epoch millis). */
object Export {
    private fun typeName(t: Tx) = when (t.type) {
        TxType.OUT -> "برداشت"; TxType.IN -> "واریز"; TxType.FEE -> "کارمزد"; TxType.ADJUST -> "تعدیل"
        TxType.TRANSFER -> if (t.dirIn) "انتقال (ورودی)" else "انتقال (خروجی)"
    }

    /** [after] = running balance per tx id from Ledger.compute; [catNames] = category id -> name. */
    fun transactions(cards: List<Card>, txs: List<Tx>, after: Map<String, Long>, catNames: Map<String, String>, from: Long, to: Long): ByteArray {
        val cardName = cards.associate { it.id to it.name }
        val rows = txs.filter { it.at in from until to }.sortedWith(Ledger.order)
        val head = listOf("تاریخ", "ساعت", "کارت", "نوع", "مبلغ", "مانده پس از تراکنش", "دسته", "محدوده", "یادداشت", "منبع")
        val body = rows.map { t ->
            val dt = Jalali.fromMillis(t.at)
            listOf(
                Jalali.format(dt.date, fa = false), "%02d:%02d".format(dt.h, dt.mi), cardName[t.cardId] ?: "", typeName(t), Ledger.effect(t),
                after[t.id], t.categoryId?.let { catNames[it] ?: it } ?: "", if (t.business) "کسب‌وکار" else "شخصی", t.note, t.source,
            )
        }
        val byCat = HashMap<String, LongArray>()
        for (t in rows) {
            val key = t.categoryId?.let { catNames[it] ?: it } ?: "بدون دسته"
            val a = byCat.getOrPut(key) { LongArray(2) }
            when (t.type) { TxType.IN -> a[0] += t.amount; TxType.OUT, TxType.FEE -> a[1] += t.amount; else -> {} }
        }
        val summary = listOf(listOf("دسته", "درآمد", "هزینه")) + byCat.entries.sortedByDescending { it.value[1] + it.value[0] }.map { listOf(it.key, it.value[0], it.value[1]) } +
            listOf(listOf("جمع", byCat.values.sumOf { it[0] }, byCat.values.sumOf { it[1] }))
        return Xlsx.build(listOf(
            Xlsx.Sheet("تراکنش‌ها", listOf(head) + body, listOf(13, 8, 14, 14, 16, 18, 16, 10, 24, 8)),
            Xlsx.Sheet("خلاصه دسته‌ها", summary, listOf(20, 16, 16)),
        ))
    }
}
