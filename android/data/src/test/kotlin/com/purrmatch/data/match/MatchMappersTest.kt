package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MatchMappersTest {

    private fun domainData(): MatchData = MatchData(
        profiles = listOf(
            Profile(
                id = "owner",
                name = "Анна",
                login = "owner_login",
                city = "Москва",
                about = "Описание",
                telegram = "@owner",
                phone = "+79991234567",
                website = "https://example.com",
                surname = "Иванова",
                coordinates = Coordinates(
                    55.75,
                    37.61
                ),
                whatsapp = "+79997654321",
                verification = VerificationStatus.APPROVED,
                catteryName = "Луна",
            )
        ),
        listings = listOf(
            Listing(
                id = "cat",
                name = "Мия",
                authorId = "owner",
                breed = "Метис",
                sex = "кот",
                birthday = LocalDate.of(
                    2024,
                    2,
                    29
                ),
                color = "Чёрный",
                eyes = "Зелёные",
                character = "Игривый",
                description = "Описание кошки",
                city = "Казань",
                price = 12345,
                photos = listOf(
                    ListingPhoto("local:cover"),
                    ListingPhoto("picked:2")
                ),
                coordinates = Coordinates(
                    55.8,
                    49.1
                ),
                available = false,
                dogs = false,
                cats = false,
                children = false,
            )
        ),
        selections = mapOf(
            "reader" to UserSelection(
                favorites = listOf("cat"),
                seen = listOf(
                    "other",
                    "cat"
                ),
                filters = SearchFilters(
                    "Метис",
                    2,
                    "Чёрный",
                    "Зелёные",
                    "1",
                    "7",
                    "Казань",
                    2,
                    "12345"
                ),
            )
        ),
    )

    @Test
    fun everyFieldSurvivesMappingAndNullsRemainUnknown() {
        val expected = domainData()
        val source = expected.toRecord()
        assertEquals(
            "2024-02-29",
            source.listings.single().birthday
        )
        assertEquals(
            listOf(
                "local:cover",
                "picked:2"
            ),
            source.listings.single().photos
        )
        assertEquals(
            VerificationRecord.APPROVED,
            source.profiles.single().verification
        )
        assertEquals(
            expected,
            source.toDomain()
        )
        val empty = MatchRecords(
            listOf(ProfileRecord()),
            listOf(
                ListingRecord(
                    "unknown",
                    "Кошка"
                )
            )
        )
        assertNull(empty.toDomain().listings.single().birthday)
        assertNull(empty.toDomain().listings.single().coordinates)
        assertNull(empty.toDomain().profiles.single().coordinates)
        assertEquals(
            empty,
            empty.toDomain().toRecord()
        )
        for (status in VerificationStatus.entries) {
            val profile = expected.profiles.single().copy(verification = status)
            assertEquals(
                profile,
                profile.toRecord().toDomain()
            )
        }
    }

    @Test
    fun debugSaveAdapterUsesOnlySourceRecords() {
        val expected = domainData()
        val bytes = encodeMockMatchData(expected)
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("com.purrmatch.domain"))
        assertEquals(
            expected,
            decodeMockMatchData(bytes)
        )
    }

    @Test
    fun snapshotsContainOnlySourceModelsAndRestoreAllDomainFields() = runBlocking {
        val directory = Files.createTempDirectory("source-models").toFile()
        try {
            val file = File(
                directory,
                "matches"
            )
            val expected = domainData()
            val repository = MockMatchRepository(
                file,
                initialData = expected
            )
            repository.applyFilters(
                "reader",
                expected.selection("reader").filters
            ).getOrThrow()
            val wire = file.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(wire.contains("com.purrmatch.data.match.MatchRecords"))
            assertFalse(wire.contains("com.purrmatch.domain"))
            assertEquals(
                expected.toRecord(),
                readMatchSnapshot(file).data
            )
            val restored = MockMatchRepository(file)
            restored.awaitReady()
            assertEquals(
                expected,
                restored.data.value
            )
            assertEquals(
                "owner",
                restored.authenticate(
                    "owner_login",
                    "purrmatch-demo"
                ).getOrThrow()
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
