package com.r2r.invoicescanner.security

import android.content.Context
import android.util.Base64
import com.r2r.invoicescanner.settings.SecretBox

/**
 * App lock with an authenticator app (experimental). The TOTP secret is encrypted with the
 * Android Keystore; recovery codes are kept only as hashes and each works once.
 */
object MfaStore {
    private const val PREFS = "mfa"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false) && secret(context) != null

    fun secret(context: Context): ByteArray? =
        prefs(context).getString("secret", null)
            ?.let { SecretBox.open(it) }
            ?.let { Base64.decode(it, Base64.NO_WRAP) }

    /** Saves the secret and recovery codes and turns the lock on. Returns false if the Keystore is unavailable. */
    fun enable(context: Context, secret: ByteArray, recoveryCodes: List<String>): Boolean {
        val sealed = SecretBox.seal(Base64.encodeToString(secret, Base64.NO_WRAP)) ?: return false
        prefs(context).edit()
            .putString("secret", sealed)
            .putStringSet("recovery", recoveryCodes.map { Totp.hashRecoveryCode(it) }.toSet())
            .putBoolean("enabled", true)
            .apply()
        return true
    }

    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun recoveryCodesLeft(context: Context): Int = prefs(context).getStringSet("recovery", emptySet()).orEmpty().size

    /** Checks a 6-digit code, or a recovery code (which is then used up). */
    fun check(context: Context, input: String): Boolean {
        val secret = secret(context) ?: return false
        if (input.count { it.isDigit() } == Totp.DIGITS && input.none { it.isLetter() }) {
            return Totp.verify(secret, input)
        }
        val hash = Totp.hashRecoveryCode(input)
        val left = prefs(context).getStringSet("recovery", emptySet()).orEmpty()
        if (hash !in left) return false
        prefs(context).edit().putStringSet("recovery", left - hash).apply()
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
