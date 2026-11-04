package com.purrmatch.data.match

import com.purrmatch.data.backend.MockBackend
import com.purrmatch.domain.match.Listing
import com.purrmatch.domain.match.ListingPage
import com.purrmatch.domain.match.RequestFailedException
import com.purrmatch.domain.match.SearchFilters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BrowsePagesTest {
    private fun repository() = MockMatchRepository(MockBackend(latencyMillis = 0))

    private suspend fun MockMatchRepository.browseAll(user: String, limit: Int): List<ListingPage> {
        val pages = mutableListOf<ListingPage>()
        var cursor: String? = null
        do {
            val page = browse(user, limit, cursor).getOrThrow()
            pages += page
            cursor = page.nextCursor
        } while (!page.endReached)
        return pages
    }

    @Test fun pagesCoverAllAvailableListingsOnceInOrderWithoutUnavailableOnes() = runBlocking {
        val repository = repository()
        val pages = repository.browseAll("anna", 10)
        assertEquals(listOf(10, 10, 6), pages.map { it.listings.size })
        val ids = pages.flatMap { page -> page.listings.map { it.id } }
        assertEquals(26, ids.size)
        assertEquals(ids.distinct(), ids)
        assertFalse("sonya" in ids || "iris" in ids)
        assertEquals("mia", ids.first())
        assertEquals("varya", pages.last().nextCursor)
    }

    @Test fun exactlyFullLastPageIsFollowedByAnEmptyOneThatKeepsTheBookmark() = runBlocking {
        val repository = repository()
        val full = repository.browse("anna", 50).getOrThrow()
        assertTrue(full.endReached)
        val empty = repository.browse("anna", 5, full.nextCursor).getOrThrow()
        assertEquals(ListingPage(emptyList(), full.nextCursor, true), empty)
    }

    @Test fun seenListingsAreSkippedForThatUserOnly() = runBlocking {
        val repository = repository()
        repository.swipe("anna", "mia", false).getOrThrow()
        assertEquals("peach", repository.browse("anna", 3).getOrThrow().listings.first().id)
        assertEquals("mia", repository.browse("olga", 3).getOrThrow().listings.first().id)
    }

    @Test fun savedFiltersDecideWhichListingsArrive() = runBlocking {
        val repository = repository()
        repository.applyFilters("anna", SearchFilters(city = "Казань")).getOrThrow()
        val ids = repository.browseAll("anna", 2).flatMap { page -> page.listings.map { it.id } }
        assertEquals(listOf("barsik", "tim", "kuzya", "ponchik"), ids)
        repository.applyFilters("anna", SearchFilters(terms = 1)).getOrThrow()
        val free = repository.browseAll("anna", 50).flatMap { it.listings }
        assertTrue(free.isNotEmpty() && free.all { it.price == 0L })
        repository.applyFilters("anna", SearchFilters(price = "3000")).getOrThrow()
        val cheap = repository.browseAll("anna", 50).flatMap { it.listings }
        assertTrue(cheap.any { it.price in 1..3000 } && cheap.all { it.price <= 3000 })
        repository.applyFilters("anna", SearchFilters(minAge = "100")).getOrThrow()
        assertTrue(repository.browse("anna", 10).getOrThrow().listings.isEmpty())
    }

    @Test fun listingPublishedLaterIsFoundByContinuingFromTheOldBookmark() = runBlocking {
        val repository = repository()
        val last = repository.browse("anna", 50).getOrThrow()
        repository.publish("olga", Listing("fresh", "Новая")).getOrThrow()
        val next = repository.browse("anna", 50, last.nextCursor).getOrThrow()
        assertEquals(listOf("fresh"), next.listings.map { it.id })
        assertEquals("fresh", next.nextCursor)
    }

    @Test fun readingPagesDoesNotTouchTheObservableCache() = runBlocking {
        val repository = repository()
        repository.load("anna").getOrThrow()
        val before = repository.data.value
        repository.browse("anna", 10).getOrThrow()
        assertSame(before, repository.data.value)
    }

    @Test fun invalidRequestsAreRejectedWithContractErrors() = runBlocking {
        val repository = repository()
        assertEquals(422, (repository.browse("anna", 0).exceptionOrNull() as RequestFailedException).status)
        assertEquals(422, (repository.browse("anna", 51).exceptionOrNull() as RequestFailedException).status)
        assertEquals(422, (repository.browse("anna", 10, "nope").exceptionOrNull() as RequestFailedException).status)
        assertEquals(404, (repository.browse("nobody", 10).exceptionOrNull() as RequestFailedException).status)
    }
}
