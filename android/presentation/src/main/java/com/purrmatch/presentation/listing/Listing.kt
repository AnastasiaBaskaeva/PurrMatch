package com.purrmatch.presentation.listing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.purrmatch.presentation.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias Listing = com.purrmatch.domain.match.Listing

private val russian = Locale.forLanguageTag("ru")
private val birthdayFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
internal val inputBirthdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    .withResolverStyle(java.time.format.ResolverStyle.STRICT)

val Listing.birthdayText: String get() = birthday?.format(birthdayFormat).orEmpty()
val Listing.priceText: String
    @Composable get() = if (price == 0L) stringResource(R.string.free) else
        stringResource(R.string.price_rubles, String.format(russian, "%,d", price).replace('\u00a0', ' '))

val Listing.ageText: String
    @Composable get() = ageText(LocalDate.now())

@Composable
fun Listing.ageText(onDate: LocalDate): String {
    val age = age(onDate) ?: return ""
    val years = age.years
    if (years == 0) return stringResource(R.string.age_months, age.months)
    val format = when {
        years % 100 in 11..14 -> R.string.age_years_many
        years % 10 == 1 -> R.string.age_years_one
        years % 10 in 2..4 -> R.string.age_years_few
        else -> R.string.age_years_many
    }
    return stringResource(format, years)
}
