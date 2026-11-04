package com.purrmatch.data.backend

import com.purrmatch.data.backend.dto.*
import com.purrmatch.domain.match.Coordinates
import com.purrmatch.domain.match.SearchFilters
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class FixtureContractTest {
    private val scenarios = listOf("default", "catalog", "no-results", "empty-favorites", "complete-profile")

    @Test fun allScenariosStrictlyDecodeRoundTripAndPreserveIdentityLinks() {
        for (name in scenarios) {
            val fixture = MockFixtures.load(name)
            assertEquals(fixture, MockFixtures.json.decodeFromString<MockFixtureDto>(MockFixtures.json.encodeToString(fixture)))
            val domain = fixture.toDomain()
            assertEquals(fixture.profiles, domain.profiles.map { it.toDto() })
            assertEquals(fixture.listings, domain.listings.map { it.toDto() })
            assertEquals(fixture.selections, domain.selections.mapValues { it.value.toDto() })
            val users = fixture.profiles.map { it.id }.toSet()
            val listings = fixture.listings.map { it.id }.toSet()
            assertEquals(users.size, fixture.profiles.size)
            assertEquals(listings.size, fixture.listings.size)
            assertTrue(fixture.listings.all { it.authorId in users })
            assertTrue(fixture.selections.all { (id, selection) ->
                id in users && (selection.favorites + selection.seen).all { it in listings }
            })
            assertEquals(users, fixture.credentials.map { it.userId }.toSet())
            fixture.credentials.forEach { credential ->
                assertEquals(fixture.profiles.single { it.id == credential.userId }.login, credential.login)
                assertEquals("purrmatch-demo", credential.password)
                assertEquals(credential, MockFixtures.json.decodeFromString<CredentialDto>(MockFixtures.json.encodeToString(credential)))
            }
            (fixture.profiles.mapNotNull { it.coordinates } + fixture.listings.mapNotNull { it.coordinates }).forEach {
                assertTrue(it.latitude.isFinite() && it.latitude in -90.0..90.0)
                assertTrue(it.longitude.isFinite() && it.longitude in -180.0..180.0)
                assertEquals(it, it.toDomain().toDto())
            }
        }
    }

    @Test fun defaultFixtureIsDiverseAndContainsOnlyOpaqueMockPhotos() {
        val fixture = MockFixtures.load()
        assertEquals(28, fixture.listings.size)
        assertTrue(fixture.profiles.size >= 6)
        assertTrue(fixture.listings.map { it.city }.distinct().size >= 6)
        assertTrue(fixture.listings.map { it.breed }.distinct().size >= 6)
        assertTrue(fixture.listings.any { it.price == 0L } && fixture.listings.any { it.price > 0L })
        assertTrue(fixture.listings.any { it.birthday == null })
        assertTrue(fixture.listings.any { !it.dogs } && fixture.listings.any { !it.cats } && fixture.listings.any { !it.children })
        fixture.listings.forEach { listing ->
            assertTrue(listing.photos.isNotEmpty())
            assertEquals(listing.photos.indices.map { "mock://listing/${listing.id}/${it + 1}.jpg" }, listing.photos)
            assertTrue(listing.photos.none { it.startsWith("sample:") || it.startsWith("picked:") })
            assertNotNull(listing.coordinates)
        }
        assertEquals(listOf("mia", "peach", "sonya"), fixture.selections.getValue("anna").favorites)
        assertFalse(fixture.listings.single { it.id == "sonya" }.available)
        assertTrue(fixture.listings.any { it.id == "barsik" })
    }

    @Test fun scenarioSpecificStatesAndLoaderAllowlistRemainExplicit() {
        assertTrue(MockFixtures.load("no-results").listings.isEmpty())
        assertTrue(MockFixtures.load("empty-favorites").selections.getValue("anna").favorites.isEmpty())
        val anna = MockFixtures.load("complete-profile").profiles.single { it.id == "anna" }
        assertTrue(listOf(anna.city, anna.about, anna.telegram, anna.phone, anna.website, anna.whatsapp).all { it.isEmpty() })
        assertNull(anna.coordinates)
        val catalog = MockFixtures.load("catalog")
        assertFalse(catalog.listings.any { it.id == "barsik" })
        assertEquals("Помогаю кошкам находить дом", catalog.profiles.single { it.id == "anna" }.about)
        assertThrows(IllegalArgumentException::class.java) { MockFixtures.load("../default") }
        assertThrows(IllegalArgumentException::class.java) { MockFixtures.load("errors") }
    }

    @Test fun unknownFieldsAreRejectedInsteadOfSilentlyDrifting() {
        assertThrows(SerializationException::class.java) {
            MockFixtures.json.decodeFromString<ProfileDto>("""{"id":"anna","verified":true}""")
        }
        assertThrows(SerializationException::class.java) {
            MockFixtures.json.decodeFromString<ListingDto>("""{"id":"cat","name":"Кот","cattery":"unknown"}""")
        }
        assertThrows(IllegalArgumentException::class.java) { CoordinatesDto(91.0, 0.0).toDomain() }
        assertThrows(IllegalArgumentException::class.java) { CoordinatesDto(0.0, Double.NaN).toDomain() }
    }

    @Test fun filtersTranslateUiValuesToTypedJsonAndBack() {
        val filters = SearchFilters(breed = "Метис", sex = 2, color = "Серый", eyes = "Зелёные",
            minAge = "1", maxAge = "8", city = "Казань", terms = 2, price = "25000")
        assertEquals(FiltersDto("Метис", "кот", "Серый", "Зелёные", "Казань", 1, 8, 25000, false), filters.toDto())
        assertEquals(filters, filters.toDto().toDomain())
        assertEquals(SearchFilters(), FiltersDto().toDomain())
        assertEquals(FiltersDto(), SearchFilters().toDto())
        assertEquals(true, SearchFilters(terms = 1).toDto().freeOnly)
        assertEquals("кошка", SearchFilters(sex = 1).toDto().sex)
        val blankFilters = SearchFilters(breed = "", color = "", eyes = "", city = "")
        assertEquals(FiltersDto(breed = "", color = "", eyes = "", city = ""), blankFilters.toDto())
        assertEquals(blankFilters, blankFilters.toDto().toDomain())
        assertEquals(Coordinates(55.75, 37.61), CoordinatesDto(55.75, 37.61).toDomain())
    }

    @Test fun failureRulesAndStandaloneResponseSamplesUseTheirActualDtos() {
        val rules = MockFixtures.failures()
        assertEquals(setOf("/auth/login", "/auth/register"), rules.map { it.path }.toSet())
        rules.forEach {
            assertEquals("POST", it.method)
            assertEquals("server_error", it.login)
            assertEquals(500, it.status)
            assertEquals("INTERNAL_ERROR", it.error.code)
            assertTrue(it.error.message.isNotBlank())
            assertEquals(it, MockFixtures.json.decodeFromString<FailureRuleDto>(MockFixtures.json.encodeToString(it)))
        }
        assertEquals("anna_cats", MockFixtures.json.decodeFromString<LoginRequestDto>(sample("auth-login-request")).login)
        assertEquals("anna", MockFixtures.json.decodeFromString<AuthResponseDto>(sample("auth-response")).userId)
        assertEquals("anna", MockFixtures.json.decodeFromString<ProfileDto>(sample("profile-response")).id)
        assertEquals("mia", MockFixtures.json.decodeFromString<ListingDto>(sample("listing-response")).id)
        assertEquals(listOf("mia", "peach"), MockFixtures.json.decodeFromString<List<ListingDto>>(sample("listings-response")).map { it.id })
        assertEquals("INVALID_CREDENTIALS", MockFixtures.json.decodeFromString<ErrorDto>(sample("error-response")).code)
    }

    private fun sample(name: String) = checkNotNull(javaClass.getResourceAsStream("/mock/examples/$name.json"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
