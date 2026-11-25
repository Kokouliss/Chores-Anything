package com.kokouliss.choresanything.feature.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreRef
import com.kokouliss.choresanything.core.domain.model.ChoreStatus
import com.kokouliss.choresanything.core.domain.model.DEFAULT_DUE_MINUTE
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.MAX_INFO_LENGTH
import com.kokouliss.choresanything.core.domain.model.MAX_MINUTES
import com.kokouliss.choresanything.core.domain.model.MAX_NEEDS
import com.kokouliss.choresanything.core.domain.model.MAX_NEED_LENGTH
import com.kokouliss.choresanything.core.domain.model.MAX_TITLE_LENGTH
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
import com.kokouliss.choresanything.core.ui.component.LoadingBox
import com.kokouliss.choresanything.core.ui.component.pageWidth
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The chore being written, and what is known about where it goes. */
internal data class EditorState(
    val household: Household,
    val me: Member,
    /** The chore as it is now, or null for a new one. */
    val original: Chore?,
    val draft: Chore,
) {
    val isAdult: Boolean get() = household.isAdult(me.id)
    val canSave: Boolean get() = draft.title.isNotBlank()
}

/** Minutes before the due time a reminder can ring, in the order the chips show them. */
internal val REMINDER_CHOICES = listOf(0, 15, 60, 24 * 60)

private const val DAY_MILLIS = 86_400_000L

@HiltViewModel(assistedFactory = ChoreEditorViewModel.Factory::class)
internal class ChoreEditorViewModel @AssistedInject constructor(
    @Assisted private val key: ChoreEditorKey,
    householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<EditorState?>(null)

    /** Null until the household (and the chore, when one is edited) has been read. */
    val state: StateFlow<EditorState?> = _state.asStateFlow()

    /** True when the household or the chore turned out to be gone: the screen closes. */
    val gone = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val household = householdRepository.observeHousehold(key.householdId).first()
            val me = household?.member(authRepository.currentUser.first()?.id)
            val original = key.choreId?.let { choreRepository.loadChore(ChoreRef(key.householdId, it)) }
            if (household == null || me == null || (key.choreId != null && original == null)) {
                gone.value = true
                return@launch
            }
            val draft = original?.withNames(household.names)
                ?: Chore(id = "", householdId = key.householdId, title = "", dueDay = key.dueDay, createdById = me.id, createdByName = me.name)
            _state.value = EditorState(household, me, original, draft)
        }
    }

    fun edit(change: (Chore) -> Chore) = _state.update { it?.copy(draft = change(it.draft)) }

    /** Writes the draft. A kid's new chore waits for an adult; the caller then leaves the screen. */
    suspend fun save() {
        val state = _state.value ?: return
        val draft = state.draft.copy(
            title = state.draft.title.trim(),
            info = state.draft.info.trim(),
            needs = state.draft.needs.map { it.trim() }.filter { it.isNotEmpty() },
        )
        if (state.original == null) {
            choreRepository.addChore(draft.copy(status = if (state.isAdult) ChoreStatus.OPEN else ChoreStatus.PENDING))
        } else {
            choreRepository.updateChore(draft)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ChoreEditorKey): ChoreEditorViewModel
    }
}

