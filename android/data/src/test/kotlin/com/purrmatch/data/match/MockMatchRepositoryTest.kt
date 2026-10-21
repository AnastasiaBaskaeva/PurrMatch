package com.purrmatch.data.match

import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.BrowseListings
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.domain.session.SessionState
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MockMatchRepositoryTest {
    @Test fun changesAndSessionSurviveNewRepositoryInstances() {
        val directory = Files.createTempDirectory("purrmatch").toFile()
        try {
            val file = File(directory, "matches")
            val repository = MockMatchRepository(file)
            runBlocking {
                repository.ensureProfile("maria", "maria_cats", "Мария")
                repository.saveProfile(repository.data.value.profile("maria")!!.copy(city = "Казань"))
                repository.publish("maria", Listing("luna", "Луна"), listOf(-1, 2))
                repository.toggleFavorite("maria", "mia")
            }
            val restored = MockMatchRepository(file)
            runBlocking { restored.awaitReady() }
            assertEquals("Мария", restored.data.value.profile("maria")!!.name)
            assertEquals("Казань", restored.data.value.profile("maria")!!.city)
            assertEquals("maria", restored.data.value.listing("luna")!!.authorId)
            assertEquals(listOf(-1, 2), restored.data.value.photoSlots["luna"])
            assertEquals(listOf("mia"), restored.data.value.selection("maria").favorites)
            val sessionFile = File(directory, "session")
            val session = LocalSessionRepository(sessionFile)
            runBlocking { session.signIn("maria") }
            val restoredSession = LocalSessionRepository(sessionFile)
            runBlocking { restoredSession.awaitReady() }
            assertEquals(SessionState.SignedIn("maria"), restoredSession.session.value)
            runBlocking { restoredSession.signOut() }
            assertEquals(SessionState.Guest, restoredSession.session.value)
        } finally { directory.deleteRecursively() }
    }

    @Test fun applyingFiltersDoesNotReshowSwipedCardOrChangeOtherAccount() {
        val repository = MockMatchRepository()
        runBlocking {
            repository.swipe("maria", "mia", true)
            repository.swipe("maria", "mia", true)
            repository.applyFilters("maria", SearchFilters(breed = "Сибирская"))
            repository.applyFilters("maria", SearchFilters())
        }
        assertEquals("peach", BrowseListings()(repository.data.value, "maria")!!.id)
        assertEquals(listOf("mia"), repository.data.value.selection("maria").seen)
        assertEquals(listOf("mia"), repository.data.value.selection("maria").favorites)
        assertTrue(repository.data.value.selection("other").favorites.isEmpty())
        assertEquals("mia", BrowseListings()(repository.data.value, "other")!!.id)
    }

    @Test fun deletingRequiresOwnerAndKeepsOthersFavorites() {
        val repository = MockMatchRepository()
        runBlocking {
            repository.toggleFavorite("maria", "mia")
            repository.delete("maria", "mia")
        }
        assertTrue(repository.data.value.listing("mia")!!.available)
        runBlocking { repository.delete("anna", "mia") }
        assertFalse(repository.data.value.listing("mia")!!.available)
        assertEquals(listOf("mia"), repository.data.value.selection("maria").favorites)
        assertNull(repository.data.value.listing("missing"))
        assertNotEquals(repository.data.value.listing("mia")!!.authorId, repository.data.value.listing("barsik")!!.authorId)
    }

    @Test fun failedWriteReturnsErrorAndKeepsInMemoryStateForRetry() {
        val directory = Files.createTempDirectory("purrmatch").toFile()
        try {
            val target = File(directory, "matches")
            File(directory, "matches.tmp").mkdir()
            val repository = MockMatchRepository(target)
            val result = runBlocking { repository.toggleFavorite("maria", "mia") }
            assertTrue(result.isFailure)
            assertTrue(repository.data.value.selection("maria").favorites.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
