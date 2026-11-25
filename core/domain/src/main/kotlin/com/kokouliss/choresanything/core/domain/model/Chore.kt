package com.kokouliss.choresanything.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A chore and the household it belongs to. */
data class ChoreRef(val householdId: String, val choreId: String)

/** Where a chore stands. [key] is the stored name. */
enum class ChoreStatus(val key: String) {
    /** Asked for by a kid. Waits for an adult to confirm, change or decline it. */
    PENDING("pending"),
    OPEN("open"),
}

/** How often a chore comes round again. [key] is what the database stores. */
enum class Repeat(val key: String) {
    DAILY("day"),
    WEEKLY("week"),
    FORTNIGHTLY("twoWeeks"),
    MONTHLY("month"),
    ;

    /**
     * The first day of this rhythm after [after], counting from [fromDay]; always at least one step
     * on. Both are counted like `dueDay`. Steps are taken from [fromDay] itself, so the 31st of a
     * month comes back on the 31st after a February.
     */
    fun next(fromDay: Long, after: Long): Long {
        val start = LocalDate.ofEpochDay(fromDay)
        var steps = 1L
        while (true) {
            val day = when (this) {
                DAILY -> start.plusDays(steps)
                WEEKLY -> start.plusWeeks(steps)
                FORTNIGHTLY -> start.plusWeeks(2 * steps)
                MONTHLY -> start.plusMonths(steps)
            }.toEpochDay()
            if (day > after) return day
            steps++
        }
    }
}

/** A chore passed to someone who has not said yes or no yet. */
data class Handoff(
    val toId: String,
    val toName: String,
    val byId: String,
    val byName: String,
)

/** The longest a chore's title can be. The security rules say the same. */
const val MAX_TITLE_LENGTH = 80
const val MAX_INFO_LENGTH = 1000
const val MAX_NEEDS = 20
const val MAX_NEED_LENGTH = 60
const val MAX_MINUTES = 24 * 60

/** The time of day a chore without one is due, for reminders and the order of the day: nine in the morning. */
const val DEFAULT_DUE_MINUTE = 9 * 60

/**
 * One job in the house or outside it, from the dishes to the supermarket. Its how-to ([info] and
 * [needs]) is what lets anyone in the household do it.
 */
