package com.purrmatch.presentation

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.match.MockMatchRepository
import com.purrmatch.data.session.MockSessionRepository
import kotlinx.coroutines.runBlocking

/**
 * Both repositories share one in-memory server, including newly registered accounts.
 * [userId] signs in an account from the fixture; [newUser] registers a fresh account with no listings,
 * favorites or swipes and signs it in. Either way the signed-in id is [user].
 */
internal class MockRepositories(fixtureName: String = "default", userId: String? = null, newUser: String? = null) {
    val backend = MockBackend(fixtureName = fixtureName, latencyMillis = 0)
    val matches = MockMatchRepository(backend)
    val sessions = MockSessionRepository(backend)
    val user: String?

    init {
        user = runBlocking {
            val id = newUser?.let { matches.register(it, "password123", it).getOrThrow() } ?: userId
            id?.let {
                matches.load(it).getOrThrow()
                sessions.signIn(it).getOrThrow()
            }
            id
        }
    }
}

/** Screens fill in after their first request, so tests wait for the text instead of asserting at once. */
internal fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 10_000) =
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
