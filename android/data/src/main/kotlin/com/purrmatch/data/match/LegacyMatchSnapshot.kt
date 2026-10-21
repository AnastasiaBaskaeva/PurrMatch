package com.purrmatch.data.match

import com.purrmatch.domain.match.*
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Only the original prototype snapshot is remapped; new snapshots use the domain types. */
internal fun readMatchData(file: File): MatchData = object : ObjectInputStream(file.inputStream()) {
    private var legacySnapshot = false

    override fun readClassDescriptor(): ObjectStreamClass {
        val descriptor = super.readClassDescriptor()
        if (descriptor.name == "com.purrmatch.domain.match.MatchData") {
            legacySnapshot = descriptor.fields.any { it.name == "photoSlots" }
        }
        val legacy = when (descriptor.name) {
            "[Lcom.purrmatch.domain.match.Listing;" -> arrayOfNulls<LegacyListing>(0).javaClass.takeIf { legacySnapshot }
            "[Lcom.purrmatch.domain.match.Profile;" -> arrayOfNulls<LegacyProfile>(0).javaClass.takeIf { legacySnapshot }
            "com.purrmatch.domain.match.MatchData" -> LegacyMatchData::class.java.takeIf {
                descriptor.fields.any { it.name == "photoSlots" }
            }
            "com.purrmatch.domain.match.Listing" -> LegacyListing::class.java.takeIf {
                descriptor.fields.any { it.name == "birthday" && it.typeString == "Ljava/lang/String;" }
            }
            "com.purrmatch.domain.match.Profile" -> LegacyProfile::class.java.takeUnless {
                descriptor.fields.any { it.name == "surname" }
            }
            else -> null
        }
        return legacy?.let(ObjectStreamClass::lookup) ?: descriptor
    }
}.use { stream ->
    when (val snapshot = stream.readObject()) {
        is MatchData -> snapshot
        is LegacyMatchData -> snapshot.toDomain()
        else -> error("Unsupported match snapshot")
    }
}

// Field names/types deliberately match the original Serializable classes.
private class LegacyMatchData(
    val profiles: List<LegacyProfile>, val listings: List<LegacyListing>,
    val selections: Map<String, UserSelection>, val photoSlots: Map<String, List<Int>>,
) : Serializable {
    fun toDomain() = MatchData(profiles.map { it.toDomain() }, listings.map { listing ->
        listing.toDomain(photoSlots[listing.id])
    }, selections)
}

private class LegacyProfile(
    val id: String, val name: String, val login: String, val city: String,
    val about: String, val telegram: String, val phone: String, val website: String,
) : Serializable {
    fun toDomain() = Profile(id = id, name = name, login = login, city = city, about = about,
        telegram = telegram, phone = phone, website = website)
}

private class LegacyListing(
    val id: String, val name: String, val authorId: String, val breed: String,
    val sex: String, val age: String, val birthday: String, val color: String, val eyes: String,
    val character: String, val description: String, val city: String, val price: String,
    val available: Boolean, val dogs: Boolean, val cats: Boolean, val children: Boolean,
) : Serializable {
    fun toDomain(slots: List<Int>?) = Listing(id = id, name = name, authorId = authorId,
        breed = breed, sex = sex, birthday = parseBirthday(birthday), color = color, eyes = eyes,
        character = character, description = description, city = city,
        price = price.filter(Char::isDigit).toLongOrNull() ?: 0,
        photos = slots?.map { ListingPhoto(if (it == -1) "sample:upload" else "picked:$it") }
            ?: List(3) { ListingPhoto("sample:detail") },
        available = available, dogs = dogs, cats = cats, children = children)
}

private fun parseBirthday(value: String): LocalDate? {
    val formats = listOf("dd.MM.uuuu", "d MMMM uuuu")
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru"))
                .withResolverStyle(ResolverStyle.STRICT))
        }.getOrNull()
    }
}
