package com.kokouliss.choresanything.feature.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.Repeat
import com.kokouliss.choresanything.core.domain.model.dayOf
import com.kokouliss.choresanything.core.domain.model.withNames
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import com.kokouliss.choresanything.core.domain.repository.HouseholdRepository
import com.kokouliss.choresanything.core.ui.R as CoreR
import com.kokouliss.choresanything.core.ui.component.AppIcon
import com.kokouliss.choresanything.core.ui.component.AppIcons
import com.kokouliss.choresanything.core.ui.component.BackButton
import com.kokouliss.choresanything.core.ui.component.ConfirmDialog
import com.kokouliss.choresanything.core.ui.component.InitialAvatar
import com.kokouliss.choresanything.core.ui.component.LoadingCrossfade
import com.kokouliss.choresanything.core.ui.component.pageWidth
import com.kokouliss.choresanything.core.ui.component.rememberPaperScroll
import com.kokouliss.choresanything.core.ui.component.ruledPaper
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface ChoreUiState {
    data object Loading : ChoreUiState

    /** The chore or its household is gone, or this user is no longer a member. The screen closes itself. */
    data object Gone : ChoreUiState

    data class Ready(val household: Household, val chore: Chore, val currentUserId: String, val today: Long) : ChoreUiState {
        val isAdult: Boolean get() = household.isAdult(currentUserId)
        val me: Member? get() = household.member(currentUserId)
        val canEdit: Boolean get() = chore.canEdit(currentUserId, isAdult)
        val canPass: Boolean get() = chore.canPass(currentUserId, isAdult)
        val isPassedToMe: Boolean get() = chore.handoff?.toId == currentUserId
    }
}

