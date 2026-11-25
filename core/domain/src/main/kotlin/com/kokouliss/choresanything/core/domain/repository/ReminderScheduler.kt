package com.kokouliss.choresanything.core.domain.repository

import com.kokouliss.choresanything.core.domain.model.ChoreRef

/**
 * Rings this phone about a chore at its reminder time, and once a day about the chores due today.
 * Each phone plans the reminders of the chores that are for its own person, from what it sees.
 */
interface ReminderScheduler {
    /** Replaces any earlier reminder for the chore. */
    fun schedule(chore: ChoreRef, atMillis: Long)

    fun cancel(choreId: String)

    /** The hour of the day, 0 to 23, of the daily "today's chores" notification. */
    val dailyHour: Int

    /** Moves the daily notification to [hour], from its next time on. */
    fun setDailyHour(hour: Int)
}