data class Chore(
    val id: String,
    val householdId: String,
    val title: String,
    /** How it is done and where things are, for example "the broom is in the hall closet". */
    val info: String = "",
    /** What is needed, one thing per entry. */
    val needs: List<String> = emptyList(),
    /** About how long it takes. */
    val minutes: Int? = null,
    /** Who does it. Null is anyone: the first to do it. */
    val assigneeId: String? = null,
    val assigneeName: String? = null,
    /** Member ids the chore goes round, in this order. Each time it is done, the next one is up. */
    val rotation: List<String> = emptyList(),
    /** Days since 1 January 1970. Null is "whenever". */
    val dueDay: Long? = null,
    /** Minutes after midnight on the due day. Null is any time that day. */
    val dueMinute: Int? = null,
    val repeat: Repeat? = null,
    /** How long before the due time the reminder rings. Null is no reminder. */
    val remindMinutesBefore: Int? = null,
    val status: ChoreStatus = ChoreStatus.OPEN,
    val createdById: String,
    val createdByName: String,
    val createdAtMillis: Long = 0L,
    /** When it was last done and by whom. A chore that does not repeat is finished once it has these. */
    val doneAtMillis: Long? = null,
    val doneById: String? = null,
    val doneByName: String? = null,
    val handoff: Handoff? = null,
) {
    val isPending: Boolean get() = status == ChoreStatus.PENDING

    /** A chore that does not repeat and was done. A repeating one is never finished: it comes round again. */
    val isDone: Boolean get() = doneAtMillis != null && repeat == null

    /** True when the chore is for [userId], or for anyone. */
    fun isFor(userId: String): Boolean = assigneeId == null || assigneeId == userId

    /** The moment the chore is due, or null without a day. A chore without a time is due at [DEFAULT_DUE_MINUTE]. */
    fun dueAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long? {
        val day = dueDay ?: return null
        val time = LocalTime.ofSecondOfDay((dueMinute ?: DEFAULT_DUE_MINUTE) * 60L)
        return LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** The moment the reminder rings, or null for a chore without one or without a day. */
    fun remindAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long? {
        val before = remindMinutesBefore ?: return null
        return dueAtMillis(zone)?.minus(before * 60_000L)
    }

    /**
     * Whether [userId] may change what the chore is: adults always, a kid only a chore they asked
     * for that still waits. Deleting follows the same rule. The security rules say the same.
     */
    fun canEdit(userId: String, isAdult: Boolean): Boolean = isAdult || (isPending && createdById == userId)

    /** Whether [userId] may pass the chore on: whoever it is for, or any adult, while nobody is being asked. */
    fun canPass(userId: String, isAdult: Boolean): Boolean =
        !isPending && handoff == null && (isAdult || assigneeId == userId || assigneeId == null)

    /**
     * The next person up once the chore is done: the one after the assignee in [rotation], skipping
     * anyone who left ([members]). Null when the chore does not rotate, or nobody in the rotation is left.
     */
    fun nextInRotation(members: List<Member>): Member? {
        val present = rotation.mapNotNull { id -> members.firstOrNull { it.id == id } }
        if (present.isEmpty()) return null
        val at = present.indexOfFirst { it.id == assigneeId }
        return present[(at + 1).mod(present.size)]
    }

    /** This chore with the names its people use now ([names]: member id to name). Someone who left keeps the stored name. */
    fun withNames(names: Map<String, String>): Chore = copy(
        assigneeName = assigneeId?.let { names[it] ?: assigneeName },
        createdByName = names[createdById] ?: createdByName,
        doneByName = doneById?.let { names[it] ?: doneByName },
        handoff = handoff?.let { it.copy(toName = names[it.toId] ?: it.toName, byName = names[it.byId] ?: it.byName) },
    )

    /** The chore once [by] has done it on [day]: a one-off finishes, a repeating one moves to its next day and the next person. */
    fun done(by: Member, atMillis: Long, day: Long, members: List<Member>): Chore {
        val finished = copy(doneAtMillis = atMillis, doneById = by.id, doneByName = by.name, handoff = null)
        val rhythm = repeat ?: return finished
        val next = nextInRotation(members)
        val from = dueDay ?: day
        return finished.copy(
            dueDay = rhythm.next(from, after = maxOf(day, from)),
            assigneeId = next?.id ?: assigneeId,
            assigneeName = next?.name ?: assigneeName,
        )
    }
}

/** The day [millis] falls on in [zone], counted like `dueDay`. */
fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/** Where a chore shows on the Today tab, in the order the groups are shown. */
enum class ChoreGroup {
    /** Asked for by a kid; an adult sees it here to confirm. */
    NEEDS_OK,

    /** What this kid asked for and an adult has not answered yet. */
    WAITING_OK,

    /** Passed to this person, who has not said yes or no. */
    PASSED_TO_YOU,
    OVERDUE,
    TODAY,

    /** The coming week. */
    SOON,
    LATER,

    /** No day set. */
    WHENEVER,

    /** Finished today. Older finished chores show on the calendar and in the history only. */
    DONE,
}

/** How far ahead [ChoreGroup.SOON] looks, in days. */
const val SOON_DAYS = 7

/**
 * The chores sorted into the groups of the Today tab for [userId], who is an adult or not, on
 * [today]. Empty groups are left out. Kids see only their own waiting chores, not other kids'.
 * Inside a group the earliest comes first, then by title.
 */
fun List<Chore>.grouped(today: Long, userId: String, isAdult: Boolean, zone: ZoneId = ZoneId.systemDefault()): Map<ChoreGroup, List<Chore>> {
    val sorted = sortedWith(
        compareBy<Chore> { it.dueDay ?: Long.MAX_VALUE }.thenBy { it.dueMinute ?: DEFAULT_DUE_MINUTE }.thenBy { it.title.lowercase() },
    )
    val groups = sortedMapOf<ChoreGroup, MutableList<Chore>>()
    for (chore in sorted) {
        val day = chore.dueDay
        val group = when {
            chore.isPending -> if (isAdult) ChoreGroup.NEEDS_OK else if (chore.createdById == userId) ChoreGroup.WAITING_OK else null
            chore.handoff?.toId == userId -> ChoreGroup.PASSED_TO_YOU
            chore.isDone -> if (chore.doneAtMillis?.let { dayOf(it, zone) } == today) ChoreGroup.DONE else null
            day == null -> ChoreGroup.WHENEVER
            day < today -> ChoreGroup.OVERDUE
            day == today -> ChoreGroup.TODAY
            day <= today + SOON_DAYS -> ChoreGroup.SOON
            else -> ChoreGroup.LATER
        } ?: continue
        groups.getOrPut(group) { mutableListOf() } += chore
    }
    return groups
}

/** The minutes each member spent, by member id, on the chores finished since [sinceMillis]. A chore without minutes counts nothing. */
fun List<Activity>.minutesByMember(sinceMillis: Long): Map<String, Int> =
    filter { it.kind == ActivityKind.DONE && it.atMillis >= sinceMillis }
        .groupBy { it.byId }
        .mapValues { (_, lines) -> lines.sumOf { it.minutes ?: 0 } }

/**
 * The chores by the day they fall on, from [from] to [to] inclusive, for the calendar: an open chore
 * on its due day, a repeating one on every day of its rhythm in the range (as a copy due that day),
 * and a finished chore on the day it was done.
 */
fun List<Chore>.onDays(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Long, List<Chore>> {
    val days = sortedMapOf<Long, MutableList<Chore>>()
    fun put(day: Long, chore: Chore) {
        if (day in from..to) days.getOrPut(day) { mutableListOf() } += chore
    }
    for (chore in this) {
        if (chore.isPending) continue
        if (chore.isDone) {
            put(dayOf(chore.doneAtMillis!!, zone), chore)
            continue
        }
        val due = chore.dueDay ?: continue
        put(due, chore)
        val rhythm = chore.repeat ?: continue
        var day = rhythm.next(due, maxOf(due, from - 1))
        while (day <= to) {
            if (day != due) put(day, chore.copy(dueDay = day))
            day = rhythm.next(due, day)
        }
    }
    return days
}
