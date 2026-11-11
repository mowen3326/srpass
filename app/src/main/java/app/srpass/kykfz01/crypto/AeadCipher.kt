package app.srpass.kykfz01.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM output: the IV used, plus ciphertext with the auth tag appended. */
data class SealedData(
    val iv: ByteArray,
    val ciphertext: ByteArray
)

/**
 * AES-256-GCM authenticated encryption.
 *
 * GCM is used rather than CBC because it is authenticated: any tampering with
 * the ciphertext or IV makes decryption throw rather than yield garbage. That
 * property is what lets the tests assert that a modified envelope cannot be
 * opened.
 */
object AeadCipher {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ALGORITHM = "AES"

    /**
     * Encrypts [plaintext] under [key].
     *
     * [associatedData] is authenticated but not encrypted. It binds an
     * envelope to its context (for example the unlock channel name), so an
     * envelope captured from one channel cannot be replayed as another.
     */
    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): SealedData {
        val iv = ByteArray(CryptoConstants.GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), gcmSpec(iv))
        associatedData?.let { cipher.updateAAD(it) }

        return SealedData(iv = iv, ciphertext = cipher.doFinal(plaintext))
    }

    /**
     * Decrypts [sealed] under [key].
     *
     * @throws javax.crypto.AEADBadTagException if the ciphertext, IV or
     * associated data was altered -- never returns corrupted plaintext.
     */
    fun open(
        key: ByteArray,
        sealed: SealedData,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(key), gcmSpec(sealed.iv))
        associatedData?.let { cipher.updateAAD(it) }

        return cipher.doFinal(sealed.ciphertext)
    }

    /**
     * Encrypts with a caller-supplied [Cipher] rather than a raw key.
     *
     * Required for the biometric channel: its key lives inside the Android
     * Keystore and cannot be exported, so it is only usable through a cipher
     * that has just passed a Class 3 authentication.
     */
    fun sealWithCipher(
        cipher: Cipher,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): SealedData {
        associatedData?.let { cipher.updateAAD(it) }
        return SealedData(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    /**
     * Decrypts with a caller-supplied [Cipher]. See [sealWithCipher].
     *
     * @throws javax.crypto.AEADBadTagException on any tampering.
     */
    fun openWithCipher(
        cipher: Cipher,
        sealed: SealedData,
        associatedData: ByteArray? = null
    ): ByteArray {
        associatedData?.let { cipher.updateAAD(it) }
        return cipher.doFinal(sealed.ciphertext)
    }

    private fun gcmSpec(iv: ByteArray) =
        GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv)

    private fun secretKey(key: ByteArray): SecretKey = SecretKeySpec(key, ALGORITHM)
}
