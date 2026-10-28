package app.mlino.core

enum class TxType { OUT, IN, TRANSFER, FEE, ADJUST }
enum class TxStatus { INBOX, CONFIRMED }

/** All amounts are integer rial. Times are epoch millis; Jalali is display/input only. */
data class Card(
    val id: String,
    val name: String,
    val bank: String = "",
    val last4: String = "",
    /** Account number printed in the bank message (or a fixed word like "blu") used to route messages to this card. */
    val smsKey: String = "",
    val purpose: String = "",
    val color: Long = 0xFF14304F,
    val openingBalance: Long = 0,
    val openingAt: Long = 0,
)

data class Tx(
    val id: String,
    val cardId: String,
    val at: Long,
    val type: TxType,
    /** > 0, except ADJUST which is signed. */
    val amount: Long,
    /** TRANSFER only: true = money came into this card. */
    val dirIn: Boolean = false,
    /** Balance reported by the bank after this transaction; null for manual entries. */
    val balanceAfter: Long? = null,
    val categoryId: String? = null,
    val business: Boolean = false,
    val note: String = "",
    val source: String = "manual",
    val rawId: String? = null,
    val transferPairId: String? = null,
    val status: TxStatus = TxStatus.CONFIRMED,
    val dedupe: String? = null,
    val createdAt: Long = at,
)
