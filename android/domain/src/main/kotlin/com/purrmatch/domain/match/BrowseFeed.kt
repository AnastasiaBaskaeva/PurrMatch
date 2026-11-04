package com.purrmatch.domain.match

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseFeedState(
    val queue: List<Listing> = emptyList(),
    val loading: Boolean = true,
    /** The last portion request failed; cards already in the queue stay usable. */
    val failed: Boolean = false,
) {
    val current: Listing? get() = queue.firstOrNull()
}

/**
 * A small queue of cards fed by portions from the server: only a few listings live in memory,
 * and the next portion is requested while a few cards are still left. Call from one thread (the main one).
 */
class BrowseFeed(
    private val repository: MatchRepository,
    private val userId: String,
    private val scope: CoroutineScope,
    private val batchSize: Int = 10,
    private val prefetchAt: Int = 3,
) {
    private val mutableState = MutableStateFlow(BrowseFeedState())
    val state: StateFlow<BrowseFeedState> = mutableState.asStateFlow()

    private val swiped = mutableSetOf<String>()
    private var cursor: String? = null
    private var endReached = false
    private var job: Job? = null

    init { require(batchSize > 0 && prefetchAt >= 0) }

    /** Starts from scratch: used on the first open and when the user's filters change. */
    fun restart() {
        job?.cancel()
        cursor = null
        endReached = false
        mutableState.value = BrowseFeedState()
        load()
    }

    /**
     * Asks the server again from the saved bookmark: retry after a failure, or a look for newly published cards
     * when the queue is empty or nearly so. Does nothing while enough cards are queued.
     */
    fun reload() {
        val state = mutableState.value
        if (state.loading || (state.queue.size > prefetchAt && !state.failed)) return
        endReached = false
        load()
    }

    /**
     * The card leaves the queue only after the server confirmed the swipe, so a failed request keeps it
     * on screen for a retry. A listing that is gone from the server (404, 409) is dropped either way.
     */
    suspend fun swipe(listingId: String, like: Boolean): Result<Unit> =
        repository.swipe(userId, listingId, like)
            .onSuccess { leave(listingId) }
            .onFailure { failure -> if ((failure as? RequestFailedException)?.status in GONE) leave(listingId) }

    private fun leave(listingId: String) {
        swiped += listingId
        mutableState.update { state -> state.copy(queue = state.queue.filterNot { it.id == listingId }) }
        prefetch()
    }

    private fun prefetch() {
        val state = mutableState.value
        if (!state.loading && !state.failed && !endReached && state.queue.size <= prefetchAt) load()
    }

    private fun load() {
        if (job?.isActive == true) return
        mutableState.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            val result = repository.browse(userId, batchSize, cursor)
            ensureActive()
            result.onSuccess { page ->
                cursor = page.nextCursor
                endReached = page.endReached
                mutableState.update { state ->
                    val known = state.queue.mapTo(HashSet()) { it.id }
                    // A swipe still in flight may not be recorded by the server yet, so filter locally too.
                    val fresh = page.listings.filter { it.id !in known && it.id !in swiped }
                    state.copy(queue = state.queue + fresh, loading = false)
                }
            }.onFailure { mutableState.update { it.copy(loading = false, failed = true) } }
        }
    }

    private companion object { val GONE = setOf(404, 409) }
}
