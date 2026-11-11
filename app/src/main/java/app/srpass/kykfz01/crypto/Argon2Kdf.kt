package app.srpass.kykfz01.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id key derivation.
 *
 * Argon2id is chosen over Argon2d/Argon2i because it resists both GPU/ASIC
 * cracking (thanks to the memory-hard first pass) and side-channel attacks
 * (thanks to the data-independent second pass).
 *
 * The implementation is BouncyCastle's, so no cryptographic primitive is
 * written by hand.
 */
object Argon2Kdf {

    /**
     * Derives [outputBytes] bytes from [password] using Argon2id.
     *
     * [password] is passed to BouncyCastle as a char array so the caller can
     * wipe it afterwards; no immutable [String] is created from it here.
     */
    fun derive(
        password: CharArray,
        salt: ByteArray,
        outputBytes: Int = CryptoConstants.ARGON2_OUTPUT_BYTES,
        params: KdfParams = KdfParams()
    ): ByteArray {
        require(salt.isNotEmpty()) { "salt must not be empty" }

        val spec = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKiB)
            .withParallelism(params.parallelism)
            .build()

        val output = ByteArray(outputBytes)
        val generator = Argon2BytesGenerator()
        generator.init(spec)
        generator.generateBytes(password, output)
        return output
    }

    /** Convenience overload for callers holding raw bytes. */
    fun derive(
        password: ByteArray,
        salt: ByteArray,
        outputBytes: Int = CryptoConstants.ARGON2_OUTPUT_BYTES,
        params: KdfParams = KdfParams()
    ): ByteArray {
        val spec = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKiB)
            .withParallelism(params.parallelism)
            .build()

        val output = ByteArray(outputBytes)
        val generator = Argon2BytesGenerator()
        generator.init(spec)
        generator.generateBytes(password, output)
        return output
    }
}
