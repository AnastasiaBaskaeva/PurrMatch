package com.purrmatch.domain.match

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseFeedTest {
    private class FakeRepository(var listings: List<Listing>) : MatchRepository {
        val cursors = mutableListOf<String?>()
        var failBrowse = false
        var gate: CompletableDeferred<Unit>? = null
        var swipeResult: Result<Unit> = Result.success(Unit)
        val swipes = mutableListOf<String>()

        override suspend fun browse(userId: String, limit: Int, after: String?): Result<ListingPage> {
            cursors += after
            gate?.await()
            if (failBrowse) return Result.failure(RequestFailedException(500, "INTERNAL_ERROR", "down"))
            val page = listings.drop(if (after == null) 0 else listings.indexOfFirst { it.id == after } + 1).take(limit)
            return Result.success(ListingPage(page, page.lastOrNull()?.id ?: after, page.size < limit))
        }

        override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
            swipes += listingId
            return swipeResult
        }

        override val data = MutableStateFlow(MatchData(emptyList(), emptyList()))
        override suspend fun register(login: String, password: String, name: String) = TODO()
        override suspend fun authenticate(login: String, password: String) = TODO()
        override suspend fun load(userId: String) = TODO()
        override suspend fun getFavorites(userId: String) = TODO()
        override suspend fun getAuthorListings(authorId: String) = TODO()
        override fun remember(listing: Listing) = TODO()
        override suspend fun getProfile(id: String) = TODO()
        override suspend fun getListings(userId: String, filters: SearchFilters) = TODO()
        override suspend fun getListing(id: String) = TODO()
        override suspend fun upsertProfile(profile: Profile) = TODO()
        override suspend fun toggleFavorite(userId: String, listingId: String) = TODO()
        override suspend fun applyFilters(userId: String, filters: SearchFilters) = TODO()
        override suspend fun publish(userId: String, listing: Listing) = TODO()
        override suspend fun delete(userId: String, listingId: String) = TODO()
    }

    private fun cats(count: Int) = List(count) { Listing("c$it", "Кошка $it") }
    private fun BrowseFeedState.ids() = queue.map { it.id }

    @Test fun firstPortionIsLoadedOnRestartAndOnlyThatPortionIsHeld() = runTest {
        val repository = FakeRepository(cats(100))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 10)
        assertTrue(feed.state.value.loading)
        feed.restart(); runCurrent()
        assertEquals(cats(10).map { it.id }, feed.state.value.ids())
        assertFalse(feed.state.value.loading)
        assertEquals(listOf<String?>(null), repository.cursors)
    }

    @Test fun nextPortionIsRequestedFromBookmarkWhenFewCardsRemain() = runTest {
        val repository = FakeRepository(cats(25))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 10, prefetchAt = 3)
        feed.restart(); runCurrent()
        repeat(6) { feed.swipe("c$it", true).getOrThrow() }
        runCurrent()
        assertEquals(listOf<String?>(null), repository.cursors)
        feed.swipe("c6", true).getOrThrow(); runCurrent()
        assertEquals(listOf(null, "c9"), repository.cursors)
        assertEquals((7..19).map { "c$it" }, feed.state.value.ids())
    }

    @Test fun reachedEndStopsAutomaticRequestsButReloadFindsNewListings() = runTest {
        val repository = FakeRepository(cats(4))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 10)
        feed.restart(); runCurrent()
        repeat(4) { feed.swipe("c$it", false).getOrThrow() }
        runCurrent()
        assertEquals(1, repository.cursors.size)
        assertNull(feed.state.value.current)
        repository.listings = cats(6)
        feed.reload(); runCurrent()
        assertEquals(listOf(null, "c3"), repository.cursors)
        assertEquals(listOf("c4", "c5"), feed.state.value.ids())
    }

    @Test fun reloadDoesNothingWhileEnoughCardsAreQueued() = runTest {
        val repository = FakeRepository(cats(30))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 10)
        feed.restart(); runCurrent()
        feed.reload(); runCurrent()
        assertEquals(1, repository.cursors.size)
    }

    @Test fun restartDropsStalePortionOfPreviousFilters() = runTest {
        val repository = FakeRepository(cats(10))
        repository.gate = CompletableDeferred()
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 5)
        feed.restart(); runCurrent()
        val staleGate = repository.gate!!
        repository.gate = null
        repository.listings = cats(20).drop(10)
        feed.restart(); runCurrent()
        staleGate.complete(Unit); runCurrent()
        assertEquals((10..14).map { "c$it" }, feed.state.value.ids())
        assertEquals(listOf<String?>(null, null), repository.cursors)
    }

    @Test fun failedPortionKeepsQueueAndReloadRetries() = runTest {
        val repository = FakeRepository(cats(30))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 5, prefetchAt = 3)
        feed.restart(); runCurrent()
        repository.failBrowse = true
        feed.swipe("c0", true); feed.swipe("c1", true); runCurrent()
        assertTrue(feed.state.value.failed)
        assertEquals(listOf("c2", "c3", "c4"), feed.state.value.ids())
        feed.swipe("c2", true); runCurrent()
        assertEquals(2, repository.cursors.size) // no automatic retry loop
        repository.failBrowse = false
        feed.reload(); runCurrent()
        assertFalse(feed.state.value.failed)
        assertEquals((3..9).map { "c$it" }, feed.state.value.ids())
    }

    @Test fun cardStaysUntilTheServerConfirmsTheSwipe() = runTest {
        val repository = FakeRepository(cats(5))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 5, prefetchAt = 0)
        feed.restart(); runCurrent()
        repository.swipeResult = Result.failure(RequestFailedException(500, "INTERNAL_ERROR", "down"))
        assertTrue(feed.swipe("c0", true).isFailure)
        assertEquals("c0", feed.state.value.current?.id)
        repository.swipeResult = Result.success(Unit)
        feed.swipe("c0", true).getOrThrow()
        assertEquals("c1", feed.state.value.current?.id)
    }

    @Test fun listingGoneFromServerIsDroppedEvenThoughTheSwipeFailed() = runTest {
        val repository = FakeRepository(cats(5))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 5, prefetchAt = 0)
        feed.restart(); runCurrent()
        repository.swipeResult = Result.failure(RequestFailedException(409, "LISTING_UNAVAILABLE", "gone"))
        assertTrue(feed.swipe("c0", true).isFailure)
        assertEquals("c1", feed.state.value.current?.id)
    }

    @Test fun swipedCardDoesNotComeBackFromARestartBeforeServerRecordsIt() = runTest {
        val repository = FakeRepository(cats(5))
        val feed = BrowseFeed(repository, "u", backgroundScope, batchSize = 5)
        feed.restart(); runCurrent()
        feed.swipe("c0", true).getOrThrow()
        feed.restart(); runCurrent() // the fake server still returns c0 as unseen
        assertEquals(listOf("c1", "c2", "c3", "c4"), feed.state.value.ids())
    }
}
