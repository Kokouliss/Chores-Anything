package com.kokouliss.choresanything

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.ui.component.AppIcons
import com.kokouliss.choresanything.core.ui.component.EmptyState
import com.kokouliss.choresanything.core.ui.component.LoadingBox
import com.kokouliss.choresanything.core.ui.theme.ChoresAnythingTheme
import com.kokouliss.choresanything.core.ui.theme.pageChange
import com.kokouliss.choresanything.core.ui.theme.rememberReducedMotion
import com.kokouliss.choresanything.feature.auth.FirstNamePrompt
import com.kokouliss.choresanything.feature.auth.ProfileKey
import com.kokouliss.choresanything.feature.auth.SignInRoute
import com.kokouliss.choresanything.feature.auth.profileEntries
import com.kokouliss.choresanything.feature.chores.HouseholdKey
import com.kokouliss.choresanything.feature.chores.HouseholdsKey
import com.kokouliss.choresanything.feature.chores.choresEntries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class AuthState { Loading, SignedOut, SignedIn }

@HiltViewModel
internal class AppViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.currentUser
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    init {
        // On every start and after every sign-in, because the address notifications go to can change.
        viewModelScope.launch {
            authRepository.currentUser.filterNotNull().collect { authRepository.registerDevice() }
        }
    }

    /** Each time the app comes back, for example from the mail app after tapping the confirmation link. */
    fun onAppStart() {
        viewModelScope.launch { authRepository.refreshEmailVerification() }
    }
}

@Composable
internal fun ChoresAnythingApp(launch: Launch, onLaunchHandled: () -> Unit, viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.onAppStart()
        onStopOrDispose {}
    }
    when (authState) {
        AuthState.Loading -> Surface { LoadingBox() }
        AuthState.SignedOut -> SignInRoute()
        // Until then [launch] waits, so an invite link also works for someone who signs in first.
        AuthState.SignedIn -> {
            SignedInNavigation(launch, onLaunchHandled)
            FirstNamePrompt()
        }
    }
}

@Composable
private fun SignedInNavigation(launch: Launch, onLaunchHandled: () -> Unit) {
    val backStack = rememberNavBackStack(HouseholdsKey)
    val context = LocalContext.current
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    // A screen that closes itself because its chore or household is gone says so on the screen below it.
    var notice by remember { mutableStateOf<String?>(null) }
    val onGone = { message: String ->
        goBack()
        notice = message
    }

    // Everyone in a household hears about its chores, so the question comes right after sign-in.
    // Android shows it at most twice; after an answer it shows nothing.
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // A tapped notification opens its chore, with the household behind it so that back lands there.
    LaunchedEffect(launch) {
        val chore = launch.chore ?: return@LaunchedEffect
        val household = HouseholdKey(chore.householdId)
        if (household !in backStack) backStack.add(household)
        if (chore !in backStack) backStack.add(chore)
        onLaunchHandled()
    }

    // Deeper comes in from the right, back from the left, also under a back swipe.
    val slide = !rememberReducedMotion()
    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        transitionSpec = { pageChange(forward = true, slide) },
        popTransitionSpec = { pageChange(forward = false, slide) },
        predictivePopTransitionSpec = { pageChange(forward = false, slide) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            choresEntries(
                onOpenHousehold = { householdId -> backStack.add(HouseholdKey(householdId)) },
                onOpenChore = { backStack.add(it) },
                onEditChore = { backStack.add(it) },
                onOpenProfile = { backStack.add(ProfileKey) },
                onBack = goBack,
                onGone = onGone,
                notice = notice,
                onNoticeShown = { notice = null },
                inviteCode = launch.inviteCode,
                onInviteHandled = onLaunchHandled,
            )
            profileEntries(onBack = goBack)
        },
    )
}

/** Shown by builds made without `app/google-services.json`, instead of crashing on the first Firebase call. */
@Composable
internal fun SetupNeededScreen() {
    Surface {
        EmptyState(
            icon = AppIcons.Refresh,
            title = stringResource(R.string.setup_needed_title),
            body = stringResource(R.string.setup_needed_body),
        )
    }
}

@PreviewLightDark
@Composable
private fun SetupNeededScreenPreview() {
    ChoresAnythingTheme { SetupNeededScreen() }
}
