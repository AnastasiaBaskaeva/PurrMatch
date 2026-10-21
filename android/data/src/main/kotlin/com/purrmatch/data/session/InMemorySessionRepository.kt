package com.purrmatch.data.session

import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Navigation scaffold only: no credentials, network or persisted authentication. */
class InMemorySessionRepository : SessionRepository {
    private val mutableSession = MutableStateFlow(SessionState.Guest)
    override val session = mutableSession.asStateFlow()

    override fun enterDemoSession() {
        mutableSession.value = SessionState.Demo
    }

    override fun signOut() {
        mutableSession.value = SessionState.Guest
    }
}
