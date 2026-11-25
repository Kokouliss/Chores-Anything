package com.kokouliss.choresanything.feature.chores

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The signed-in start screen: the user's households and pending invitations. */
@Serializable
data object HouseholdsKey : NavKey

/** One household: its chores for today, its calendar, its history, its members. */
@Serializable
data class HouseholdKey(val householdId: String) : NavKey

/** One chore: its how-to, who does it, and what can be done with it. */
@Serializable
data class ChoreKey(val householdId: String, val choreId: String) : NavKey

/** Makes a new chore, due on [dueDay] when given, or changes [choreId]. */
@Serializable
data class ChoreEditorKey(val householdId: String, val choreId: String? = null, val dueDay: Long? = null) : NavKey

fun EntryProviderScope<NavKey>.choresEntries(
    onOpenHousehold: (householdId: String) -> Unit,
    onOpenChore: (ChoreKey) -> Unit,
    onEditChore: (ChoreEditorKey) -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
    /** The household or chore was deleted or access to it ended: the screen closes and [message] goes on the one below. */
    onGone: (message: String) -> Unit,
    /** A message from a screen that closed itself, shown once by the screen now on top. */
    notice: String?,
    onNoticeShown: () -> Unit,
    /** The code of an invite link that opened the app. The households screen joins with it. */
    inviteCode: String? = null,
    onInviteHandled: () -> Unit = {},
) {
    entry<HouseholdsKey> {
        HouseholdsRoute(
            onOpenHousehold = onOpenHousehold,
            onOpenProfile = onOpenProfile,
            notice = notice,
            onNoticeShown = onNoticeShown,
            inviteCode = inviteCode,
            onInviteHandled = onInviteHandled,
        )
    }
    entry<HouseholdKey> { key ->
        HouseholdRoute(
            viewModel = hiltViewModel<HouseholdViewModel, HouseholdViewModel.Factory> { it.create(key) },
            onOpenChore = { chore -> onOpenChore(ChoreKey(key.householdId, chore.id)) },
            onNewChore = { day -> onEditChore(ChoreEditorKey(key.householdId, dueDay = day)) },
            onBack = onBack,
            onGone = onGone,
            notice = notice,
            onNoticeShown = onNoticeShown,
        )
    }
    entry<ChoreKey> { key ->
        ChoreRoute(
            viewModel = hiltViewModel<ChoreViewModel, ChoreViewModel.Factory> { it.create(key) },
            onEdit = { onEditChore(ChoreEditorKey(key.householdId, key.choreId)) },
            onBack = onBack,
            onGone = onGone,
        )
    }
    entry<ChoreEditorKey> { key ->
        ChoreEditorRoute(
            viewModel = hiltViewModel<ChoreEditorViewModel, ChoreEditorViewModel.Factory> { it.create(key) },
            onBack = onBack,
        )
    }
}
