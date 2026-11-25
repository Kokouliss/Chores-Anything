package com.kokouliss.choresanything

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kokouliss.choresanything.core.data.isBackendConfigured
import com.kokouliss.choresanything.core.domain.model.InviteCode
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme
import com.kokouliss.choresanything.feature.chores.ChoreKey
import dagger.hilt.android.AndroidEntryPoint

/** What the app was opened with, besides its icon. Each part is handled once and then cleared. */
internal data class Launch(
    /** The code of a tapped or scanned invite link. */
    val inviteCode: String? = null,
    /** The chore a tapped notification is about. */
    val chore: ChoreKey? = null,
)

/** The extras on a notification about one chore: firebase/functions puts them on its, this app on its reminders. */
internal const val EXTRA_HOUSEHOLD_ID = "householdId"
internal const val EXTRA_CHORE_ID = "choreId"

// Everything in an intent comes from outside the app. It is only ever parsed into a code or used to
// open a chore, which the security rules guard.
private fun Intent.toLaunch() = Launch(
    inviteCode = if (action == Intent.ACTION_VIEW) dataString?.let(InviteCode::fromLink) else null,
    chore = getStringExtra(EXTRA_HOUSEHOLD_ID)?.let { householdId ->
        getStringExtra(EXTRA_CHORE_ID)?.let { ChoreKey(householdId, it) }
    },
)

// AppCompatActivity, because it is what applies the language chosen on the profile page on Android 9 to 12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var launch by mutableStateOf(Launch())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after a rotation: by then the screen it asked for is already open.
        if (savedInstanceState == null) launch = intent.toLaunch()
        // Checked before any screen asks Hilt for Firebase, which would crash without a configuration.
        val isConfigured = isBackendConfigured(this)
        setContent {
            ChoresAnythingTheme {
                if (isConfigured) {
                    ChoresAnythingApp(launch = launch, onLaunchHandled = { launch = Launch() })
                } else {
                    SetupNeededScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch = intent.toLaunch()
    }
}
