package com.purrmatch.data.match

import com.purrmatch.data.session.LocalSessionRepository
import com.purrmatch.domain.match.BrowseListings
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.domain.match.Coordinates
import com.purrmatch.domain.match.VerificationStatus
import java.time.LocalDate
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
                repository.saveProfile(repository.data.value.profile("maria")!!.copy(city = "Казань",
                    surname = "Иванова", coordinates = Coordinates(55.8, 49.1), whatsapp = "+79991234567",
                    verification = VerificationStatus.PENDING, catteryName = "Луна"))
                repository.publish("maria", Listing("luna", "Луна", birthday = LocalDate.of(2025, 3, 4),
                    price = 1000, photos = listOf(ListingPhoto("local:luna"))))
                repository.toggleFavorite("maria", "mia")
            }
            val restored = MockMatchRepository(file)
            runBlocking { restored.awaitReady() }
            assertEquals("Мария", restored.data.value.profile("maria")!!.name)
            assertEquals("Казань", restored.data.value.profile("maria")!!.city)
            assertEquals("Иванова", restored.data.value.profile("maria")!!.surname)
            assertEquals(Coordinates(55.8, 49.1), restored.data.value.profile("maria")!!.coordinates)
            assertEquals("+79991234567", restored.data.value.profile("maria")!!.whatsapp)
            assertEquals(VerificationStatus.PENDING, restored.data.value.profile("maria")!!.verification)
            assertEquals("maria", restored.data.value.listing("luna")!!.authorId)
            assertEquals(listOf(ListingPhoto("local:luna")), restored.data.value.listing("luna")!!.photos)
            assertEquals(LocalDate.of(2025, 3, 4), restored.data.value.listing("luna")!!.birthday)
            assertEquals(1000L, restored.data.value.listing("luna")!!.price)
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

    @Test fun originalSnapshotMigratesAndSurvivesAnotherRestart() {
        val directory = Files.createTempDirectory("purrmatch-legacy").toFile()
        try {
            val file = File(directory, "matches")
            javaClass.getResourceAsStream("/matches-v1.bin")!!.use { input ->
                file.outputStream().use(input::copyTo)
            }
            val repository = MockMatchRepository(file)
            runBlocking { repository.awaitReady() }
            val data = readMatchSnapshot(file).data
            assertEquals(repository.data.value, data)
            assertEquals(LocalDate.of(2024, 5, 14), data.listing("mia")!!.birthday)
            assertEquals(5000L, data.listing("peach")!!.price)
            assertEquals(listOf("sample:upload", "picked:2"), data.listing("mia")!!.photos.map { it.reference })
            assertEquals(listOf("mia", "peach", "sonya"), data.selection("anna").favorites)
            assertEquals("@anna_cats", data.profile("anna")!!.telegram)
            assertTrue(data.profile("anna")!!.whatsapp.isEmpty())
            assertFalse(data.listing("sonya")!!.available)
            runBlocking { repository.swipe("reader", "mia", true).getOrThrow() }
            val restored = MockMatchRepository(file)
            runBlocking { restored.awaitReady() }
            assertEquals(repository.data.value, restored.data.value)
        } finally { directory.deleteRecursively() }
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
