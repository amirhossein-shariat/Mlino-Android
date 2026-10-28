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

    /** 0 = follow the device, 1 = light, 2 = dark. */
    val themeMode = kotlinx.coroutines.flow.MutableStateFlow(prefs.getInt("theme", 0))
    fun setThemeMode(m: Int) { themeMode.value = m; prefs.edit().putInt("theme", m).apply() }

    /** The 5-step intro tour on the main screens; shown once after onboarding. */
    var tourDone: Boolean
        get() = prefs.getBoolean("tour_done", false)
        set(v) { prefs.edit().putBoolean("tour_done", v).apply() }

    /** Messages containing any of these words are skipped and never stored (default: «رمز»). */
    var ignoreWords: Set<String>
        get() = prefs.getStringSet("ignore_words", Parsers.DEFAULT_IGNORE_WORDS.toSet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("ignore_words", v).apply() }

    fun looksLikeBank(sender: String, text: String): Boolean =
        bankSenders.any { sender.contains(it, ignoreCase = true) } || Parsers.parse(text, System.currentTimeMillis(), templates.value, sender) != null

    suspend fun seedCategories() {
        db.categories().insertMissing(DEFAULT_CATEGORIES)
    }

    /** Full pipeline for one captured message. Returns the created tx id (or null). Personal messages never get here. */
    suspend fun ingest(msg: RawMsg, history: Boolean = false): String? = db.withTransaction {
        val cards = db.cards().all().map { it.toCore() }
        val near = db.tx().between(msg.receivedMs - 2 * 86_400_000L, msg.receivedMs + 86_400_000L).map { it.toCore() }
        val res = Ingest.processWith(msg, cards, near, templates.value, ignoreWords, newId = ::newId)
        val rawId = newId()
        suspend fun raw(state: String, txId: String? = null) =
            db.raw().put(RawE(rawId, msg.kind, msg.sender, msg.title, msg.body, msg.receivedMs, state, txId))
        when (res) {
            is IngestResult.Ignored -> null
            IngestResult.Unparsed -> { if (db.raw().sameCount(msg.sender, msg.body) == 0) raw("unparsed"); null }
            is IngestResult.Unmapped -> { if (db.raw().sameCount(msg.sender, msg.body) == 0) raw("unmapped"); null }
            IngestResult.Duplicate -> { raw("duplicate"); null }
            is IngestResult.Created -> {
                var withRule = res.txs.map { t -> if (t.id == res.newTxId) suggest(t) else t }
                if (history) {
                    // history import: the bank's own balance is the truth. A message older than the card's start moves the start back,
                    // with the opening balance derived from that message; later differences then show up as gaps for the user to decide.
                    val main = withRule.firstOrNull { it.id == res.newTxId }
                    val card = main?.let { m -> cards.firstOrNull { it.id == m.cardId } }
                    val bal = main?.balanceAfter
                    if (main != null && card != null && bal != null && main.at < card.openingAt) {
                        val fresh = withRule.filter { it.cardId == card.id && it.id !in near.map { n -> n.id } }
                        val newStart = fresh.minOf { it.at } - 1000
                        db.cards().upsert(card.copy(openingAt = newStart, openingBalance = bal - fresh.sumOf { Ledger.effect(it) }).toE())
                    }
                    // old messages are bank facts, not guesses: no inbox step
                    withRule = withRule.map { if (it.id == res.newTxId) it.copy(status = TxStatus.CONFIRMED) else it }
                }
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

    data class ImportResult(val added: Int, val duplicates: Int)

    /** Adds the rows of a statement as confirmed transactions. Rows already known (same card, direction, amount, balance, minute) are skipped. */
    suspend fun importStatement(cardId: String, rows: List<Statement.Row>): ImportResult = db.withTransaction {
        val card = db.cards().all().firstOrNull { it.id == cardId }?.toCore() ?: return@withTransaction ImportResult(0, 0)
        val sorted = rows.sortedBy { it.at }
        if (sorted.isEmpty()) return@withTransaction ImportResult(0, 0)
        val known = db.tx().between(sorted.first().at - 86_400_000L, sorted.last().at + 86_400_000L).filter { it.cardId == cardId }.mapNotNull { it.dedupe }.toHashSet()
        var dup = 0
        val fresh = ArrayList<Tx>()
        for ((i, r) in sorted.withIndex()) {
            val key = Ingest.dedupeKey(cardId, r.out, r.amount, r.balance ?: -1L, r.at)
            if (key in known) { dup++; continue }
            val base = Tx(newId(), cardId, r.at, if (r.out) TxType.OUT else TxType.IN, r.amount, balanceAfter = r.balance, note = r.note,
                source = "import", status = TxStatus.CONFIRMED, dedupe = key, createdAt = r.at + i)
            fresh.add(suggest(base))
        }
        // the statement is older than the card's start: move the start back, opening balance derived from the first row that has a balance
        val first = fresh.firstOrNull()
        if (first != null && first.at < card.openingAt) {
            val k = fresh.indexOfFirst { it.balanceAfter != null }
            if (k >= 0) {
                val opening = fresh[k].balanceAfter!! - fresh.take(k + 1).sumOf { Ledger.effect(it) }
                db.cards().upsert(card.copy(openingAt = first.at - 1000, openingBalance = opening).toE())
            }
        }
        db.tx().upsert(fresh.map { it.toE() })
        ImportResult(fresh.size, dup)
    }

    /** The opening balance was entered after this message: restart the card from it (balance = the bank balance it reported). */
    suspend fun moveStartAfter(txId: String) {
        val t = db.tx().get(txId)?.toCore() ?: return
        val bal = t.balanceAfter ?: return
        val card = db.cards().all().firstOrNull { it.id == t.cardId }?.toCore() ?: return
        db.cards().upsert(card.copy(openingAt = t.at + 1, openingBalance = bal).toE())
    }

    enum class Reset { AllTx, TxRange, Learned, Log, Plan, Everything }

    /** Destructive clean-ups offered in settings (the caller makes a safety backup first). [to] is exclusive. */
    suspend fun reset(kind: Reset, from: Long = 0, to: Long = Long.MAX_VALUE) {
        when (kind) {
            Reset.AllTx -> db.withTransaction { db.tx().deleteAll(); db.raw().deleteAll() }
            Reset.TxRange -> db.tx().deleteBetween(from, to)
            Reset.Learned -> { db.rules().deleteAll(); saveTemplates(emptyList()); ignoreWords = Parsers.DEFAULT_IGNORE_WORDS.toSet() }
            Reset.Log -> db.raw().deleteAll()
            Reset.Plan -> resetPlan()
            Reset.Everything -> {
                db.withTransaction { db.tx().deleteAll(); db.raw().deleteAll(); db.rules().deleteAll(); db.cards().deleteAll(); db.categories().deleteAll() }
                resetPlan(); saveTemplates(emptyList())
                prefs.edit().remove("ignore_words").remove("bank_pkgs").remove("gap_snooze").apply()
                snoozedGaps.value = emptySet()
                seedCategories()
            }
        }
    }

    private fun resetPlan() {
        saveBudgets(emptyList()); saveDebts(emptyList()); saveGoals(emptyList()); saveAssets(emptyList()); savePrices(Prices())
    }

    /** Gaps the user postponed; they stay faded in the list but leave the inbox badge. */
    val snoozedGaps = kotlinx.coroutines.flow.MutableStateFlow(prefs.getStringSet("gap_snooze", emptySet()) ?: emptySet())
    fun snoozeGap(txId: String, on: Boolean) {
        val v = if (on) snoozedGaps.value + txId else snoozedGaps.value - txId
        snoozedGaps.value = v
        prefs.edit().putStringSet("gap_snooze", v).apply()
    }

    /** Adds the missing amount of a gap as the transaction the user chose, inside the gap's period (just before the message that revealed it). */
    suspend fun resolveGap(gap: Ledger.Gap, type: TxType, categoryId: String?, note: String, chosenAt: Long? = null) {
        val t = db.tx().get(gap.txId)?.toCore() ?: return
        // anywhere inside the gap period; outside it the balance would not add up again
        val at = (chosenAt ?: (t.at - 1000)).coerceIn(gap.from + 1, t.at - 1)
        val amount = kotlin.math.abs(gap.diff)
        val fix = when (type) {
            TxType.OUT -> Tx(newId(), gap.cardId, at, TxType.OUT, amount, note = note, categoryId = categoryId)
            TxType.FEE -> Tx(newId(), gap.cardId, at, TxType.FEE, amount, note = note.ifBlank { "کارمزد" }, categoryId = "fee")
            TxType.IN -> Tx(newId(), gap.cardId, at, TxType.IN, amount, note = note, categoryId = categoryId)
            else -> Tx(newId(), gap.cardId, at, TxType.ADJUST, gap.diff, note = note.ifBlank { "تعدیل مانده" })
        }
        db.tx().upsert(listOf(fix.toE()))
        snoozeGap(gap.txId, false)
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
            // added later: insertMissing adds them on update without touching what the user already has
            CategoryE("rent", "اجاره و مسکن", "expense", false, 14), CategoryE("telecom", "موبایل و اینترنت", "expense", false, 15),
            CategoryE("edu", "آموزش", "expense", false, 16), CategoryE("fun", "تفریح و رستوران", "expense", false, 17),
            CategoryE("gift", "هدیه و کمک", "expense", false, 18), CategoryE("invest", "سرمایه‌گذاری", "expense", false, 19),
            CategoryE("interest", "سود سپرده", "income", false, 20), CategoryE("refund", "بازگشت وجه", "income", false, 21),
            CategoryE("gift_in", "هدیه", "income", false, 22),
            CategoryE("grocery", "سوپرمارکت و خواروبار", "expense", false, 23), CategoryE("installment", "قسط و بازپرداخت وام", "expense", false, 24),
            CategoryE("insurance", "بیمه", "expense", false, 25), CategoryE("car", "خودرو و تعمیرات", "expense", false, 26),
            CategoryE("clothes", "پوشاک", "expense", false, 27), CategoryE("beauty", "آرایش و زیبایی", "expense", false, 28),
            CategoryE("subscription", "اشتراک‌ها و اپ‌ها", "expense", false, 29), CategoryE("travel", "سفر", "expense", false, 30),
            CategoryE("tax", "مالیات و عوارض", "expense", false, 31), CategoryE("loan_out", "قرض دادن", "expense", false, 32),
            CategoryE("freelance", "پروژه و فریلنس", "income", false, 33), CategoryE("sales", "فروش کالا و خدمات", "income", true, 34),
            CategoryE("rent_in", "اجاره‌ی دریافتی", "income", false, 35), CategoryE("bonus", "پاداش و عیدی", "income", false, 36),
            CategoryE("profit", "سود سرمایه‌گذاری", "income", false, 37), CategoryE("loan_in", "وام و قرض دریافتی", "income", false, 38),
            CategoryE("subsidy", "یارانه و کمک‌هزینه", "income", false, 39), CategoryE("sell_asset", "فروش دارایی", "income", false, 40),
            CategoryE("cashback", "کش‌بک و تخفیف", "income", false, 41), CategoryE("debt_back", "بازگشت قرض", "income", false, 42),
        )
    }
}
