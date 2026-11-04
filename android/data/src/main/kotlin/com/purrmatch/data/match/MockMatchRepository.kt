package com.purrmatch.data.match

import com.purrmatch.data.backend.BackendResponse
import com.purrmatch.data.backend.MockBackend
import com.purrmatch.data.backend.dto.*
import com.purrmatch.data.backend.toResult
import com.purrmatch.domain.match.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Maps mock HTTP DTOs into a small observable cache. The cache holds only what responses brought:
 * the user's own profile and selection, and the profiles and listings of screens that were opened.
 * All mutations live in the backend's memory.
 */
class MockMatchRepository(
    private val backend: MockBackend = MockBackend(),
) : MatchRepository {
    // Writes are applied one at a time, so an older response cannot overwrite a newer one.
    private val mutex = Mutex()
    private val mutableData = MutableStateFlow(MatchData(emptyList(), emptyList()))
    override val data = mutableData.asStateFlow()

    override suspend fun load(userId: String): Result<Unit> {
        request({ backend.getProfile(userId) }) { profile -> cache { withProfile(profile.toDomain()) } }
            .onFailure { return Result.failure(it) }
        return request({ backend.getSelection(userId) }) { selection ->
            cache { withSelection(userId, selection.toDomain()) }
        }
    }

    override suspend fun register(login: String, password: String, name: String): Result<String> =
        request({ backend.register(RegisterRequestDto(login, password, name)) }) { it.userId }

    override suspend fun authenticate(login: String, password: String): Result<String> =
        request({ backend.authenticate(LoginRequestDto(login, password)) }) { it.userId }

    override suspend fun getProfile(id: String): Result<Profile> = request({ backend.getProfile(id) }) {
        it.toDomain().also { profile -> cache { withProfile(profile) } }
    }

    override suspend fun getFavorites(userId: String): Result<List<Listing>> =
        request({ backend.getFavorites(userId) }) { dtos ->
            dtos.map { it.toDomain() }.also { listings -> cache { withListings(listings) } }
        }

    override suspend fun getAuthorListings(authorId: String): Result<List<Listing>> =
        request({ backend.getListings(authorId = authorId) }) { dtos ->
            dtos.map { it.toDomain() }.also { listings -> cache { withListings(listings) } }
        }

    override fun remember(listing: Listing) { cache { withListings(listOf(listing)) } }

    override suspend fun getListings(userId: String, filters: SearchFilters): Result<List<Listing>> =
        request({ backend.getListings(userId, filters.toDto()) }) { listings -> listings.map { it.toDomain() } }

    // A read that changes nothing and may be slow, so it neither waits for other writes nor touches the cache.
    override suspend fun browse(userId: String, limit: Int, after: String?): Result<ListingPage> =
        capture { backend.browse(userId, limit, after).toResult().getOrThrow().toDomain() }

    override suspend fun getListing(id: String): Result<Listing> = request({ backend.getListing(id) }) {
        it.toDomain().also { listing -> cache { withListings(listOf(listing)) } }
    }

    override suspend fun upsertProfile(profile: Profile): Result<Unit> =
        request({ backend.upsertProfile(profile.toDto()) }) { saved -> cache { withProfile(saved.toDomain()) } }

    override suspend fun toggleFavorite(userId: String, listingId: String): Result<Unit> =
        request({ backend.toggleFavorite(listingId, FavoriteRequestDto(userId)) }) { cacheSelection(userId, it) }

    override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> =
        request({ backend.swipe(listingId, SwipeRequestDto(userId, like)) }) { cacheSelection(userId, it) }

    override suspend fun applyFilters(userId: String, filters: SearchFilters): Result<Unit> =
        request({ backend.applyFilters(userId, filters.toDto()) }) { cacheSelection(userId, it) }

    override suspend fun publish(userId: String, listing: Listing): Result<Unit> =
        request({ backend.publish(userId, listing.toDto()) }) { published ->
            cache { withListings(listOf(published.toDomain())) }
        }

    override suspend fun delete(userId: String, listingId: String): Result<Unit> =
        request({ backend.delete(userId, listingId) }) {
            // The server only hides the listing (soft deletion), so saved favorites keep working.
            cache { copy(listings = listings.map { if (it.id == listingId) it.copy(available = false) else it }) }
        }

    private suspend fun <T : Any, R> request(call: suspend () -> BackendResponse<T>, map: (T) -> R): Result<R> =
        mutex.withLock { capture { map(call().toResult().getOrThrow()) } }

    private fun cacheSelection(userId: String, selection: SelectionDto) {
        cache { withSelection(userId, selection.toDomain()) }
    }

    private fun cache(change: MatchData.() -> MatchData) = mutableData.update(change)

    // Replaces entries with the same id in place, appends new ones.
    private fun MatchData.withProfile(profile: Profile) =
        copy(profiles = if (profiles.any { it.id == profile.id }) profiles.map { if (it.id == profile.id) profile else it }
            else profiles + profile)

    private fun MatchData.withListings(fresh: List<Listing>): MatchData {
        val byId = fresh.associateBy { it.id }
        val known = listings.mapTo(HashSet()) { it.id }
        return copy(listings = listings.map { byId[it.id] ?: it } + fresh.filter { it.id !in known })
    }

    private fun MatchData.withSelection(userId: String, selection: UserSelection) =
        copy(selections = selections + (userId to selection))

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { Result.failure(failure) }
}
