package app.srpass.kykfz01.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the device-bound AES key used by the biometric unlock channel.
 *
 * The key never leaves the Keystore: callers only ever receive a [Cipher],
 * which must be authorised by BiometricPrompt before it can encrypt or
 * decrypt.
 */
class KeyStoreManager {

    // Lazy: constructing this manager on the JVM (unit tests) must not try to
    // load the AndroidKeyStore provider, which only exists on a device.
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * @return one of BiometricManager.BIOMETRIC_* constants. Only
     * BIOMETRIC_SUCCESS means a usable Class 3 sensor is enrolled.
     */
    fun strongBiometricStatus(context: Context): Int =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

    fun hasBiometricKey(): Boolean =
        runCatching { keyStore.containsAlias(BIOMETRIC_KEY_ALIAS) }.getOrDefault(false)

    /** Generates (or re-generates) the Keystore key. */
    fun createBiometricKey() {
        deleteBiometricKey()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(biometricKeySpec())
        generator.generateKey()
    }

    fun deleteBiometricKey() {
        runCatching { keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS) }
    }

    /**
     * A cipher that refuses to work until a Class 3 biometric has been
     * verified. Hand this to BiometricPrompt via its CryptoObject.
     *
     * Returns null instead of throwing when the Keystore key cannot be used.
     * That happens in two real situations: the key was deleted, and -- because
     * of [setInvalidatedByBiometricEnrollment] -- the user enrolled a new
     * fingerprint, which permanently invalidates it.
     */
    fun createEncryptCipher(): Cipher? {
        val key = biometricKey() ?: return null
        return runCatching {
            Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
        }.getOrNull()
    }

    /**
     * Builds the decrypt cipher for a stored IV, or null if the Keystore key is
     * missing or invalidated. See [createEncryptCipher].
     */
    fun createDecryptCipher(iv: ByteArray): Cipher? {
        val key = biometricKey() ?: return null
        return runCatching {
            Cipher.getInstance(AES_GCM).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv))
            }
        }.getOrNull()
    }

    private fun biometricKeySpec(): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // time = 0  => re-authorise on EVERY use, not once per N seconds.
            // AUTH_BIOMETRIC_STRONG => Class 3 only; Class 2 (plain 2D face
            // unlock) is refused outright rather than silently accepted.
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setRandomizedEncryptionRequired(true)
            // Destroy this key if a NEW fingerprint is enrolled, so someone
            // who can briefly unlock the phone cannot add their own print and
            // then keep using it to open the vault.
            .setInvalidatedByBiometricEnrollment(true)
            .build()

    /**
     * Returns the Keystore key, or null when it is absent.
     *
     * The cast must stay a safe cast: a deleted key makes [KeyStore.getKey]
     * return null, and `null as SecretKey` used to crash the unlock screen.
     */
    private fun biometricKey(): SecretKey? =
        runCatching { keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM = "AES/GCM/NoPadding"
        const val BIOMETRIC_KEY_ALIAS = "srpass_biometric_wrap_key"
    }
}
