package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.io.File
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local mock source. No credentials or network DTOs enter presentation. */
class MockMatchRepository(
    private val file: File? = null,
    initialData: MatchData? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MatchRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var records = initialData?.toRecord() ?: sampleMatchRecords()

    // A stored account must not see fixture profiles/filters while its file is still loading.
    private val mutableData = MutableStateFlow(
        if (file == null) records.toDomain() else MatchData(
            emptyList(),
            emptyList()
        ),
    )
    override val data = mutableData.asStateFlow()
    private var credentials = emptyMap<String, PasswordVerifier>()
    private var loadFailure: Throwable? = null

    init {
        scope.launch {
            mutex.withLock {
                runCatching {
                    val stored = file?.takeIf { it.exists() }?.let(::readMatchSnapshot)
                        ?: legacyMatchSnapshot(records)
                    records = stored.data
                    mutableData.value = records.toDomain()
                    credentials = stored.credentials
                }.onFailure { loadFailure = it }
                ready.complete(Unit)
            }
        }
    }

    internal suspend fun awaitReady() = ready.await()

    private suspend fun update(transform: (MatchRecords) -> MatchRecords): Result<Unit> = withContext(ioDispatcher) {
        ready.await()
        mutex.withLock {
            runCatching {
                retryFailedLoad()
                val next = transform(records)
                persist(
                    next,
                    credentials
                )
                records = next
                mutableData.value = records.toDomain()
            }
        }
    }

    private fun retryFailedLoad() {
        if (loadFailure == null) return
        // A failed existing snapshot must be read again, never replaced with fresh fixtures.
        val stored = readMatchSnapshot(checkNotNull(file))
        records = stored.data
        mutableData.value = records.toDomain()
        credentials = stored.credentials
        loadFailure = null
    }

    private fun persist(
        data: MatchRecords,
        credentials: Map<String, PasswordVerifier>
    ) {
        file?.let { target ->
            val temporary = File(
                target.parentFile,
                "${target.name}.tmp"
            )
            ObjectOutputStream(temporary.outputStream()).use {
                it.writeObject(
                    StoredMatches(
                        data,
                        credentials
                    )
                )
            }
            if (!temporary.renameTo(target)) throw IOException("Cannot save mock data")
        }
    }

    override suspend fun register(
        login: String,
        password: String,
        name: String
    ): Result<String> = withContext(ioDispatcher) {
        ready.await()
        mutex.withLock {
            runCatching {
                retryFailedLoad()
                val normalized = login.trim()
                require(normalized.isNotEmpty() && name.isNotBlank() && password.length >= 8)
                if (records.profiles.any { it.login == normalized }) throw LoginOccupiedException()
                val id = UUID.randomUUID().toString()
                val profile = ProfileRecord(
                    id = id,
                    login = normalized,
                    name = name.trim(),
                    city = "",
                    about = "",
                    telegram = "",
                    phone = "",
                    website = ""
                )
                val next = records.copy(profiles = records.profiles + profile)
                val nextCredentials = credentials + (id to PasswordVerifier.create(password))
                persist(
                    next,
                    nextCredentials
                )
                credentials = nextCredentials
                records = next
                mutableData.value = records.toDomain()
                id
            }
        }
    }

    override suspend fun authenticate(
        login: String,
        password: String
    ): Result<String> = withContext(ioDispatcher) {
        ready.await()
        mutex.withLock {
            runCatching {
                retryFailedLoad()
                val profile = records.profiles.firstOrNull { it.login == login.trim() }
                if (profile == null || credentials[profile.id]?.matches(password) != true) throw InvalidCredentialsException()
                profile.id
            }
        }
    }

    private suspend fun select(
        userId: String,
        transform: (SelectionRecord) -> SelectionRecord
    ) = update {
        it.copy(selections = it.selections + (userId to transform(it.selection(userId))))
    }

    override suspend fun ensureProfile(
        userId: String,
        login: String,
        name: String
    ): Result<Unit> {
        return update { data ->
            if (data.profile(userId) != null) data else data.copy(
                profiles = data.profiles + ProfileRecord(
                    id = userId,
                    name = name,
                    login = login,
                    city = "",
                    about = "",
                    telegram = "",
                    phone = "",
                    website = ""
                ),
            )
        }
    }

    override suspend fun saveProfile(profile: Profile) = update {
        it.copy(profiles = it.profiles.filterNot { p -> p.id == profile.id } + profile.toRecord())
    }

    override suspend fun toggleFavorite(
        userId: String,
        listingId: String
    ): Result<Unit> {
        return update { data ->
            if (data.listing(listingId) == null) data else data.copy(
                selections = data.selections + (userId to data.selection(userId).let {
                    it.copy(favorites = if (listingId in it.favorites) it.favorites - listingId else it.favorites + listingId)
                }),
            )
        }
    }

    override suspend fun swipe(
        userId: String,
        listingId: String,
        like: Boolean
    ): Result<Unit> {
        return update { data ->
            val selection = data.selection(userId)
            if (data.listing(listingId)?.available != true || listingId in selection.seen) data else data.copy(
                selections = data.selections + (userId to selection.copy(
                    seen = selection.seen + listingId,
                    favorites = if (like && listingId !in selection.favorites) selection.favorites + listingId else selection.favorites,
                )),
            )
        }
    }

    override suspend fun applyFilters(
        userId: String,
        filters: SearchFilters
    ) = select(userId) { it.copy(filters = filters.toRecord()) }

    override suspend fun publish(
        userId: String,
        listing: Listing
    ): Result<Unit> {
        return update { data ->
            if (data.listing(listing.id) != null) data else data.copy(
                listings = data.listings + listing.toRecord().copy(authorId = userId),
            )
        }
    }

    override suspend fun delete(
        userId: String,
        listingId: String
    ) = update {
        it.copy(listings = it.listings.map { listing ->
            if (listing.id == listingId && listing.authorId == userId) listing.copy(available = false) else listing
        })
    }
}

/** Domain fixtures for debug hosts and tests; production mocks use source records. */
fun sampleMatchData(): MatchData = sampleMatchRecords().toDomain()
