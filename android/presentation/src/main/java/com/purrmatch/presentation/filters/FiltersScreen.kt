package com.purrmatch.presentation.filters

import androidx.compose.ui.res.stringArrayResource
import com.purrmatch.presentation.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.purrmatch.presentation.preview.ScreenPreview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.breeds
import com.purrmatch.presentation.listing.cities

@Composable
fun FiltersScreen(filters: Filters, onChange: (Filters) -> Unit, onReset: () -> Unit, onBack: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    ScreenLayout(stringResource(R.string.filters), modifier, onBack, actions = {
        Button(stringResource(R.string.reset), onReset, style = ButtonStyle.Text)
    }, footer = {
        ActionFooter { Button(stringResource(R.string.show_listings), onApply, Modifier.fillMaxWidth()) }
    }) {
        FormContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    stringResource(R.string.breed_label),
                    filters.breed,
                    listOf(stringResource(R.string.breed_any)) + breeds,
                    { onChange(filters.copy(breed = it)) },
                )
                ChipGroup(
                    stringResource(R.string.sex_label),
                    stringArrayResource(R.array.filter_sexes).toList(),
                    filters.sex,
                    { onChange(filters.copy(sex = it)) },
                )
                DropdownField(
                    stringResource(R.string.color_label),
                    filters.color,
                    stringArrayResource(R.array.filter_colors).toList(),
                    { onChange(filters.copy(color = it)) },
                )
                DropdownField(
                    stringResource(R.string.eye_color_label),
                    filters.eyes,
                    stringArrayResource(R.array.filter_eyes).toList(),
                    { onChange(filters.copy(eyes = it)) },
                )
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.age_label), style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            filters.minAge,
                            { onChange(filters.copy(minAge = it.filter(Char::isDigit))) },
                            stringResource(R.string.from_label),
                            Modifier.weight(1f),
                            stringResource(R.string.min_age_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        TextField(
                            filters.maxAge,
                            { onChange(filters.copy(maxAge = it.filter(Char::isDigit))) },
                            stringResource(R.string.to_label),
                            Modifier.weight(1f),
                            stringResource(R.string.max_age_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                DropdownField(
                    stringResource(R.string.city_label),
                    filters.city,
                    listOf(stringResource(R.string.select_city)) + cities,
                    { onChange(filters.copy(city = it)) },
                )
                ChipGroup(
                    stringResource(R.string.transfer_terms),
                    stringArrayResource(R.array.filter_terms).toList(),
                    filters.terms,
                    { onChange(filters.copy(terms = it)) },
                )
                TextField(
                    filters.price,
                    { onChange(filters.copy(price = it.filter(Char::isDigit))) },
                    stringResource(R.string.max_price_label),
                    placeholder = stringResource(R.string.price_any_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

@Preview(name = "Фильтры", widthDp = 376, heightDp = 803)
@Composable
private fun FiltersScreenPreview() {
    var filters by remember { mutableStateOf(Filters()) }
    ScreenPreview {
        FiltersScreen(filters, { filters = it }, { filters = Filters() }, {}, {})
    }
}
