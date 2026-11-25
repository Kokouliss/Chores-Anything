package com.kokouliss.choresanything.feature.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.ChoreGroup
import com.kokouliss.choresanything.core.ui.component.AppIcon
import com.kokouliss.choresanything.core.ui.component.AppIcons
import com.kokouliss.choresanything.core.ui.component.Doodle
import com.kokouliss.choresanything.core.ui.component.EmptyState

/** The chores of the household by group: what waits for an answer first, then what is due, then the rest. */
@Composable
internal fun TodayTab(state: HouseholdUiState.Ready, actions: ChoreActions, onOnlyMine: (Boolean) -> Unit, onNewChore: () -> Unit) {
    if (state.chores.isEmpty()) {
        EmptyState(
            icon = AppIcons.Checklist,
            title = stringResource(R.string.feature_chores_no_chores_title),
            body = stringResource(R.string.feature_chores_no_chores_body),
            doodle = Doodle.Broom,
        ) {
            Button(onClick = onNewChore) { Text(stringResource(R.string.feature_chores_new_chore)) }
        }
        return
    }
    val groups = state.groups
    LazyColumn(
        // Bottom room so the last card is not hidden behind the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.household.members.size > 1) {
            item(key = "mine") {
                FilterChip(
                    selected = state.onlyMine,
                    onClick = { onOnlyMine(!state.onlyMine) },
                    label = { Text(stringResource(R.string.feature_chores_only_mine)) },
                    leadingIcon = if (state.onlyMine) { { AppIcon(AppIcons.Check, null) } } else null,
                )
            }
        }
        if (groups.isEmpty()) {
            item(key = "none") { Hint(stringResource(R.string.feature_chores_nothing_for_you), Modifier.padding(vertical = 24.dp)) }
        }
        groups.forEach { (group, chores) ->
            item(key = "group-$group") {
                SectionTitle(
                    pluralStringResource(R.plurals.feature_chores_group_count, chores.size, stringResource(group.label), chores.size),
                    Modifier.padding(top = 8.dp),
                )
            }
            items(chores, key = { it.id }) { chore ->
                ChoreCard(
                    chore = chore,
                    today = state.today,
                    modifier = Modifier.animateItem(),
                    onOpen = actions.onOpen,
                    onDone = actions.onDone.takeIf { group != ChoreGroup.DONE && !chore.isPending },
                ) {
                    when (group) {
                        ChoreGroup.NEEDS_OK -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.End)) {
                            TextButton(onClick = { actions.onDecline(chore) }) { Text(stringResource(R.string.feature_chores_decline)) }
                            Button(onClick = { actions.onConfirm(chore) }) { Text(stringResource(R.string.feature_chores_confirm)) }
                        }
                        ChoreGroup.PASSED_TO_YOU -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.End)) {
                            TextButton(onClick = { actions.onRefuse(chore) }) { Text(stringResource(R.string.feature_chores_refuse)) }
                            Button(onClick = { actions.onTake(chore) }) { Text(stringResource(R.string.feature_chores_take)) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * One chore as a card: a circle to tick it off ([onDone], when given), its title and its details,
 * and [extra] buttons under them. A tap opens it.
 */
@Composable
internal fun ChoreCard(
    chore: Chore,
    today: Long,
    modifier: Modifier = Modifier,
    onOpen: (Chore) -> Unit,
    onDone: ((Chore) -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    ElevatedCard(onClick = { onOpen(chore) }, modifier = modifier.fillMaxWidth().semantics { role = Role.Button }) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                onDone != null -> IconButton(onClick = { onDone(chore) }) {
                    AppIcon(AppIcons.Circle, stringResource(R.string.feature_chores_mark_done, chore.title))
                }
                chore.isDone -> AppIcon(AppIcons.CheckCircle, null, Modifier.padding(12.dp), MaterialTheme.colorScheme.primary)
                else -> AppIcon(AppIcons.Schedule, null, Modifier.padding(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (chore.isDone) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val overdue = !chore.isDone && (chore.dueDay ?: Long.MAX_VALUE) < today
                Text(
                    details(chore, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        extra()
    }
}

/** "Today 20:00 · Maria · 15 min · Every day", with only the parts the chore has. */
@Composable
private fun details(chore: Chore, today: Long): String {
    val context = LocalContext.current
    val handoff = chore.handoff
    val who = when {
        handoff != null -> stringResource(R.string.feature_chores_passed_to, handoff.toName)
        chore.isDone -> stringResource(R.string.feature_chores_done_by, chore.doneByName.orEmpty())
        chore.assigneeName != null -> chore.assigneeName
        else -> stringResource(R.string.feature_chores_anyone)
    }
    return listOfNotNull(
        chore.dueDay?.let { day -> dayText(day, today) + (chore.dueMinute?.let { " " + context.formatTime(it) } ?: "") },
        who,
        chore.minutes?.let { minutesText(it) },
        chore.repeat?.let { stringResource(it.label) },
    ).joinToString(" · ")
}
