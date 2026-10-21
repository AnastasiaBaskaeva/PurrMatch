package com.purrmatch.presentation.profile

typealias Profile = com.purrmatch.domain.match.Profile

val Profile.displayName: String get() = listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ")
