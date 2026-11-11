package app.srpass.kykfz01.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869).
 *
 * Derives a purpose-bound subkey from the master key so the media encryption
 * key never shares key material with the KDBX credential. The master key (MK)
 * is high-entropy (256-bit random), so a fixed salt is safe here: in HKDF the
 * salt breaks key reuse across domains, it does not add secrecy.
 */
object Hkdf {

    private const val ALG = "HmacSHA256"
    private const val HASH_LEN = 32

    /** Derives [length] bytes (default 32, an AES-256 key). */
    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val prk = hmac(salt, ikm)
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var produced = 0
        var counter = 1
        while (produced < length) {
            val mac = Mac.getInstance(ALG)
            mac.init(SecretKeySpec(prk, ALG))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val need = minOf(HASH_LEN, length - produced)
            System.arraycopy(t, 0, okm, produced, need)
            produced += need
            counter++
        }
        return okm
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALG)
        mac.init(SecretKeySpec(key, ALG))
        return mac.doFinal(data)
    }
}
