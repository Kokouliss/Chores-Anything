package com.kokouliss.choresanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kokouliss.choresanything.core.data.isBackendConfigured
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.kokouliss.choresanything.core.ui.R as CoreR

/**
 * Once a day, at the chosen hour, one notification that lists the chores due today for the signed-in
 * person: theirs and the ones for anyone, in every household. Quiet on days with nothing due.
 */
internal class TodayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** What the workers of this app take from Hilt, which cannot inject them directly. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun choreRepository(): ChoreRepository
        fun authRepository(): AuthRepository
    }

    @SuppressLint("MissingPermission") // Stops early when notifications are off.
    override suspend fun doWork(): Result {
        val context = applicationContext
        val notifications = NotificationManagerCompat.from(context)
        if (!isBackendConfigured(context) || !notifications.areNotificationsEnabled()) return Result.success()
        val dependencies = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        dependencies.authRepository().currentUser.first() ?: return Result.success()

        val due = try {
            dependencies.choreRepository().loadDueChores(LocalDate.now().toEpochDay())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (due.isEmpty()) return Result.success()

        val open = Intent(context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val titles = due.joinToString(separator = "\n") { it.title }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_checklist)
            .setContentTitle(context.resources.getQuantityString(R.plurals.due_reminder_title, due.size, due.size))
            .setContentText(titles)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        notifications.notify(WORK, 0, notification)
        return Result.success()
    }

    companion object {
        /** The channel of this and of the chores' own reminders. */
        const val CHANNEL = "due"
        private const val WORK = "today"
        private const val PREFERENCES = "reminders"
        private const val HOUR = "daily_hour"
        private const val DEFAULT_HOUR = 9

        fun hour(context: Context): Int = preferences(context).getInt(HOUR, DEFAULT_HOUR)

        fun setHour(context: Context, hour: Int) {
            preferences(context).edit().putInt(HOUR, hour).apply()
            schedule(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        /** Plans the next run at the chosen hour, then daily. Safe on every start: an existing plan is kept. */
        fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour(context), 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<TodayWorker>(Duration.ofDays(1))
                .setInitialDelay(Duration.between(now, next))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, policy, request)
        }

        private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }
}
