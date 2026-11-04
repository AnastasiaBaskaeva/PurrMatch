package com.purrmatch.data.backend

import com.purrmatch.data.backend.dto.ErrorDto
import com.purrmatch.domain.match.InvalidCredentialsException
import com.purrmatch.domain.match.LoginOccupiedException
import com.purrmatch.domain.match.RequestFailedException

/** HTTP-like metadata around a DTO body. No exceptions represent expected server errors. */
data class BackendResponse<out T>(val status: Int, val body: T? = null, val error: ErrorDto? = null)

internal fun <T : Any> BackendResponse<T>.toResult(): Result<T> =
    if (status in 200..299 && body != null) Result.success(body)
    else Result.failure(when (error?.code) {
        "LOGIN_OCCUPIED" -> LoginOccupiedException()
        "INVALID_CREDENTIALS" -> InvalidCredentialsException()
        else -> RequestFailedException(status, error?.code ?: "INVALID_RESPONSE", error?.message ?: "Invalid backend response")
    })
