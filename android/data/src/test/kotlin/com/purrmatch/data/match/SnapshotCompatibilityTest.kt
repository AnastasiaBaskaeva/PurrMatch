package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SnapshotCompatibilityTest {
    private data class Fixture(val file: String, val id: String, val login: String, val password: String, val expected: MatchData)

    private fun fixtures(): List<Fixture> {
        val typed = sampleMatchData()
        val legacy = typed.copy(listings = typed.listings.map {
            it.copy(birthday = LocalDate.of(2024, 5, 14), photos = if (it.id == "mia")
                listOf(ListingPhoto("sample:upload"), ListingPhoto("picked:2")) else it.photos)
        })
        fun owner(id: String, login: String) = Profile(id, "Светлана", login, "Тула", "Описание автора",
            "@legacy_owner", "+79991234567", "owner.example.com")
        fun cat(id: String) = Listing("compat-cat", "Луна", id, "Метис", "кот", LocalDate.of(2024, 2, 29),
            "Чёрный", "Зелёные", "Игривый", "Полное описание", "Тула", 12_345,
            listOf(ListingPhoto("sample:upload"), ListingPhoto("picked:1"), ListingPhoto("picked:3")),
            dogs = false, cats = true, children = false)
        val selection = UserSelection(listOf("mia", "compat-cat"), listOf("peach", "sonya"),
            SearchFilters("Метис", 2, "Чёрный", "Зелёные", "1", "7", "Тула", 2, "12345"))
        val authId = "auth18-owner"
        val typedId = "typed19-owner"
        return listOf(
            Fixture("matches-v1.bin", "anna", "anna_cats", "purrmatch-demo", legacy),
            Fixture("auth18-wrapped-v1.bin", authId, "auth18_owner", "auth18-password-🐈", legacy.copy(
                profiles = legacy.profiles + owner(authId, "auth18_owner"),
                listings = legacy.listings + cat(authId), selections = legacy.selections + (authId to selection))),
            Fixture("typed19-matches-v2.bin", typedId, "typed19_owner", "purrmatch-demo", typed.copy(
                profiles = typed.profiles + owner(typedId, "typed19_owner").copy(surname = "Иванова",
                    coordinates = Coordinates(54.193, 37.617), whatsapp = "+79997654321",
                    verification = VerificationStatus.APPROVED, catteryName = "Питомник"),
                listings = typed.listings + cat(typedId).copy(photos = listOf(ListingPhoto("local:typed-cover"),
                    ListingPhoto("sample:upload")), coordinates = Coordinates(56.0, 37.0)),
                selections = typed.selections + (typedId to selection))),
        )
    }

    private fun copyFixture(fixture: Fixture, file: File) {
        javaClass.getResourceAsStream("/${fixture.file}")!!.use { input -> file.outputStream().use(input::copyTo) }
    }

    @Test fun allPreviousFormatsKeepEveryFieldAndPasswordThroughCombinedRegistrationAndRestart() = runBlocking {
        for (fixture in fixtures()) {
            val directory = Files.createTempDirectory("compatible-snapshot").toFile()
            try {
                val file = File(directory, "matches")
                copyFixture(fixture, file)
                val repository = MockMatchRepository(file)
                assertEquals(fixture.file, fixture.id, repository.authenticate(fixture.login, fixture.password).getOrThrow())
                assertTrue(repository.authenticate(fixture.login, "wrong-password").isFailure)
                if (fixture.id == "auth18-owner") assertTrue(repository.authenticate(fixture.login, "purrmatch-demo").isFailure)
                assertEquals(fixture.file, fixture.expected, repository.data.value)
                val id = repository.register("after-upgrade", "new-salted-password", "Новый").getOrThrow()
                val expected = repository.data.value
                assertEquals(fixture.expected.listings, expected.listings)
                assertEquals(fixture.expected.selections, expected.selections)
                assertEquals(fixture.expected.profiles, expected.profiles.dropLast(1))
                assertTrue(readMatchSnapshot(file).credentials.keys.containsAll(expected.profiles.map { it.id }))
                val restored = MockMatchRepository(file)
                assertEquals(fixture.id, restored.authenticate(fixture.login, fixture.password).getOrThrow())
                assertEquals(id, restored.authenticate("after-upgrade", "new-salted-password").getOrThrow())
                assertTrue(restored.authenticate("after-upgrade", "purrmatch-demo").isFailure)
                assertEquals(expected, restored.data.value)
                assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("new-salted-password"))
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun failedInitialReadRetriesEveryPreviousFormatWithoutResettingCredentialsOrData() = runBlocking {
        for (fixture in fixtures()) {
            val directory = Files.createTempDirectory("retry-compatible-snapshot").toFile()
            try {
                val file = File(directory, "matches")
                copyFixture(fixture, file)
                val bytes = file.readBytes()
                file.writeText("temporarily unreadable")
                val repository = MockMatchRepository(file)
                assertTrue(repository.authenticate(fixture.login, fixture.password).isFailure)
                assertTrue(repository.register("unsafe", "unsafe-password", "Unsafe").isFailure)
                assertTrue(repository.toggleFavorite(fixture.id, "mia").isFailure)
                assertEquals("temporarily unreadable", file.readText())
                file.delete()
                assertTrue(repository.authenticate("anna_cats", "purrmatch-demo").isFailure)
                assertTrue(repository.register("unsafe", "unsafe-password", "Unsafe").isFailure)
                assertFalse(file.exists())
                file.writeBytes(bytes)
                assertEquals(fixture.id, repository.authenticate(fixture.login, fixture.password).getOrThrow())
                assertEquals(fixture.expected, repository.data.value)
                repository.toggleFavorite(fixture.id, "barsik").getOrThrow()
                val restored = MockMatchRepository(file)
                assertEquals(fixture.id, restored.authenticate(fixture.login, fixture.password).getOrThrow())
                assertEquals(repository.data.value, restored.data.value)
            } finally { directory.deleteRecursively() }
        }
    }
}
