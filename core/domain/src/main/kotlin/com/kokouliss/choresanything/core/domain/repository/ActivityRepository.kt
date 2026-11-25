package com.kokouliss.choresanything.core.domain.repository

import com.kokouliss.choresanything.core.domain.model.Activity
import kotlinx.coroutines.flow.Flow

/**
 * The history of a household: who did, added, passed or confirmed which chore, and when. The
 * chore repository writes it as things happen; entries go by themselves after `ACTIVITY_KEEP_DAYS`.
 */
interface ActivityRepository {
    /** The newest entries first, at most [ACTIVITY_SHOWN] of them. */
    fun observeActivity(householdId: String): Flow<List<Activity>>

    companion object {
        /** Each visit reads at most this many entries: about a year of a busy family. */
        const val ACTIVITY_SHOWN = 1000L
    }
}
