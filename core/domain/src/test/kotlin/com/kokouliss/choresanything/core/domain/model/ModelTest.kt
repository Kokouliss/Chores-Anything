package com.kokouliss.choresanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class ModelTest {

    private val panos = Member("u1", "Panos")
    private val maria = Member("u2", "Maria")
    private val nikos = Member("u3", "Nikos", Role.KID)
    private val members = listOf(panos, maria, nikos)
    private val today = LocalDate.of(2026, 11, 25).toEpochDay()
    private val utc = ZoneOffset.UTC

    private fun chore(id: String, title: String = id, dueDay: Long? = null, assigneeId: String? = null, status: ChoreStatus = ChoreStatus.OPEN) =
        Chore(id, "h1", title, dueDay = dueDay, assigneeId = assigneeId, status = status, createdById = "u1", createdByName = "Panos")

    @Test
    fun `a rhythm steps from its start, past the day it is asked after`() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        // Monthly from the 31st: February has no 31st, so the 28th, then back on the 31st in March.
        assertThat(Repeat.MONTHLY.next(jan31, after = jan31)).isEqualTo(LocalDate.of(2026, 2, 28).toEpochDay())
        assertThat(Repeat.MONTHLY.next(jan31, after = LocalDate.of(2026, 2, 28).toEpochDay())).isEqualTo(LocalDate.of(2026, 3, 31).toEpochDay())
        // A weekly chore done three weeks late comes back next week, not three times.
        assertThat(Repeat.WEEKLY.next(today, after = today + 21)).isEqualTo(today + 28)
        assertThat(Repeat.DAILY.next(today, after = today)).isEqualTo(today + 1)
        assertThat(Repeat.FORTNIGHTLY.next(today, after = today)).isEqualTo(today + 14)
    }

    @Test
    fun `doing a repeating chore moves it on and to the next person in turn`() {
        val weekly = chore("c1", dueDay = today, assigneeId = "u1").copy(repeat = Repeat.WEEKLY, rotation = listOf("u1", "u2", "gone"))
        val done = weekly.done(by = maria, atMillis = 1_000, day = today, members = members)
        assertThat(done.dueDay).isEqualTo(today + 7)
        assertThat(done.assigneeId).isEqualTo("u2")
        assertThat(done.doneByName).isEqualTo("Maria")
        assertThat(done.isDone).isFalse()
        // The last in turn goes round to the first; someone who left is skipped.
        assertThat(done.nextInRotation(members)?.id).isEqualTo("u1")

        val once = chore("c2", dueDay = today).done(by = panos, atMillis = 1_000, day = today, members = members)
        assertThat(once.isDone).isTrue()
        assertThat(once.dueDay).isEqualTo(today)
    }

    @Test
    fun `the Today tab groups chores for the person looking at it`() {
        val chores = listOf(
            chore("asked", status = ChoreStatus.PENDING).copy(createdById = "u3"),
            chore("late", dueDay = today - 1),
            chore("now", dueDay = today),
            chore("soon", dueDay = today + 3),
            chore("later", dueDay = today + 30),
            chore("whenever"),
            chore("passed").copy(handoff = Handoff("u2", "Maria", "u1", "Panos")),
            chore("finished", dueDay = today).copy(doneAtMillis = today * 86_400_000L + 3_600_000L),
            chore("old", dueDay = today - 5).copy(doneAtMillis = (today - 5) * 86_400_000L),
        )
        val adult = chores.grouped(today, "u1", isAdult = true, zone = utc)
        assertThat(adult.keys).containsExactly(
            ChoreGroup.NEEDS_OK, ChoreGroup.OVERDUE, ChoreGroup.TODAY, ChoreGroup.SOON, ChoreGroup.LATER, ChoreGroup.WHENEVER, ChoreGroup.DONE,
        ).inOrder()
        assertThat(adult[ChoreGroup.WHENEVER]?.map { it.id }).containsExactly("passed", "whenever")

        val kid = chores.grouped(today, "u3", isAdult = false, zone = utc)
        assertThat(kid[ChoreGroup.WAITING_OK]?.map { it.id }).containsExactly("asked")
        assertThat(kid).doesNotContainKey(ChoreGroup.NEEDS_OK)

        val maria = chores.grouped(today, "u2", isAdult = true, zone = utc)
        assertThat(maria[ChoreGroup.PASSED_TO_YOU]?.map { it.id }).containsExactly("passed")
    }

    @Test
    fun `who may change, pass and take what`() {
        val adults = chore("c1", assigneeId = "u2")
        assertThat(adults.canEdit("u3", isAdult = false)).isFalse()
        assertThat(adults.canEdit("u2", isAdult = true)).isTrue()
        assertThat(adults.canPass("u3", isAdult = false)).isFalse()
        assertThat(adults.canPass("u2", isAdult = false)).isTrue()
        assertThat(adults.copy(handoff = Handoff("u3", "Nikos", "u2", "Maria")).canPass("u2", isAdult = true)).isFalse()
        val asked = chore("c2", status = ChoreStatus.PENDING).copy(createdById = "u3")
        assertThat(asked.canEdit("u3", isAdult = false)).isTrue()
        assertThat(asked.canPass("u1", isAdult = true)).isFalse()
    }

    @Test
    fun `the calendar shows a repeating chore on every day of its rhythm, and finished ones where they were done`() {
        val chores = listOf(
            chore("daily", dueDay = today).copy(repeat = Repeat.DAILY),
            chore("once", dueDay = today + 2),
            chore("finished", dueDay = today - 3).copy(doneAtMillis = (today - 1) * 86_400_000L + 1),
            chore("asked", dueDay = today, status = ChoreStatus.PENDING),
        )
        val days = chores.onDays(today, today + 2, zone = utc)
        assertThat(days.keys).containsExactly(today, today + 1, today + 2).inOrder()
        assertThat(days[today + 1]?.single()?.dueDay).isEqualTo(today + 1)
        assertThat(days[today + 2]?.map { it.id }).containsExactly("daily", "once")
        assertThat(chores.onDays(today - 1, today - 1, zone = utc)[today - 1]?.map { it.id }).containsExactly("finished")
    }

    @Test
    fun `the weekly tally adds the minutes of what each person did since the start`() {
        val lines = listOf(
            Activity("a1", ActivityKind.DONE, "u1", "Panos", "c1", "Dishes", atMillis = 100, minutes = 15),
            Activity("a2", ActivityKind.DONE, "u1", "Panos", "c2", "Bins", atMillis = 200),
            Activity("a3", ActivityKind.DONE, "u2", "Maria", "c3", "Shopping", atMillis = 300, minutes = 40),
            Activity("a4", ActivityKind.DONE, "u2", "Maria", "c3", "Shopping", atMillis = 50, minutes = 40),
            Activity("a5", ActivityKind.ADDED, "u2", "Maria", "c4", "Garden", atMillis = 300, minutes = 90),
        )
        assertThat(lines.minutesByMember(sinceMillis = 100)).containsExactly("u1", 15, "u2", 40)
    }

    @Test
    fun `names follow the member list, and someone who left keeps the stored name`() {
        val chore = chore("c1", assigneeId = "u2").copy(assigneeName = "M.", handoff = Handoff("gone", "Old", "u1", "P."))
        val named = chore.withNames(mapOf("u1" to "Panos", "u2" to "Maria"))
        assertThat(named.assigneeName).isEqualTo("Maria")
        assertThat(named.createdByName).isEqualTo("Panos")
        assertThat(named.handoff).isEqualTo(Handoff("gone", "Old", "u1", "Panos"))
    }

    @Test
    fun `invite codes are read from links and typed text`() {
        val code = InviteCode.generate()
        assertThat(InviteCode.isValid(code)).isTrue()
        assertThat(InviteCode.fromLink(InviteCode.link(code))).isEqualTo(code)
        assertThat(InviteCode.fromLink("https://example.com/join/$code")).isNull()
        assertThat(InviteCode.normalize(" ab-cd 2345 ")).isEqualTo("ABCD2345")
        assertThat(normalizeEmail(" Panos@Example.com ")).isEqualTo("panos@example.com")
        assertThat(normalizeEmail("not an email")).isNull()
    }
}