@HiltViewModel(assistedFactory = ChoreViewModel.Factory::class)
internal class ChoreViewModel @AssistedInject constructor(
    @Assisted private val key: ChoreKey,
    householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val ref = ChoreRef(key.householdId, key.choreId)

    val uiState: StateFlow<ChoreUiState> = combine(
        householdRepository.observeHousehold(key.householdId),
        choreRepository.observeChore(ref).catch { emit(null) },
        authRepository.currentUser,
    ) { household, chore, user ->
        if (household == null || chore == null || user == null) {
            ChoreUiState.Gone
        } else {
            ChoreUiState.Ready(household, chore.withNames(household.names), user.id, dayOf(System.currentTimeMillis()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoreUiState.Loading)

    private val _undo = MutableStateFlow<Chore?>(null)

    /** The chore as it was before Done was tapped on this screen, while an Undo still makes sense. */
    val undo: StateFlow<Chore?> = _undo.asStateFlow()

    private val ready: ChoreUiState.Ready? get() = uiState.value as? ChoreUiState.Ready

    fun markDone() {
        val state = ready ?: return
        val me = state.me ?: return
        _undo.value = state.chore
        viewModelScope.launch { choreRepository.markDone(state.chore, me, state.household.members) }
    }

    fun undoDone() {
        val before = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { choreRepository.undoDone(before) }
    }

    fun confirm() = act { choreRepository.confirm(it) }

    fun decline() = act { choreRepository.decline(it) }

    fun pass(to: Member) = act { choreRepository.pass(it, to) }

    fun take() = act { choreRepository.take(it) }

    fun refuse() = act { choreRepository.refuse(it) }

    fun delete() = act { choreRepository.deleteChore(it) }

    private fun act(action: suspend (Chore) -> Unit) {
        val chore = ready?.chore ?: return
        viewModelScope.launch { action(chore) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ChoreKey): ChoreViewModel
    }
}

@Composable
internal fun ChoreRoute(viewModel: ChoreViewModel, onEdit: () -> Unit, onBack: () -> Unit, onGone: (message: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    // Set when this screen ends the chore itself, so the screen below says what happened and not that it vanished.
    var leaving by rememberSaveable { mutableStateOf<Int?>(null) }
    if (uiState is ChoreUiState.Gone) {
        LaunchedEffect(Unit) { onGone(resources.getString(leaving ?: R.string.feature_chores_chore_gone)) }
    }
    ChoreScreen(
        uiState = uiState,
        undo = undo,
        onBack = onBack,
        onEdit = onEdit,
        onDone = viewModel::markDone,
        onUndo = viewModel::undoDone,
        onConfirm = viewModel::confirm,
        onDecline = {
            leaving = R.string.feature_chores_chore_declined
            viewModel.decline()
        },
        onPass = viewModel::pass,
        onTake = viewModel::take,
        onRefuse = viewModel::refuse,
        onDelete = {
            leaving = R.string.feature_chores_chore_deleted
            viewModel.delete()
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoreScreen(
    uiState: ChoreUiState,
    undo: Chore?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onPass: (Member) -> Unit,
    onTake: () -> Unit,
    onRefuse: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = uiState as? ChoreUiState.Ready
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var choosePerson by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val title = ready?.chore?.title.orEmpty()
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready?.canEdit == true) {
                        IconButton(onClick = onEdit) { AppIcon(AppIcons.Edit, stringResource(R.string.feature_chores_edit_chore)) }
                        IconButton(onClick = { confirmDelete = true }) { AppIcon(AppIcons.Delete, stringResource(R.string.feature_chores_delete_chore)) }
                    }
                },
            )
        },
    ) { padding ->
        LoadingCrossfade(ready == null, Modifier.fillMaxSize().ruledPaper(MaterialTheme.colorScheme.primary, rememberPaperScroll()).padding(padding).pageWidth()) {
            if (ready == null) return@LoadingCrossfade
            val chore = ready.chore
            val context = LocalContext.current
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(chore.title, style = MaterialTheme.typography.headlineMedium)
                StatusLine(ready)

                InfoCard(stringResource(R.string.feature_chores_when)) {
                    val day = chore.dueDay
                    Line(
                        AppIcons.Event,
                        if (day == null) {
                            stringResource(R.string.feature_chores_whenever)
                        } else {
                            dayText(day, ready.today) + (chore.dueMinute?.let { " · " + context.formatTime(it) } ?: "")
                        },
                    )
                    chore.repeat?.let { Line(AppIcons.Repeat, stringResource(it.label)) }
                    chore.remindMinutesBefore?.let { Line(AppIcons.Alarm, reminderText(it)) }
                    chore.minutes?.let { Line(AppIcons.Timer, minutesText(it)) }
                }
                InfoCard(stringResource(R.string.feature_chores_who)) {
                    Line(AppIcons.Person, chore.assigneeName ?: stringResource(R.string.feature_chores_anyone))
                    if (chore.rotation.size > 1) {
                        val names = chore.rotation.mapNotNull { ready.household.member(it)?.name }
                        Line(AppIcons.SwapHoriz, stringResource(R.string.feature_chores_takes_turns_list, names.joinToString(" → ")))
                    }
                    Line(AppIcons.Info, stringResource(R.string.feature_chores_added_by, chore.createdByName))
                }
                if (chore.info.isNotEmpty() || chore.needs.isNotEmpty()) {
                    InfoCard(stringResource(R.string.feature_chores_how)) {
                        if (chore.info.isNotEmpty()) Text(chore.info, style = MaterialTheme.typography.bodyLarge)
                        chore.needs.forEach { Line(AppIcons.Inventory, it) }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        chore.isPending -> if (ready.isAdult) {
                            Button(onClick = onConfirm) { Text(stringResource(R.string.feature_chores_confirm)) }
                            TextButton(onClick = onDecline) { Text(stringResource(R.string.feature_chores_decline)) }
                        }
                        ready.isPassedToMe -> {
                            Button(onClick = onTake) { Text(stringResource(R.string.feature_chores_take)) }
                            TextButton(onClick = onRefuse) { Text(stringResource(R.string.feature_chores_refuse)) }
                        }
                        else -> {
                            if (!chore.isDone) Button(onClick = onDone) { Text(stringResource(R.string.feature_chores_done)) }
                            if (undo != null) TextButton(onClick = onUndo) { Text(stringResource(CoreR.string.core_ui_undo)) }
                            if (!chore.isDone && chore.assigneeId == null && chore.handoff == null) {
                                OutlinedButton(onClick = onTake) { Text(stringResource(R.string.feature_chores_take_anyone)) }
                            }
                            if (ready.canPass && ready.household.members.size > 1) {
                                OutlinedButton(onClick = { choosePerson = true }) {
                                    AppIcon(AppIcons.SwapHoriz, null, Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.feature_chores_pass_on))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_chores_delete_chore),
            text = stringResource(R.string.feature_chores_delete_chore_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (choosePerson && ready != null) {
        AlertDialog(
            onDismissRequest = { choosePerson = false },
            title = { Text(stringResource(R.string.feature_chores_pass_to_title)) },
            text = {
                Column {
                    ready.household.members.filter { it.id != ready.currentUserId }.forEach { member ->
                        ListItem(
                            headlineContent = { Text(member.name) },
                            supportingContent = { Text(stringResource(member.role.label)) },
                            leadingContent = { InitialAvatar(member.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = {
                                choosePerson = false
                                onPass(member)
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.feature_chores_pass_to, member.name)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosePerson = false }) { Text(stringResource(CoreR.string.core_ui_cancel)) } },
        )
    }
}

/** What stands out about the chore right now, on a yellow note. Nothing for an ordinary open chore. */
@Composable
private fun StatusLine(state: ChoreUiState.Ready) {
    val chore = state.chore
    val context = LocalContext.current
    val handoff = chore.handoff
    val doneAt = chore.doneAtMillis
    val text = when {
        chore.isPending -> stringResource(R.string.feature_chores_status_pending)
        handoff != null -> stringResource(R.string.feature_chores_status_passed, handoff.toName, handoff.byName)
        doneAt != null -> stringResource(
            if (chore.isDone) R.string.feature_chores_status_done else R.string.feature_chores_status_last_done,
            chore.doneByName.orEmpty(),
            context.formatDay(dayOf(doneAt)),
        )
        (chore.dueDay ?: Long.MAX_VALUE) < state.today -> stringResource(R.string.feature_chores_group_overdue)
        else -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(text, Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun Line(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        AppIcon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@PreviewLightDark
@Composable
private fun ChoreScreenPreview() {
    val household = Household("1", "Family", "u1", "ABCD2345", listOf(Member("u1", "Panos"), Member("u2", "Maria")), emptyList())
    ChoresAnythingTheme {
        ChoreScreen(
            uiState = ChoreUiState.Ready(
                household = household,
                chore = Chore(
                    "c1", "1", "Sweep the balcony",
                    info = "The broom is in the hall closet. Water the plants while you are out there.",
                    needs = listOf("broom", "dustpan"),
                    minutes = 10,
                    assigneeId = "u2", assigneeName = "Maria",
                    dueDay = 20_783, dueMinute = 18 * 60, repeat = Repeat.WEEKLY, remindMinutesBefore = 15,
                    createdById = "u1", createdByName = "Panos",
                ),
                currentUserId = "u1",
                today = 20_783,
            ),
            undo = null,
            onBack = {}, onEdit = {}, onDone = {}, onUndo = {}, onConfirm = {}, onDecline = {}, onPass = {}, onTake = {}, onRefuse = {}, onDelete = {},
        )
    }
}
