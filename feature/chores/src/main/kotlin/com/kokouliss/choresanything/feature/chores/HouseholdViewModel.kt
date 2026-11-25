package com.kokouliss.choresanything.feature.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.choresanything.core.domain.model.Activity
import com.kokouliss.choresanything.core.domain.model.ActivityKind
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreGroup
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.Role
import com.kokouliss.choresanything.core.domain.model.dayOf
import com.kokouliss.choresanything.core.domain.model.grouped
import com.kokouliss.choresanything.core.domain.model.minutesByMember
import com.kokouliss.choresanything.core.domain.model.normalizeEmail
import com.kokouliss.choresanything.core.domain.model.withNames
import com.kokouliss.choresanything.core.domain.repository.ActivityRepository
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import com.kokouliss.choresanything.core.domain.repository.HouseholdRepository
import com.kokouliss.choresanything.core.ui.component.Undoable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface HouseholdUiState {
    data object Loading : HouseholdUiState

    /** The household was deleted, or this user left or was removed. The screen closes itself. */
    data object Gone : HouseholdUiState

    data class Ready(
        val household: Household,
        /** Every chore of the household with current names, finished ones included. */
        val chores: List<Chore>,
        val currentUserId: String,
        /** Today, counted like `Chore.dueDay`. */
        val today: Long,
        /** True when the Today tab shows only the chores for this person. */
        val onlyMine: Boolean = false,
    ) : HouseholdUiState {
        val isOwner: Boolean get() = household.ownerId == currentUserId
        val isAdult: Boolean get() = household.isAdult(currentUserId)
        val me: Member? get() = household.member(currentUserId)

        /** The Today tab: the chores by group, only this person's when [onlyMine]. */
        val groups: Map<ChoreGroup, List<Chore>>
            get() = chores.filter { !onlyMine || it.isFor(currentUserId) || it.handoff?.toId == currentUserId }
                .grouped(today, currentUserId, isAdult)
    }
}

/** What one member did this week. */
internal data class Tally(val member: Member, val done: Int, val minutes: Int)

@HiltViewModel(assistedFactory = HouseholdViewModel.Factory::class)
internal class HouseholdViewModel @AssistedInject constructor(
    @Assisted private val key: HouseholdKey,
    private val householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
    authRepository: AuthRepository,
    activityRepository: ActivityRepository,
) : ViewModel() {

    private val onlyMine = MutableStateFlow(false)

    /** Email invites taken off the list at once and cancelled only once their Undo snackbar is gone. */
    val cancelledInvites = Undoable<String>(viewModelScope) { householdRepository.cancelEmailInvite(key.householdId, it) }

    /** Chores ticked off at once and written as done only once their Undo snackbar is gone. */
    val dones = Undoable<Chore>(viewModelScope) { chore ->
        val state = uiState.value as? HouseholdUiState.Ready
        val me = state?.me ?: return@Undoable
        choreRepository.markDone(chore, me, state.household.members)
    }

    val uiState: StateFlow<HouseholdUiState> = combine(
        householdRepository.observeHousehold(key.householdId),
        // Losing access ends this stream with an error; the household stream reports that as "gone".
        choreRepository.observeChores(key.householdId).catch { emit(emptyList()) },
        authRepository.currentUser,
        combine(cancelledInvites.pending, dones.pending, onlyMine) { cancelled, done, mine -> Triple(cancelled, done, mine) },
    ) { household, chores, user, (cancelled, done, mine) ->
        if (household == null || user == null) {
            HouseholdUiState.Gone
        } else {
            val names = household.names
            HouseholdUiState.Ready(
                household = household.copy(invitedEmails = household.invitedEmails.filter { it != cancelled?.removal }),
                chores = chores.filter { it.id != done?.removal?.id }.map { it.withNames(names) },
                currentUserId = user.id,
                today = dayOf(System.currentTimeMillis()),
                onlyMine = mine,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdUiState.Loading)

    /**
     * The household's history with current names, null until it arrives. Only the History tab
     * collects it, so a visit to the household that never opens the tab reads none of it.
     */
    val history: StateFlow<List<Activity>?> = combine(
        activityRepository.observeActivity(key.householdId).catch { emit(emptyList()) },
        uiState.filterIsInstance<HouseholdUiState.Ready>(),
    ) { entries, state -> entries.withNames(state.household.names) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Who did how much since Monday, most first. Null until the history arrives. */
    val weekTally: StateFlow<List<Tally>?> = combine(history, uiState.filterIsInstance<HouseholdUiState.Ready>()) { lines, state ->
        if (lines == null) return@combine null
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val minutes = lines.minutesByMember(monday)
        val done = lines.filter { it.kind == ActivityKind.DONE && it.atMillis >= monday }.groupingBy { it.byId }.eachCount()
        state.household.members.map { Tally(it, done[it.id] ?: 0, minutes[it.id] ?: 0) }.sortedByDescending { it.done }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ready: HouseholdUiState.Ready? get() = uiState.value as? HouseholdUiState.Ready

    fun setOnlyMine(mine: Boolean) = onlyMine.update { mine }

    fun markDone(chore: Chore) = dones.remove(chore)

    fun confirm(chore: Chore) {
        viewModelScope.launch { choreRepository.confirm(chore) }
    }

    fun decline(chore: Chore) {
        viewModelScope.launch { choreRepository.decline(chore) }
    }

    fun take(chore: Chore) {
        viewModelScope.launch { choreRepository.take(chore) }
    }

    fun refuse(chore: Chore) {
        viewModelScope.launch { choreRepository.refuse(chore) }
    }

    fun renameHousehold(name: String) {
        viewModelScope.launch { householdRepository.renameHousehold(key.householdId, name) }
    }

    /** The screen only enables sending for a valid address; the new invite then shows up in the pending list. */
    fun inviteEmail(input: String) {
        val email = normalizeEmail(input) ?: return
        viewModelScope.launch { householdRepository.inviteEmail(key.householdId, email) }
    }

    fun cancelEmailInvite(email: String) = cancelledInvites.remove(email)

    fun removeMember(memberId: String) {
        val household = ready?.household ?: return
        viewModelScope.launch { householdRepository.removeMember(household, memberId) }
    }

    fun setRole(memberId: String, role: Role) {
        viewModelScope.launch { householdRepository.setRole(key.householdId, memberId, role) }
    }

    fun resetInviteCode() {
        val household = ready?.household ?: return
        viewModelScope.launch { householdRepository.resetInviteCode(household) }
    }

    fun leaveHousehold() {
        viewModelScope.launch { householdRepository.leaveHousehold(key.householdId) }
    }

    fun deleteHousehold() {
        val household = ready?.household ?: return
        viewModelScope.launch { householdRepository.deleteHousehold(household) }
    }

    override fun onCleared() {
        cancelledInvites.flush()
        dones.flush()
    }

    @AssistedFactory
    interface Factory {
        fun create(key: HouseholdKey): HouseholdViewModel
    }
}
