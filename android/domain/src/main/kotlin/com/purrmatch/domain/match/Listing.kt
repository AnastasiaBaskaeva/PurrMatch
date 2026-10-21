package com.purrmatch.domain.match

import java.io.Serializable
import java.time.LocalDate
import java.time.Period

/** An opaque photo reference resolved by the data/presentation adapter. */
data class ListingPhoto(val reference: String) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}

data class Listing(
    val id: String, val name: String, val authorId: String = "", val breed: String = "",
    val sex: String = "кошка", val birthday: LocalDate? = null,
    val color: String = "", val eyes: String = "", val character: String = "",
    val description: String = "", val city: String = "",
    /** Transfer price in whole Russian rubles; zero means free. */
    val price: Long = 0,
    val photos: List<ListingPhoto> = emptyList(),
    val coordinates: Coordinates? = null,
    val available: Boolean = true,
    val dogs: Boolean = true, val cats: Boolean = true, val children: Boolean = true,
) : Serializable {
    private companion object { private const val serialVersionUID = 2L }

    init { require(price >= 0) }

    /** Null means an unknown or future birth date, never a guessed age. */
    fun age(onDate: LocalDate = LocalDate.now()): Period? =
        birthday?.takeUnless { it.isAfter(onDate) }?.let { Period.between(it, onDate) }
}
