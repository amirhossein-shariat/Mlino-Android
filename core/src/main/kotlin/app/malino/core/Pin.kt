package app.malino.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN hashing (PBKDF2-HMAC-SHA256) and the wrong-attempt policy. This is a screen lock, not encryption of the stored data. */
object Pin {
    const val ITERATIONS = 120_000
    const val MIN_LEN = 4
    const val MAX_LEN = 8
    const val MAX_TRIES = 5
    const val BLOCK_MS = 30_000L

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    fun verify(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)

    fun isValid(pin: String) = pin.length in MIN_LEN..MAX_LEN && pin.all { it in '0'..'9' }

    /** After a wrong PIN: the new fail count, and until when input is blocked (0 = not blocked). The count resets after a block. */
    data class Fail(val count: Int, val blockedUntil: Long)

    fun afterWrong(count: Int, now: Long): Fail =
        if (count + 1 >= MAX_TRIES) Fail(0, now + BLOCK_MS) else Fail(count + 1, 0)
}
