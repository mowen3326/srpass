package app.srpass.kykfz01.crypto

import java.security.SecureRandom
import java.util.Locale

/**
 * The recovery key: a one-time, hand-copyable secret that can reset the master
 * password. Because SrPass is fully offline there is no server to fall back on,
 * so this key is the ONLY escape hatch if the master password is forgotten.
 *
 * Format: 32 Crockford-Base32 characters in 4 groups of 8, e.g.
 * `A1B2C3D4-E5F6G7H8-J9K0M1N2-P3Q4R5S6`
 *
 * 32 Base32 characters carry 160 bits of entropy, which is far beyond brute
 * force, so the recovery key itself needs no slow KDF to stay safe.
 */
object RecoveryKey {

    // Crockford Base32: digits then letters, omitting I, L, O and U.
    // I/L/O are dropped because they are easily confused with 1 and 0 when
    // read off a piece of paper; U is dropped to avoid accidental words.
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private const val GROUP_SIZE = 8
    private const val GROUP_COUNT = 4
    private const val CHAR_COUNT = GROUP_SIZE * GROUP_COUNT

    /** 20 bytes = 160 bits, which encodes to exactly 32 Base32 characters. */
    private const val ENTROPY_BYTES = 20

    private const val SEPARATOR = "-"

    fun generate(): String {
        val entropy = ByteArray(ENTROPY_BYTES)
        SecureRandom().nextBytes(entropy)

        val chars = CharArray(CHAR_COUNT)
        var buffer = 0
        var bits = 0
        var index = 0

        for (byte in entropy) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && index < CHAR_COUNT) {
                val shift = bits - 5
                chars[index++] = ALPHABET[(buffer shr shift) and 0x1F]
                bits -= 5
            }
        }

        return chars.concatToString().chunked(GROUP_SIZE).joinToString(SEPARATOR)
    }

    /**
     * Accepts user input in any reasonable shape and returns the canonical
     * 32-character form: uppercase, separators removed, and the commonly
     * confused letters folded onto the digits they resemble.
     *
     * Characters outside the alphabet (including U) are dropped.
     */
    fun normalize(input: String): String =
        // Locale.ROOT, same reason as Base32: a Turkish default locale turns
        // "i" into "İ" and the recovery key would never match.
        input.uppercase(Locale.ROOT)
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .filter { it in ALPHABET }

    fun isValid(input: String): Boolean {
        val normalized = normalize(input)
        return normalized.length == CHAR_COUNT && normalized.all { it in ALPHABET }
    }

    /**
     * Key material for Argon2id. Returned as a [CharArray] so callers can wipe
     * it; the value is already high-entropy, so no extra stretching is needed
     * for strength (the KDF only adds attack cost).
     */
    fun toKeyMaterial(input: String): CharArray = normalize(input).toCharArray()
}
