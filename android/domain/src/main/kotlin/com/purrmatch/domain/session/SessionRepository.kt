package com.purrmatch.domain.session

import kotlinx.coroutines.flow.StateFlow

enum class SessionState { Guest, Demo }

/** Temporary session contract. Real authentication will replace the demo operation. */
interface SessionRepository {
    val session: StateFlow<SessionState>
    fun enterDemoSession()
    fun signOut()
}
