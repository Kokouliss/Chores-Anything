package com.kokouliss.choresanything.feature.chores

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.InviteCode
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.Repeat
import com.kokouliss.choresanything.core.domain.model.Role
import com.kokouliss.choresanything.core.domain.model.normalizeEmail
import com.kokouliss.choresanything.core.ui.R as CoreR
import com.kokouliss.choresanything.core.ui.component.AppIcon
import com.kokouliss.choresanything.core.ui.component.AppIcons
import com.kokouliss.choresanything.core.ui.component.BackButton
import com.kokouliss.choresanything.core.ui.component.ConfirmDialog
import com.kokouliss.choresanything.core.ui.component.InitialAvatar
import com.kokouliss.choresanything.core.ui.component.LoadingCrossfade
import com.kokouliss.choresanything.core.ui.component.PopIn
import com.kokouliss.choresanything.core.ui.component.TextInputDialog
import com.kokouliss.choresanything.core.ui.component.UndoSnackbar
import com.kokouliss.choresanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.choresanything.core.ui.component.pageWidth
import com.kokouliss.choresanything.core.ui.component.rememberPaperScroll
import com.kokouliss.choresanything.core.ui.component.ruledPaper
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme
import com.kokouliss.choresanything.core.ui.theme.pageChange
import com.kokouliss.choresanything.core.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

private enum class HouseholdDialog { None, Rename, Leave, Delete, ResetCode }

private enum class HouseholdTab(val label: Int) {
    Today(R.string.feature_chores_tab_today),
    Calendar(R.string.feature_chores_tab_calendar),
    History(R.string.feature_chores_tab_history),
}

/** What the Today tab can do with a chore without opening it. */
internal class ChoreActions(
    val onOpen: (Chore) -> Unit,
    val onDone: (Chore) -> Unit,
    val onConfirm: (Chore) -> Unit,
    val onDecline: (Chore) -> Unit,
    val onTake: (Chore) -> Unit,
    val onRefuse: (Chore) -> Unit,
)

@Composable
internal fun HouseholdRoute(
    viewModel: HouseholdViewModel,
    onOpenChore: (Chore) -> Unit,
    onNewChore: (dueDay: Long?) -> Unit,
    onBack: () -> Unit,
    onGone: (message: String) -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (uiState is HouseholdUiState.Gone) {
        LaunchedEffect(Unit) { onGone(resources.getString(R.string.feature_chores_household_gone)) }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            scope.launch { snackbarHostState.showSnackbar(notice) }
            onNoticeShown()
        }
    }
    UndoSnackbar(viewModel.cancelledInvites, snackbarHostState) { resources.getString(R.string.feature_chores_invite_cancelled, it) }
    UndoSnackbar(viewModel.dones, snackbarHostState) { resources.getString(R.string.feature_chores_done_snackbar, it.title) }

    HouseholdScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        actions = ChoreActions(
            onOpen = onOpenChore,
            onDone = viewModel::markDone,
            onConfirm = viewModel::confirm,
            onDecline = viewModel::decline,
            onTake = viewModel::take,
            onRefuse = viewModel::refuse,
        ),
        onNewChore = onNewChore,
        onOnlyMine = viewModel::setOnlyMine,
        onRenameHousehold = viewModel::renameHousehold,
        onShareCode = { household ->
            val text = resources.getString(
                R.string.feature_chores_share_text,
                household.name,
                household.inviteCode,
                InviteCode.link(household.inviteCode),
            )
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        },
        onResetCode = viewModel::resetInviteCode,
        onInviteEmail = viewModel::inviteEmail,
        onCancelEmailInvite = viewModel::cancelEmailInvite,
        onRemoveMember = viewModel::removeMember,
        onSetRole = viewModel::setRole,
        onLeaveHousehold = viewModel::leaveHousehold,
        onDeleteHousehold = viewModel::deleteHousehold,
        // Collected here, inside the tab, so the history is read only while it is open.
        historyTab = {
            HistoryTab(viewModel.history.collectAsStateWithLifecycle().value, viewModel.weekTally.collectAsStateWithLifecycle().value)
        },
    )
}

