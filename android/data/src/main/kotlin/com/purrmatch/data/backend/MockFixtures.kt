package com.purrmatch.data.backend

import com.purrmatch.data.backend.dto.FailureRuleDto
import com.purrmatch.data.backend.dto.FailureRulesDto
import com.purrmatch.data.backend.dto.MockFixtureDto
import kotlinx.serialization.json.Json

/** Immutable JVM resources; callers perform this blocking read on their IO dispatcher. */
object MockFixtures {
    val json = Json { encodeDefaults = true }

    private val names = setOf("default", "no-results", "empty-favorites", "complete-profile", "catalog")

    fun load(name: String = "default"): MockFixtureDto {
        require(name in names) { "Unknown mock fixture: $name" }
        return json.decodeFromString<MockFixtureDto>(readResource(name))
    }

    fun failures(): List<FailureRuleDto> =
        json.decodeFromString<FailureRulesDto>(readResource("errors")).rules

    private fun readResource(name: String): String =
        checkNotNull(MockFixtures::class.java.getResourceAsStream("/mock/$name.json")) {
            "Missing mock resource: /mock/$name.json"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
