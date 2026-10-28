package app.malino.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.malino.MalinoApp
import app.malino.core.Parsers
import app.malino.core.RawMsg
import kotlinx.coroutines.launch

/** Reads bank-app notifications (e.g. Blu). Only notifications a bank parser understands are kept; everything else is ignored. */
class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (body.isBlank()) return
        val text = "$title\n$body"
        if (Parsers.parse(text, sbn.postTime) == null) return
        val app = applicationContext as MalinoApp
        app.appScope.launch {
            val id = app.repo.ingest(RawMsg("notif", sbn.packageName, title, body, sbn.postTime))
            if (id != null) Notifier.newTransaction(applicationContext)
        }
    }
}
