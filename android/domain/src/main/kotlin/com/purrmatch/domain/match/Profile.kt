package com.purrmatch.domain.match

import java.io.Serializable

data class Profile(
    val id: String = "", val name: String = "", val login: String = "", val city: String = "",
    val about: String = "", val telegram: String = "", val phone: String = "", val website: String = "",
) : Serializable
