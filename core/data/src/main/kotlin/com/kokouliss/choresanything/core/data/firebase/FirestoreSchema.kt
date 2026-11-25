package com.kokouliss.choresanything.core.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.ChoreStatus
import com.kokouliss.choresanything.core.domain.model.Handoff
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.Repeat
import com.kokouliss.choresanything.core.domain.model.Role
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.tasks.await

// Document layout. firebase/firestore.rules validates the same names; change both together.
//
// households/{householdId}                     name, ownerId, memberIds[], members{uid: {name, role}}, inviteCode, invitedEmails[], createdAt
// invites/{inviteCode}                         householdId
// households/{householdId}/chores/{choreId}    title, info, needs[], minutes?, assigneeId?, assigneeName?, rotation[], dueDay?, dueMinute?,
//                                              repeat?, remindBefore?, status, createdById, createdByName, createdAt,
//                                              doneAt?, doneById?, doneByName?, handoff?{toId, toName, byId, byName}
// households/{householdId}/activity/{entryId}  kind, byId, byName, choreId, choreTitle, forId?, forName?, minutes?, at, expireAt:
//                                              the household's history; Firestore deletes each entry at expireAt
// users/{uid}/tokens/{token}                   updatedAt. One per device that gets this person's notifications.
//
// role is "adult" or "kid". createdById, doneById and handoff.byId tell firebase/functions who not
// to notify about a change, and assigneeId or handoff.toId whom to notify.

internal object Collections {
    const val HOUSEHOLDS = "households"
    const val INVITES = "invites"
    const val CHORES = "chores"
    const val ACTIVITY = "activity"
    const val USERS = "users"
    const val TOKENS = "tokens"
}

internal object Fields {
    const val NAME = "name"
    const val ROLE = "role"
    const val OWNER_ID = "ownerId"
    const val MEMBER_IDS = "memberIds"
    const val MEMBERS = "members"
    const val INVITE_CODE = "inviteCode"
    const val INVITED_EMAILS = "invitedEmails"
    const val CREATED_AT = "createdAt"
    const val HOUSEHOLD_ID = "householdId"
    const val TITLE = "title"
    const val INFO = "info"
    const val NEEDS = "needs"
    const val MINUTES = "minutes"
    const val ASSIGNEE_ID = "assigneeId"
    const val ASSIGNEE_NAME = "assigneeName"
    const val ROTATION = "rotation"
    const val DUE_DAY = "dueDay"
    const val DUE_MINUTE = "dueMinute"
    const val REPEAT = "repeat"
    const val REMIND_BEFORE = "remindBefore"
    const val STATUS = "status"
    const val CREATED_BY_ID = "createdById"
    const val CREATED_BY_NAME = "createdByName"
    const val DONE_AT = "doneAt"
    const val DONE_BY_ID = "doneById"
    const val DONE_BY_NAME = "doneByName"
    const val HANDOFF = "handoff"
    const val TO_ID = "toId"
    const val TO_NAME = "toName"
    const val BY_ID = "byId"
    const val BY_NAME = "byName"
    const val KIND = "kind"
    const val CHORE_ID = "choreId"
    const val CHORE_TITLE = "choreTitle"
    const val FOR_ID = "forId"
    const val FOR_NAME = "forName"
    const val AT = "at"
    const val EXPIRE_AT = "expireAt"
    const val UPDATED_AT = "updatedAt"
}

internal fun DocumentSnapshot.toHousehold(): Household? {
    if (!exists()) return null
    val ownerId = getString(Fields.OWNER_ID) ?: return null
    val members = (get(Fields.MEMBERS) as? Map<*, *>).orEmpty()
        .mapNotNull { (id, entry) ->
            val name = (entry as? Map<*, *>)?.get(Fields.NAME) as? String
            val role = Role.entries.firstOrNull { it.key == (entry as? Map<*, *>)?.get(Fields.ROLE) } ?: Role.ADULT
            if (id is String && name != null) Member(id, name, role) else null
        }
        .sortedWith(compareByDescending<Member> { it.id == ownerId }.thenBy { it.name.lowercase() })
    return Household(
        id = id,
        name = getString(Fields.NAME).orEmpty(),
        ownerId = ownerId,
        inviteCode = getString(Fields.INVITE_CODE).orEmpty(),
        members = members,
        invitedEmails = (get(Fields.INVITED_EMAILS) as? List<*>).orEmpty().filterIsInstance<String>(),
    )
}

internal fun FirebaseFirestore.chores(householdId: String): CollectionReference =
    collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.CHORES)

internal fun FirebaseFirestore.chore(ref: ChoreRef): DocumentReference = chores(ref.householdId).document(ref.choreId)

