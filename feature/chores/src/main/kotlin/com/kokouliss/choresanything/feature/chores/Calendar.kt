package com.kokouliss.choresanything.feature.chores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kokouliss.choresanything.core.domain.model.Chore
import com.kokouliss.choresanything.core.domain.model.onDays
import com.kokouliss.choresanything.core.ui.component.AppIcon
import com.kokouliss.choresanything.core.ui.component.AppIcons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** A month of the household with a dot per chore, and the chores of the chosen day under it. */
@Composable
internal fun CalendarTab(state: HouseholdUiState.Ready, onOpenChore: (Chore) -> Unit, onNewChore: (dueDay: Long?) -> Unit) {
    val today = state.today
    var monthStart by rememberSaveable { mutableLongStateOf(LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()) }
    var selected by rememberSaveable { mutableLongStateOf(today) }
    val month = LocalDate.ofEpochDay(monthStart)
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val lead = (month.dayOfWeek.value - firstDay.value + 7) % 7
    val length = month.lengthOfMonth()
    val byDay = remember(state.chores, monthStart) { state.chores.onDays(monthStart, monthStart + length - 1) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthStart = month.minusMonths(1).toEpochDay() }) {
                AppIcon(AppIcons.ChevronLeft, stringResource(R.string.feature_chores_previous_month))
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                Modifier.weight(1f).semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { monthStart = month.plusMonths(1).toEpochDay() }) {
                AppIcon(AppIcons.ChevronRight, stringResource(R.string.feature_chores_next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                val day = DayOfWeek.of((firstDay.value - 1 + i) % 7 + 1)
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val rows = (lead + length + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column - lead
                    if (index in 0 until length) {
                        val day = monthStart + index
                        DayCell(
                            number = index + 1,
                            chores = byDay[day].orEmpty(),
                            isToday = day == today,
                            isSelected = day == selected,
                            onClick = { selected = day },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(dayText(selected, today), Modifier.weight(1f))
            TextButton(onClick = { onNewChore(selected) }) {
                AppIcon(AppIcons.Add, null, Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.feature_chores_add))
            }
        }
        val chores = byDay[selected].orEmpty()
        if (chores.isEmpty()) {
            Hint(stringResource(R.string.feature_chores_nothing_planned), Modifier.padding(vertical = 8.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chores.forEach { ChoreCard(it, today, onOpen = onOpenChore, onDone = null) }
            }
        }
    }
}

/** One day of the month: its number and up to three dots, one per chore. A finished chore's dot is faint. */
@Composable
private fun RowScope.DayCell(number: Int, chores: List<Chore>, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = pluralStringResource(R.plurals.feature_chores_day_description, chores.size, number, chores.size)
    Column(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else colors.surface.copy(alpha = 0f))
            .then(if (isToday && !isSelected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(number.toString(), color = if (isSelected) colors.onPrimary else colors.onSurface, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            chores.take(3).forEach { chore ->
                val dot = when {
                    chore.isDone -> colors.outline
                    isSelected -> colors.onPrimary
                    else -> colors.primary
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
            }
        }
    }
}
