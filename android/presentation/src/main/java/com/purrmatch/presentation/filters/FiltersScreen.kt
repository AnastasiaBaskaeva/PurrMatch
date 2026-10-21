package com.purrmatch.presentation.filters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purrmatch.presentation.App
import com.purrmatch.presentation.Screen
import com.purrmatch.presentation.components.*
import com.purrmatch.presentation.listing.breeds
import com.purrmatch.presentation.listing.cities

@Stable
class Filters {
    var breed by mutableStateOf("Любая порода")
    var sex by mutableIntStateOf(0)
    var color by mutableStateOf("Любой окрас")
    var eyes by mutableStateOf("Любой цвет")
    var minAge by mutableStateOf("")
    var maxAge by mutableStateOf("")
    var city by mutableStateOf("Выберите город")
    var terms by mutableIntStateOf(0)
    var price by mutableStateOf("")
    fun reset() {
        breed = "Любая порода"; sex = 0; color = "Любой окрас"; eyes = "Любой цвет"
        minAge = ""; maxAge = ""; city = "Выберите город"; terms = 0; price = ""
    }
}

@Composable
fun FiltersScreen(filters: Filters, onBack: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    ScreenLayout("Фильтры", modifier, onBack, actions = {
        Button("Сбросить", filters::reset, style = ButtonStyle.Text)
    }, footer = {
        ActionFooter { Button("Показать анкеты", onApply, Modifier.fillMaxWidth()) }
    }) {
        FormContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField("Порода", filters.breed, listOf("Любая порода") + breeds, { filters.breed = it })
                ChipGroup("Пол", listOf("Любой", "Кошка", "Кот"), filters.sex, { filters.sex = it })
                DropdownField("Окрас", filters.color, listOf("Любой окрас", "Серый", "Рыжий", "Белый"), { filters.color = it })
                DropdownField("Цвет глаз", filters.eyes, listOf("Любой цвет", "Янтарные", "Зелёные", "Голубые"), { filters.eyes = it })
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Возраст", style = Typography.GroupLabel, color = Colors.GroupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(filters.minAge, { filters.minAge = it.filter(Char::isDigit) }, "От", Modifier.weight(1f), "0 лет", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TextField(filters.maxAge, { filters.maxAge = it.filter(Char::isDigit) }, "До", Modifier.weight(1f), "20 лет", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                DropdownField("Город", filters.city, listOf("Выберите город") + cities, { filters.city = it })
                ChipGroup("Условия передачи", listOf("Любые", "Бесплатно", "Платно"), filters.terms, { filters.terms = it })
                TextField(filters.price, { filters.price = it.filter(Char::isDigit) }, "Цена до", placeholder = "Любая цена ₽",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }
}

@Preview(name = "Фильтры", widthDp = 376, heightDp = 803, showBackground = true)
@Composable
private fun FiltersScreenPreview() {
    App(initialScreen = Screen.Filters)
}
