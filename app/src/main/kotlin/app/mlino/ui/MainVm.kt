package app.mlino.ui

import android.app.Application
import android.content.Context
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mlino.MlinoApp
import app.mlino.core.*
import app.mlino.data.CategoryE
import app.mlino.data.RawE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CardUi(val card: Card, val balance: Long)
data class GapUi(val gap: Ledger.Gap, val cardName: String)
data class TxUi(val tx: Tx, val card: Card?, val categoryName: String?, val dimmed: Boolean, val after: Long?)

data class UiState(
    val loaded: Boolean = false,
    val cards: List<CardUi> = emptyList(),
    val total: Long = 0,
    val txs: List<TxUi> = emptyList(),
    val inbox: List<TxUi> = emptyList(),
    val gaps: List<GapUi> = emptyList(),
    val attention: List<RawE> = emptyList(),
    val categories: List<CategoryE> = emptyList(),
    val monthIn: Long = 0,
    val monthOut: Long = 0,
)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MlinoApp).repo

    val state: StateFlow<UiState> = combine(repo.cards, repo.txs, repo.categories, repo.attention) { cardsE, txsE, cats, attention ->
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
            TxUi(t, byCard[t.cardId], t.categoryId?.let { catName[it] }, t.id in res.before, res.after[t.id])
        }.sortedWith(compareByDescending<TxUi> { it.tx.at }.thenByDescending { it.tx.createdAt })
        UiState(
            loaded = true,
            cards = cards.map { CardUi(it, res.current[it.id] ?: it.openingBalance) },
            total = res.current.values.sum(),
            txs = ui,
            inbox = ui.filter { it.tx.status == TxStatus.INBOX },
            gaps = res.gaps.map { GapUi(it, byCard[it.cardId]?.name.orEmpty()) },
            attention = attention,
            categories = cats,
            monthIn = mIn, monthOut = mOut,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    /** -1 = idle, otherwise the number of messages scanned so far. */
    val importing = MutableStateFlow(-1)
    val importedCount = MutableStateFlow(0)

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun confirm(txId: String, cat: String?) = io { repo.confirm(txId, cat) }
    fun confirmAll(picks: Map<String, String?>) = io { state.value.inbox.forEach { repo.confirm(it.tx.id, picks[it.tx.id] ?: it.tx.categoryId) } }
    fun saveTx(t: Tx) = io { repo.saveTx(t) }
    fun deleteTx(id: String) = io { repo.deleteTx(id) }
    fun saveCard(c: Card) = io { repo.saveCard(c) }
    fun resolveGap(g: Ledger.Gap, asFee: Boolean) = io { repo.resolveGap(g, asFee) }
    fun unlink(txId: String) = io { repo.unlinkTransfer(txId) }
    fun dismissRaw(id: String) = io { repo.dismissRaw(id) }
    fun reprocess() = io { repo.reprocess() }
    fun deleteCard(c: Card, onRefused: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val ce = repo.db.cards().all().firstOrNull { it.id == c.id } ?: return@launch
        if (!repo.deleteCard(ce)) launch(Dispatchers.Main) { onRefused() }
    }

    /** Reads the SMS inbox once and ingests every message from a bank sender (oldest first so transfers pair up). */
    fun importSms(ctx: Context) {
        if (importing.value >= 0) return
        io {
            importing.value = 0; importedCount.value = 0
            try {
                ctx.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), null, null, "date ASC")?.use { c ->
                    var n = 0
                    while (c.moveToNext()) {
                        val addr = c.getString(0).orEmpty(); val body = c.getString(1).orEmpty(); val date = c.getLong(2)
                        if (repo.looksLikeBank(addr, body) && repo.ingest(RawMsg("sms", addr, "", body, date)) != null) importedCount.value++
                        if (++n % 25 == 0) importing.value = n
                    }
                }
            } finally { importing.value = -1 }
        }
    }

    fun newId() = UUID.randomUUID().toString()
}
