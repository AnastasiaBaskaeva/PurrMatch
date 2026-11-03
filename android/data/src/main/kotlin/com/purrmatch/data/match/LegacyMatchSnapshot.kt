package com.purrmatch.data.match

import java.io.Externalizable
import java.io.ObjectInput
import java.io.ObjectOutput
import java.io.InvalidObjectException
import java.io.IOException
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Decoder-only surrogates accept both old and typed payloads without altering stored credentials. */
internal fun readCompatibleMatchSnapshot(file: File): StoredMatches = object : ObjectInputStream(file.inputStream()) {
    private var legacySnapshot = false

    override fun readClassDescriptor(): ObjectStreamClass {
        val descriptor = super.readClassDescriptor()
        if (descriptor.name == "com.purrmatch.domain.match.MatchData") {
            legacySnapshot = descriptor.fields.any { it.name == "photoSlots" }
        }
        val legacy = when (descriptor.name) {
            "com.purrmatch.data.match.StoredMatches" -> DecodedStoredMatches::class.java
            "[Lcom.purrmatch.domain.match.Listing;" -> if (legacySnapshot) arrayOfNulls<LegacyListing>(0).javaClass else arrayOfNulls<TypedListing>(0).javaClass
            "[Lcom.purrmatch.domain.match.Profile;" -> if (legacySnapshot) arrayOfNulls<LegacyProfile>(0).javaClass else arrayOfNulls<TypedProfile>(0).javaClass
            "com.purrmatch.domain.match.MatchData" -> if (legacySnapshot) LegacyMatchData::class.java else TypedMatchData::class.java
            "com.purrmatch.domain.match.Listing" -> if (descriptor.fields.any {
                    it.name == "birthday" && it.typeString == "Ljava/lang/String;"
                }) LegacyListing::class.java else TypedListing::class.java

            "com.purrmatch.domain.match.Profile" -> if (descriptor.fields.any {
                    it.name == "surname"
                }) TypedProfile::class.java else LegacyProfile::class.java

            "[Lcom.purrmatch.domain.match.ListingPhoto;" -> arrayOfNulls<TypedPhoto>(0).javaClass
            "com.purrmatch.domain.match.ListingPhoto" -> TypedPhoto::class.java
            "com.purrmatch.domain.match.Coordinates" -> TypedCoordinates::class.java
            "com.purrmatch.domain.match.VerificationStatus" -> VerificationRecord::class.java
            "com.purrmatch.domain.match.SearchFilters" -> FiltersRecord::class.java
            "com.purrmatch.domain.match.UserSelection" -> SelectionRecord::class.java
            "java.time.Ser", "j$.time.Ser" -> LegacyDate::class.java
            else -> null
        }
        return legacy?.let(ObjectStreamClass::lookup) ?: descriptor
    }
}.use { stream ->
    when (val snapshot = stream.readObject()) {
        is TypedMatchData -> legacyMatchSnapshot(snapshot.toRecord())
        is LegacyMatchData -> legacyMatchSnapshot(snapshot.toRecord())
        is DecodedStoredMatches -> StoredMatches(
            snapshot.sourceData(),
            snapshot.credentials
        )

        else -> throw IOException("Unsupported match snapshot")
    }
}

// Object slots share a wire shape; decode old wrappers and new source records here.
private class DecodedStoredMatches(
    val data: Any,
    val credentials: Map<String, PasswordVerifier>,
) : Serializable {

    fun sourceData(): MatchRecords = when (val value = data) {
        is MatchRecords -> value
        is TypedMatchData -> value.toRecord()
        is LegacyMatchData -> value.toRecord()
        else -> throw IOException("Unsupported wrapped match data")
    }
}

// Field names/types deliberately match the original Serializable classes.
private class LegacyMatchData(
    val profiles: List<LegacyProfile>,
    val listings: List<LegacyListing>,
    val selections: Map<String, SelectionRecord>,
    val photoSlots: Map<String, List<Int>>,
) : Serializable {

    fun toRecord() = MatchRecords(
        profiles.map { it.toRecord() },
        listings.map { listing ->
            listing.toRecord(photoSlots[listing.id])
        },
        selections
    )
}

private class LegacyProfile(
    val id: String,
    val name: String,
    val login: String,
    val city: String,
    val about: String,
    val telegram: String,
    val phone: String,
    val website: String,
) : Serializable {

    fun toRecord() = ProfileRecord(
        id = id,
        name = name,
        login = login,
        city = city,
        about = about,
        telegram = telegram,
        phone = phone,
        website = website
    )
}

