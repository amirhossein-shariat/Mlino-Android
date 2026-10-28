package app.malino.data

import android.content.Context
import androidx.room.withTransaction
import app.malino.core.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class Repo(ctx: Context) {
    val db = Db.create(ctx)
    val plan = PlanStore(ctx)
    val budgets = kotlinx.coroutines.flow.MutableStateFlow(plan.budgets())
    val debts = kotlinx.coroutines.flow.MutableStateFlow(plan.debts())
    val goals = kotlinx.coroutines.flow.MutableStateFlow(plan.goals())
    val assets = kotlinx.coroutines.flow.MutableStateFlow(plan.assets())
    val prices = kotlinx.coroutines.flow.MutableStateFlow(plan.prices())
    val templates = kotlinx.coroutines.flow.MutableStateFlow(plan.templates())
    fun saveTemplates(l: List<app.malino.core.Template>) { plan.saveTemplates(l); templates.value = l }

    fun saveBudgets(l: List<app.malino.core.Budget>) { plan.saveBudgets(l); budgets.value = l }
    fun saveDebts(l: List<app.malino.core.Debt>) { plan.saveDebts(l); debts.value = l }
    fun saveGoals(l: List<app.malino.core.Goal>) { plan.saveGoals(l); goals.value = l }
    fun saveAssets(l: List<app.malino.core.Asset>) { plan.saveAssets(l); assets.value = l }
    fun savePrices(p: app.malino.core.Prices) { plan.savePrices(p); prices.value = p }
    private val prefs = ctx.getSharedPreferences("malino", Context.MODE_PRIVATE)
    val stats = app.malino.capture.CaptureStats(ctx)
    val security = app.malino.security.SecurityStore(ctx)

    val cards: Flow<List<CardE>> = db.cards().observe()
    val txs: Flow<List<TxE>> = db.tx().observe()
    val categories: Flow<List<CategoryE>> = db.categories().observe()
    val attention: Flow<List<RawE>> = db.raw().observeAttention()
    val recentRaw: Flow<List<RawE>> = db.raw().observeRecent()

    var hideBalance: Boolean
        get() = prefs.getBoolean("hide_balance", false)
        set(v) { prefs.edit().putBoolean("hide_balance", v).apply() }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }

    private fun newId() = UUID.randomUUID().toString()

    /** Senders (SMS) whose messages count as bank messages even when no parser understands them yet. */
    var bankSenders: Set<String>
        get() = prefs.getStringSet("senders", DEFAULT_SENDERS) ?: DEFAULT_SENDERS
        set(v) { prefs.edit().putStringSet("senders", v).apply() }

    /** Apps whose notifications are always kept (unparsed ones show up in the inbox for review). */
    var bankPackages: Set<String>
        get() = prefs.getStringSet("bank_pkgs", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("bank_pkgs", v).apply() }

    fun looksLikeBank(sender: String, text: String): Boolean =
        bankSenders.any { sender.contains(it, ignoreCase = true) } || Parsers.parse(text, System.currentTimeMillis(), templates.value, sender) != null

    suspend fun seedCategories() {
        db.categories().insertMissing(DEFAULT_CATEGORIES)
    }

    /** Full pipeline for one captured message. Returns the created tx id (or null). Personal messages never get here. */
    suspend fun ingest(msg: RawMsg): String? = db.withTransaction {
        val cards = db.cards().all().map { it.toCore() }
        val near = db.tx().between(msg.receivedMs - 2 * 86_400_000L, msg.receivedMs + 86_400_000L).map { it.toCore() }
        val res = Ingest.processWith(msg, cards, near, templates.value, ::newId)
        val rawId = newId()
        suspend fun raw(state: String, txId: String? = null) =
            db.raw().put(RawE(rawId, msg.kind, msg.sender, msg.title, msg.body, msg.receivedMs, state, txId))
        when (res) {
            is IngestResult.Ignored -> null
            IngestResult.Unparsed -> { if (db.raw().sameCount(msg.sender, msg.body) == 0) raw("unparsed"); null }
            is IngestResult.Unmapped -> { if (db.raw().sameCount(msg.sender, msg.body) == 0) raw("unmapped"); null }
            IngestResult.Duplicate -> { raw("duplicate"); null }
            is IngestResult.Created -> {
                val withRule = res.txs.map { t -> if (t.id == res.newTxId) suggest(t) else t }
                db.tx().upsert(withRule.map { it.toE() })
                raw("done", res.newTxId)
                res.newTxId
            }
        }
    }

    /** Pre-selects the learned category (same card, type and amount). */
    private suspend fun suggest(t: Tx): Tx {
        val rule = db.rules().get("${t.cardId}|${t.type.name}|${t.amount}") ?: return t
        return t.copy(categoryId = rule.categoryId)
    }

    /** Re-runs messages that failed earlier (after the user fixed a card key). */
    suspend fun reprocess(): Int {
        var n = 0
        for (r in db.raw().attention()) {
            val before = db.raw()
            val id = ingest(RawMsg(r.kind, r.sender, r.title, r.body, r.receivedAt))
            if (id != null) { n++; before.setState(r.id, "dismissed", id) }
        }
        return n
    }

    suspend fun dismissRaw(id: String) = db.raw().setState(id, "dismissed", null)

    suspend fun confirm(txId: String, categoryId: String?) = db.withTransaction {
        val t = db.tx().get(txId)?.toCore() ?: return@withTransaction
        db.tx().upsert(listOf(t.copy(status = TxStatus.CONFIRMED, categoryId = categoryId ?: t.categoryId).toE()))
        if (categoryId != null && (t.type == TxType.OUT || t.type == TxType.IN)) {
            val key = "${t.cardId}|${t.type.name}|${t.amount}"
            db.rules().put(RuleE(key, categoryId, (db.rules().get(key)?.uses ?: 0) + 1))
        }
    }

    suspend fun saveTx(t: Tx) = db.tx().upsert(listOf(t.toE()))
    suspend fun deleteTx(id: String) = db.tx().delete(id)
    suspend fun saveCard(c: Card) = db.cards().upsert(c.toE())

    /** A card that still has transactions cannot be deleted. */
    suspend fun deleteCard(c: CardE): Boolean {
        if (db.tx().countForCard(c.id) > 0) return false
        db.cards().delete(c); return true
    }

    /** Adds the missing amount of a gap 1 s before the transaction that revealed it. */
    suspend fun resolveGap(gap: Ledger.Gap, asFee: Boolean) {
        val t = db.tx().get(gap.txId)?.toCore() ?: return
        val fix = if (asFee && gap.diff < 0)
            Tx(newId(), gap.cardId, t.at - 1000, TxType.FEE, -gap.diff, note = "کارمزد", categoryId = "fee")
        else Tx(newId(), gap.cardId, t.at - 1000, TxType.ADJUST, gap.diff, note = "تعدیل مانده")
        db.tx().upsert(listOf(fix.toE()))
    }

    /** Undo an auto-detected transfer: the pair goes back to plain out/in and the fee is merged back into the out side. */
    suspend fun unlinkTransfer(txId: String) = db.withTransaction {
        val t = db.tx().get(txId)?.toCore() ?: return@withTransaction
        val pair = t.transferPairId ?: return@withTransaction
        val all = db.tx().between(t.at - 3_600_000L, t.at + 3_600_000L).map { it.toCore() }
        val parts = all.filter { it.transferPairId == pair && it.type == TxType.TRANSFER }
        val out = parts.firstOrNull { !it.dirIn }
        val fee = out?.let { o -> all.firstOrNull { it.type == TxType.FEE && it.cardId == o.cardId && it.at == o.at - 1000 && it.note == "کارمزد انتقال" } }
        val fixed = parts.map { p ->
            if (p.dirIn) p.copy(type = TxType.IN, dirIn = false, transferPairId = null)
            else p.copy(type = TxType.OUT, dirIn = false, transferPairId = null, amount = p.amount + (fee?.amount ?: 0))
        }
        db.tx().upsert(fixed.map { it.toE() })
        fee?.let { db.tx().delete(it.id) }
    }

    companion object {
        val DEFAULT_SENDERS = setOf("PARSIANBANK", "TEJARAT", "SAMAN", "MELLI", "MELLAT", "SEPAH", "PASARGAD", "KESHAVARZI", "REFAH", "BLUBANK")
        val DEFAULT_CATEGORIES = listOf(
            CategoryE("food", "خوراک", "expense", false, 1), CategoryE("transport", "حمل‌ونقل", "expense", false, 2),
            CategoryE("shopping", "خرید", "expense", false, 3), CategoryE("bills", "قبض و اقساط", "expense", false, 4),
            CategoryE("health", "سلامت", "expense", false, 5), CategoryE("family", "همسر و خانواده", "expense", false, 6),
            CategoryE("biz_cost", "هزینه‌ی کسب‌وکار", "expense", true, 7), CategoryE("fee", "کارمزد بانکی", "expense", false, 8),
            CategoryE("other_out", "سایر", "expense", false, 9),
            CategoryE("invoice", "درآمد فاکتور", "income", true, 10), CategoryE("salary", "حقوق و درآمد", "income", false, 11),
            CategoryE("other_in", "سایر", "income", false, 12),
            CategoryE(Ingest.ROUNDUP, "پس‌انداز رُند", "expense", false, 13),
        )
    }
}
