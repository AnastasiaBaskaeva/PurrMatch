package com.purrmatch.data.session

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.backend.dto.SessionRequestDto
import com.purrmatch.data.backend.toResult
import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Client session for the shared mock server. Nothing, including user IDs, is persisted to disk. */
class MockSessionRepository(private val backend: MockBackend = MockBackend()) : SessionRepository {
    private val mutex = Mutex()
    private val mutableSession = MutableStateFlow<SessionState>(SessionState.Guest)
    override val session = mutableSession.asStateFlow()

    override suspend fun signIn(userId: String, onboarding: Boolean): Result<Unit> = mutex.withLock {
        backend.signIn(SessionRequestDto(userId)).toResult().map {
            mutableSession.value = SessionState.SignedIn(checkNotNull(it.userId), onboarding)
        }
    }

    override suspend fun signOut(): Result<Unit> = mutex.withLock {
        backend.signOut().toResult().map { mutableSession.value = SessionState.Guest }
    }
}
