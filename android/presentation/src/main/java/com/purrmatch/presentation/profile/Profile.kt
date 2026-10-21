package com.purrmatch.presentation.profile

import androidx.compose.runtime.Immutable

@Immutable
data class Profile(
    val name: String = "Анна", val login: String = "anna_cats", val city: String = "Москва",
    val about: String = "Помогаю кошкам находить заботливый дом. Отвечаю на сообщения каждый день.",
    val telegram: String = "@anna_cats", val phone: String = "+7 (•••) •••-••-••", val website: String = "example.com",
) : java.io.Serializable