private class LegacyListing(
    val id: String,
    val name: String,
    val authorId: String,
    val breed: String,
    val sex: String,
    val age: String,
    val birthday: String,
    val color: String,
    val eyes: String,
    val character: String,
    val description: String,
    val city: String,
    val price: String,
    val available: Boolean,
    val dogs: Boolean,
    val cats: Boolean,
    val children: Boolean,
) : Serializable {

    fun toRecord(slots: List<Int>?) =
        ListingRecord(
            id = id,
            name = name,
            authorId = authorId,
            breed = breed,
            sex = sex,
            birthday = parseBirthday(birthday)?.toString(),
            color = color,
            eyes = eyes,
            character = character,
            description = description,
            city = city,
            price = price.filter(Char::isDigit).toLongOrNull() ?: 0,
            photos = slots?.map { if (it == -1) "sample:upload" else "picked:$it" }
                ?: List(3) { "sample:detail" },
            available = available,
            dogs = dogs,
            cats = cats,
            children = children
        )
}

private fun parseBirthday(value: String): LocalDate? {
    val formats = listOf(
        "dd.MM.uuuu",
        "d MMMM uuuu"
    )
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalDate.parse(
                value,
                DateTimeFormatter.ofPattern(
                    pattern,
                    Locale.forLanguageTag("ru")
                )
                    .withResolverStyle(ResolverStyle.STRICT)
            )
        }.getOrNull()
    }
}

// Historical typed layouts. Their field types and names belong to this decoder only.
private class TypedMatchData(
    val profiles: List<TypedProfile>,
    val listings: List<TypedListing>,
    val selections: Map<String, SelectionRecord>,
) : Serializable {

    fun toRecord() = MatchRecords(
        profiles.map { it.toRecord() },
        listings.map { it.toRecord() },
        selections
    )
}

private class TypedPhoto(val reference: String) : Serializable
private class TypedCoordinates(
    val latitude: Double,
    val longitude: Double
) : Serializable {

    fun toRecord() = CoordinatesRecord(
        latitude,
        longitude
    )
}

private class TypedProfile(
    val id: String = "",
    val name: String = "",
    val login: String = "",
    val city: String = "",
    val about: String = "",
    val telegram: String = "",
    val phone: String = "",
    val website: String = "",
    val surname: String = "",
    val coordinates: TypedCoordinates? = null,
    val whatsapp: String = "",
    val verification: VerificationRecord = VerificationRecord.UNVERIFIED,
    val catteryName: String = "",
) : Serializable {

    fun toRecord() = ProfileRecord(
        id,
        name,
        login,
        city,
        about,
        telegram,
        phone,
        website,
        surname,
        coordinates?.toRecord(),
        whatsapp,
        verification,
        catteryName
    )
}

private class TypedListing(
    val id: String,
    val name: String,
    val authorId: String = "",
    val breed: String = "",
    val sex: String = "кошка",
    val birthday: Any? = null,
    val color: String = "",
    val eyes: String = "",
    val character: String = "",
    val description: String = "",
    val city: String = "",
    /** Transfer price in whole Russian rubles; zero means free. */
    val price: Long = 0,
    val photos: List<TypedPhoto> = emptyList(),
    val coordinates: TypedCoordinates? = null,
    val available: Boolean = true,
    val dogs: Boolean = true,
    val cats: Boolean = true,
    val children: Boolean = true,
) : Serializable {

    fun toRecord() = ListingRecord(
        id,
        name,
        authorId,
        breed,
        sex,
        birthday?.toString(),
        color,
        eyes,
        character,
        description,
        city,
        price,
        photos.map { it.reference },
        coordinates?.toRecord(),
        available,
        dogs,
        cats,
        children
    )
}

/** Decoder for the LocalDate serialization proxy, shared by native and desugared java.time.
 * Wire form: type 3, year (int), month (byte), day (byte).
 * See OpenJDK java.time.Ser and LocalDate.writeExternal; no platform date object enters a field.
 */
internal class LegacyDate : Externalizable {

    private var isoDate: String? = null

    override fun readExternal(input: ObjectInput) {
        if (input.readByte().toInt() != 3) throw InvalidObjectException("Unsupported historical date type")
        isoDate = LocalDate.of(
            input.readInt(),
            input.readByte().toInt(),
            input.readByte().toInt()
        ).toString()
    }

    override fun writeExternal(output: ObjectOutput) {
        throw UnsupportedOperationException("Decoder only")
    }

    private fun readResolve(): Any = checkNotNull(isoDate)
}
