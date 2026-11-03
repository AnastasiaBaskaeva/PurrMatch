package com.purrmatch.presentation.contacts

import com.purrmatch.domain.match.Profile

fun Profile.contactAddress(type: String): String? = when (type) {
    "website" -> website.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("https://") || it.startsWith("http://")) it else "https://$it" }

    "telegram" -> telegram.trim().removePrefix("@").takeIf { it.isNotBlank() }?.let { "https://t.me/$it" }
    "phone", "whatsapp" -> {
        val number = (if (type == "whatsapp") whatsapp.ifBlank { phone } else phone).filter(Char::isDigit)
        number.takeIf { it.length >= 10 }?.let { if (type == "phone") "tel:$it" else "https://wa.me/$it" }
    }

    else -> null
}
