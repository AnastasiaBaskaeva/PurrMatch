package com.purrmatch.domain.match

import java.time.LocalDate
import java.time.Period
import org.junit.Assert.*
import org.junit.Test

class ListingTest {
    @Test fun ageComesOnlyFromBirthdayAndHandlesBirthdayBoundaries() {
        val listing = Listing("cat", "Кошка", birthday = LocalDate.of(2024, 5, 14))
        assertEquals(Period.of(1, 11, 29), listing.age(LocalDate.of(2026, 5, 13)))
        assertEquals(Period.ofYears(2), listing.age(LocalDate.of(2026, 5, 14)))
        assertNull(listing.age(LocalDate.of(2024, 5, 13)))
        assertNull(Listing("unknown", "Кошка").age(LocalDate.of(2026, 5, 14)))
        assertEquals(Period.of(0, 11, 30), Listing("leap", "Кошка", birthday = LocalDate.of(2024, 2, 29))
            .age(LocalDate.of(2025, 2, 28)))
    }

    @Test fun filtersUseNumericPriceAndComputedAge() {
        val listing = Listing("cat", "Кошка", birthday = LocalDate.of(2024, 5, 14), price = 5_000)
        val onDate = LocalDate.of(2026, 5, 14)
        fun browse(filters: SearchFilters) = BrowseListings()(MatchData(emptyList(), listOf(listing),
            mapOf("reader" to UserSelection(filters = filters))), "reader", onDate)
        assertEquals(listing, browse(SearchFilters(minAge = "2", maxAge = "2", price = "5000")))
        assertNull(browse(SearchFilters(maxAge = "1")))
        assertNull(browse(SearchFilters(price = "4999")))
        assertNull(browse(SearchFilters(terms = 1)))
    }

    @Test(expected = IllegalArgumentException::class) fun negativePriceIsRejected() {
        Listing("cat", "Кошка", price = -1)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesAreRejected() {
        Coordinates(Double.NaN, 37.6)
    }
}
