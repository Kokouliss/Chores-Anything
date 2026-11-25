package com.kokouliss.choresanything.core.domain.model

/** What a member did, as the history of a household tells it. [key] is the stored name. */
enum class ActivityKind(val key: String) {
    ADDED("added"),

    /** A kid asked for a chore; it waits for an adult. */
    ASKED("asked"),
    CONFIRMED("confirmed"),
    DECLINED("declined"),
    CHANGED("changed"),
    DELETED("deleted"),
    DONE("done"),
    UNDONE("undone"),

    /** Passed to [Activity.forName], who has not answered yet. */
    PASSED("passed"),

    /** Took over a chore passed to them, or an "anyone" chore. */
    TOOK("took"),

    /** Said no to a chore passed to them. */
    REFUSED("refused"),
}

/**
 * One line of a household's history: [byName] did [kind] to the chore [choreTitle]. [forName] is
 * the other person of a hand-over, [minutes] how long a finished chore takes, for the weekly tally.
 */
data class Activity(
    val id: String,
    val kind: ActivityKind,
    val byId: String,
    val byName: String,
    val choreId: String,
    val choreTitle: String,
    val atMillis: Long,
    val forId: String? = null,
    val forName: String? = null,
    val minutes: Int? = null,
)

/** How long a household keeps its history. firebase/firestore.rules allows a day more for a wrong clock. */
const val ACTIVITY_KEEP_DAYS = 365

/** The history with people under their current names ([names] by id). Someone who left keeps the stored name. */
fun List<Activity>.withNames(names: Map<String, String>): List<Activity> = map { line ->
    line.copy(byName = names[line.byId] ?: line.byName, forName = line.forId?.let { names[it] ?: line.forName })
}
