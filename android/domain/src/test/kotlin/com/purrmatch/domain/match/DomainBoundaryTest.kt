package com.purrmatch.domain.match

import java.io.Serializable
import org.junit.Assert.assertFalse
import org.junit.Test

class DomainBoundaryTest {

    @Test
    fun modelsDoNotImplementAStorageSerializationContract() {
        val models = listOf(
            MatchData::class.java,
            Listing::class.java,
            ListingPhoto::class.java,
            Profile::class.java,
            Coordinates::class.java,
            SearchFilters::class.java,
            UserSelection::class.java,
        )
        for (model in models) {
            assertFalse(
                model.name,
                Serializable::class.java.isAssignableFrom(model)
            )
            assertFalse(
                model.name,
                model.declaredFields.any { it.name == "serialVersionUID" })
        }
    }
}
