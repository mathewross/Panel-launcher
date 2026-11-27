package com.panel.launcher.data

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One notification, reduced to what the home screen shows. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val count: Int,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
)

/**
 * Live notifications per app, fed by [com.panel.launcher.MediaListener] — only while the user
 * has granted Panel notification access (off by default).
 */
object NotificationStore {
    private val _byPackage = MutableStateFlow<Map<String, List<NotifItem>>>(emptyMap())
    val byPackage: StateFlow<Map<String, List<NotifItem>>> = _byPackage

    fun update(active: Array<StatusBarNotification>?, ownPackage: String) {
        _byPackage.value = active.orEmpty()
            .filter { sbn ->
                val n = sbn.notification
                sbn.packageName != ownPackage &&
                    n.flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                    n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    n.flags and Notification.FLAG_FOREGROUND_SERVICE == 0
            }
            .map { parse(it) }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.time }
            .groupBy { it.packageName }
    }

    fun clear() { _byPackage.value = emptyMap() }

    private fun parse(sbn: StatusBarNotification): NotifItem {
        val n = sbn.notification
        val ex = n.extras
        // For chats, prefer the latest message and its sender.
        val messages = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        var title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val last = (messages?.lastOrNull() as? android.os.Bundle)
        if (last != null) {
            last.getCharSequence("text")?.let { text = it.toString() }
            val sender = last.getCharSequence("sender")?.toString()
            if (title.isBlank() && sender != null) title = sender
        }
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        return NotifItem(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            time = sbn.postTime,
            count = (messages?.size ?: 0).coerceAtLeast(1).coerceAtMost(99),
            contentIntent = n.contentIntent,
            replyAction = reply,
        )
    }
}
