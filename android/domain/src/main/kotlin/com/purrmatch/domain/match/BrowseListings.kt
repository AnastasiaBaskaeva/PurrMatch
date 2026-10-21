package com.purrmatch.domain.match

/** The string-based prototype filters are preserved until the geography/age migration. */
class BrowseListings {
    operator fun invoke(data: MatchData, userId: String): Listing? {
        val selection = data.selection(userId)
        val filters = selection.filters
        return data.listings.firstOrNull { listing ->
            val age = if (listing.age.contains("мес")) 0 else listing.age.filter(Char::isDigit).toIntOrNull() ?: 0
            val price = listing.price.filter(Char::isDigit).toIntOrNull() ?: 0
            listing.available && listing.id !in selection.seen &&
                (filters.breed == "Любая порода" || filters.breed == listing.breed) &&
                (filters.sex == 0 || (filters.sex == 1) == (listing.sex == "кошка")) &&
                (filters.color == "Любой окрас" || filters.color.equals(listing.color, true)) &&
                (filters.eyes == "Любой цвет" || filters.eyes.equals(listing.eyes, true)) &&
                (filters.city == "Выберите город" || filters.city == listing.city) &&
                (filters.terms == 0 || (filters.terms == 1) == (price == 0)) &&
                (filters.price.toIntOrNull()?.let { price <= it } ?: true) &&
                (filters.minAge.toIntOrNull()?.let { age >= it } ?: true) &&
                (filters.maxAge.toIntOrNull()?.let { age <= it } ?: true)
        }
    }
}