@Composable
internal fun ChoreEditorRoute(viewModel: ChoreEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (gone) LaunchedEffect(Unit) { onBack() }
    ChoreEditorScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = {
            scope.launch {
                viewModel.save()
                onBack()
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoreEditorScreen(state: EditorState?, onEdit: ((Chore) -> Chore) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    val title = stringResource(if (state?.original == null) R.string.feature_chores_new_chore else R.string.feature_chores_edit_chore)
    var pickDay by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(onClick = onSave, enabled = state?.canSave == true) { Text(stringResource(CoreR.string.core_ui_save)) }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val draft = state.draft
        val members = state.household.members
        val context = LocalContext.current
        val today = dayOf(System.currentTimeMillis())
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pageWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { text -> onEdit { it.copy(title = text.take(MAX_TITLE_LENGTH)) } },
                label = { Text(stringResource(R.string.feature_chores_what)) },
                supportingText = if (draft.title.length >= MAX_TITLE_LENGTH) { { Text(stringResource(CoreR.string.core_ui_at_limit, MAX_TITLE_LENGTH)) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.info,
                onValueChange = { text -> onEdit { it.copy(info = text.take(MAX_INFO_LENGTH)) } },
                label = { Text(stringResource(R.string.feature_chores_how)) },
                supportingText = { Text(stringResource(R.string.feature_chores_how_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.feature_chores_needs_title))
            if (draft.needs.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.needs.forEach { need ->
                        AssistChip(
                            onClick = { onEdit { it.copy(needs = it.needs - need) } },
                            label = { Text(need) },
                            trailingIcon = { AppIcon(AppIcons.Close, stringResource(R.string.feature_chores_remove_need, need)) },
                        )
                    }
                }
            }
            if (draft.needs.size < MAX_NEEDS) NeedField { need -> onEdit { it.copy(needs = (it.needs + need).distinct()) } }

            OutlinedTextField(
                value = draft.minutes?.toString().orEmpty(),
                onValueChange = { text -> onEdit { it.copy(minutes = text.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, MAX_MINUTES)) } },
                label = { Text(stringResource(R.string.feature_chores_about_how_long)) },
                leadingIcon = { AppIcon(AppIcons.Timer, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.feature_chores_who))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.assigneeId == null,
                    onClick = { onEdit { it.copy(assigneeId = null, assigneeName = null) } },
                    label = { Text(stringResource(R.string.feature_chores_anyone)) },
                )
                members.forEach { member ->
                    FilterChip(
                        selected = draft.assigneeId == member.id,
                        onClick = { onEdit { it.copy(assigneeId = member.id, assigneeName = member.name) } },
                        label = { Text(member.name) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.feature_chores_when))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickDay = true },
                    label = { Text(draft.dueDay?.let { dayText(it, today) } ?: stringResource(R.string.feature_chores_pick_day)) },
                    leadingIcon = { AppIcon(AppIcons.Event, null) },
                )
                if (draft.dueDay != null) {
                    AssistChip(
                        onClick = { pickTime = true },
                        label = { Text(draft.dueMinute?.let { context.formatTime(it) } ?: stringResource(R.string.feature_chores_any_time)) },
                        leadingIcon = { AppIcon(AppIcons.Schedule, null) },
                    )
                    AssistChip(
                        onClick = { onEdit { it.copy(dueDay = null, dueMinute = null, repeat = null, remindMinutesBefore = null, rotation = emptyList()) } },
                        label = { Text(stringResource(R.string.feature_chores_whenever)) },
                        leadingIcon = { AppIcon(AppIcons.Close, null) },
                    )
                }
            }

            if (draft.dueDay != null) {
                SectionTitle(stringResource(R.string.feature_chores_repeats))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.repeat == null,
                        onClick = { onEdit { it.copy(repeat = null, rotation = emptyList()) } },
                        label = { Text(stringResource(R.string.feature_chores_never)) },
                    )
                    Repeat.entries.forEach { rhythm ->
                        FilterChip(
                            selected = draft.repeat == rhythm,
                            onClick = { onEdit { it.copy(repeat = rhythm) } },
                            label = { Text(stringResource(rhythm.label)) },
                        )
                    }
                }
                if (draft.repeat != null && members.size > 1) {
                    SectionTitle(stringResource(R.string.feature_chores_takes_turns))
                    Hint(stringResource(R.string.feature_chores_takes_turns_hint))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            val inTurn = member.id in draft.rotation
                            FilterChip(
                                selected = inTurn,
                                onClick = {
                                    onEdit {
                                        val rotation = if (inTurn) it.rotation - member.id else it.rotation + member.id
                                        // The first in the rotation starts, unless someone in it is already set.
                                        val first = rotation.firstOrNull { id -> id == it.assigneeId } ?: rotation.firstOrNull()
                                        it.copy(rotation = rotation, assigneeId = first ?: it.assigneeId, assigneeName = first?.let { id -> state.household.member(id)?.name } ?: it.assigneeName)
                                    }
                                },
                                label = { Text(member.name) },
                            )
                        }
                    }
                }
                SectionTitle(stringResource(R.string.feature_chores_reminder))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.remindMinutesBefore == null,
                        onClick = { onEdit { it.copy(remindMinutesBefore = null) } },
                        label = { Text(stringResource(R.string.feature_chores_reminder_none)) },
                    )
                    REMINDER_CHOICES.forEach { before ->
                        FilterChip(
                            selected = draft.remindMinutesBefore == before,
                            onClick = { onEdit { it.copy(remindMinutesBefore = before) } },
                            label = { Text(reminderText(before)) },
                        )
                    }
                }
            }
            if (!state.isAdult && state.original == null) Hint(stringResource(R.string.feature_chores_kid_hint))
        }
    }

    if (pickDay) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (draft(state).dueDay ?: dayOf(System.currentTimeMillis())) * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { millis -> onEdit { it.copy(dueDay = Math.floorDiv(millis, DAY_MILLIS)) } }
                        pickDay = false
                    },
                ) { Text(stringResource(CoreR.string.core_ui_save)) }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text(stringResource(CoreR.string.core_ui_cancel)) } },
        ) { DatePicker(picker) }
    }
    if (pickTime) {
        val minute = draft(state).dueMinute ?: DEFAULT_DUE_MINUTE
        val picker = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit { it.copy(dueMinute = picker.hour * 60 + picker.minute) }
                        pickTime = false
                    },
                ) { Text(stringResource(CoreR.string.core_ui_save)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(CoreR.string.core_ui_cancel)) } },
        )
    }
}

private fun draft(state: EditorState?): Chore = checkNotNull(state).draft

/** "At the time", "15 min before", "1 h before", "1 day before". */
@Composable
internal fun reminderText(minutesBefore: Int): String = when {
    minutesBefore == 0 -> stringResource(R.string.feature_chores_reminder_at_time)
    minutesBefore >= 24 * 60 -> stringResource(R.string.feature_chores_reminder_day_before)
    else -> stringResource(R.string.feature_chores_reminder_before, minutesText(minutesBefore))
}

@Composable
private fun NeedField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        val need = text.trim()
        if (need.isNotEmpty()) {
            onAdd(need)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(MAX_NEED_LENGTH) },
        label = { Text(stringResource(R.string.feature_chores_add_need)) },
        leadingIcon = { AppIcon(AppIcons.Inventory, null) },
        trailingIcon = {
            IconButton(onClick = add, enabled = text.isNotBlank()) {
                AppIcon(AppIcons.Add, stringResource(R.string.feature_chores_add))
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@PreviewLightDark
@Composable
private fun ChoreEditorScreenPreview() {
    val me = Member("u1", "Panos")
    val household = Household("1", "Family", "u1", "ABCD2345", listOf(me, Member("u2", "Maria")), emptyList())
    ChoresAnythingTheme {
        ChoreEditorScreen(
            state = EditorState(
                household = household,
                me = me,
                original = null,
                draft = Chore("", "1", "Sweep the balcony", info = "The broom is in the hall closet.", needs = listOf("broom"), minutes = 10, dueDay = 20_783, repeat = Repeat.WEEKLY, createdById = "u1", createdByName = "Panos"),
            ),
            onEdit = {},
            onSave = {},
            onBack = {},
        )
    }
}
