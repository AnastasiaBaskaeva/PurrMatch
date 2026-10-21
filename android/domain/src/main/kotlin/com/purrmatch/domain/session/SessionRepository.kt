package com.purrmatch.domain.session

import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {
    data object Loading : SessionState
    data object Guest : SessionState
    data class SignedIn(val userId: String, val onboarding: Boolean = false) : SessionState
}

/** Local authentication boundary; server authentication will replace signIn. */
interface SessionRepository {
    val session: StateFlow<SessionState>
    suspend fun signIn(userId: String, onboarding: Boolean = false): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
