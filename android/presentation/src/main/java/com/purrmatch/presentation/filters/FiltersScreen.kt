package com.purrmatch.presentation.filters

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
    ScreenLayout("Фильтры", modifier, onBack, actions = {
        Button("Сбросить", onReset, style = ButtonStyle.Text)
    }, footer = {
        ActionFooter { Button("Показать анкеты", onApply, Modifier.fillMaxWidth()) }
    }) {
        FormContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField("Порода", filters.breed, listOf("Любая порода") + breeds, { onChange(filters.copy(breed = it)) })
                ChipGroup("Пол", listOf("Любой", "Кошка", "Кот"), filters.sex, { onChange(filters.copy(sex = it)) })
                DropdownField("Окрас", filters.color, listOf("Любой окрас", "Серый", "Рыжий", "Белый"), { onChange(filters.copy(color = it)) })
                DropdownField("Цвет глаз", filters.eyes, listOf("Любой цвет", "Янтарные", "Зелёные", "Голубые"), { onChange(filters.copy(eyes = it)) })
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Возраст", style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(filters.minAge, { onChange(filters.copy(minAge = it.filter(Char::isDigit))) }, "От", Modifier.weight(1f), "0 лет", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TextField(filters.maxAge, { onChange(filters.copy(maxAge = it.filter(Char::isDigit))) }, "До", Modifier.weight(1f), "20 лет", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                DropdownField("Город", filters.city, listOf("Выберите город") + cities, { onChange(filters.copy(city = it)) })
                ChipGroup("Условия передачи", listOf("Любые", "Бесплатно", "Платно"), filters.terms, { onChange(filters.copy(terms = it)) })
                TextField(filters.price, { onChange(filters.copy(price = it.filter(Char::isDigit))) }, "Цена до", placeholder = "Любая цена ₽",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }
}
