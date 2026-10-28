package app.malino.ui

import android.app.Application
import android.content.Context
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.malino.MalinoApp
import app.malino.core.*
import app.malino.data.CategoryE
import app.malino.data.RawE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CardUi(val card: Card, val balance: Long)
data class GapUi(val gap: Ledger.Gap, val card: Card?) { val cardName get() = card?.name.orEmpty() }
data class TxUi(val tx: Tx, val card: Card?, val categoryName: String?, val dimmed: Boolean, val after: Long?, val unsettled: Boolean = false)

data class UiState(
    val loaded: Boolean = false,
    val cards: List<CardUi> = emptyList(),
    val total: Long = 0,
    val txs: List<TxUi> = emptyList(),
    val inbox: List<TxUi> = emptyList(),
    val gaps: List<GapUi> = emptyList(),
    val snoozedGaps: List<GapUi> = emptyList(),
    val attention: List<RawE> = emptyList(),
    val categories: List<CategoryE> = emptyList(),
    val monthIn: Long = 0,
    val monthOut: Long = 0,
    val coreCards: List<Card> = emptyList(),
    val coreTxs: List<Tx> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val budgets: List<Budget> = emptyList(),
    val budgetStatus: List<Insights.BudgetStatus> = emptyList(),
    val monthSpend: Map<String, Long> = emptyMap(),
    val debts: List<Debt> = emptyList(),
    val dueSoon: List<Insights.Due> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalProgress: Map<String, Insights.GoalProgress> = emptyMap(),
    val assets: List<Asset> = emptyList(),
    val prices: Prices = Prices(),
    val assetsValue: Long = 0,
    val recurring: List<Insights.Recurring> = emptyList(),
    val forecast: Insights.Forecast? = null,
    val suspicious: List<TxUi> = emptyList(),
)

