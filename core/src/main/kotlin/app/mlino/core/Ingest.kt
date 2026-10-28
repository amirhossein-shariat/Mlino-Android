package app.mlino.core

import java.security.MessageDigest

/** One captured bank message. For notifications [sender] is the app package and [title] its title. */
data class RawMsg(val kind: String, val sender: String, val title: String, val body: String, val receivedMs: Long) {
    val text: String get() = if (title.isBlank()) body else title + "\n" + body
}

sealed interface IngestResult {
    data class Ignored(val reason: String) : IngestResult
    data object Unparsed : IngestResult
    /** Parsed fine but no card has this smsKey yet; kept so a later "reprocess" can pick it up. */
    data class Unmapped(val parsed: Parsed) : IngestResult
    data object Duplicate : IngestResult
    /** Txs to save: the new one, plus (when an internal transfer was recognised) the updated counterpart and the fee. */
    data class Created(val txs: List<Tx>, val newTxId: String) : IngestResult
}

object Ingest {
    fun dedupeKey(cardId: String, out: Boolean, amount: Long, balance: Long, atMs: Long): String {
        val s = "$cardId|${if (out) "out" else "in"}|$amount|$balance|${atMs / 60_000}"
        return MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Pipeline after the raw message has been stored: ignore OTP/promo -> parse -> match card by smsKey -> dedupe -> transfer matching.
     * [near] must hold every tx within +-10 minutes of the message time (any card) — enough for dedupe and transfer matching.
     */
    fun process(msg: RawMsg, cards: List<Card>, near: List<Tx>, newId: () -> String): IngestResult {
        Parsers.ignorable(msg.text)?.let { return IngestResult.Ignored(it) }
        val p = Parsers.parse(msg.text, msg.receivedMs) ?: return IngestResult.Unparsed
        val card = cards.firstOrNull { it.smsKey.isNotBlank() && it.smsKey.trim().equals(p.key, ignoreCase = true) }
            ?: return IngestResult.Unmapped(p)
        val dedupe = dedupeKey(card.id, p.out, p.amount, p.balance, p.at)
        if (near.any { it.dedupe == dedupe }) return IngestResult.Duplicate
        val id = newId()
        val tx = Tx(
            id = id, cardId = card.id, at = p.at, type = if (p.out) TxType.OUT else TxType.IN, amount = p.amount,
            balanceAfter = p.balance, source = msg.kind, status = TxStatus.INBOX, dedupe = dedupe, createdAt = msg.receivedMs,
        )
        val match = Transfers.find(tx, near)
        if (match != null) return IngestResult.Created(Transfers.apply(match, newId()), id)
        return IngestResult.Created(listOf(tx), id)
    }
}
