package com.purrmatch.domain.match

import java.time.LocalDate

/** Presentation supplies filter text; listing values remain typed. */
class BrowseListings {
    operator fun invoke(data: MatchData, userId: String, onDate: LocalDate = LocalDate.now()): Listing? {
        val selection = data.selection(userId)
        val filters = selection.filters
        return data.listings.firstOrNull { listing ->
            val age = listing.age(onDate)?.years
            val price = listing.price
            listing.available && listing.authorId != userId && listing.id !in selection.seen &&
                (filters.breed == "Любая порода" || filters.breed == listing.breed) &&
                (filters.sex == 0 || (filters.sex == 1) == (listing.sex == "кошка")) &&
                (filters.color == "Любой окрас" || filters.color.equals(listing.color, true)) &&
                (filters.eyes == "Любой цвет" || filters.eyes.equals(listing.eyes, true)) &&
                (filters.city == "Выберите город" || filters.city == listing.city) &&
                (filters.terms == 0 || (filters.terms == 1) == (price == 0L)) &&
                (filters.price.toLongOrNull()?.let { price <= it } ?: true) &&
                (filters.minAge.toIntOrNull()?.let { age != null && age >= it } ?: true) &&
                (filters.maxAge.toIntOrNull()?.let { age != null && age <= it } ?: true)
        }
    }
}
