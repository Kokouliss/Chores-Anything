package com.kokouliss.choresanything

import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.SOON_DAYS
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import com.kokouliss.choresanything.core.domain.repository.HouseholdRepository
import com.kokouliss.choresanything.core.domain.repository.ReminderScheduler
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

/**
 * Keeps this phone's reminders in step with the chores it sees: one for each open chore of the
 * signed-in person (theirs or anyone's) whose reminder time is still ahead and within the next week.
 * A stale reminder is harmless: [ReminderWorker] checks the chore again before it rings.
 */
@Singleton
internal class ChoreReminderSync @Inject constructor(
    private val auth: AuthRepository,
    private val households: HouseholdRepository,
    private val chores: ChoreRepository,
    private val scheduler: ReminderScheduler,
) {
    private var planned = emptyMap<String, Long>()

    /** Runs for the life of the app. Signing out empties the plan; signing in fills it again. */
    fun start() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            auth.currentUser
                .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else allChores().map { all -> all.filter { it.isFor(user.id) } } }
                // A household that closed its door mid-listen fails the whole stream; start again a little later.
                .retry { delay(10_000); true }
                .collect(::plan)
        }
    }

    private fun allChores(): Flow<List<Chore>> = households.observeHouseholds().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList()) else combine(list.map { chores.observeChores(it.id) }) { it.flatMap { c -> c } }
    }

    private fun plan(mine: List<Chore>) {
        val now = System.currentTimeMillis()
        val horizon = now + Duration.ofDays(SOON_DAYS.toLong()).toMillis()
        val wanted = mine.filter { !it.isPending && !it.isDone && it.handoff == null }
            .mapNotNull { chore -> chore.remindAtMillis()?.takeIf { it in (now + 1)..horizon }?.let { chore to it } }
        val byId = wanted.associate { (chore, at) -> chore.id to at }
        (planned.keys - byId.keys).forEach(scheduler::cancel)
        wanted.forEach { (chore, at) -> if (planned[chore.id] != at) scheduler.schedule(ChoreRef(chore.householdId, chore.id), at) }
        planned = byId
    }
}
