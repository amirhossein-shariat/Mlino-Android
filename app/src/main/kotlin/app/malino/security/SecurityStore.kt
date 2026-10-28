package app.malino.security

import android.content.Context
import android.util.Base64
import app.malino.core.Pin

/** Screen-lock settings. The PIN is stored only as a salted PBKDF2 hash. */
class SecurityStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("security", Context.MODE_PRIVATE)

    val enabled get() = p.getString("hash", null) != null && p.getBoolean("enabled", false)
    val pinLen get() = p.getInt("pin_len", 4)
    var autoLockSec: Int
        get() = p.getInt("auto_lock", 60)
        set(v) { p.edit().putInt("auto_lock", v).apply() }
    var biometric: Boolean
        get() = p.getBoolean("biometric", false)
        set(v) { p.edit().putBoolean("biometric", v).apply() }
    var blockedUntil: Long
        get() = p.getLong("blocked_until", 0)
        private set(v) { p.edit().putLong("blocked_until", v).apply() }
    private var fails: Int
        get() = p.getInt("fails", 0)
        set(v) { p.edit().putInt("fails", v).apply() }

    fun setPin(pin: String) {
        val salt = Pin.newSalt()
        p.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).putString("hash", Base64.encodeToString(Pin.hash(pin, salt), Base64.NO_WRAP))
            .putInt("pin_len", pin.length).putBoolean("enabled", true).putInt("fails", 0).putLong("blocked_until", 0).apply()
    }

    fun check(pin: String): Boolean {
        val salt = p.getString("salt", null) ?: return false
        val hash = p.getString("hash", null) ?: return false
        return Pin.verify(pin, Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP))
    }

    fun resetFails() { fails = 0; blockedUntil = 0 }

    /** Registers a wrong attempt; returns the time until which input is blocked (0 = not blocked). */
    fun wrong(): Long {
        val f = Pin.afterWrong(fails, System.currentTimeMillis())
        fails = f.count; blockedUntil = f.blockedUntil
        return f.blockedUntil
    }

    fun disable() { p.edit().remove("hash").remove("salt").putBoolean("enabled", false).putBoolean("biometric", false).apply() }
}
