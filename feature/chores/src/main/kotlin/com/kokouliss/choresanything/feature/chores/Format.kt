package com.kokouliss.choresanything.feature.chores

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kokouliss.choresanything.core.domain.model.ChoreGroup
import com.kokouliss.choresanything.core.domain.model.Repeat
import com.kokouliss.choresanything.core.domain.model.Role
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** "Today", "Tomorrow", "Yesterday", or a short date with its weekday. [day] and [today] count like `Chore.dueDay`. */
@Composable
internal fun dayText(day: Long, today: Long): String = when (day) {
    today -> stringResource(R.string.feature_chores_today)
    today + 1 -> stringResource(R.string.feature_chores_tomorrow)
    today - 1 -> stringResource(R.string.feature_chores_yesterday)
    else -> LocalContext.current.formatDay(day)
}

internal fun Context.formatDay(day: Long): String {
    val millis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val thisYear = LocalDate.ofEpochDay(day).year == LocalDate.now().year
    var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
    if (thisYear) flags = flags or DateUtils.FORMAT_NO_YEAR
    return DateUtils.formatDateTime(this, millis, flags)
}

/** The time of day [minute] minutes after midnight, in the phone's format. */
internal fun Context.formatTime(minute: Int): String {
    val millis = LocalDate.now().atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_TIME)
}

/** "25 min" or "1 h 30 min". */
@Composable
internal fun minutesText(minutes: Int): String = if (minutes < 60) {
    stringResource(R.string.feature_chores_minutes, minutes)
} else if (minutes % 60 == 0) {
    stringResource(R.string.feature_chores_hours, minutes / 60)
} else {
    stringResource(R.string.feature_chores_hours_minutes, minutes / 60, minutes % 60)
}

internal val Repeat.label: Int
    get() = when (this) {
        Repeat.DAILY -> R.string.feature_chores_repeat_daily
        Repeat.WEEKLY -> R.string.feature_chores_repeat_weekly
        Repeat.FORTNIGHTLY -> R.string.feature_chores_repeat_fortnightly
        Repeat.MONTHLY -> R.string.feature_chores_repeat_monthly
    }

internal val ChoreGroup.label: Int
    get() = when (this) {
        ChoreGroup.NEEDS_OK -> R.string.feature_chores_group_needs_ok
        ChoreGroup.WAITING_OK -> R.string.feature_chores_group_waiting_ok
        ChoreGroup.PASSED_TO_YOU -> R.string.feature_chores_group_passed
        ChoreGroup.OVERDUE -> R.string.feature_chores_group_overdue
        ChoreGroup.TODAY -> R.string.feature_chores_today
        ChoreGroup.SOON -> R.string.feature_chores_group_soon
        ChoreGroup.LATER -> R.string.feature_chores_group_later
        ChoreGroup.WHENEVER -> R.string.feature_chores_group_whenever
        ChoreGroup.DONE -> R.string.feature_chores_group_done
    }

internal val Role.label: Int
    get() = when (this) {
        Role.ADULT -> R.string.feature_chores_role_adult
        Role.KID -> R.string.feature_chores_role_kid
    }

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
