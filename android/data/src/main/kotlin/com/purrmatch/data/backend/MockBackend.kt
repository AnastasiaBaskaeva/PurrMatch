package com.purrmatch.data.backend

import com.purrmatch.data.backend.dto.*
import com.purrmatch.domain.match.MIN_PASSWORD_LENGTH
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A session-scoped simulated server. JSON is loaded once on IO; every request has a cancellable delay.
 * Passwords are compared directly with public fixtures, exclusively for mock authentication.
 * Real authentication and password handling belong to the server; this is not a security mechanism.
 */
class MockBackend(
    private val fixtureName: String = "default",
    private val latencyMillis: Long = 350,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val failureRules: List<FailureRuleDto>? = null,
) {
    private val mutex = Mutex()
    @Volatile private var fixture: MockFixtureDto? = null
    private var rules = emptyList<FailureRuleDto>()
    private var signedInUserId: String? = null

    init { require(latencyMillis >= 0) }

    // Immutable snapshot of the whole mock server, for tests only.
    internal fun snapshot(): MockFixtureDto? = fixture
    private var store: MockFixtureDto
        get() = checkNotNull(fixture)
        set(value) { fixture = value }

    suspend fun register(request: RegisterRequestDto): BackendResponse<AuthResponseDto> =
        request("POST", "/auth/register", request.login.trim(), status = 201) {
            val login = request.login.trim()
            validate(login.isNotEmpty() && request.name.isNotBlank() && request.password.length >= MIN_PASSWORD_LENGTH)
            if (store.profiles.any { it.login == login }) fail(409, "LOGIN_OCCUPIED", "Login is already registered")
            val id = UUID.randomUUID().toString()
            val profile = ProfileDto(id = id, login = login, name = request.name.trim())
            store = store.copy(
                profiles = store.profiles + profile,
                credentials = store.credentials + CredentialDto(id, login, request.password),
            )
            AuthResponseDto(id)
        }

    suspend fun authenticate(request: LoginRequestDto): BackendResponse<AuthResponseDto> =
        request("POST", "/auth/login", request.login.trim()) {
            val credential = store.credentials.firstOrNull {
                it.login == request.login.trim() && it.password == request.password
            } ?: fail(401, "INVALID_CREDENTIALS", "Invalid login or password")
            AuthResponseDto(credential.userId)
        }

    suspend fun getProfiles(): BackendResponse<List<ProfileDto>> = request("GET", "/profiles") { store.profiles }

    suspend fun getSelection(userId: String): BackendResponse<SelectionDto> =
        request("GET", "/users/$userId/selection") { profile(userId); selection(userId) }

    /** Favorites in the order they were added, including unavailable listings. */
    suspend fun getFavorites(userId: String): BackendResponse<List<ListingDto>> =
        request("GET", "/users/$userId/favorites") {
            profile(userId)
            selection(userId).favorites.mapNotNull { id -> store.listings.firstOrNull { it.id == id } }
        }

    suspend fun getSelections(): BackendResponse<Map<String, SelectionDto>> =
        request("GET", "/users/selections") { store.selections }

    suspend fun getProfile(id: String): BackendResponse<ProfileDto> = request("GET", "/profiles/$id") { profile(id) }

    /** Full DTO upsert; login is immutable for an existing account. */
    suspend fun upsertProfile(profile: ProfileDto): BackendResponse<ProfileDto> = request("PATCH", "/profiles/${profile.id}") {
        validate(profile.id.isNotBlank() && profile.login.isNotBlank() && profile.name.isNotBlank())
        validateCoordinates(profile.coordinates)
        val previous = store.profiles.firstOrNull { it.id == profile.id }
        validate(previous == null || previous.login == profile.login)
        if (store.profiles.any { it.id != profile.id && it.login == profile.login })
            fail(409, "LOGIN_OCCUPIED", "Login is already registered")
        store = store.copy(profiles = store.profiles.filterNot { it.id == profile.id } + profile)
        profile
    }

    suspend fun getListings(
        userId: String? = null,
        filters: FiltersDto = FiltersDto(),
        includeUnavailable: Boolean = false,
        authorId: String? = null,
    ): BackendResponse<List<ListingDto>> = request("GET", "/listings") {
        if (userId != null) profile(userId)
        validateFilters(filters)
        val seen = userId?.let { selection(it).seen }.orEmpty()
        store.listings.filter { listing ->
            (includeUnavailable || listing.available) && listing.id !in seen && matches(listing, filters) &&
                (authorId == null || listing.authorId == authorId)
        }
    }

    /**
     * The next portion for the user's feed: available, unseen listings of other authors matching the user's saved filters, in
     * publication order. [after] is the id of the last listing from the previous portion (null for the first one).
     * Listings are only ever appended, so the bookmark stays valid while new ones are published.
     */
    suspend fun browse(userId: String, limit: Int, after: String? = null): BackendResponse<ListingPageDto> =
        request("GET", "/listings/browse") {
            profile(userId)
            validate(limit in 1..MAX_PAGE_SIZE)
            val start = if (after == null) 0 else store.listings.indexOfFirst { it.id == after }
                .also { validate(it >= 0) } + 1
            val selection = selection(userId)
            val page = store.listings.asSequence().drop(start)
                .filter { it.available && it.authorId != userId && it.id !in selection.seen && matches(it, selection.filters) }
                .take(limit).toList()
            ListingPageDto(page, nextCursor = page.lastOrNull()?.id ?: after, endReached = page.size < limit)
        }

    suspend fun getListing(id: String): BackendResponse<ListingDto> = request("GET", "/listings/$id") { listing(id) }

    suspend fun publish(userId: String, listing: ListingDto): BackendResponse<ListingDto> =
        request("POST", "/listings", status = 201) {
            profile(userId)
            validate(listing.id.isNotBlank() && listing.name.isNotBlank() && listing.price >= 0)
            validate(listing.sex in listOf("кошка", "кот"))
            validateCoordinates(listing.coordinates)
            listing.birthday?.let { value ->
                val birthday = try { LocalDate.parse(value) } catch (_: DateTimeParseException) {
                    fail(422, "VALIDATION_ERROR", "birthday must use yyyy-MM-dd")
                }
                validate(birthday.toString() == value && !birthday.isAfter(LocalDate.now()))
            }
            if (store.listings.any { it.id == listing.id }) fail(409, "LISTING_EXISTS", "Listing already exists")
            val published = listing.copy(authorId = userId, available = true)
            store = store.copy(listings = store.listings + published)
            published
        }

    suspend fun delete(userId: String, id: String): BackendResponse<Unit> = request("DELETE", "/listings/$id", status = 204) {
        profile(userId)
        val listing = listing(id)
        if (listing.authorId != userId) fail(403, "NOT_OWNER", "Only the author can delete a listing")
        store = store.copy(listings = store.listings.map { if (it.id == id) it.copy(available = false) else it })
    }

    suspend fun toggleFavorite(id: String, request: FavoriteRequestDto): BackendResponse<SelectionDto> =
        request("POST", "/listings/$id/favorite") {
            profile(request.userId)
            listing(id)
            val current = selection(request.userId)
            val updated = current.copy(favorites = if (id in current.favorites) current.favorites - id else current.favorites + id)
            updateSelection(request.userId, updated)
        }

    suspend fun swipe(id: String, request: SwipeRequestDto): BackendResponse<SelectionDto> =
        request("POST", "/listings/$id/swipe") {
            profile(request.userId)
            val listing = listing(id)
            if (!listing.available) fail(409, "LISTING_UNAVAILABLE", "Listing is unavailable")
            val current = selection(request.userId)
            val updated = if (id in current.seen) current else current.copy(
                seen = current.seen + id,
                favorites = if (request.like && id !in current.favorites) current.favorites + id else current.favorites,
            )
            updateSelection(request.userId, updated)
        }

    suspend fun applyFilters(userId: String, filters: FiltersDto): BackendResponse<SelectionDto> =
        request("PUT", "/users/$userId/filters") {
            profile(userId)
            validateFilters(filters)
            updateSelection(userId, selection(userId).copy(filters = filters))
        }

    suspend fun signIn(request: SessionRequestDto): BackendResponse<SessionResponseDto> = request("POST", "/auth/session") {
        profile(request.userId)
        signedInUserId = request.userId
        SessionResponseDto(signedInUserId)
    }

    suspend fun signOut(): BackendResponse<SessionResponseDto> = request("DELETE", "/auth/session") {
        signedInUserId = null
        SessionResponseDto(null)
    }

    private fun profile(id: String) = store.profiles.firstOrNull { it.id == id }
        ?: fail(404, "PROFILE_NOT_FOUND", "Profile not found")

    private fun listing(id: String) = store.listings.firstOrNull { it.id == id }
        ?: fail(404, "LISTING_NOT_FOUND", "Listing not found")

    private fun selection(userId: String) = store.selections[userId] ?: SelectionDto()

    private fun updateSelection(userId: String, selection: SelectionDto): SelectionDto {
        store = store.copy(selections = store.selections + (userId to selection))
        return selection
    }

    private fun validateCoordinates(value: CoordinatesDto?) {
        if (value == null) return
        validate(value.latitude.isFinite() && value.latitude in -90.0..90.0 &&
            value.longitude.isFinite() && value.longitude in -180.0..180.0)
    }

    private fun validateFilters(filters: FiltersDto) {
        validate(filters.sex == null || filters.sex in listOf("кошка", "кот"))
        validate(filters.minAge == null || filters.minAge >= 0)
        validate(filters.maxAge == null || filters.maxAge >= 0)
        validate(filters.minAge == null || filters.maxAge == null || filters.minAge <= filters.maxAge)
        validate(filters.maxPrice == null || filters.maxPrice >= 0)
    }

    private fun matches(listing: ListingDto, filters: FiltersDto): Boolean {
        val birthday = listing.birthday?.let(LocalDate::parse)
        val age = birthday?.takeUnless { it.isAfter(LocalDate.now()) }?.let { Period.between(it, LocalDate.now()).years }
        return (filters.breed == null || listing.breed == filters.breed) &&
            (filters.sex == null || listing.sex == filters.sex) &&
            (filters.color == null || listing.color.equals(filters.color, ignoreCase = true)) &&
            (filters.eyes == null || listing.eyes.equals(filters.eyes, ignoreCase = true)) &&
            (filters.city == null || listing.city == filters.city) &&
            (filters.minAge == null || age != null && age >= filters.minAge) &&
            (filters.maxAge == null || age != null && age <= filters.maxAge) &&
            (filters.maxPrice == null || listing.price <= filters.maxPrice) &&
            (filters.freeOnly == null || (listing.price == 0L) == filters.freeOnly)
    }

    private suspend fun <T> request(
        method: String, path: String, login: String? = null, status: Int = 200, action: () -> T,
    ): BackendResponse<T> {
        delay(latencyMillis)
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (fixture == null) {
                val loaded = withContext(ioDispatcher) { MockFixtures.load(fixtureName) to (failureRules ?: MockFixtures.failures()) }
                rules = loaded.second
                fixture = loaded.first
            }
            val failure = rules.firstOrNull { it.method == method && it.path == path && (it.login == null || it.login == login) }
            if (failure != null) return@withLock BackendResponse(failure.status, error = failure.error)
            try { BackendResponse(status, body = action()) }
            catch (failure: RejectedRequest) { BackendResponse(failure.status, error = failure.error) }
        }
    }

    private companion object { const val MAX_PAGE_SIZE = 50 }

    private fun validate(condition: Boolean) {
        if (!condition) fail(422, "VALIDATION_ERROR", "Invalid request fields")
    }

    private fun fail(status: Int, code: String, message: String): Nothing = throw RejectedRequest(status, ErrorDto(code, message))
    private class RejectedRequest(val status: Int, val error: ErrorDto) : RuntimeException()
}
