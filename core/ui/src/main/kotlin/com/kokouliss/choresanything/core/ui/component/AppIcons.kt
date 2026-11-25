package com.kokouliss.choresanything.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.kokouliss.choresanything.core.ui.R

/** Material Symbols (outlined) shipped as vector drawables in this module. */
object AppIcons {
    val Add = R.drawable.core_ui_ic_add
    val Alarm = R.drawable.core_ui_ic_alarm
    val ArrowBack = R.drawable.core_ui_ic_arrow_back
    val CalendarMonth = R.drawable.core_ui_ic_calendar_month
    val Check = R.drawable.core_ui_ic_check
    val CheckCircle = R.drawable.core_ui_ic_check_circle
    val Checklist = R.drawable.core_ui_ic_checklist
    val ChevronLeft = R.drawable.core_ui_ic_chevron_left
    val ChevronRight = R.drawable.core_ui_ic_chevron_right
    val Circle = R.drawable.core_ui_ic_radio_button_unchecked
    val Close = R.drawable.core_ui_ic_close
    val CloudOff = R.drawable.core_ui_ic_cloud_off
    val ContentCopy = R.drawable.core_ui_ic_content_copy
    val Delete = R.drawable.core_ui_ic_delete
    val Edit = R.drawable.core_ui_ic_edit
    val Event = R.drawable.core_ui_ic_event
    val Group = R.drawable.core_ui_ic_group
    val GroupAdd = R.drawable.core_ui_ic_group_add
    val History = R.drawable.core_ui_ic_history
    val Home = R.drawable.core_ui_ic_home
    val Info = R.drawable.core_ui_ic_info
    val Inventory = R.drawable.core_ui_ic_inventory_2
    val Logout = R.drawable.core_ui_ic_logout
    val Mail = R.drawable.core_ui_ic_mail
    val MoreVert = R.drawable.core_ui_ic_more_vert
    val Notifications = R.drawable.core_ui_ic_notifications
    val Person = R.drawable.core_ui_ic_person
    val PersonAdd = R.drawable.core_ui_ic_person_add
    val QrCodeScanner = R.drawable.core_ui_ic_qr_code_scanner
    val Refresh = R.drawable.core_ui_ic_refresh
    val Repeat = R.drawable.core_ui_ic_repeat
    val Schedule = R.drawable.core_ui_ic_schedule
    val Send = R.drawable.core_ui_ic_send
    val Settings = R.drawable.core_ui_ic_settings
    val Share = R.drawable.core_ui_ic_share
    val SwapHoriz = R.drawable.core_ui_ic_swap_horiz
    val ThumbUp = R.drawable.core_ui_ic_thumb_up
    val Timer = R.drawable.core_ui_ic_timer
    val Undo = R.drawable.core_ui_ic_undo
}

/** Pass null as [contentDescription] only when the icon sits next to text that says the same. */
@Composable
fun AppIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = modifier, tint = tint)
}
