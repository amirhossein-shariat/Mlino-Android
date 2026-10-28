package app.malino.capture

import android.content.Context

/** Cheap counters that let the user verify auto-capture is alive. Only counts and times are kept, never the content of non-bank messages. */
data class CaptureSnapshot(
    val smsSeen: Int, val smsLast: Long, val notifSeen: Int, val notifLast: Long,
    val bankLast: Long, val bankLastKind: String, val listenerConnected: Boolean, val listenerAt: Long,
)

class CaptureStats(ctx: Context) {
    private val p = ctx.getSharedPreferences("capture_stats", Context.MODE_PRIVATE)
    private fun inc(k: String) = p.edit().putInt(k, p.getInt(k, 0) + 1)

    fun smsSeen() = inc("sms_seen").putLong("sms_last", System.currentTimeMillis()).apply()
    fun notifSeen() = inc("notif_seen").putLong("notif_last", System.currentTimeMillis()).apply()
    fun bankHit(kind: String) = p.edit().putLong("bank_last", System.currentTimeMillis()).putString("bank_kind", kind).apply()
    fun listener(connected: Boolean) =
        p.edit().putBoolean("listener_on", connected).putLong("listener_at", System.currentTimeMillis()).apply()

    fun snapshot() = CaptureSnapshot(
        p.getInt("sms_seen", 0), p.getLong("sms_last", 0), p.getInt("notif_seen", 0), p.getLong("notif_last", 0),
        p.getLong("bank_last", 0), p.getString("bank_kind", "").orEmpty(), p.getBoolean("listener_on", false), p.getLong("listener_at", 0),
    )
}
