package com.kokouliss.choresanything.core.domain.model

/** What a member may do. [key] is the stored name. */
enum class Role(val key: String) {
    /** Adds chores that go through at once, confirms or declines what a kid asks for, and changes any chore. */
    ADULT("adult"),

    /** Does chores and asks for new ones, which an adult confirms. Cannot change a chore an adult made. */
    KID("kid"),
}

/** The people who share a home and its chores. */
data class Household(
    val id: String,
    val name: String,
    val ownerId: String,
    /** Anyone who has this code can join. The owner can reset it. */
    val inviteCode: String,
    val members: List<Member>,
    /** Lower-case email addresses that were invited but have not joined yet. */
    val invitedEmails: List<String>,
) {
    fun member(id: String?): Member? = members.firstOrNull { it.id == id }

    /** True for an adult of this household. Someone who left counts as no adult. */
    fun isAdult(id: String?): Boolean = member(id)?.role == Role.ADULT

    /** Member ids to the names they use now. */
    val names: Map<String, String> get() = members.associate { it.id to it.name }
}

data class Member(
    val id: String,
    val name: String,
    val role: Role = Role.ADULT,
)

/** Outcome of trying to join a household with an invite code. */
enum class JoinResult { Joined, AlreadyMember, InvalidCode, Unavailable }
