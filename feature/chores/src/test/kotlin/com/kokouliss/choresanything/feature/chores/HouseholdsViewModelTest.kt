package com.kokouliss.choresanything.feature.chores

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.choresanything.core.domain.model.JoinResult
import com.kokouliss.choresanything.core.domain.model.Member
import com.kokouliss.choresanything.core.domain.model.Role
import com.kokouliss.choresanything.core.domain.model.Household
import com.kokouliss.choresanything.core.domain.model.User
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.EmailAuthResult
import com.kokouliss.choresanything.core.domain.repository.HouseholdRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class HouseholdsViewModelTest {

    private val family = Household("s1", "Family", "u1", "ABCD2345", listOf(Member("u1", "Panos")), emptyList())
    private val flatmates = family.copy(id = "s2", name = "Flatmates")
    private val households = FakeHouseholdRepository()
    private val auth = FakeAuthRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the user's households and invitations`() = runTest {
        households.households.value = listOf(family)
        households.invitations.value = listOf(flatmates)

        HouseholdsViewModel(households, auth).uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(
                HouseholdsUiState.Ready(
                    userName = "Panos",
                    households = listOf(family),
                    invitations = listOf(flatmates),
                    join = JoinState.Idle,
                ),
            )
        }
    }

    @Test
    fun `a join result stays until the screen has shown it`() = runTest {
        households.joinResult = JoinResult.InvalidCode
        val viewModel = HouseholdsViewModel(households, auth)

        viewModel.uiState.test {
            viewModel.joinWithCode("WRONG")
            assertThat((expectMostRecentItem() as HouseholdsUiState.Ready).join)
                .isEqualTo(JoinState.Done(JoinResult.InvalidCode))

            viewModel.joinShown()
            assertThat((expectMostRecentItem() as HouseholdsUiState.Ready).join).isEqualTo(JoinState.Idle)
        }
    }

    @Test
    fun `a declined invitation goes at once and is written after keep`() = runTest {
        households.invitations.value = listOf(flatmates)
        val viewModel = HouseholdsViewModel(households, auth)

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.declineInvitation("s2")
            assertThat((expectMostRecentItem() as HouseholdsUiState.Ready).invitations).isEmpty()
            assertThat(households.declined).isEmpty()

            viewModel.declines.keep(viewModel.declines.pending.value!!.token)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(households.declined).containsExactly("s2")
    }

    @Test
    fun `a failing stream shows the error state and retry recovers`() = runTest {
        households.failHouseholds = true
        val viewModel = HouseholdsViewModel(households, auth)

        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(HouseholdsUiState.Error)

            households.failHouseholds = false
            viewModel.retry()

            assertThat(expectMostRecentItem()).isInstanceOf(HouseholdsUiState.Ready::class.java)
        }
    }
}

private class FakeAuthRepository : AuthRepository {
    override val currentUser: Flow<User?> = flowOf(User("u1", "Panos", "panos@example.com"))
    override suspend fun signInWithGoogle(idToken: String) = true
    override suspend fun registerDevice() = Unit
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(idToken: String) = true
    override suspend fun signInWithEmail(email: String, password: String) = EmailAuthResult.Success
    override suspend fun createAccount(name: String, email: String, password: String) = EmailAuthResult.Success
    override suspend fun sendPasswordReset(email: String) = EmailAuthResult.Success
    override suspend fun sendVerificationEmail() = true
    override suspend fun refreshEmailVerification() = Unit
    override suspend fun deleteAccountWithPassword(password: String) = true
    override val asksForName: Flow<Boolean> = flowOf(false)
    override suspend fun setName(name: String) = true
    override fun keepName() = Unit
}

private class FakeHouseholdRepository : HouseholdRepository {
    val households = MutableStateFlow(emptyList<Household>())
    val invitations = MutableStateFlow(emptyList<Household>())
    var failHouseholds = false
    var joinResult = JoinResult.Joined
    val declined = mutableListOf<String>()

    override fun observeHouseholds(): Flow<List<Household>> = flow {
        if (failHouseholds) throw IOException("permission denied")
        emitAll(households)
    }

    override fun observeInvitations(): Flow<List<Household>> = invitations
    override fun observeHousehold(householdId: String): Flow<Household?> = flowOf(null)
    override suspend fun joinWithCode(code: String) = joinResult

    override suspend fun createHousehold(name: String) = Unit
    override suspend fun renameHousehold(householdId: String, name: String) = Unit
    override suspend fun acceptInvitation(householdId: String) = Unit
    override suspend fun declineInvitation(householdId: String) {
        declined += householdId
    }
    override suspend fun inviteEmail(householdId: String, email: String) = Unit
    override suspend fun cancelEmailInvite(householdId: String, email: String) = Unit
    override suspend fun removeMember(household: Household, memberId: String) = Unit
    override suspend fun setRole(householdId: String, memberId: String, role: Role) = Unit
    override suspend fun resetInviteCode(household: Household) = Unit
    override suspend fun leaveHousehold(householdId: String) = Unit
    override suspend fun deleteHousehold(household: Household) = Unit
}
