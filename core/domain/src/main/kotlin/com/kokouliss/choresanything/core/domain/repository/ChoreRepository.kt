package com.kokouliss.choresanything.core.domain.repository

import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.Member
import kotlinx.coroutines.flow.Flow

/**
 * The chores of a household. Writes are offline-first: they show on this phone at once and reach
 * the others when there is a connection. Every change is also written to the household's history.
 */
interface ChoreRepository {
    /** Every chore of the household, finished ones included. The screen sorts them. */
    fun observeChores(householdId: String): Flow<List<Chore>>

    /** Null once the chore is deleted. */
    fun observeChore(ref: ChoreRef): Flow<Chore?>

    /** Once, from the server when it can be reached, otherwise from what the phone has. Null when the chore is gone. */
    suspend fun loadChore(ref: ChoreRef): Chore?

    /** The open chores for the signed-in person (theirs or anyone's) due on [today] or earlier, across every household. */
    suspend fun loadDueChores(today: Long): List<Chore>

    /** Adds [chore] to its household. A kid's chore is saved as waiting for an adult. Returns the new id. */
    suspend fun addChore(chore: Chore): String

    /** Saves what changed on [chore]. */
    suspend fun updateChore(chore: Chore)

    suspend fun deleteChore(chore: Chore)

    /** Marks [chore] done by the signed-in person [me]; a repeating chore moves to its next day and the next person. */
    suspend fun markDone(chore: Chore, me: Member, members: List<Member>)

    /** Puts [before] back as it was before it was marked done. */
    suspend fun undoDone(before: Chore)

    /** An adult lets a kid's chore through. */
    suspend fun confirm(chore: Chore)

    /** An adult says no to a kid's chore, which goes away. */
    suspend fun decline(chore: Chore)

    /** Asks [to] to take the chore over. They see it as passed to them until they answer. */
    suspend fun pass(chore: Chore, to: Member)

    /** The person a chore was passed to takes it and becomes its assignee. Also claims an "anyone" chore. */
    suspend fun take(chore: Chore)

    /** The person a chore was passed to says no: it stays with whoever had it. */
    suspend fun refuse(chore: Chore)
}
