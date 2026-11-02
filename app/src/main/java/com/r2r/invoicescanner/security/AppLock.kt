package com.r2r.invoicescanner.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** App lock preferences shared by biometric unlock and the authenticator app (MFA). */
object AppLock {
    private const val PREFS = "app_lock"

    /** Never lock again while the app is running; it still locks when it is opened afresh. */
    const val NEVER = 0

    /** Choices for "Lock again after … in the background". */
    val RELOCK_MINUTES = listOf(5, 10, 15, NEVER)
    const val DEFAULT_RELOCK_MINUTES = 5

    fun biometricEnabled(context: Context): Boolean = prefs(context).getBoolean("biometric", false)

    fun setBiometricEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("biometric", on).apply()
    }

    fun relockMinutes(context: Context): Int = parseMinutes(prefs(context).getInt("relockMinutes", DEFAULT_RELOCK_MINUTES))

    fun setRelockMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt("relockMinutes", parseMinutes(minutes)).apply()
    }

    /** Unknown values fall back to the default. */
    fun parseMinutes(minutes: Int): Int = minutes.takeIf { it in RELOCK_MINUTES } ?: DEFAULT_RELOCK_MINUTES

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Unlock with the phone's fingerprint / face, or its PIN, pattern or password as a fallback
 * (the system's own prompt; nothing biometric is seen or stored by the app).
 */
object Biometric {
    private val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True when the phone has a fingerprint, face or at least a screen lock set up. */
    fun available(context: Context): Boolean {
        val manager = context.getSystemService(BiometricManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return manager?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        }
        @Suppress("DEPRECATION")
        val bio = manager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        return bio || context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    /** Shows the system prompt. [onResult] gets null on success, or the reason it failed / was cancelled. */
    fun prompt(context: Context, title: String, onResult: (String?) -> Unit) {
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(AUTHENTICATORS)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(null)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onResult(errString.toString().ifBlank { "Not unlocked" })
            },
        )
    }
}
