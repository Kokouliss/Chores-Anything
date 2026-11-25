package com.kokouliss.choresanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kokouliss.choresanything.core.data.isBackendConfigured
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.DEFAULT_DUE_MINUTE
import com.kokouliss.choresanything.core.domain.repository.ReminderScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.kokouliss.choresanything.core.ui.R as CoreR

/** Rings once about one chore at its reminder time, unless the chore is done, gone or someone else's by then. */
internal class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // Stops early when notifications are off.
    override suspend fun doWork(): Result {
        val context = applicationContext
        val householdId = inputData.getString(EXTRA_HOUSEHOLD_ID) ?: return Result.failure()
        val choreId = inputData.getString(EXTRA_CHORE_ID) ?: return Result.failure()
        val notifications = NotificationManagerCompat.from(context)
        if (!isBackendConfigured(context) || !notifications.areNotificationsEnabled()) return Result.success()

        val dependencies = EntryPointAccessors.fromApplication(context, TodayWorker.Dependencies::class.java)
        val chore = try {
            dependencies.choreRepository().loadChore(ChoreRef(householdId, choreId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        // Done, gone, still waiting for an adult, being passed on, or set for another moment since: nothing to say.
        if (chore == null || chore.isPending || chore.isDone || chore.handoff != null ||
            chore.remindAtMillis() != inputData.getLong(REMIND_AT, 0)
        ) {
            return Result.success()
        }
        // Someone else's since: their phone reminds them.
        val me = dependencies.authRepository().currentUser.first()?.id ?: return Result.success()
        if (!chore.isFor(me)) return Result.success()

        val open = Intent(context, MainActivity::class.java).putExtra(EXTRA_HOUSEHOLD_ID, householdId).putExtra(EXTRA_CHORE_ID, choreId)
        val tap = PendingIntent.getActivity(context, choreId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val startsAt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.ofSecondOfDay((chore.dueMinute ?: DEFAULT_DUE_MINUTE) * 60L))
        val text = if (chore.remindMinutesBefore == 0) context.getString(R.string.reminder_now) else context.getString(R.string.reminder_at, startsAt)
        val notification = NotificationCompat.Builder(context, TodayWorker.CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_alarm)
            .setContentTitle(chore.title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        notifications.notify(choreId, 0, notification)
        return Result.success()
    }

    companion object {
        const val REMIND_AT = "remindAt"
    }
}

// shortcut: WorkManager can be minutes late when the phone sleeps deeply. Move to AlarmManager's
// exact alarms, which need their own permission, if someone needs the minute.
internal class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    override fun schedule(chore: ChoreRef, atMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.ofMillis((atMillis - System.currentTimeMillis()).coerceAtLeast(0)))
            .setInputData(
                workDataOf(
                    EXTRA_HOUSEHOLD_ID to chore.householdId,
                    EXTRA_CHORE_ID to chore.choreId,
                    ReminderWorker.REMIND_AT to atMillis,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(chore.choreId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(choreId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(choreId))
    }

    override val dailyHour: Int get() = TodayWorker.hour(context)

    override fun setDailyHour(hour: Int) = TodayWorker.setHour(context, hour)

    private fun workName(choreId: String) = "remind-$choreId"
}
