package app.malino.security

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** Fingerprint / face through the platform prompt (API 28+), so no extra library is needed. */
@SuppressLint("NewApi")
object Biometric {
    fun available(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
        val code = if (Build.VERSION.SDK_INT >= 30) bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) else bm.canAuthenticate()
        return code == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(activity: Activity, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) return
        val prompt = BiometricPrompt.Builder(activity).setTitle("باز کردن مالینو")
            .setNegativeButton("استفاده از پین", activity.mainExecutor) { _, _ -> }.build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onSuccess() }
        })
    }
}
