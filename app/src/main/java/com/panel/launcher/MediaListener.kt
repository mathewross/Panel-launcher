package com.panel.launcher

import android.service.notification.NotificationListenerService

/**
 * Exists only so that, *if the user chooses to grant notification access*, Panel may read the
 * phone's media sessions (what's playing + shuffle/like buttons). It ignores notifications.
 * Off by default; without it Panel uses Spotify's broadcast instead.
 */
class MediaListener : NotificationListenerService()
