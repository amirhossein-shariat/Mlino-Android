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
    private val app get() = applicationContext as MalinoApp

    override fun onListenerConnected() { app.repo.stats.listener(true) }
    override fun onListenerDisconnected() { app.repo.stats.listener(false) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        app.repo.stats.notifSeen()
        val e = sbn.notification.extras
        val title = (e.getCharSequence(Notification.EXTRA_TITLE) ?: e.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString().orEmpty()
        val main = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n") { it.toString() }.orEmpty()
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val body = listOf(main, lines, sub).filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (body.isBlank()) return
        if (Parsers.parse("$title\n$body", sbn.postTime) == null) return
        app.repo.stats.bankHit("notif")
        app.appScope.launch {
            val id = app.repo.ingest(RawMsg("notif", sbn.packageName, title, body, sbn.postTime))
            if (id != null) Notifier.newTransaction(applicationContext)
        }
    }
}