data class PlanData(val budgets: List<Budget>, val debts: List<Debt>, val goals: List<Goal>, val assets: List<Asset>, val prices: Prices)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MalinoApp).repo

    private val planFlow = combine(repo.budgets, repo.debts, repo.goals, repo.assets, repo.prices, ::PlanData)

    val state: StateFlow<UiState> = combine(combine(repo.cards, repo.txs, repo.categories, repo.attention) { a, b, c, d -> listOf(a, b, c, d) }, planFlow, repo.snoozedGaps) { four, plan, snoozed ->
        @Suppress("UNCHECKED_CAST")
        val cardsE = four[0] as List<app.malino.data.CardE>
        @Suppress("UNCHECKED_CAST")
        val txsE = four[1] as List<app.malino.data.TxE>
        @Suppress("UNCHECKED_CAST")
        val cats = four[2] as List<CategoryE>
        @Suppress("UNCHECKED_CAST")
        val attention = four[3] as List<RawE>
        val cards = cardsE.map { it.toCore() }
        val txs = txsE.map { it.toCore() }
        val res = Ledger.compute(cards, txs)
        val byCard = cards.associateBy { it.id }
        val catName = cats.associate { it.id to it.name }
        val month = Jalali.today().let { Jalali.monthRange(it.y, it.m) }
        var mIn = 0L; var mOut = 0L
        val ui = txs.map { t ->
            if (t.id !in res.before && t.at in month) when (t.type) {
                TxType.IN -> mIn += t.amount
                TxType.OUT, TxType.FEE -> mOut += t.amount
                else -> {}
            }
            TxUi(t, byCard[t.cardId], t.categoryId?.let { catName[it] }, t.id in res.before, res.after[t.id], t.id in res.unsettled)
        }.sortedWith(compareByDescending<TxUi> { it.tx.at }.thenByDescending { it.tx.createdAt })
        val nowMs = System.currentTimeMillis()
        val today = Jalali.today(nowMs)
        val spend = Insights.spendByCategory(txs, cards, today.y, today.m)
        val recurring = Insights.detectRecurring(txs, nowMs)
        val sus = Insights.suspicious(txs, cards, nowMs).map { it.txId }.toSet()
        UiState(
            loaded = true,
            cards = cards.map { CardUi(it, res.current[it.id] ?: it.openingBalance) },
            total = res.current.values.sum(),
            txs = ui,
            inbox = ui.filter { it.tx.status == TxStatus.INBOX },
            gaps = res.gaps.filter { it.txId !in snoozed }.map { GapUi(it, byCard[it.cardId]) },
            snoozedGaps = res.gaps.filter { it.txId in snoozed }.map { GapUi(it, byCard[it.cardId]) },
            attention = attention,
            categories = cats,
            monthIn = mIn, monthOut = mOut, coreCards = cards, coreTxs = txs, balances = res.current,
            budgets = plan.budgets, budgetStatus = Insights.budgetStatus(plan.budgets, spend), monthSpend = spend,
            debts = plan.debts, dueSoon = Insights.dueInstallments(plan.debts, nowMs, 7),
            goals = plan.goals, goalProgress = plan.goals.associate { it.id to Insights.goalProgress(it, cards, res.current, plan.assets, plan.prices, txs, nowMs) },
            assets = plan.assets, prices = plan.prices, assetsValue = plan.assets.sumOf { Insights.assetValue(it, plan.prices) ?: 0L },
            recurring = recurring, forecast = if (recurring.isEmpty()) null else Insights.forecastNextMonth(recurring, nowMs),
            suspicious = ui.filter { it.tx.id in sus },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    val recentRaw: StateFlow<List<RawE>> = repo.recentRaw.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val stats get() = repo.stats
    val security get() = repo.security
    fun bankApps(): Set<String> = repo.bankPackages
    fun ignoreWords(): Set<String> = repo.ignoreWords
    fun setIgnoreWord(w: String, on: Boolean) { val t = w.trim(); if (t.isNotEmpty()) repo.ignoreWords = if (on) repo.ignoreWords + t else repo.ignoreWords - t }
    fun setBankApp(pkg: String, on: Boolean) { repo.bankPackages = if (on) repo.bankPackages + pkg else repo.bankPackages - pkg }
    val lockEnabled = MutableStateFlow(repo.security.enabled)
    fun refreshLock() { lockEnabled.value = repo.security.enabled }

    /** Result text of the last backup / restore / export, shown on the data page. */
    val dataMsg = MutableStateFlow<String?>(null)
    val restoreStaged = MutableStateFlow(false)

    fun backupTo(ctx: Context, uri: android.net.Uri) = io {
        dataMsg.value = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { app.malino.data.Backup.write(ctx, repo, it) }
            "پشتیبان ذخیره شد."
        }.getOrElse { "ذخیره‌ی پشتیبان ناموفق بود." }
    }

    fun restoreFrom(ctx: Context, input: () -> java.io.InputStream?) = io {
        val r = runCatching { input()!!.use { app.malino.data.Backup.stageRestore(ctx, it) } }.getOrElse { app.malino.data.Backup.Staged.Fail("خواندن فایل ممکن نشد") }
        when (r) {
            is app.malino.data.Backup.Staged.Ok -> { restoreStaged.value = true; dataMsg.value = "فایل معتبر است. برای اعمال، برنامه را ببندید و دوباره باز کنید." }
            is app.malino.data.Backup.Staged.Fail -> dataMsg.value = r.why
        }
    }

    /** Safety backup first, then the clean-up. [to] is exclusive. */
    fun reset(ctx: Context, kind: app.malino.data.Repo.Reset, from: Long = 0, to: Long = Long.MAX_VALUE) = io {
        val saved = app.malino.data.Backup.safety(ctx, repo)
        runCatching { repo.reset(kind, from, to) }
            .onSuccess { dataMsg.value = "انجام شد." + if (saved) " یک نسخه‌ی ایمنی قبل از آن در «پشتیبان‌های خودکار» ذخیره شد." else "" }
            .onFailure { dataMsg.value = "انجام نشد." }
    }

    fun exportXlsx(ctx: Context, uri: android.net.Uri, from: Long, to: Long) = io {
        dataMsg.value = runCatching {
            val st = state.value
            val after = st.txs.mapNotNull { u -> u.after?.let { u.tx.id to it } }.toMap()
            val bytes = Export.transactions(st.coreCards, st.coreTxs, after, st.categories.associate { it.id to it.name }, from, to)
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            "فایل اکسل ذخیره شد."
        }.getOrElse { "ساخت فایل اکسل ناموفق بود." }
    }
    val hideBalance = MutableStateFlow(repo.hideBalance)
    fun toggleHide() { hideBalance.value = !hideBalance.value; repo.hideBalance = hideBalance.value }
    var onboarded: Boolean
        get() = repo.onboarded
        set(v) { repo.onboarded = v }

    /** Dry run of the capture pipeline on pasted text: nothing is saved. */
    data class ParseCheck(val ignored: String?, val parsed: Parsed?, val cardName: String?)

    fun checkText(text: String): ParseCheck {
        val ignored = Parsers.ignorable(text, repo.ignoreWords)
        val parsed = if (ignored == null) Parsers.parse(text, System.currentTimeMillis()) else null
        val card = parsed?.let { p -> state.value.coreCards.firstOrNull { it.smsKey.isNotBlank() && it.smsKey.trim().equals(p.key, true) } }
        return ParseCheck(ignored, parsed, card?.name)
    }

    /** -1 = idle, otherwise the number of messages scanned so far. */
    val importing = MutableStateFlow(-1)
    val importedCount = MutableStateFlow(0)

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun confirm(txId: String, cat: String?) = io { repo.confirm(txId, cat) }
    fun confirmAll(picks: Map<String, String?>) = io { state.value.inbox.forEach { repo.confirm(it.tx.id, picks[it.tx.id] ?: it.tx.categoryId) } }
    fun saveTx(t: Tx) = io { repo.saveTx(t) }
    fun deleteTx(id: String) = io { repo.deleteTx(id) }
    fun saveCard(c: Card) = io { repo.saveCard(c) }
    fun resolveGap(g: Ledger.Gap, type: TxType, cat: String?, note: String, at: Long) = io { repo.resolveGap(g, type, cat, note, at) }
    fun snoozeGap(txId: String, on: Boolean) = repo.snoozeGap(txId, on)
    fun unlink(txId: String) = io { repo.unlinkTransfer(txId) }
    fun dismissRaw(id: String) = io { repo.dismissRaw(id) }
    fun reprocess() = io { repo.reprocess() }
    fun deleteCard(c: Card, onRefused: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val ce = repo.db.cards().all().firstOrNull { it.id == c.id } ?: return@launch
        if (!repo.deleteCard(ce)) launch(Dispatchers.Main) { onRefused() }
    }

    /** Reads the SMS inbox once and ingests every message from a bank sender (oldest first so transfers pair up). */
    fun importSms(ctx: Context, fromMs: Long = 0L) {
        if (importing.value >= 0) return
        io {
            importing.value = 0; importedCount.value = 0
            try {
                ctx.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), "date >= ?", arrayOf(fromMs.toString()), "date ASC")?.use { c ->
                    var n = 0
                    while (c.moveToNext()) {
                        val addr = c.getString(0).orEmpty(); val body = c.getString(1).orEmpty(); val date = c.getLong(2)
                        if (repo.looksLikeBank(addr, body) && repo.ingest(RawMsg("sms", addr, "", body, date), history = true) != null) importedCount.value++
                        if (++n % 25 == 0) importing.value = n
                    }
                }
            } finally { importing.value = -1 }
        }
    }

    fun saveBudgets(l: List<Budget>) = io { repo.saveBudgets(l) }
    fun saveDebts(l: List<Debt>) = io { repo.saveDebts(l) }
    fun saveGoals(l: List<Goal>) = io { repo.saveGoals(l) }
    fun saveAssets(l: List<Asset>) = io { repo.saveAssets(l) }
    fun savePrices(p: Prices) = io { repo.savePrices(p) }

    val templates = repo.templates
    fun deleteTemplate(id: String) = io { repo.saveTemplates(repo.templates.value.filter { it.id != id }) }

    /** Learns a template from a message the user marked, checks that it reads the same message back, saves it and re-runs unparsed messages. */
    fun teach(raw: RawE, text: String, picks: Map<Role, Int>, out: Boolean, dirWord: String, cardId: String, done: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            val t = Templates.learn(newId(), raw.sender, cardId, out, dirWord, text, picks, System.currentTimeMillis())
            val check = Templates.apply(t, text, raw.receivedAt)
            if (check == null || check.amount <= 0) {
                launch(Dispatchers.Main) { done(false, "الگو ساخته نشد؛ مبلغ و مانده را دوباره انتخاب کنید.") }
                return@launch
            }
            repo.saveTemplates(repo.templates.value + t)
            val n = repo.reprocess()
            launch(Dispatchers.Main) { done(true, "یاد گرفتم. ${fa(n)} پیام ثبت شد.") }
        }
    }

    fun saveCardAndReprocess(c: Card) = io { repo.saveCard(c); repo.reprocess() }

    fun shareSample(ctx: Context, raw: RawE) {
        val text = Masking.mask(if (raw.title.isBlank()) raw.body else raw.title + "\n" + raw.body)
        shareText(ctx, "نمونه‌ی پیام بانک (فرستنده: ${raw.sender})\n\n$text", "ارسال نمونه")
    }

    fun shareBackup(ctx: Context) = io {
        runCatching {
            val d = Jalali.today()
            val f = java.io.File(exportsDir(ctx), "malino-backup-%04d%02d%02d.zip".format(d.y, d.m, d.d))
            f.outputStream().use { app.malino.data.Backup.write(ctx, repo, it) }
            withContext(Dispatchers.Main) { shareFile(ctx, f, "application/zip", "ارسال پشتیبان") }
        }.onFailure { dataMsg.value = "ساخت پشتیبان ناموفق بود." }
    }

    fun shareXlsx(ctx: Context, from: Long, to: Long) = io {
        runCatching {
            val st = state.value
            val after = st.txs.mapNotNull { u -> u.after?.let { u.tx.id to it } }.toMap()
            val bytes = Export.transactions(st.coreCards, st.coreTxs, after, st.categories.associate { it.id to it.name }, from, to)
            val d = Jalali.today()
            val f = java.io.File(exportsDir(ctx), "malino-transactions-%04d%02d%02d.xlsx".format(d.y, d.m, d.d))
            f.writeBytes(bytes)
            withContext(Dispatchers.Main) { shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ارسال فایل اکسل") }
        }.onFailure { dataMsg.value = "ساخت فایل اکسل ناموفق بود." }
    }

    fun newId() = UUID.randomUUID().toString()
}
