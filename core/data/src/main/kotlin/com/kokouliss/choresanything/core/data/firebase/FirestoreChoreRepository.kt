package com.kokouliss.choresanything.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.choresanything.core.domain.model.ActivityKind
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.ChoreStatus
import com.kokouliss.choresanything.core.domain.model.Handoff
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.dayOf
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

internal class FirestoreChoreRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val households: FirestoreHouseholdRepository,
    private val activity: FirestoreActivityRepository,
) : ChoreRepository {

    override fun observeChores(householdId: String): Flow<List<Chore>> =
        db.chores(householdId).snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toChore(householdId) } }

    override fun observeChore(ref: ChoreRef): Flow<Chore?> =
        db.chore(ref).snapshots().map { it.toChore(ref.householdId) }
            // Being removed from the household ends the listener with "permission denied".
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun loadChore(ref: ChoreRef): Chore? =
        try {
            // From the server when it can be reached, otherwise Firestore answers from the device.
            db.chore(ref).get().await().toChore(ref.householdId)
        } catch (e: FirebaseFirestoreException) {
            null
        }

    override suspend fun loadDueChores(today: Long): List<Chore> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return households.loadMyHouseholds().flatMap { household ->
            // One inequality needs no composite index; the rest is filtered here.
            db.chores(household.id).whereLessThanOrEqualTo(Fields.DUE_DAY, today).get().await().documents
                .mapNotNull { it.toChore(household.id) }
                .filter { !it.isPending && !it.isDone && it.isFor(uid) }
                .map { it.withNames(household.names) }
        }
    }

    override suspend fun addChore(chore: Chore): String {
        val user = auth.requireUser()
        val doc = db.chores(chore.householdId).document()
        doc.set(chore.newMap(user)).logFailure("add chore")
        activity.note(chore.copy(id = doc.id), if (chore.isPending) ActivityKind.ASKED else ActivityKind.ADDED)
        return doc.id
    }

    override suspend fun updateChore(chore: Chore) {
        write(chore, "change chore")
        activity.note(chore, ActivityKind.CHANGED)
    }

    override suspend fun deleteChore(chore: Chore) {
        db.chore(chore.ref).delete().logFailure("delete chore")
        activity.note(chore, ActivityKind.DELETED)
    }

    override suspend fun markDone(chore: Chore, me: Member, members: List<Member>) {
        val now = System.currentTimeMillis()
        write(chore.done(me, now, dayOf(now), members), "mark chore done")
        activity.note(chore, ActivityKind.DONE, minutes = chore.minutes)
    }

    override suspend fun undoDone(before: Chore) {
        write(before, "undo chore")
        activity.note(before, ActivityKind.UNDONE)
    }

    override suspend fun confirm(chore: Chore) {
        write(chore.copy(status = ChoreStatus.OPEN), "confirm chore")
        activity.note(chore, ActivityKind.CONFIRMED, other = Member(chore.createdById, chore.createdByName))
    }

    override suspend fun decline(chore: Chore) {
        db.chore(chore.ref).delete().logFailure("decline chore")
        activity.note(chore, ActivityKind.DECLINED, other = Member(chore.createdById, chore.createdByName))
    }

    override suspend fun pass(chore: Chore, to: Member) {
        val user = auth.requireUser()
        write(chore.copy(handoff = Handoff(to.id, to.name, user.uid, user.memberName)), "pass chore")
        activity.note(chore, ActivityKind.PASSED, other = to)
    }

    override suspend fun take(chore: Chore) {
        val user = auth.requireUser()
        write(chore.copy(assigneeId = user.uid, assigneeName = user.memberName, handoff = null), "take chore")
        activity.note(chore, ActivityKind.TOOK, other = chore.handoff?.let { Member(it.byId, it.byName) })
    }

    override suspend fun refuse(chore: Chore) {
        write(chore.copy(handoff = null), "refuse chore")
        activity.note(chore, ActivityKind.REFUSED, other = chore.handoff?.let { Member(it.byId, it.byName) })
    }

    private fun write(chore: Chore, action: String) {
        db.chore(chore.ref).update(chore.changesMap()).logFailure(action)
    }

    private val Chore.ref: ChoreRef get() = ChoreRef(householdId, id)
}
