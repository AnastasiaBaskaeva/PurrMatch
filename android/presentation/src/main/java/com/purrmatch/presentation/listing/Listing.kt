package com.purrmatch.presentation.listing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias Listing = com.purrmatch.domain.match.Listing

private val russian = Locale.forLanguageTag("ru")
private val birthdayFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
internal val inputBirthdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    .withResolverStyle(java.time.format.ResolverStyle.STRICT)

val Listing.birthdayText: String get() = birthday?.format(birthdayFormat).orEmpty()
val Listing.priceText: String get() = if (price == 0L) "Бесплатно" else
    "${String.format(russian, "%,d", price).replace('\u00a0', ' ')} ₽"
val Listing.ageText: String get() = ageText(LocalDate.now())

fun Listing.ageText(onDate: LocalDate): String {
    val age = age(onDate) ?: return ""
    val years = age.years
    if (years == 0) return "${age.months} мес."
    val word = when {
        years % 100 in 11..14 -> "лет"
        years % 10 == 1 -> "год"
        years % 10 in 2..4 -> "года"
        else -> "лет"
    }
    return "$years $word"
}
