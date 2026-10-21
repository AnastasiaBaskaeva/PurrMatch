package com.purrmatch.data.match

import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.*
import com.purrmatch.domain.session.SessionState
import java.io.*
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthenticationTest {
    @Test fun unknownLoginAndWrongPasswordNeverCreateAccounts() = runBlocking {
        val repository = MockMatchRepository()
        assertTrue(repository.authenticate("unknown", "password123").exceptionOrNull() is InvalidCredentialsException)
        assertTrue(repository.authenticate("anna_cats", "wrong").exceptionOrNull() is InvalidCredentialsException)
        assertEquals("anna", repository.authenticate(" anna_cats ", "purrmatch-demo").getOrThrow())
        assertEquals(2, repository.data.value.profiles.size)
        assertTrue(repository.register(" anna_cats ", "password123", "Other").exceptionOrNull() is LoginOccupiedException)
    }

    @Test fun registrationIsSerializedAndAccountsAreIndependentAfterRestart() = runBlocking {
        val dir = Files.createTempDirectory("auth").toFile()
        try {
            val file = File(dir, "matches")
            val repository = MockMatchRepository(file)
            val results = List(5) { async(Dispatchers.Default) { repository.register("alice", "alice-pass", "Alice") } }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is LoginOccupiedException })
            val alice = results.first { it.isSuccess }.getOrThrow()
            val bob = repository.register("bob", "bob-pass", "Bob").getOrThrow()
            repository.swipe(alice, "mia", true).getOrThrow()
            repository.saveProfile(repository.data.value.profile(alice)!!.copy(city = "Казань")).getOrThrow()
            repository.publish(alice, Listing("alice-cat", "Cat", photos = listOf(ListingPhoto("local:alice")))).getOrThrow()
            val restored = MockMatchRepository(file)
            // Deliberately do not await initialization: authentication and registration must wait themselves.
            assertTrue(restored.register("alice", "other-pass", "Other").exceptionOrNull() is LoginOccupiedException)
            assertEquals(alice, restored.authenticate("alice", "alice-pass").getOrThrow())
            assertEquals(bob, restored.authenticate("bob", "bob-pass").getOrThrow())
            assertTrue(restored.authenticate("bob", "alice-pass").isFailure)
            assertEquals("Казань", restored.data.value.profile(alice)!!.city)
            assertEquals(alice, restored.data.value.listing("alice-cat")!!.authorId)
            assertEquals(listOf("mia"), restored.data.value.selection(alice).seen)
            assertEquals(listOf("mia"), restored.data.value.selection(alice).favorites)
            assertEquals(UserSelection(), restored.data.value.selection(bob))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("alice-pass"))
            val sessionFile = File(dir, "session")
            val sessions = LocalSessionRepository(sessionFile)
            sessions.awaitReady()
            assertEquals(SessionState.Guest, sessions.session.value)
            sessions.signIn(alice, onboarding = true).getOrThrow()
            val next = LocalSessionRepository(sessionFile)
            next.awaitReady()
            assertEquals(SessionState.SignedIn(alice), next.session.value)
            next.signOut().getOrThrow()
            val signedOut = LocalSessionRepository(sessionFile)
            signedOut.awaitReady()
            assertEquals(SessionState.Guest, signedOut.session.value)
        } finally { dir.deleteRecursively() }
    }

    @Test fun legacyDataKeepsProfilesListingsFavoritesAndHistory() = runBlocking {
        val dir = Files.createTempDirectory("legacy-auth").toFile()
        try {
            val file = File(dir, "matches")
            val original = sampleMatchData().let { it.copy(
                profiles = it.profiles + Profile(id = "old", login = "old-login", name = "Old"),
                selections = it.selections + ("old" to UserSelection(listOf("mia"), listOf("peach"))),
            ) }
            ObjectOutputStream(file.outputStream()).use { it.writeObject(original) }
            val repository = MockMatchRepository(file)
            assertEquals("old", repository.authenticate("old-login", "purrmatch-demo").getOrThrow())
            assertTrue(repository.authenticate("old-login", "old-password").isFailure)
            assertEquals(original, repository.data.value)
            repository.register("new", "new-password", "New").getOrThrow()
            val restored = MockMatchRepository(file)
            assertEquals("old", restored.authenticate("old-login", "purrmatch-demo").getOrThrow())
            assertEquals(original.listings, restored.data.value.listings)
            assertEquals(original.selections, restored.data.value.selections)
            assertEquals(original.profiles, restored.data.value.profiles.take(original.profiles.size))
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedWriteDoesNotCreateAccountAndCanBeRetried() = runBlocking {
        val dir = Files.createTempDirectory("failed-auth").toFile()
        try {
            val file = File(dir, "matches")
            val obstruction = File(dir, "matches.tmp").also { it.mkdir() }
            val repository = MockMatchRepository(file)
            assertTrue(repository.register("new", "new-password", "New").isFailure)
            assertNull(repository.data.value.profiles.find { it.login == "new" })
            assertTrue(repository.authenticate("new", "new-password").isFailure)
            obstruction.delete()
            val id = repository.register("new", "new-password", "New").getOrThrow()
            assertEquals(id, MockMatchRepository(file).authenticate("new", "new-password").getOrThrow())
            val sessionFile = File(dir, "session")
            val session = LocalSessionRepository(sessionFile)
            session.signIn(id).getOrThrow()
            File(dir, "session.tmp").mkdir()
            assertTrue(session.signIn("other").isFailure)
            assertEquals(SessionState.SignedIn(id), session.session.value)
            assertEquals(id, sessionFile.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun unreadableDataIsNeverOverwrittenWithFreshFixtures() = runBlocking {
        val dir = Files.createTempDirectory("broken-auth").toFile()
        try {
            val file = File(dir, "matches").also { it.writeText("broken") }
            val repository = MockMatchRepository(file)
            assertTrue(repository.authenticate("anna_cats", "purrmatch-demo").isFailure)
            assertTrue(repository.register("new", "new-password", "New").isFailure)
            assertEquals("broken", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedInitialReadCanRecoverInSameRepositoryWithoutReplacingAccountData() = runBlocking {
        val dir = Files.createTempDirectory("retry-auth").toFile()
        try {
            val file = File(dir, "matches")
            val writer = MockMatchRepository(file)
            val id = writer.register("retry", "retry-password", "Retry").getOrThrow()
            writer.swipe(id, "mia", true).getOrThrow()
            writer.saveProfile(writer.data.value.profile(id)!!.copy(city = "Казань")).getOrThrow()
            writer.publish(id, Listing("retry-cat", "Cat", photos = listOf(ListingPhoto("local:retry")))).getOrThrow()
            val expected = writer.data.value
            val snapshot = file.readBytes()
            file.writeText("temporarily unreadable")
            val repository = MockMatchRepository(file)
            assertTrue(repository.authenticate("retry", "retry-password").isFailure)
            file.delete()
            assertTrue(repository.authenticate("anna_cats", "purrmatch-demo").isFailure)
            assertTrue(repository.register("other", "other-password", "Other").isFailure)
            assertTrue(repository.toggleFavorite(id, "peach").isFailure)
            assertFalse(file.exists())
            file.writeBytes(snapshot)
            assertEquals(id, repository.authenticate("retry", "retry-password").getOrThrow())
            assertEquals(expected, repository.data.value)
            repository.toggleFavorite(id, "peach").getOrThrow()
            val restored = MockMatchRepository(file)
            assertEquals(id, restored.authenticate("retry", "retry-password").getOrThrow())
            assertEquals(repository.data.value, restored.data.value)
        } finally { dir.deleteRecursively() }
    }

    @Test fun passwordVerifierUsesRandomSaltAndSurvivesSerialization() {
        fun bytes(verifier: PasswordVerifier) = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(verifier) }
        }.toByteArray()
        val first = PasswordVerifier.create("пароль-🐈")
        val second = PasswordVerifier.create("пароль-🐈")
        assertTrue(first.matches("пароль-🐈"))
        assertFalse(first.matches("пароль-🐕"))
        assertFalse(first.matches(""))
        assertFalse(bytes(first).contentEquals(bytes(second)))
        val restored = ObjectInputStream(ByteArrayInputStream(bytes(first))).use { it.readObject() as PasswordVerifier }
        assertTrue(restored.matches("пароль-🐈"))
    }
}
