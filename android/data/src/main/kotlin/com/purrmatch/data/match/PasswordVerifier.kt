package com.purrmatch.data.match

import java.io.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA1 is available from Android API 10 (the app supports API 24). */
internal class PasswordVerifier private constructor(
    private val salt: ByteArray,
    private val hash: ByteArray,
) : Serializable {
    fun matches(password: String): Boolean = MessageDigest.isEqual(hash, derive(password, salt))

    companion object {
        private const val serialVersionUID = 1L
        private const val ITERATIONS = 210_000
        // Public mock fixture only. Precomputed to avoid password stretching during data loading.
        val legacyFixture = PasswordVerifier(
            byteArrayOf(50, 110, -15, -32, -44, 30, 104, -11, -65, -14, 85, 84, -50, 119, -117, -87),
            byteArrayOf(25, -15, -83, 88, 102, -95, 49, 43, 13, -57, 22, 12, -28, 121, 73, -108, 123, 9, 48, 108, 56, 108, 22, -128, 94, -80, 45, 49, 14, -78, -13, -114),
        )

        fun create(password: String): PasswordVerifier {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return PasswordVerifier(salt, derive(password, salt))
        }

        private fun derive(password: String, salt: ByteArray): ByteArray {
            val characters = password.toCharArray()
            val spec = PBEKeySpec(characters, salt, ITERATIONS, 256)
            characters.fill('\u0000')
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            } finally { spec.clearPassword() }
        }
    }
}
