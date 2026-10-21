package com.purrmatch.data.session

import com.purrmatch.domain.session.SessionRepository
import com.purrmatch.domain.session.SessionState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional local session storage; passwords are never written here. */
class LocalSessionRepository(
    private val file: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val mutableSession = MutableStateFlow<SessionState>(SessionState.Loading)
    override val session = mutableSession.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                val userId = file?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
                    ?.takeIf { it.isNotBlank() }
                mutableSession.value = userId?.let { SessionState.SignedIn(it) } ?: SessionState.Guest
                ready.complete(Unit)
            }
        }
    }

    internal suspend fun awaitReady() = ready.await()

    override suspend fun signIn(userId: String, onboarding: Boolean): Result<Unit> = update(SessionState.SignedIn(userId, onboarding)) {
        file?.writeText(userId)
    }

    override suspend fun signOut(): Result<Unit> = update(SessionState.Guest) {
        file?.takeIf { it.exists() }?.let { if (!it.delete()) throw IllegalStateException("Cannot clear session") }
    }

    private suspend fun update(next: SessionState, write: () -> Unit): Result<Unit> = withContext(ioDispatcher) {
        ready.await()
        mutex.withLock {
            runCatching {
                write()
                mutableSession.value = next
            }
        }
    }
}
