package com.purrmatch.domain.match

import java.io.Serializable
import kotlinx.coroutines.flow.StateFlow

data class SearchFilters(
    val breed: String = "Любая порода", val sex: Int = 0,
    val color: String = "Любой окрас", val eyes: String = "Любой цвет",
    val minAge: String = "", val maxAge: String = "", val city: String = "Выберите город",
    val terms: Int = 0, val price: String = "",
) : Serializable

data class UserSelection(
    val favorites: List<String> = emptyList(),
    /** Completed swipes only; opening a card or cancelling a drag does not mark it seen. */
    val seen: List<String> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
) : Serializable

data class MatchData(
    val profiles: List<Profile>,
    val listings: List<Listing>,
    val selections: Map<String, UserSelection> = emptyMap(),
) : Serializable {
    private companion object { private const val serialVersionUID = 2L }

    fun selection(userId: String) = selections[userId] ?: UserSelection()
    fun profile(userId: String) = profiles.firstOrNull { it.id == userId }
    fun listing(listingId: String) = listings.firstOrNull { it.id == listingId }
}

/** Authentication failures are distinct from storage failures; both leave the form available. */
class LoginOccupiedException : IllegalArgumentException()
class InvalidCredentialsException : IllegalArgumentException()

interface MatchRepository {
    /** Logins are case-sensitive, with surrounding whitespace removed. */
    suspend fun register(login: String, password: String, name: String): Result<String>
    suspend fun authenticate(login: String, password: String): Result<String>
    val data: StateFlow<MatchData>
    suspend fun ensureProfile(userId: String, login: String, name: String = login): Result<Unit>
    suspend fun saveProfile(profile: Profile): Result<Unit>
    suspend fun toggleFavorite(userId: String, listingId: String): Result<Unit>
    suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit>
    suspend fun applyFilters(userId: String, filters: SearchFilters): Result<Unit>
    suspend fun publish(userId: String, listing: Listing): Result<Unit>
    suspend fun delete(userId: String, listingId: String): Result<Unit>
}
