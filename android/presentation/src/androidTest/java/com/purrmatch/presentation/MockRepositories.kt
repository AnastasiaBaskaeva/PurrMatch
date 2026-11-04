package com.purrmatch.presentation

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.MockSessionRepository
import kotlinx.coroutines.runBlocking

/** Both repositories share one in-memory server, including newly registered accounts. */
internal class MockRepositories(fixtureName: String = "default", userId: String? = null) {
    val backend = MockBackend(fixtureName = fixtureName, latencyMillis = 0)
    val matches = MockMatchRepository(backend)
    val sessions = MockSessionRepository(backend)

    init {
        runBlocking {
            userId?.let {
                matches.load(it).getOrThrow()
                sessions.signIn(it).getOrThrow()
            }
        }
    }
}
