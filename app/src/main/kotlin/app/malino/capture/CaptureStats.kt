package app.malino.capture

import android.content.Context

/** Cheap counters that let the user verify auto-capture is alive. Only counts and times are kept, never the content of non-bank messages. */
data class CaptureSnapshot(
    val smsSeen: Int, val smsLast: Long, val notifSeen: Int, val notifLast: Long,
    val bankLast: Long, val bankLastKind: String, val listenerConnected: Boolean, val listenerAt: Long,
)

data class NotifSource(val pkg: String, val label: String, val count: Int, val last: Long)

class CaptureStats(ctx: Context) {
    private val p = ctx.getSharedPreferences("capture_stats", Context.MODE_PRIVATE)
    private fun inc(k: String) = p.edit().putInt(k, p.getInt(k, 0) + 1)

    fun smsSeen() = inc("sms_seen").putLong("sms_last", System.currentTimeMillis()).apply()
    fun notifSeen() = inc("notif_seen").putLong("notif_last", System.currentTimeMillis()).apply()
    fun bankHit(kind: String) = p.edit().putLong("bank_last", System.currentTimeMillis()).putString("bank_kind", kind).apply()
    fun listener(connected: Boolean) =
        p.edit().putBoolean("listener_on", connected).putLong("listener_at", System.currentTimeMillis()).apply()

    /** Remembers which apps post notifications (package, label, count, time; never the content) so the user can mark bank apps. */
    @Synchronized
    fun source(pkg: String, label: String) {
        val all = runCatching { org.json.JSONObject(p.getString("sources", "{}")) }.getOrDefault(org.json.JSONObject())
        val e = all.optJSONObject(pkg) ?: org.json.JSONObject()
        all.put(pkg, e.put("n", e.optInt("n") + 1).put("last", System.currentTimeMillis()).put("label", label))
        if (all.length() > 60) {
            val oldest = all.keys().asSequence().minByOrNull { all.getJSONObject(it).optLong("last") }
            if (oldest != null) all.remove(oldest)
        }
        p.edit().putString("sources", all.toString()).apply()
    }

    fun sources(): List<NotifSource> {
        val all = runCatching { org.json.JSONObject(p.getString("sources", "{}")) }.getOrDefault(org.json.JSONObject())
        return all.keys().asSequence().map { k -> all.getJSONObject(k).let { NotifSource(k, it.optString("label", k), it.optInt("n"), it.optLong("last")) } }
            .sortedByDescending { it.last }.toList()
    }

    fun snapshot() = CaptureSnapshot(
        p.getInt("sms_seen", 0), p.getLong("sms_last", 0), p.getInt("notif_seen", 0), p.getLong("notif_last", 0),
        p.getLong("bank_last", 0), p.getString("bank_kind", "").orEmpty(), p.getBoolean("listener_on", false), p.getLong("listener_at", 0),
    )
}
