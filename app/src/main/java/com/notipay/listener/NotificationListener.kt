package com.notipay.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache

class NotificationListener : NotificationListenerService() {

    private val recentNotifications = LruCache<String, Long>(128)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Prefs.setListenerConnected(this, true)
        if (Prefs.isServiceEnabled(this)) {
            KeeperService.start(this)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Prefs.setListenerConnected(this, false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val packageName = statusBarNotification.packageName
        val selected = Prefs.selectedPackages(this)
        if (!selected.contains(packageName)) return

        val notification = statusBarNotification.notification ?: return
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (statusBarNotification.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val textParts = buildList {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.forEach(::add)
        }.distinct()

        val text = textParts.joinToString(" · ")
        if (text.isBlank()) return

        val dedupeKey = "${statusBarNotification.key}|$text"
        val now = System.currentTimeMillis()
        val previous = recentNotifications.get(dedupeKey)
        if (previous != null && now - previous < 15_000) return
        recentNotifications.put(dedupeKey, now)

        if (Prefs.callbackUrl(this).isBlank() || Prefs.apiKey(this).isBlank()) return
        CallbackSender.enqueueNotification(this, packageName, title, text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed. Notifications are forwarded only when posted.
    }
}
