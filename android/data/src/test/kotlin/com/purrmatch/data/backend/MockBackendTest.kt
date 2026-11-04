package com.purrmatch.data.backend

import com.purrmatch.data.backend.dto.*
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockBackendTest {
    @Test fun authenticationUsesHttpStatusesAndDeterministicFailures() = runTest {
        val backend = MockBackend(latencyMillis = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        val registered = backend.register(RegisterRequestDto(" alice ", "alice-password", " Alice "))
        assertEquals(201, registered.status)
        assertNull(registered.error)
        val id = registered.body!!.userId
        assertEquals("alice", backend.getProfile(id).body!!.login)
        assertEquals("Alice", backend.getProfile(id).body!!.name)
        assertEquals(id, backend.authenticate(LoginRequestDto(" alice ", "alice-password")).body!!.userId)
        assertError(backend.authenticate(LoginRequestDto("ALICE", "alice-password")), 401, "INVALID_CREDENTIALS")
        assertError(backend.authenticate(LoginRequestDto("alice", "wrong-password")), 401, "INVALID_CREDENTIALS")
        assertError(backend.register(RegisterRequestDto("alice", "another-password", "Other")), 409, "LOGIN_OCCUPIED")
        assertError(backend.register(RegisterRequestDto("", "short", "")), 422, "VALIDATION_ERROR")
        repeat(2) {
            assertError(backend.authenticate(LoginRequestDto(" server_error ", "password123")), 500, "INTERNAL_ERROR")
            assertError(backend.register(RegisterRequestDto("server_error", "password123", "Name")), 500, "INTERNAL_ERROR")
        }
        assertEquals("anna", backend.authenticate(LoginRequestDto("anna_cats", "purrmatch-demo")).body!!.userId)
        assertEquals(200, backend.signIn(SessionRequestDto(id)).status)
        assertEquals(id, backend.signIn(SessionRequestDto(id)).body!!.userId)
        assertError(backend.signIn(SessionRequestDto("missing")), 404, "PROFILE_NOT_FOUND")
        assertNull(backend.signOut().body!!.userId)
    }

    @Test fun profileUpsertValidatesIdentityAndCoordinates() = runTest {
        val backend = MockBackend(latencyMillis = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        val original = backend.getProfile("anna").body!!
        val updated = original.copy(city = "Казань", coordinates = CoordinatesDto(55.8, 49.1), whatsapp = "+79991234567")
        assertEquals(updated, backend.upsertProfile(updated).body)
        assertEquals(updated, backend.getProfile("anna").body)
        assertError(backend.upsertProfile(updated.copy(login = "changed")), 422, "VALIDATION_ERROR")
        assertError(backend.upsertProfile(updated.copy(name = "")), 422, "VALIDATION_ERROR")
        assertError(backend.upsertProfile(updated.copy(coordinates = CoordinatesDto(91.0, 0.0))), 422, "VALIDATION_ERROR")
        assertError(backend.upsertProfile(updated.copy(coordinates = CoordinatesDto(0.0, Double.NaN))), 422, "VALIDATION_ERROR")
        assertError(backend.upsertProfile(ProfileDto(id = "new", name = "Name", login = "anna_cats")), 409, "LOGIN_OCCUPIED")
        val inserted = ProfileDto(id = "new", name = "Name", login = "unique_login")
        assertEquals(inserted, backend.upsertProfile(inserted).body)
        assertEquals(inserted, backend.getProfile("new").body)
        assertError(backend.getProfile("missing"), 404, "PROFILE_NOT_FOUND")
        assertEquals(updated, backend.getProfile("anna").body)
    }

    @Test fun publishingAndDeletionPreserveOwnerAndUnavailableFavoriteReferences() = runTest {
        val backend = MockBackend(latencyMillis = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        val input = ListingDto("new-cat", "Кот", authorId = "olga", available = false, birthday = "2024-05-14")
        val published = backend.publish("anna", input)
        assertEquals(201, published.status)
        assertEquals(input.copy(authorId = "anna", available = true), published.body)
        assertError(backend.publish("anna", input), 409, "LISTING_EXISTS")
        assertError(backend.publish("missing", input.copy(id = "another")), 404, "PROFILE_NOT_FOUND")
        assertError(backend.publish("anna", input.copy(id = "bad-price", price = -1)), 422, "VALIDATION_ERROR")
        assertError(backend.publish("anna", input.copy(id = "bad-date", birthday = "2024-02-30")), 422, "VALIDATION_ERROR")
        assertError(backend.publish("anna", input.copy(id = "future", birthday = LocalDate.now().plusDays(1).toString())), 422, "VALIDATION_ERROR")
        assertError(backend.publish("anna", input.copy(id = "blank", name = "")), 422, "VALIDATION_ERROR")
        backend.toggleFavorite("new-cat", FavoriteRequestDto("olga"))
        assertError(backend.delete("olga", "new-cat"), 403, "NOT_OWNER")
        assertTrue(backend.getListing("new-cat").body!!.available)
        val deleted = backend.delete("anna", "new-cat")
        assertEquals(204, deleted.status)
        assertEquals(Unit, deleted.body)
        assertNull(deleted.error)
        assertFalse(backend.getListing("new-cat").body!!.available)
        assertTrue(backend.getSelections().body!!.getValue("olga").favorites.contains("new-cat"))
        assertTrue(backend.getListings().body!!.none { it.id == "new-cat" })
        assertError(backend.swipe("new-cat", SwipeRequestDto("olga", true)), 409, "LISTING_UNAVAILABLE")
        assertError(backend.getListing("missing"), 404, "LISTING_NOT_FOUND")
        assertError(backend.delete("anna", "missing"), 404, "LISTING_NOT_FOUND")
    }

    @Test fun browsingFiltersSeenAndUnavailableSemanticsAreIndependent() = runTest {
        val backend = MockBackend(latencyMillis = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        val all = backend.getListings(includeUnavailable = true).body!!
        assertEquals(28, all.size)
        assertTrue(backend.getListings().body!!.all { it.available })
        assertFalse(backend.getListing("sonya").body!!.available)
        val filters = FiltersDto(breed = "Британская короткошёрстная", sex = "кошка", color = "серый",
            eyes = "янтарные", city = "Москва", maxPrice = 0, freeOnly = true)
        assertEquals(listOf("mia"), backend.getListings("olga", filters).body!!.map { it.id })
        backend.swipe("mia", SwipeRequestDto("olga", false))
        assertTrue(backend.getListings("olga", filters).body!!.isEmpty())
        assertEquals(listOf("mia"), backend.getListings("anna", filters).body!!.map { it.id })
        assertTrue(backend.getListings(filters = FiltersDto(freeOnly = false)).body!!.all { it.price > 0 })
        assertTrue(backend.getListings(filters = FiltersDto(maxPrice = 1000)).body!!.all { it.price <= 1000 })
        val dated = backend.getListings(filters = FiltersDto(minAge = 0, maxAge = 100)).body!!
        assertTrue(dated.none { it.birthday == null })
        val ageListing = ListingDto("age-test", "Кот", birthday = LocalDate.now().minusYears(3).toString())
        backend.publish("anna", ageListing)
        assertTrue(backend.getListings(filters = FiltersDto(minAge = 3, maxAge = 3)).body!!.any { it.id == "age-test" })
        assertTrue(backend.getListings(filters = FiltersDto(minAge = 4)).body!!.none { it.id == "age-test" })
        val saved = FiltersDto(city = "Казань", freeOnly = true)
        assertEquals(saved, backend.applyFilters("olga", saved).body!!.filters)
        assertTrue(backend.getListings("olga", saved).body!!.all { it.city == "Казань" && it.price == 0L })
        assertError(backend.getListings("missing"), 404, "PROFILE_NOT_FOUND")
        assertError(backend.getListings(filters = FiltersDto(minAge = 3, maxAge = 2)), 422, "VALIDATION_ERROR")
        assertError(backend.applyFilters("olga", FiltersDto(maxPrice = -1)), 422, "VALIDATION_ERROR")
        assertError(backend.applyFilters("olga", FiltersDto(sex = "unknown")), 422, "VALIDATION_ERROR")
    }

    @Test fun repeatedSwipeDoesNotDuplicateAndFavoriteTogglingWorksForUnavailableListings() = runTest {
        val backend = MockBackend(latencyMillis = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        val first = backend.swipe("mia", SwipeRequestDto("olga", true)).body!!
        assertEquals(listOf("mia"), first.seen)
        assertEquals(listOf("mia"), first.favorites)
        assertEquals(first, backend.swipe("mia", SwipeRequestDto("olga", true)).body)
        assertEquals(first, backend.swipe("mia", SwipeRequestDto("olga", false)).body)
        assertTrue(backend.toggleFavorite("mia", FavoriteRequestDto("olga")).body!!.favorites.isEmpty())
        assertEquals(listOf("mia"), backend.toggleFavorite("mia", FavoriteRequestDto("olga")).body!!.favorites)
        val unavailable = backend.toggleFavorite("sonya", FavoriteRequestDto("olga")).body!!
        assertEquals(listOf("mia", "sonya"), unavailable.favorites)
        assertError(backend.swipe("sonya", SwipeRequestDto("olga", true)), 409, "LISTING_UNAVAILABLE")
        assertError(backend.toggleFavorite("missing", FavoriteRequestDto("olga")), 404, "LISTING_NOT_FOUND")
        assertEquals(unavailable, backend.getSelections().body!!.getValue("olga"))
        assertEquals(listOf("mia", "peach", "sonya"), backend.getSelections().body!!.getValue("anna").favorites)
    }

    @Test fun cancellingDelayedMutationLeavesStoreUnchangedAndRetrySucceeds() = runTest {
        val backend = MockBackend(latencyMillis = 350, ioDispatcher = StandardTestDispatcher(testScheduler))
        val before = backend.getProfiles().body!!
        val pending = launch { backend.register(RegisterRequestDto("cancelled", "password123", "Name")) }
        runCurrent()
        advanceTimeBy(349)
        runCurrent()
        assertTrue(pending.isActive)
        assertEquals(before, backend.snapshot()!!.profiles)
        pending.cancelAndJoin()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(before, backend.snapshot()!!.profiles)
        assertError(backend.authenticate(LoginRequestDto("cancelled", "password123")), 401, "INVALID_CREDENTIALS")
        assertEquals(201, backend.register(RegisterRequestDto("cancelled", "password123", "Name")).status)
        assertEquals(200, backend.authenticate(LoginRequestDto("cancelled", "password123")).status)
    }

    @Test fun concurrentDuplicateRegistrationCreatesExactlyOneAccount() = runTest {
        val backend = MockBackend(latencyMillis = 10, ioDispatcher = StandardTestDispatcher(testScheduler))
        val responses = List(8) {
            async { backend.register(RegisterRequestDto("concurrent", "password123", "Name")) }
        }.awaitAll()
        assertEquals(1, responses.count { it.status == 201 })
        responses.filter { it.status != 201 }.forEach { assertError(it, 409, "LOGIN_OCCUPIED") }
        assertEquals(1, backend.getProfiles().body!!.count { it.login == "concurrent" })
    }

    private fun assertError(response: BackendResponse<*>, status: Int, code: String) {
        assertEquals(status, response.status)
        assertNull(response.body)
        assertEquals(code, response.error!!.code)
        assertTrue(response.error.message.isNotBlank())
    }
}
