package com.purrmatch.data.match

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.session.MockSessionRepository
import com.purrmatch.domain.match.InvalidCredentialsException
import com.purrmatch.domain.match.LoginOccupiedException
import com.purrmatch.domain.match.RequestFailedException
import com.purrmatch.domain.session.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthenticationTest {
    @Test fun credentialsAreTrimmedCaseSensitiveAndDoNotCreateUnknownAccounts() = runBlocking {
        val repository = MockMatchRepository(MockBackend(latencyMillis = 0))
        repository.load("anna").getOrThrow()
        val before = repository.data.value
        assertTrue(repository.authenticate("unknown", "password123").exceptionOrNull() is InvalidCredentialsException)
        assertTrue(repository.authenticate("anna_cats", "wrong-password").exceptionOrNull() is InvalidCredentialsException)
        assertTrue(repository.authenticate("ANNA_CATS", "purrmatch-demo").exceptionOrNull() is InvalidCredentialsException)
        assertEquals(before, repository.data.value)
        assertEquals("anna", repository.authenticate(" anna_cats ", "purrmatch-demo").getOrThrow())
        assertTrue(repository.register(" anna_cats ", "password123", "Other").exceptionOrNull() is LoginOccupiedException)
    }

    @Test fun repositoryPreservesServerStatusAndAllowsRetry() = runBlocking {
        val repository = MockMatchRepository(MockBackend(latencyMillis = 0))
        repository.load("anna").getOrThrow()
        val before = repository.data.value
        val loginFailure = repository.authenticate("server_error", "password123").exceptionOrNull() as RequestFailedException
        assertEquals(500, loginFailure.status)
        assertEquals("INTERNAL_ERROR", loginFailure.code)
        val registerFailure = repository.register("server_error", "password123", "Name").exceptionOrNull() as RequestFailedException
        assertEquals(500, registerFailure.status)
        assertEquals("INTERNAL_ERROR", registerFailure.code)
        assertEquals(before, repository.data.value)
        assertEquals("anna", repository.authenticate("anna_cats", "purrmatch-demo").getOrThrow())
    }

    @Test fun registrationAndSessionShareBackendButRecreationStartsAsGuest() = runBlocking {
        val backend = MockBackend(latencyMillis = 0)
        val repository = MockMatchRepository(backend)
        val sessions = MockSessionRepository(backend)
        assertEquals(SessionState.Guest, sessions.session.value)
        val id = repository.register(" alice ", "alice-password", " Alice ").getOrThrow()
        assertNull(repository.data.value.profile(id)) // the cache only learns about the account on load
        repository.load(id).getOrThrow()
        assertEquals("Alice", repository.data.value.profile(id)!!.name)
        assertEquals("alice", repository.data.value.profile(id)!!.login)
        assertEquals(id, repository.authenticate("alice", "alice-password").getOrThrow())
        sessions.signIn(id, onboarding = true).getOrThrow()
        assertEquals(SessionState.SignedIn(id, onboarding = true), sessions.session.value)
        assertTrue(sessions.signIn("missing").isFailure)
        assertEquals(SessionState.SignedIn(id, onboarding = true), sessions.session.value)
        sessions.signOut().getOrThrow()
        assertEquals(SessionState.Guest, sessions.session.value)
        sessions.signIn(id).getOrThrow()

        val recreatedBackend = MockBackend(latencyMillis = 0)
        val recreatedRepository = MockMatchRepository(recreatedBackend)
        val recreatedSessions = MockSessionRepository(recreatedBackend)
        assertEquals(SessionState.Guest, recreatedSessions.session.value)
        assertTrue(recreatedRepository.authenticate("alice", "alice-password").exceptionOrNull() is InvalidCredentialsException)
        assertEquals(404, (recreatedRepository.load(id).exceptionOrNull() as RequestFailedException).status)
        assertNull(recreatedRepository.data.value.profile(id))
        assertTrue(recreatedSessions.signIn(id).isFailure)
        assertEquals(SessionState.Guest, recreatedSessions.session.value)
    }
}
