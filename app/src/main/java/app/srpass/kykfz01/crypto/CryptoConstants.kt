package app.srpass.kykfz01.crypto

/**
 * Central tuning knobs for SrPass cryptography.
 *
 * WARNING: changing any Argon2 constant changes derived keys, which would
 * invalidate every already-wrapped master key. Those constants are therefore
 * also written next to each wrapped key (see [KdfParams]) so a future change
 * can migrate old envelopes instead of silently breaking them.
 */
object CryptoConstants {

    /** AES-256. */
    const val MASTER_KEY_BYTES = 32

    const val SALT_BYTES = 16

    /** 12 bytes is the NIST-recommended IV length for GCM. */
    const val GCM_IV_BYTES = 12

    const val GCM_TAG_BITS = 128

    // ---------------------------------------------------------------------
    // Argon2id
    //
    // 64 MiB / t=3 / p=4: costly enough to make offline cracking of a weak
    // master password unattractive, while keeping unlock latency near one
    // second on current phones. Memory is the parameter that matters most,
    // since it is what GPU/ASIC farms cannot cheaply scale.
    // ---------------------------------------------------------------------
    const val ARGON2_MEMORY_KIB = 65_536
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 4
    const val ARGON2_OUTPUT_BYTES = 32
}

/**
 * Argon2id parameters recorded alongside a wrapped key.
 *
 * Storing them means an envelope stays decryptable even if the defaults above
 * are raised later.
 */
data class KdfParams(
    val memoryKiB: Int = CryptoConstants.ARGON2_MEMORY_KIB,
    val iterations: Int = CryptoConstants.ARGON2_ITERATIONS,
    val parallelism: Int = CryptoConstants.ARGON2_PARALLELISM
) {
    companion object {
        fun decode(encoded: String?): KdfParams {
            if (encoded.isNullOrBlank()) return KdfParams()
            val parts = encoded.split(':')
            return runCatching {
                KdfParams(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }.getOrDefault(KdfParams())
        }
    }

    fun encode(): String = "$memoryKiB:$iterations:$parallelism"
}
