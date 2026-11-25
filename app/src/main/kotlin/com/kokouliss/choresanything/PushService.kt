package com.kokouliss.choresanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kokouliss.choresanything.core.ui.R as CoreR

/**
 * Shows a notification from firebase/functions that arrives while the app is open. When the app is
 * closed the system shows it by itself and this is never called; without this, an open app drops it.
 */
// shortcut: no onNewToken, so a renewed address reaches the server at the next app start (the app
// registers on every start). Override it if notifications go missing until the app is opened.
class PushService : FirebaseMessagingService() {

    @SuppressLint("MissingPermission") // Returns early when notifications are off.
    override fun onMessageReceived(message: RemoteMessage) {
        val push = message.notification ?: return
        val notifications = NotificationManagerCompat.from(this)
        if (!notifications.areNotificationsEnabled()) return

        // The same words the system would show: the string the server names, in this phone's language.
        val text = pushText(push.bodyLocalizationKey)
            ?.let { runCatching { getString(it, *push.bodyLocalizationArgs.orEmpty()) }.getOrNull() }
            ?: push.body
        // Opens the chore, like a tap on a notification the system showed.
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_HOUSEHOLD_ID, message.data[EXTRA_HOUSEHOLD_ID])
            .putExtra(EXTRA_CHORE_ID, message.data[EXTRA_CHORE_ID])
        val tap = PendingIntent.getActivity(
            this,
            push.tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, push.channelId ?: UPDATES_CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_checklist)
            .setContentTitle(push.title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // The server's tag, so a newer notification about the same chore replaces the older one.
        notifications.notify(push.tag, 0, notification)
    }
}

/** The app string that firebase/functions/notify.js names in a notification, or null for one this app does not know. */
internal fun pushText(key: String?): Int? = when (key) {
    "push_needs_ok" -> R.string.push_needs_ok
    "push_assigned_by" -> R.string.push_assigned_by
    "push_assigned" -> R.string.push_assigned
    "push_added" -> R.string.push_added
    "push_confirmed" -> R.string.push_confirmed
    "push_declined" -> R.string.push_declined
    "push_passed" -> R.string.push_passed
    "push_took" -> R.string.push_took
    "push_refused" -> R.string.push_refused
    "push_done" -> R.string.push_done
    else -> null
}
