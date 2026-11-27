package com.panel.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.panel.launcher.data.NotificationStore
import java.lang.ref.WeakReference

/**
 * Only active if the user chooses to grant Panel notification access (off by default). Used for:
 *  - media sessions (full music controls), and
 *  - notification glow / previews / replies on the home screen.
 * Notifications stay on the phone; Panel only keeps the current ones in memory to show them.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = WeakReference(this)
        publish()
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationStore.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        NotificationStore.update(runCatching { activeNotifications }.getOrNull(), packageName)
    }

    companion object {
        private var instance: WeakReference<MediaListener>? = null

        /** Clears a notification (the × on a notification card). */
        fun dismiss(key: String) {
            instance?.get()?.let { runCatching { it.cancelNotification(key) } }
        }
    }
}
