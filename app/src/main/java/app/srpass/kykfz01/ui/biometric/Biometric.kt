package app.srpass.kykfz01.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Pre-flight check for a usable Class 3 sensor.
 *
 * Returns null when biometrics are ready, or a human-readable reason why they
 * are not. Call this BEFORE touching the Android Keystore: generating a key
 * with `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` throws
 * outright when nothing is enrolled, so a caller that builds the cipher first
 * would crash before this message could ever be shown.
 */
fun FragmentActivity.strongBiometricProblem(): String? = when (
    BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
) {
    BiometricManager.BIOMETRIC_SUCCESS -> null
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
        "请在系统设置中录入指纹或面容后再试"
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        "生物识别硬件暂不可用"
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        "当前设备没有生物识别硬件"
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        "需要安装系统安全更新后才能使用"
    else -> "当前环境不支持生物识别"
}

/**
 * Shows a BiometricPrompt bound to [cipher] (a CryptoObject). On success the
 * authenticated cipher is handed back via [onSuccess] so the caller can finish
 * the Keystore-wrapped operation.
 *
 * [onSettled] runs once the prompt reaches a terminal state, so callers can
 * release whatever they held open for it (for example the lock suppression
 * that stops the app re-locking while the system dialog is on screen).
 */
fun FragmentActivity.showBiometric(
    cipher: Cipher,
    title: String,
    subtitle: String,
    negativeText: String = "取消",
    onSuccess: (Cipher) -> Unit,
    onError: (String) -> Unit,
    onSettled: () -> Unit = {}
) {
    strongBiometricProblem()?.let {
        onError(it)
        onSettled()
        return
    }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSettled()
            val c = result.cryptoObject?.cipher
            if (c != null) onSuccess(c) else onError("未能获取密钥")
        }

        override fun onAuthenticationError(code: Int, err: CharSequence) {
            onSettled()
            // User cancelled (code 10) is not an error worth surfacing.
            if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                code != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
                onError(err.toString())
            }
        }
    }

    val prompt = BiometricPrompt(this, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeText)
        .build()

    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
