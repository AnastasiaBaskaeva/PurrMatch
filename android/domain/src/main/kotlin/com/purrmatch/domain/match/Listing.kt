package com.purrmatch.domain.match

import java.io.Serializable

data class Listing(
    val id: String, val name: String, val authorId: String = "", val breed: String = "",
    val sex: String = "кошка", val age: String = "", val birthday: String = "",
    val color: String = "", val eyes: String = "", val character: String = "",
    val description: String = "", val city: String = "", val price: String = "Бесплатно",
    val available: Boolean = true,
    val dogs: Boolean = true, val cats: Boolean = true, val children: Boolean = true,
) : Serializable
