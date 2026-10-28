package app.mlino.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.mlino.MlinoApp
import app.mlino.core.RawMsg
import kotlinx.coroutines.launch

/** Receives incoming SMS. Only messages from bank senders (or ones a bank parser understands) are kept; personal SMS are dropped untouched. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext as MlinoApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, ps) ->
                    val body = ps.joinToString("") { it.messageBody.orEmpty() }
                    if (!app.repo.looksLikeBank(sender, body)) return@forEach
                    val at = ps.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val id = app.repo.ingest(RawMsg("sms", sender, "", body, at))
                    if (id != null) Notifier.newTransaction(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
