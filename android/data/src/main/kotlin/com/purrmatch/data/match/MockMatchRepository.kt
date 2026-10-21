package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.io.ObjectOutputStream
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
    initialData: MatchData = sampleMatchData(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MatchRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val mutableData = MutableStateFlow(initialData)
    override val data = mutableData.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                file?.takeIf { it.exists() }?.let { target ->
                    runCatching { readMatchData(target) }
                        .getOrNull()?.let { mutableData.value = it }
                }
                ready.complete(Unit)
            }
        }
    }

    internal suspend fun awaitReady() = ready.await()

    private suspend fun update(transform: (MatchData) -> MatchData): Result<Unit> = withContext(ioDispatcher) {
        ready.await()
        mutex.withLock {
            runCatching {
                val next = transform(mutableData.value)
                file?.let { target ->
                    val temporary = File(target.parentFile, "${target.name}.tmp")
                    ObjectOutputStream(temporary.outputStream()).use { it.writeObject(next) }
                    if (!temporary.renameTo(target)) throw IOException("Cannot save mock data")
                }
                mutableData.value = next
            }
        }
    }

    private suspend fun select(userId: String, transform: (UserSelection) -> UserSelection) = update {
        it.copy(selections = it.selections + (userId to transform(it.selection(userId))))
    }

    override suspend fun ensureProfile(userId: String, login: String, name: String): Result<Unit> {
        return update { data ->
            if (data.profile(userId) != null) data else data.copy(
                profiles = data.profiles + Profile(id = userId, name = name, login = login,
                    city = "", about = "", telegram = "", phone = "", website = ""),
            )
        }
    }

    override suspend fun saveProfile(profile: Profile) = update {
        it.copy(profiles = it.profiles.filterNot { p -> p.id == profile.id } + profile)
    }

    override suspend fun toggleFavorite(userId: String, listingId: String): Result<Unit> {
        return update { data ->
            if (data.listing(listingId) == null) data else data.copy(
                selections = data.selections + (userId to data.selection(userId).let {
                    it.copy(favorites = if (listingId in it.favorites) it.favorites - listingId else it.favorites + listingId)
                }),
            )
        }
    }

    override suspend fun swipe(userId: String, listingId: String, like: Boolean): Result<Unit> {
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

    override suspend fun applyFilters(userId: String, filters: SearchFilters) = select(userId) { it.copy(filters = filters) }

    override suspend fun publish(userId: String, listing: Listing): Result<Unit> {
        return update { data ->
            if (data.listing(listing.id) != null) data else data.copy(
                listings = data.listings + listing.copy(authorId = userId),
            )
        }
    }

    override suspend fun delete(userId: String, listingId: String) = update {
        it.copy(listings = it.listings.map { listing ->
            if (listing.id == listingId && listing.authorId == userId) listing.copy(available = false) else listing
        })
    }
}

fun sampleMatchData(): MatchData {
    val anna = Profile(id = "anna", name = "Анна", login = "anna_cats", city = "Москва",
        about = "Помогаю кошкам находить заботливый дом. Отвечаю на сообщения каждый день.",
        telegram = "@anna_cats", phone = "+7 (•••) •••-••-••", website = "example.com")
    val mia = Listing("mia", "Мия", authorId = "anna", breed = "Британская короткошёрстная",
        sex = "кошка", birthday = LocalDate.of(2024, 5, 14), color = "Серый", eyes = "Янтарные",
        character = "Ласковая, Спокойная, Любопытная", city = "Москва",
        photos = List(3) { ListingPhoto("sample:detail") },
        description = "Мия любит играть и тихо мурлыкать рядом с человеком. Приучена к лотку.")
    return MatchData(
        profiles = listOf(anna, Profile(id = "olga", name = "Ольга", login = "olga_cats", city = "Казань",
            about = "Забочусь о кошках из приюта.", telegram = "@olga_cats", website = "shelter.example.com")),
        listings = listOf(
            mia,
            mia.copy(id = "peach", name = "Персик", breed = "Метис", sex = "кот", birthday = LocalDate.of(2026, 3, 14), price = 5_000),
            mia.copy(id = "sonya", name = "Соня", breed = "Домашняя кошка", birthday = LocalDate.of(2022, 5, 14), color = "Рыжая",
                description = "Спокойная и дружелюбная кошка.", available = false),
            mia.copy(id = "barsik", name = "Барсик", authorId = "olga", breed = "Метис", sex = "кот", city = "Казань"),
        ),
        selections = mapOf("anna" to UserSelection(favorites = listOf("mia", "peach", "sonya"))),
    )
}
