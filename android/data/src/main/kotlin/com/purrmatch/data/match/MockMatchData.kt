package com.purrmatch.data.match

import com.purrmatch.domain.match.MatchData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Debug host save/restore boundary. The byte payload contains only source records. */
fun encodeMockMatchData(data: MatchData): ByteArray = ByteArrayOutputStream().also { output ->
    ObjectOutputStream(output).use { it.writeObject(data.toRecord()) }
}.toByteArray()

fun decodeMockMatchData(bytes: ByteArray): MatchData =
    ObjectInputStream(ByteArrayInputStream(bytes)).use { (it.readObject() as MatchRecords).toDomain() }

internal fun sampleMatchRecords(): MatchRecords {
    val anna = ProfileRecord(
        id = "anna",
        name = "Анна",
        login = "anna_cats",
        city = "Москва",
        about = "Помогаю кошкам находить заботливый дом. Отвечаю на сообщения каждый день.",
        telegram = "@anna_cats",
        phone = "+7 (•••) •••-••-••",
        website = "example.com"
    )
    val mia = ListingRecord(
        "mia",
        "Мия",
        authorId = "anna",
        breed = "Британская короткошёрстная",
        sex = "кошка",
        birthday = "2024-05-14",
        color = "Серый",
        eyes = "Янтарные",
        character = "Ласковая, Спокойная, Любопытная",
        city = "Москва",
        photos = List(3) { "sample:detail" },
        description = "Мия любит играть и тихо мурлыкать рядом с человеком. Приучена к лотку."
    )
    return MatchRecords(
        profiles = listOf(
            anna,
            ProfileRecord(
                id = "olga",
                name = "Ольга",
                login = "olga_cats",
                city = "Казань",
                about = "Забочусь о кошках из приюта.",
                telegram = "@olga_cats",
                website = "shelter.example.com"
            )
        ),
        listings = listOf(
            mia,
            mia.copy(
                id = "peach",
                name = "Персик",
                breed = "Метис",
                sex = "кот",
                birthday = "2026-03-14",
                price = 5_000
            ),
            mia.copy(
                id = "sonya",
                name = "Соня",
                breed = "Домашняя кошка",
                birthday = "2022-05-14",
                color = "Рыжая",
                description = "Спокойная и дружелюбная кошка.",
                available = false
            ),
            mia.copy(
                id = "barsik",
                name = "Барсик",
                authorId = "olga",
                breed = "Метис",
                sex = "кот",
                city = "Казань"
            ),
        ),
        selections = mapOf(
            "anna" to SelectionRecord(
                favorites = listOf(
                    "mia",
                    "peach",
                    "sonya"
                )
            )
        ),
    )
}
