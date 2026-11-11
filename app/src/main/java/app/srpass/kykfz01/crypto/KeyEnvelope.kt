package app.srpass.kykfz01.crypto

/**
 * The three independent ways to unlock the vault.
 *
 * They are deliberately independent: losing or breaking one must never lock
 * the user out, which is why the master password is mandatory while the other
 * two are optional conveniences layered on top of the same master key.
 */
enum class Channel(val id: String) {
    /** Always present. The only credential that survives a Keystore wipe. */
    PASSWORD("password"),

    /** Always present. The escape hatch when the master password is lost. */
    RECOVERY("recovery"),

    /** Optional, device-bound, backed by the Android Keystore. */
    BIOMETRIC("biometric")
}

/**
 * The master key, encrypted ("wrapped") by one unlock credential.
 *
 * Only the master key is ever wrapped -- vault entries are encrypted with the
 * master key itself, so adding or rotating a credential never requires
 * re-encrypting any stored data.
 *
 * @param salt Argon2id salt; null for the biometric channel, whose wrapping
 *             key is generated and protected by the Keystore instead.
 * @param kdf  The Argon2id parameters in force when this was written, so a
 *             future parameter increase can still read old envelopes.
 */
data class KeyEnvelope(
    val channel: Channel,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val salt: ByteArray? = null,
    val kdf: KdfParams? = null
)