@Composable
internal fun HouseholdScreen(
    uiState: HouseholdUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: ChoreActions,
    onNewChore: (dueDay: Long?) -> Unit,
    onOnlyMine: (Boolean) -> Unit,
    onRenameHousehold: (String) -> Unit,
    onShareCode: (Household) -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onSetRole: (memberId: String, role: Role) -> Unit,
    onLeaveHousehold: () -> Unit,
    onDeleteHousehold: () -> Unit,
    historyTab: @Composable () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf(HouseholdDialog.None) }
    var tab by rememberSaveable { mutableStateOf(HouseholdTab.Today) }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    var memberToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = uiState as? HouseholdUiState.Ready
    val closeDialog = { dialog = HouseholdDialog.None }

    val title = ready?.household?.name.orEmpty()
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        // The open sheet covers this host, so it shows its own copy of the snackbar instead.
        snackbarHost = { if (!showMembers) SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (ready != null) {
                            IconButton(onClick = { showMembers = true }) {
                                AppIcon(AppIcons.Group, stringResource(R.string.feature_chores_members))
                            }
                            HouseholdMenu(isOwner = ready.isOwner, onSelect = { dialog = it })
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    HouseholdTab.entries.forEach { entry ->
                        Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(stringResource(entry.label)) })
                    }
                }
            }
        },
        floatingActionButton = {
            // The green of every other main action, not Material's pad yellow. The empty page has its own button.
            PopIn(ready != null && tab != HouseholdTab.History && ready.chores.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onNewChore(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_chores_new_chore)) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .ruledPaper(MaterialTheme.colorScheme.primary, rememberPaperScroll())
                .padding(padding)
                .pageWidth(),
        ) {
            LoadingCrossfade(ready == null) {
                if (ready == null) return@LoadingCrossfade
                // The tabs lie side by side, so the next one comes in from its side.
                val slide = !rememberReducedMotion()
                AnimatedContent(tab, transitionSpec = { pageChange(forward = targetState > initialState, slide) }, label = "tab") { shown ->
                    Box(Modifier.fillMaxSize()) {
                        when (shown) {
                            HouseholdTab.Today -> TodayTab(ready, actions, onOnlyMine, onNewChore = { onNewChore(null) })
                            HouseholdTab.Calendar -> CalendarTab(ready, actions.onOpen, onNewChore)
                            HouseholdTab.History -> historyTab()
                        }
                    }
                }
            }
        }
    }

    if (ready != null && showMembers) {
        MembersSheet(
            state = ready,
            snackbarHostState = snackbarHostState,
            onDismiss = { showMembers = false },
            onShareCode = { onShareCode(ready.household) },
            onResetCode = { dialog = HouseholdDialog.ResetCode },
            onInviteEmail = onInviteEmail,
            onCancelEmailInvite = onCancelEmailInvite,
            onRemoveMember = { memberToRemove = it },
            onSetRole = onSetRole,
        )
    }

    when (dialog) {
        HouseholdDialog.None -> Unit
        HouseholdDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.feature_chores_rename_household),
            label = stringResource(R.string.feature_chores_household_name),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialValue = ready?.household?.name.orEmpty(),
            onConfirm = {
                onRenameHousehold(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.Leave -> ConfirmDialog(
            title = stringResource(R.string.feature_chores_leave_household),
            text = stringResource(R.string.feature_chores_leave_confirm_text),
            confirmLabel = stringResource(R.string.feature_chores_leave),
            onConfirm = {
                closeDialog()
                onLeaveHousehold()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_chores_delete_household),
            text = stringResource(R.string.feature_chores_delete_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onDeleteHousehold()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.ResetCode -> ConfirmDialog(
            title = stringResource(R.string.feature_chores_reset_code_confirm_title),
            text = stringResource(R.string.feature_chores_reset_code_confirm_text),
            confirmLabel = stringResource(R.string.feature_chores_reset_code),
            onConfirm = {
                closeDialog()
                onResetCode()
            },
            onDismiss = closeDialog,
        )
    }

    val removing = ready?.household?.members?.firstOrNull { it.id == memberToRemove }
    if (removing != null) {
        ConfirmDialog(
            title = stringResource(R.string.feature_chores_remove_member, removing.name),
            text = stringResource(R.string.feature_chores_remove_member_confirm_text, removing.name),
            confirmLabel = stringResource(R.string.feature_chores_remove),
            onConfirm = {
                memberToRemove = null
                onRemoveMember(removing.id)
            },
            onDismiss = { memberToRemove = null },
        )
    }
}

@Composable
private fun HouseholdMenu(isOwner: Boolean, onSelect: (HouseholdDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: HouseholdDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isOwner) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chores_rename_household)) },
                    leadingIcon = { AppIcon(AppIcons.Edit, null) },
                    onClick = { select(HouseholdDialog.Rename) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chores_delete_household)) },
                    leadingIcon = { AppIcon(AppIcons.Delete, null) },
                    colors = destructiveMenuItemColors(),
                    onClick = { select(HouseholdDialog.Delete) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chores_leave_household)) },
                    leadingIcon = { AppIcon(AppIcons.Logout, null) },
                    onClick = { select(HouseholdDialog.Leave) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MembersSheet(
    state: HouseholdUiState.Ready,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onShareCode: () -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onSetRole: (memberId: String, role: Role) -> Unit,
) {
    val household = state.household
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Box {
            Column(
                Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.feature_chores_members_title))
                if (state.isOwner && household.members.size > 1) Hint(stringResource(R.string.feature_chores_roles_hint))
                household.members.forEach { member ->
                    MemberRow(
                        member = member,
                        isYou = member.id == state.currentUserId,
                        isHouseholdOwner = member.id == household.ownerId,
                        // Only the owner changes roles and removes people, never their own (enforced by the security rules as well).
                        onSetRole = if (state.isOwner && member.id != household.ownerId) {
                            { onSetRole(member.id, if (member.role == Role.ADULT) Role.KID else Role.ADULT) }
                        } else {
                            null
                        },
                        onRemove = if (state.isOwner && member.id != household.ownerId) {
                            { onRemoveMember(member.id) }
                        } else {
                            null
                        },
                    )
                }

                HorizontalDivider()
                SectionTitle(stringResource(R.string.feature_chores_invite_code_title))
                Hint(stringResource(R.string.feature_chores_invite_code_hint))
                SelectionContainer {
                    Text(
                        household.inviteCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    )
                }
                Hint(stringResource(R.string.feature_chores_qr_hint))
                QrCode(InviteCode.link(household.inviteCode), Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                // Wraps, so both buttons fit at a large font.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onShareCode) {
                        AppIcon(AppIcons.Share, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.feature_chores_share_code))
                    }
                    if (state.isOwner) {
                        TextButton(onClick = onResetCode) { Text(stringResource(R.string.feature_chores_reset_code)) }
                    }
                }

                HorizontalDivider()
                SectionTitle(stringResource(R.string.feature_chores_invite_by_email_title))
                EmailInviteField(onInviteEmail)
                household.invitedEmails.forEach { email ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(AppIcons.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            email,
                            Modifier.weight(1f).padding(horizontal = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onCancelEmailInvite(email) }) {
                            AppIcon(AppIcons.Close, stringResource(R.string.feature_chores_cancel_invite, email))
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Black on white whatever the theme, because that is what cameras read best. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    // Size 0 asks for the smallest code: one cell per module plus the quiet border.
    val matrix = remember(content) { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) }
    val description = stringResource(R.string.feature_chores_qr_description)
    Canvas(modifier.background(Color.White).semantics { contentDescription = description }) {
        val cell = size.minDimension / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                // Half a pixel wider, so rounding leaves no hairlines between dark cells.
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun EmailInviteField(onInviteEmail: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var invited by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when Send is tried with a bad address: the field then explains itself until the address is fixed.
    var showError by rememberSaveable { mutableStateOf(false) }
    val isValid = normalizeEmail(email) != null
    val send = {
        if (isValid) {
            onInviteEmail(email)
            invited = normalizeEmail(email)
            email = ""
        }
        showError = !isValid
    }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it.take(254) },
        label = { Text(stringResource(R.string.feature_chores_email_label)) },
        isError = showError && !isValid,
        supportingText = {
            val hint = stringResource(R.string.feature_chores_email_hint)
            Text(
                when {
                    showError && !isValid -> stringResource(R.string.feature_chores_email_invalid)
                    invited != null -> stringResource(R.string.feature_chores_invited, invited.orEmpty()) + " " + hint
                    else -> hint
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = isValid) {
                AppIcon(AppIcons.Send, stringResource(R.string.feature_chores_send_invite))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MemberRow(member: Member, isYou: Boolean, isHouseholdOwner: Boolean, onSetRole: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InitialAvatar(member.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (isYou) stringResource(R.string.feature_chores_you, member.name) else member.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Hint(
                listOfNotNull(
                    stringResource(member.role.label),
                    if (isHouseholdOwner) stringResource(R.string.feature_chores_owner) else null,
                ).joinToString(" · "),
            )
        }
        if (onSetRole != null) {
            val other = if (member.role == Role.ADULT) Role.KID else Role.ADULT
            TextButton(onClick = onSetRole) {
                Text(stringResource(R.string.feature_chores_make_role, stringResource(other.label)))
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                AppIcon(AppIcons.Close, stringResource(R.string.feature_chores_remove_member, member.name))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HouseholdScreenPreview() {
    val today = 20_783L
    ChoresAnythingTheme {
        HouseholdScreen(
            uiState = HouseholdUiState.Ready(
                household = Household(
                    id = "1",
                    name = "Family",
                    ownerId = "u1",
                    inviteCode = "ABCD2345",
                    members = listOf(Member("u1", "Panos"), Member("u2", "Maria"), Member("u3", "Nikos", Role.KID)),
                    invitedEmails = emptyList(),
                ),
                chores = listOf(
                    Chore("c1", "1", "Dishes", minutes = 15, dueDay = today, dueMinute = 20 * 60, repeat = Repeat.DAILY, createdById = "u1", createdByName = "Panos"),
                    Chore("c2", "1", "Supermarket", needs = listOf("bags", "the list"), assigneeId = "u2", assigneeName = "Maria", dueDay = today - 1, createdById = "u1", createdByName = "Panos"),
                    Chore("c3", "1", "Clean your room", assigneeId = "u3", assigneeName = "Nikos", dueDay = today + 2, createdById = "u2", createdByName = "Maria"),
                ),
                currentUserId = "u1",
                today = today,
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            actions = ChoreActions({}, {}, {}, {}, {}, {}),
            onNewChore = {},
            onOnlyMine = {},
            onRenameHousehold = {},
            onShareCode = {},
            onResetCode = {},
            onInviteEmail = {},
            onCancelEmailInvite = {},
            onRemoveMember = {},
            onSetRole = { _, _ -> },
            onLeaveHousehold = {},
            onDeleteHousehold = {},
        )
    }
}
