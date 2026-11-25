package com.kokouliss.choresanything

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.kokouliss.choresanything.core.data.isBackendConfigured
import com.kokouliss.choresanything.feature.auth.restoreNightMode
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** The channel of the notifications that firebase/functions sends. The manifest names it as well. */
internal const val UPDATES_CHANNEL = "updates"

@HiltAndroidApp
class ChoresAnythingApplication : Application() {

    // Lazy, because it reaches Firebase, which a build without google-services.json does not have.
    @Inject
    internal lateinit var reminderSync: Lazy<ChoreReminderSync>

    override fun onCreate() {
        super.onCreate()
        // Before any screen exists, so the first one already has the chosen light or dark look.
        restoreNightMode(this)
        // Creating a channel that exists only refreshes its name, so this is safe on every start.
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                channel(UPDATES_CHANNEL, R.string.channel_updates),
                channel(TodayWorker.CHANNEL, R.string.channel_due),
            ),
        )
        TodayWorker.schedule(this)
        if (isBackendConfigured(this)) reminderSync.get().start()
    }

    private fun channel(id: String, name: Int) =
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(getString(name)).build()
}
