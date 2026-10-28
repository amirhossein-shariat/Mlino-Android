package app.malino.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.malino.core.Card
import app.malino.core.Tx
import app.malino.core.TxStatus
import app.malino.core.TxType

@Entity(tableName = "cards")
data class CardE(
    @PrimaryKey val id: String,
    val name: String,
    val bank: String,
    val last4: String,
    val smsKey: String,
    val purpose: String,
    val color: Long,
    val openingBalance: Long,
    val openingAt: Long,
) {
    fun toCore() = Card(id, name, bank, last4, smsKey, purpose, color, openingBalance, openingAt)
}

fun Card.toE() = CardE(id, name, bank, last4, smsKey, purpose, color, openingBalance, openingAt)

@Entity(tableName = "tx", indices = [Index("cardId"), Index("at"), Index("status")])
data class TxE(
    @PrimaryKey val id: String,
    val cardId: String,
    val at: Long,
    val type: String,
    val amount: Long,
    val dirIn: Boolean,
    val balanceAfter: Long?,
    val categoryId: String?,
    val business: Boolean,
    val note: String,
    val source: String,
    val rawId: String?,
    val transferPairId: String?,
    val status: String,
    val dedupe: String?,
    val createdAt: Long,
) {
    fun toCore() = Tx(id, cardId, at, TxType.valueOf(type), amount, dirIn, balanceAfter, categoryId, business, note, source,
        rawId, transferPairId, TxStatus.valueOf(status), dedupe, createdAt)
}

fun Tx.toE() = TxE(id, cardId, at, type.name, amount, dirIn, balanceAfter, categoryId, business, note, source,
    rawId, transferPairId, status.name, dedupe, createdAt)

/** Every captured bank message is stored untouched first, so a parser bug never loses data. OTP texts are never stored. */
@Entity(tableName = "raw", indices = [Index("state")])
data class RawE(
    @PrimaryKey val id: String,
    val kind: String,
    val sender: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
    /** done | duplicate | unparsed | unmapped | dismissed */
    val state: String,
    val txId: String?,
)

@Entity(tableName = "categories")
data class CategoryE(
    @PrimaryKey val id: String,
    val name: String,
    /** expense | income */
    val kind: String,
    val business: Boolean,
    val sort: Int,
)

/** Learned rule: same card + type + amount -> category. */
@Entity(tableName = "rules")
data class RuleE(
    @PrimaryKey val key: String,
    val categoryId: String,
    val uses: Int,
)
