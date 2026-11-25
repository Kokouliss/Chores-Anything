package com.kokouliss.choresanything.feature.chores

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kokouliss.choresanything.core.domain.model.Activity
import com.kokouliss.choresanything.core.domain.model.ActivityKind
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.ui.component.AppIcons
import com.kokouliss.choresanything.core.ui.component.Doodle
import com.kokouliss.choresanything.core.ui.component.EmptyState
import com.kokouliss.choresanything.core.ui.component.InitialAvatar
import com.kokouliss.choresanything.core.ui.component.LoadingBox
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme

/** The household's history, newest first, under who did how much this week. Null while it is still loading. */
@Composable
internal fun HistoryTab(lines: List<Activity>?, tally: List<Tally>?, now: Long = System.currentTimeMillis()) {
    when {
        lines == null -> LoadingBox()
        lines.isEmpty() -> EmptyState(
            icon = AppIcons.History,
            title = stringResource(R.string.feature_chores_history_empty_title),
            body = stringResource(R.string.feature_chores_history_empty_body),
            doodle = Doodle.Cup,
        )
        // Cards like the chores of the other tabs, so the ruled paper never runs through a line.
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tally != null && tally.size > 1) item(key = "week") { WeekCard(tally) }
            items(lines, key = { it.id }) { ElevatedCard(Modifier.animateItem().fillMaxWidth()) { HistoryRow(it, now) } }
        }
    }
}

/** Who did how many chores since Monday, and how long they took. Yellow, like a note pinned on the fridge. */
@Composable
private fun WeekCard(tally: List<Tally>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.feature_chores_this_week))
            tally.forEach { (member, done, minutes) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                    InitialAvatar(member.name, size = 32.dp)
                    Text(member.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            pluralStringResource(R.plurals.feature_chores_chores_done, done, done),
                            if (minutes > 0) minutesText(minutes) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(line: Activity, now: Long) {
    // shortcut: "5 minutes ago" is worked out when the tab is drawn, not on a timer. A new line redraws it.
    val time = if (now - line.atMillis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.feature_chores_history_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(line.atMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { InitialAvatar(line.byName) },
        headlineContent = { Text(sentence(line)) },
        supportingContent = {
            Text(
                listOfNotNull(time, line.minutes?.takeIf { line.kind == ActivityKind.DONE }?.let { minutesText(it) }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // One line for a screen reader: who did what, then when.
        modifier = Modifier.semantics(mergeDescendants = true) { },
    )
}

@Composable
private fun sentence(line: Activity): String {
    val text = when (line.kind) {
        ActivityKind.ADDED -> R.string.feature_chores_history_added
        ActivityKind.ASKED -> R.string.feature_chores_history_asked
        ActivityKind.CONFIRMED -> R.string.feature_chores_history_confirmed
        ActivityKind.DECLINED -> R.string.feature_chores_history_declined
        ActivityKind.CHANGED -> R.string.feature_chores_history_changed
        ActivityKind.DELETED -> R.string.feature_chores_history_deleted
        ActivityKind.DONE -> R.string.feature_chores_history_done
        ActivityKind.UNDONE -> R.string.feature_chores_history_undone
        ActivityKind.PASSED -> R.string.feature_chores_history_passed
        ActivityKind.TOOK -> R.string.feature_chores_history_took
        ActivityKind.REFUSED -> R.string.feature_chores_history_refused
    }
    return stringResource(text, line.byName, line.choreTitle, line.forName.orEmpty())
}

@PreviewLightDark
@Composable
private fun HistoryTabPreview() {
    val now = System.currentTimeMillis()
    ChoresAnythingTheme {
        HistoryTab(
            lines = listOf(
                Activity("a1", ActivityKind.DONE, "u2", "Maria", "c1", "Dishes", now - 120_000, minutes = 15),
                Activity("a2", ActivityKind.PASSED, "u1", "Panos", "c2", "Supermarket", now - 3_600_000, forId = "u2", forName = "Maria"),
                Activity("a3", ActivityKind.ASKED, "u3", "Nikos", "c3", "Drive me to the cinema", now - 86_400_000),
            ),
            tally = listOf(Tally(Member("u2", "Maria"), 3, 45), Tally(Member("u1", "Panos"), 1, 20)),
            now = now,
        )
    }
}
