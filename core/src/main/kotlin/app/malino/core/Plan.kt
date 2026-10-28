package app.malino.core

data class Budget(val categoryId: String, val monthlyLimit: Long)

data class Installment(val due: Long, val amount: Long, val paidTxId: String? = null, val paidAt: Long? = null) {
    val paid get() = paidTxId != null || paidAt != null
}

/** [owedToMe] true = someone owes me (طلب); false = I owe (بدهی). */
data class Debt(val id: String, val person: String, val owedToMe: Boolean, val total: Long, val installments: List<Installment>, val note: String = "")

enum class AssetType { GOLD, USD, COIN, OTHER }

/** [qty]: grams (gold), dollars, coins. [value]: manual rial value for OTHER. */
data class Asset(val id: String, val type: AssetType, val name: String = "", val qty: Double = 0.0, val karat: Int = 18, val value: Long = 0)

/** Prices are typed by hand (no free official source): rial per gram of 18k gold, per USD, per coin. */
data class Prices(val gold18: Long = 0, val usd: Long = 0, val coin: Long = 0, val updatedAt: Long = 0)

data class Goal(val id: String, val name: String, val target: Long, val cardIds: List<String> = emptyList(), val assetIds: List<String> = emptyList())