internal fun DocumentSnapshot.toChore(householdId: String): Chore? {
    if (!exists()) return null
    val handoff = (get(Fields.HANDOFF) as? Map<*, *>)?.let { map ->
        val toId = map[Fields.TO_ID] as? String ?: return@let null
        val byId = map[Fields.BY_ID] as? String ?: return@let null
        Handoff(toId, map[Fields.TO_NAME] as? String ?: "?", byId, map[Fields.BY_NAME] as? String ?: "?")
    }
    return Chore(
        id = id,
        householdId = householdId,
        title = getString(Fields.TITLE).orEmpty(),
        info = getString(Fields.INFO).orEmpty(),
        needs = (get(Fields.NEEDS) as? List<*>).orEmpty().filterIsInstance<String>(),
        minutes = getLong(Fields.MINUTES)?.toInt(),
        assigneeId = getString(Fields.ASSIGNEE_ID),
        assigneeName = getString(Fields.ASSIGNEE_NAME),
        rotation = (get(Fields.ROTATION) as? List<*>).orEmpty().filterIsInstance<String>(),
        dueDay = getLong(Fields.DUE_DAY),
        dueMinute = getLong(Fields.DUE_MINUTE)?.toInt(),
        repeat = Repeat.entries.firstOrNull { it.key == getString(Fields.REPEAT) },
        remindMinutesBefore = getLong(Fields.REMIND_BEFORE)?.toInt(),
        status = ChoreStatus.entries.firstOrNull { it.key == getString(Fields.STATUS) } ?: ChoreStatus.OPEN,
        createdById = getString(Fields.CREATED_BY_ID).orEmpty(),
        createdByName = getString(Fields.CREATED_BY_NAME).orEmpty(),
        // A just-added chore has no server time yet; the estimate keeps its place.
        createdAtMillis = getTimestamp(Fields.CREATED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        doneAtMillis = getTimestamp(Fields.DONE_AT)?.toDate()?.time,
        doneById = getString(Fields.DONE_BY_ID),
        doneByName = getString(Fields.DONE_BY_NAME),
        handoff = handoff,
    )
}

/**
 * Every field of the chore that can change after it is made, with null as a deletion, for
 * `update`. Who made it and when never change, so they are not here.
 */
internal fun Chore.changesMap(): Map<String, Any> = buildMap {
    put(Fields.TITLE, title)
    put(Fields.INFO, info)
    put(Fields.NEEDS, needs)
    put(Fields.MINUTES, minutes ?: FieldValue.delete())
    put(Fields.ASSIGNEE_ID, assigneeId ?: FieldValue.delete())
    put(Fields.ASSIGNEE_NAME, assigneeName ?: FieldValue.delete())
    put(Fields.ROTATION, rotation)
    put(Fields.DUE_DAY, dueDay ?: FieldValue.delete())
    put(Fields.DUE_MINUTE, dueMinute ?: FieldValue.delete())
    put(Fields.REPEAT, repeat?.key ?: FieldValue.delete())
    put(Fields.REMIND_BEFORE, remindMinutesBefore ?: FieldValue.delete())
    put(Fields.STATUS, status.key)
    put(Fields.DONE_AT, doneAtMillis?.let { Timestamp(Date(it)) } ?: FieldValue.delete())
    put(Fields.DONE_BY_ID, doneById ?: FieldValue.delete())
    put(Fields.DONE_BY_NAME, doneByName ?: FieldValue.delete())
    put(
        Fields.HANDOFF,
        handoff?.let { mapOf(Fields.TO_ID to it.toId, Fields.TO_NAME to it.toName, Fields.BY_ID to it.byId, Fields.BY_NAME to it.byName) }
            ?: FieldValue.delete(),
    )
}

/** The document of a new chore made by [user] now. */
internal fun Chore.newMap(user: FirebaseUser): Map<String, Any> = buildMap {
    putAll(changesMap().filterValues { it !is FieldValue })
    put(Fields.CREATED_BY_ID, user.uid)
    put(Fields.CREATED_BY_NAME, user.memberName)
    put(Fields.CREATED_AT, FieldValue.serverTimestamp())
}

/**
 * The signed-in user, again whenever the name or the confirmed state of the email changes. Those
 * change without a new sign-in, so this follows the token (renewed on purpose after each change)
 * rather than the sign-in state, and skips the hourly renewals that change nothing.
 */
internal fun FirebaseAuth.userFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.IdTokenListener { trySend(it.currentUser) }
    addIdTokenListener(listener)
    awaitClose { removeIdTokenListener(listener) }
}.distinctUntilChangedBy { user -> user?.let { Triple(it.uid, it.displayName, it.isEmailVerified) } }

internal fun FirebaseAuth.requireUser(): FirebaseUser = checkNotNull(currentUser) { "No user is signed in" }

/** The name other members see next to this person's chores. */
internal val FirebaseUser.memberName: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "?"

/** The address email invites are matched against. The security rules require it to be verified. */
internal val FirebaseUser.verifiedEmail: String?
    get() = email?.lowercase()?.takeIf { isEmailVerified }

/** The document as this device has it, or null when it has none. Never goes to the network, so it costs nothing. */
internal suspend fun DocumentReference.cached(): DocumentSnapshot? =
    try {
        get(Source.CACHE).await().takeIf { it.exists() }
    } catch (e: FirebaseFirestoreException) {
        null
    }

/**
 * Offline-first writes are not awaited: Firestore applies them on the device at once and syncs
 * later. A rejection (for example by the security rules) arrives much later, so it is only logged.
 */
internal fun Task<*>.logFailure(action: String) {
    addOnFailureListener { Log.w("ChoresAnythingSync", "Could not $action", it) }
}
