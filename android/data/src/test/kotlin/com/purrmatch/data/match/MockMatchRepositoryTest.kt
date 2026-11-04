package com.purrmatch.data.match

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.backend.dto.ErrorDto
import com.purrmatch.data.backend.dto.FailureRuleDto
import com.purrmatch.domain.match.Coordinates
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.ListingPhoto
import com.purrmatch.domain.match.RequestFailedException
import com.purrmatch.domain.match.SearchFilters
import com.purrmatch.domain.match.UserSelection
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MockMatchRepositoryTest {
    @Test fun loadBringsOnlyTheUsersOwnProfileAndSelectionNotTheCatalogue() = runBlocking {
        val repository = MockMatchRepository(MockBackend(latencyMillis = 0))
        assertTrue(repository.data.value.profiles.isEmpty() && repository.data.value.listings.isEmpty())
        repository.load("anna").getOrThrow()
        val data = repository.data.value
        assertEquals(listOf("anna"), data.profiles.map { it.id })
        assertTrue(data.listings.isEmpty())
        assertEquals(listOf("mia", "peach", "sonya"), data.selection("anna").favorites)
        assertEquals(setOf("anna"), data.selections.keys)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun loadTakesTwoRequestsOfSimulatedLatency() = runTest {
        val backend = MockBackend(latencyMillis = 350, ioDispatcher = StandardTestDispatcher(testScheduler))
        val repository = MockMatchRepository(backend)
        val load = async { repository.load("anna") }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        load.await().getOrThrow()
        assertEquals("anna", repository.data.value.profile("anna")?.id)
    }

    @Test fun screensCacheOnlyWhatTheyRequestAndKeepUnavailableFavorites() = runBlocking {
        val repository = MockMatchRepository(MockBackend(latencyMillis = 0))
        repository.load("anna").getOrThrow()
        val favorites = repository.getFavorites("anna").getOrThrow()
        assertEquals(listOf("mia", "peach", "sonya"), favorites.map { it.id })
        assertFalse(favorites.last().available)
        assertEquals(favorites, repository.data.value.listings)
        val olga = repository.getAuthorListings("olga").getOrThrow()
        assertTrue(olga.isNotEmpty() && olga.all { it.authorId == "olga" && it.available })
        assertEquals(favorites.size + olga.size, repository.data.value.listings.size) // no duplicates, no catalogue
        repository.getAuthorListings("olga").getOrThrow()
        assertEquals(favorites.size + olga.size, repository.data.value.listings.size)
        assertEquals("anna", repository.getProfile("anna").getOrThrow().id)
        repository.remember(Listing("shown", "Показанная"))
        assertEquals("Показанная", repository.data.value.listing("shown")?.name)
        assertEquals(404, (repository.getFavorites("nobody").exceptionOrNull() as RequestFailedException).status)
    }

    @Test fun mutationsMapToDomainCacheAndRemainPerAccount() = runBlocking {
        val repository = MockMatchRepository(MockBackend(latencyMillis = 0))
        assertTrue(repository.getListings("anna", SearchFilters(city = "")).getOrThrow().isEmpty())
        assertFalse(repository.getListing("sonya").getOrThrow().available)
        val alice = repository.register("alice", "alice-password", "Alice").getOrThrow()
        val bob = repository.register("bob", "bob-password", "Bob").getOrThrow()
        val edited = repository.getProfile(alice).getOrThrow().copy(city = "Казань", surname = "Иванова",
            coordinates = Coordinates(55.8, 49.1), whatsapp = "+79991234567")
        repository.upsertProfile(edited).getOrThrow()
        val published = Listing("alice-cat", "Кот", authorId = bob, birthday = LocalDate.of(2025, 3, 4),
            price = 1000, photos = listOf(ListingPhoto("local:alice")), coordinates = Coordinates(55.8, 49.1))
        repository.publish(alice, published).getOrThrow()
        repository.swipe(alice, "mia", true).getOrThrow()
        repository.swipe(alice, "mia", true).getOrThrow()
        repository.applyFilters(alice, SearchFilters(breed = "Сибирская")).getOrThrow()
        assertEquals(edited, repository.data.value.profile(alice))
        assertNull(repository.data.value.profile(bob)) // never requested, so never cached
        assertEquals(published.copy(authorId = alice), repository.data.value.listing("alice-cat"))
        assertEquals(listOf("mia"), repository.data.value.selection(alice).favorites)
        assertEquals(listOf("mia"), repository.data.value.selection(alice).seen)
        assertEquals("Сибирская", repository.data.value.selection(alice).filters.breed)
        assertEquals(UserSelection(), repository.data.value.selection(bob))
        assertTrue(repository.getListings(alice).getOrThrow().none { it.id == "mia" || !it.available })
        assertTrue(repository.getListings(bob).getOrThrow().any { it.id == "mia" })
        repository.toggleFavorite(bob, "alice-cat").getOrThrow()
        val beforeRejected = repository.data.value
        val denied = repository.delete(bob, "alice-cat").exceptionOrNull() as RequestFailedException
        assertEquals(403, denied.status)
        assertEquals("NOT_OWNER", denied.code)
        assertEquals(beforeRejected, repository.data.value)
        repository.delete(alice, "alice-cat").getOrThrow()
        assertFalse(repository.data.value.listing("alice-cat")!!.available)
        assertEquals(listOf("alice-cat"), repository.data.value.selection(bob).favorites)
    }

    @Test fun recreatingBackendResetsEveryMutation() = runBlocking {
        val originalBackend = MockBackend(latencyMillis = 0)
        val original = MockMatchRepository(originalBackend)
        original.load("anna").getOrThrow()
        val fixture = originalBackend.snapshot()
        original.upsertProfile(original.getProfile("anna").getOrThrow().copy(city = "Казань")).getOrThrow()
        original.toggleFavorite("anna", "mia").getOrThrow()
        original.swipe("anna", "peach", false).getOrThrow()
        original.applyFilters("anna", SearchFilters(city = "Казань")).getOrThrow()
        original.publish("anna", Listing("new-cat", "Кот")).getOrThrow()
        original.delete("anna", "mia").getOrThrow()
        original.register("new-user", "new-password", "New").getOrThrow()
        assertNotEquals(fixture, originalBackend.snapshot())
        val recreatedBackend = MockBackend(latencyMillis = 0)
        MockMatchRepository(recreatedBackend).load("anna").getOrThrow()
        assertEquals(fixture, recreatedBackend.snapshot())
    }

    @Test fun loadFailureKeepsStatusAndLeavesCacheEmptyUntilRetrySucceeds() = runBlocking {
        val backend = MockBackend(latencyMillis = 0, failureRules = listOf(
            FailureRuleDto("GET", "/users/anna/selection", 500, ErrorDto("INTERNAL_ERROR", "Unavailable")),
        ))
        val repository = MockMatchRepository(backend)
        val failure = repository.load("anna").exceptionOrNull() as RequestFailedException
        assertEquals(500, failure.status)
        assertEquals("INTERNAL_ERROR", failure.code)
        assertTrue(repository.data.value.selections.isEmpty())
        assertTrue(repository.data.value.listings.isEmpty())
        assertTrue(repository.load("anna").isFailure)
        assertEquals("anna", repository.getProfile("anna").getOrThrow().id)
    }
}
