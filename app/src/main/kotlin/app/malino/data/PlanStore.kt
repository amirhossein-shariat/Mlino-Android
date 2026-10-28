package app.malino.data

import android.content.Context
import app.malino.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small collections (budgets, debts, goals, assets, prices) live as whole-list JSON files, like the desktop app did.
 * No database migration is needed for them, and a backup is just a copy of these files. Writes are atomic (tmp + rename).
 */
class PlanStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "collections").apply { mkdirs() }

    private fun read(name: String): String? = File(dir, "$name.json").takeIf { it.exists() }?.readText()
    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, "$name.json"))
    }
    private fun array(name: String) = runCatching { JSONArray(read(name) ?: "[]") }.getOrDefault(JSONArray())
    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

    fun budgets(): List<Budget> = array("budgets").mapObjects { Budget(it.getString("categoryId"), it.getLong("limit")) }
    fun saveBudgets(l: List<Budget>) = write("budgets", JSONArray(l.map { JSONObject().put("categoryId", it.categoryId).put("limit", it.monthlyLimit) }).toString())

    fun debts(): List<Debt> = array("debts").mapObjects { o ->
        Debt(o.getString("id"), o.getString("person"), o.getBoolean("owedToMe"), o.getLong("total"),
            o.getJSONArray("installments").mapObjects { i ->
                Installment(i.getLong("due"), i.getLong("amount"), i.optString("paidTxId").ifEmpty { null }, if (i.has("paidAt")) i.getLong("paidAt") else null)
            }, o.optString("note"))
    }
    fun saveDebts(l: List<Debt>) = write("debts", JSONArray(l.map { d ->
        JSONObject().put("id", d.id).put("person", d.person).put("owedToMe", d.owedToMe).put("total", d.total).put("note", d.note).put("installments",
            JSONArray(d.installments.map { i ->
                JSONObject().put("due", i.due).put("amount", i.amount).apply { i.paidTxId?.let { put("paidTxId", it) }; i.paidAt?.let { put("paidAt", it) } }
            }))
    }).toString())

    fun goals(): List<Goal> = array("goals").mapObjects { o ->
        Goal(o.getString("id"), o.getString("name"), o.getLong("target"),
            o.getJSONArray("cardIds").let { a -> (0 until a.length()).map { a.getString(it) } },
            o.getJSONArray("assetIds").let { a -> (0 until a.length()).map { a.getString(it) } })
    }
    fun saveGoals(l: List<Goal>) = write("goals", JSONArray(l.map {
        JSONObject().put("id", it.id).put("name", it.name).put("target", it.target).put("cardIds", JSONArray(it.cardIds)).put("assetIds", JSONArray(it.assetIds))
    }).toString())

    fun assets(): List<Asset> = array("assets").mapObjects {
        Asset(it.getString("id"), AssetType.valueOf(it.getString("type")), it.optString("name"), it.optDouble("qty", 0.0), it.optInt("karat", 18), it.optLong("value", 0))
    }
    fun saveAssets(l: List<Asset>) = write("assets", JSONArray(l.map {
        JSONObject().put("id", it.id).put("type", it.type.name).put("name", it.name).put("qty", it.qty).put("karat", it.karat).put("value", it.value)
    }).toString())

    fun prices(): Prices = runCatching {
        JSONObject(read("prices") ?: "{}").let { Prices(it.optLong("gold18"), it.optLong("usd"), it.optLong("coin"), it.optLong("updatedAt")) }
    }.getOrDefault(Prices())
    fun savePrices(p: Prices) = write("prices", JSONObject().put("gold18", p.gold18).put("usd", p.usd).put("coin", p.coin).put("updatedAt", p.updatedAt).toString())
}
