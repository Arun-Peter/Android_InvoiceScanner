package com.r2r.invoicescanner.security

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30-second steps, 6 digits), the standard
 * that Microsoft Authenticator, Google Authenticator and others use for "Other account" entries.
 */
object Totp {
    const val DIGITS = 6
    const val PERIOD_SECONDS = 30L
    const val ISSUER = "Invoice Scanner"

    private val random = SecureRandom()

    /** A new random 160-bit secret. */
    fun newSecret(): ByteArray = ByteArray(20).also { random.nextBytes(it) }

    fun code(secret: ByteArray, timeMillis: Long = System.currentTimeMillis(), digits: Int = DIGITS): String =
        codeAt(secret, timeMillis / 1000 / PERIOD_SECONDS, digits)

    fun codeAt(secret: ByteArray, counter: Long, digits: Int = DIGITS): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    /** Accepts the current code and one step either side, to allow for small clock differences. */
    fun verify(secret: ByteArray, code: String, timeMillis: Long = System.currentTimeMillis()): Boolean {
        val c = code.filter { it.isDigit() }
        if (c.length != DIGITS) return false
        val now = timeMillis / 1000 / PERIOD_SECONDS
        return (-1L..1L).any { constantTimeEquals(codeAt(secret, now + it), c) }
    }

    /** Seconds left before the current code changes. */
    fun secondsLeft(timeMillis: Long = System.currentTimeMillis()): Int =
        (PERIOD_SECONDS - (timeMillis / 1000) % PERIOD_SECONDS).toInt()

    /** otpauth:// link understood by authenticator apps (and encoded in the setup QR code). */
    fun uri(secret: ByteArray, account: String): String {
        val label = enc("$ISSUER:$account")
        return "otpauth://totp/$label?secret=${Base32.encode(secret)}&issuer=${enc(ISSUER)}" +
            "&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    // ------------------------------------------------------------ recovery codes

    private const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O, 1/I/L

    /** One-time codes such as "K7RM-2XQP" to unlock the app if the authenticator is lost. */
    fun newRecoveryCodes(count: Int = 8): List<String> = List(count) {
        val chars = (1..8).map { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }.joinToString("")
        chars.take(4) + "-" + chars.drop(4)
    }

    /** Recovery codes are stored only as hashes. Spaces, dashes and case are ignored. */
    fun hashRecoveryCode(code: String): String {
        val norm = code.uppercase().filter { it.isLetterOrDigit() }
        return MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/** RFC 4648 Base32 without padding — the format of authenticator "secret keys". */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.uppercase().filter { it in ALPHABET }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** "JBSW Y3DP EHPK 3PXP" — easier to type into an authenticator app. */
    fun grouped(data: ByteArray): String = encode(data).chunked(4).joinToString(" ")
}
