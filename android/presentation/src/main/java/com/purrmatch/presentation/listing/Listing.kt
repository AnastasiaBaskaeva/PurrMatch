package com.purrmatch.presentation.listing

import androidx.compose.runtime.Immutable

@Immutable
data class Listing(
    val id: String, val name: String, val breed: String = "Британская короткошёрстная",
    val sex: String = "кошка", val age: String = "2 года", val birthday: String = "14 мая 2024",
    val color: String = "Серый", val eyes: String = "Янтарные", val character: String = "Ласковая, Спокойная, Любопытная",
    val description: String = "Мия любит играть и тихо мурлыкать рядом с человеком. Приучена к лотку.",
    val city: String = "Москва", val price: String = "Бесплатно", val available: Boolean = true,
    val dogs: Boolean = true, val cats: Boolean = true, val children: Boolean = true,
) : java.io.Serializable

internal val designListings = listOf(
    Listing("mia", "Мия"),
    Listing("peach", "Персик", "Метис", "кот", "7 мес.", price = "5 000 ₽"),
    Listing("sonya", "Соня", "Домашняя кошка", age = "4 года", color = "Рыжая",
        description = "Спокойная и дружелюбная кошка.", available = false),
)
