package com.purrmatch.presentation.contacts

import com.purrmatch.domain.match.Profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactAddressTest {

    @Test
    fun websiteAndTelegramBecomeExternalLinks() {
        assertEquals("https://cats.example", Profile(website = "cats.example").contactAddress("website"))
        assertEquals("http://cats.example", Profile(website = "http://cats.example").contactAddress("website"))
        assertEquals("https://cats.example", Profile(website = "https://cats.example").contactAddress("website"))
        assertEquals("https://t.me/cats", Profile(telegram = " @cats ").contactAddress("telegram"))
        assertEquals("https://t.me/cats", Profile(telegram = "cats").contactAddress("telegram"))
    }

    @Test
    fun phoneAndWhatsAppUseTheirOwnNumbersWithPhoneFallback() {
        val profile = Profile(phone = "+7 (999) 123-45-67", whatsapp = "+7 900 765-43-21")
        assertEquals("tel:79991234567", profile.contactAddress("phone"))
        assertEquals("https://wa.me/79007654321", profile.contactAddress("whatsapp"))
        assertEquals("https://wa.me/79991234567", profile.copy(whatsapp = " ").contactAddress("whatsapp"))
        assertNull(profile.copy(whatsapp = "123").contactAddress("whatsapp"))
    }

    @Test
    fun missingContactsAndUnknownTypeHaveNoAddress() {
        val profile = Profile(website = " ", telegram = " @ ", phone = "+7 (•••) •••-••-••")
        listOf("website", "telegram", "phone", "whatsapp", "unknown").forEach { type ->
            assertNull(type, profile.contactAddress(type))
        }
    }
}
