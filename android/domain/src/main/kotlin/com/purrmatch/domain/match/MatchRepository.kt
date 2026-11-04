package com.purrmatch.domain.match

import kotlinx.coroutines.flow.StateFlow

data class SearchFilters(
    val breed: String = "Любая порода", val sex: Int = 0,
    val color: String = "Любой окрас", val eyes: String = "Любой цвет",
    val minAge: String = "", val maxAge: String = "", val city: String = "Выберите город",
    val terms: Int = 0, val price: String = "",
)

data class UserSelection(
    val favorites: List<String> = emptyList(),
    /** Completed swipes only; opening a card or cancelling a drag does not mark it seen. */
    val seen: List<String> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
)

data class MatchData(
    val profiles: List<Profile>,
    val listings: List<Listing>,
    val selections: Map<String, UserSelection> = emptyMap(),
) {
    fun selection(userId: String) = selections[userId] ?: UserSelection()
    fun profile(userId: String) = profiles.firstOrNull { it.id == userId }
    fun listing(listingId: String) = listings.firstOrNull { it.id == listingId }
}

/**
 * One portion of the browse feed. [nextCursor] is the bookmark to continue from; [endReached] means the server
 * had fewer listings than requested right now (more may be published later, so the bookmark stays usable).
 */
data class ListingPage(val listings: List<Listing>, val nextCursor: String?, val endReached: Boolean)

/** Expected authentication errors keep the form available for retry. */
class LoginOccupiedException : IllegalArgumentException()
class InvalidCredentialsException : IllegalArgumentException()

/** Single source of truth so presentation and storage agree on what a valid password is. */
const val MIN_PASSWORD_LENGTH = 8

/** A failed backend request, retaining the contract's status and machine-readable code. */
class RequestFailedException(val status: Int, val code: String, message: String) : Exception(message)

interface MatchRepository {
    /** Logins are case-sensitive, with surrounding whitespace removed. */
    suspend fun register(login: String, password: String, name: String): Result<String>
    suspend fun authenticate(login: String, password: String): Result<String>
    /**
     * Only what the app has loaded so far: the signed-in user's profile and selection, plus the profiles and
     * listings of screens that were opened. It is never the whole catalogue; reading it makes no request.
     */
    val data: StateFlow<MatchData>
    /** Loads the signed-in user's own profile and selection (favorites, seen, filters) into [data]. */
    suspend fun load(userId: String): Result<Unit>
    suspend fun getProfile(id: String): Result<Profile>
    /** Favorites of the user, also those no longer available, so saved cards stay viewable. Cached into [data]. */
    suspend fun getFavorites(userId: String): Result<List<Listing>>
    /** Available listings published by one author. Cached into [data]. */
    suspend fun getAuthorListings(authorId: String): Result<List<Listing>>
    /** Keeps a listing the user is about to open in [data], so its screen can show at once. */
    fun remember(listing: Listing)
    suspend fun getListings(userId: String, filters: SearchFilters = SearchFilters()): Result<List<Listing>>
    /** Next portion of listings relevant to the user: available, unseen and matching the user's saved filters. */
    suspend fun browse(userId: String, limit: Int, after: String? = null): Result<ListingPage>
    suspend fun getListing(id: String): Result<Listing>
    suspend fun upsertProfile(profile: Profile): Result<Unit>
    suspend fun toggleFavorite(userId: String, listingId: String): Result<Unit>
    suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit>
    suspend fun applyFilters(userId: String, filters: SearchFilters): Result<Unit>
    suspend fun publish(userId: String, listing: Listing): Result<Unit>
    suspend fun delete(userId: String, listingId: String): Result<Unit>
}
