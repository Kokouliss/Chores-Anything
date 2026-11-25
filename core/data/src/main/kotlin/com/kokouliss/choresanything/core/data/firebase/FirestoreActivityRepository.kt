package com.kokouliss.choresanything.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kokouliss.choresanything.core.domain.model.ACTIVITY_KEEP_DAYS
import com.kokouliss.choresanything.core.domain.model.Activity
import com.kokouliss.choresanything.core.domain.model.ActivityKind
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.repository.ActivityRepository
import com.kokouliss.choresanything.core.domain.repository.ActivityRepository.Companion.ACTIVITY_SHOWN
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun FirebaseFirestore.activity(householdId: String): CollectionReference =
    collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.ACTIVITY)

/**
 * One document per thing a member did, written by the device that did it, as a separate write so
 * a rejected history line never takes the change itself down. Firestore deletes each one at its
 * expireAt (a TTL policy in firebase/firestore.indexes.json) and a visit reads only the newest
 * [ACTIVITY_SHOWN], so the history costs one write per change and a capped read per visit.
 */
internal class FirestoreActivityRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : ActivityRepository {

    override fun observeActivity(householdId: String): Flow<List<Activity>> =
        db.activity(householdId).orderBy(Fields.AT, Query.Direction.DESCENDING).limit(ACTIVITY_SHOWN).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toActivity() } }

    /** Notes that the signed-in user did [kind] to [chore], to or for [other] when there is one. [minutes] is kept for a finished chore. */
    fun note(chore: Chore, kind: ActivityKind, other: Member? = null, minutes: Int? = null) {
        val user = auth.currentUser ?: return
        db.activity(chore.householdId).document()
            .set(
                buildMap {
                    put(Fields.KIND, kind.key)
                    put(Fields.BY_ID, user.uid)
                    put(Fields.BY_NAME, user.memberName)
                    put(Fields.CHORE_ID, chore.id)
                    put(Fields.CHORE_TITLE, chore.title)
                    other?.let { put(Fields.FOR_ID, it.id); put(Fields.FOR_NAME, it.name) }
                    minutes?.let { put(Fields.MINUTES, it) }
                    put(Fields.AT, FieldValue.serverTimestamp())
                    put(Fields.EXPIRE_AT, Timestamp(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ACTIVITY_KEEP_DAYS.toLong()))))
                },
            )
            .logFailure("note activity")
    }
}

private fun DocumentSnapshot.toActivity(): Activity? {
    val kind = ActivityKind.entries.firstOrNull { it.key == getString(Fields.KIND) } ?: return null
    return Activity(
        id = id,
        kind = kind,
        byId = getString(Fields.BY_ID).orEmpty(),
        byName = getString(Fields.BY_NAME).orEmpty(),
        choreId = getString(Fields.CHORE_ID).orEmpty(),
        choreTitle = getString(Fields.CHORE_TITLE).orEmpty(),
        // A line that has not reached the server yet is estimated, so it shows as just now.
        atMillis = getTimestamp(Fields.AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        forId = getString(Fields.FOR_ID),
        forName = getString(Fields.FOR_NAME),
        minutes = getLong(Fields.MINUTES)?.toInt(),
    )
}
