package app.srpass.kykfz01.vault

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Hash algorithm an OTP entry can use.
 *
 * SHA1 is what virtually every site issues (Google, GitHub, AWS, ...). The
 * larger digests are only ever seen in enterprise provisioning, but they cost
 * three lines to support so they are here.
 */
enum class OtpAlgorithm(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512")
}

/**
 * One authenticator account.
 *
 * [secret] is the shared key in Base32 (RFC 4648), stored without spaces -- it
 * is persisted encrypted inside the KDBX like every other sensitive field.
 */
data class OtpEntry(
    val uuid: UUID,
    val issuer: String = "",   // "GitHub"
    val account: String = "",  // "alice@example.com"
    val secret: String = "",   // Base32, uppercase, no spaces
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val updatedAt: Long = 0L,
    /** 放进隐藏空间的标记，存在自己的 KDBX 条目里，备份/恢复后依然是隐藏的。 */
    val hidden: Boolean = false
) {
    /** What the list shows when the user gave neither issuer nor account. */
    fun displayTitle(): String = when {
        issuer.isNotBlank() && account.isNotBlank() -> "$issuer ($account)"
        issuer.isNotBlank() -> issuer
        account.isNotBlank() -> account
        else -> "未命名"
    }
}

/**
 * RFC 4648 Base32.
 *
 * Android ships Base64 but not Base32, and OTP secrets are universally Base32,
 * so this is hand-rolled. Only decoding is needed -- we never re-encode a
 * secret for display.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Tolerant on purpose: secrets get copied out of setup pages with spaces,
     * dashes and padding, and users paste them as-is.
     */
    fun decode(input: String): ByteArray {
        // Locale.ROOT: under a Turkish default locale "i".uppercase() is "İ",
        // which silently makes every Base32 secret undecodable.
        val s = input.uppercase(Locale.ROOT).filter { it != ' ' && it != '-' && it != '=' }
        if (s.isEmpty()) return ByteArray(0)
        val out = ByteArray(s.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var i = 0
        for (c in s) {
            val value = ALPHABET.indexOf(c)
            // Reject junk rather than silently decoding a wrong secret, which
            // would produce codes that look valid but never match the server.
            if (value < 0) throw IllegalArgumentException("Invalid Base32 character: $c")
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out[i++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        return out.copyOf(i)
    }

    /** True when [secret] is well-formed Base32 for this alphabet. */
    fun isValid(secret: String): Boolean = runCatching {
        val s = secret.uppercase(Locale.ROOT).filter { it != ' ' && it != '-' && it != '=' }
        s.isNotEmpty() && s.all { it in ALPHABET }
    }.getOrDefault(false)
}

/**
 * TOTP -- RFC 6238 (time-based) built on HOTP -- RFC 6229's predecessor step,
 * RFC 4226.
 *
 * Pure Kotlin with no Android APIs: the module has JVM unit tests, where
 * anything from android.* throws "not mocked".
 */
object Totp {

    /**
     * Current code for [entry]. Returns a row of dashes instead of throwing
     * when the secret is malformed -- a bad entry must not crash the list.
     */
    fun generate(entry: OtpEntry, nowMillis: Long = System.currentTimeMillis()): String {
        val key = runCatching { Base32.decode(entry.secret) }.getOrNull()
        if (key == null || key.isEmpty()) return "-".repeat(entry.digits.coerceIn(6, 8))
        val period = entry.period.coerceAtLeast(1)
        return hotp(key, nowMillis / 1000 / period, entry.digits, entry.algorithm)
    }

    /** Seconds until the current code rotates; drives the countdown ring. */
    fun remainingSeconds(entry: OtpEntry, nowMillis: Long = System.currentTimeMillis()): Int {
        val period = entry.period.coerceAtLeast(1)
        return (period - (nowMillis / 1000) % period).toInt()
    }

    /** Progress of the current window, 1.0 at the start down to 0.0 at rotation. */
    fun progress(entry: OtpEntry, nowMillis: Long = System.currentTimeMillis()): Float {
        val period = entry.period.coerceAtLeast(1)
        return remainingSeconds(entry, nowMillis) / period.toFloat()
    }

    /** HOTP: HMAC the 8-byte counter, then dynamic truncation (RFC 4226 §5.3). */
    private fun hotp(
        key: ByteArray,
        counter: Long,
        digits: Int,
        algorithm: OtpAlgorithm
    ): String {
        val message = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = Mac.getInstance(algorithm.macName).run {
            init(SecretKeySpec(key, algorithm.macName))
            doFinal(message)
        }
        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val modulo = 10.0.pow(digits.coerceIn(6, 8)).toLong()
        return (binary % modulo).toString().padStart(digits, '0')
    }

    /**
     * Parses an `otpauth://totp/...` URI, the format behind every QR code a
     * site shows at 2FA setup:
     *
     *   otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30
     *
     * Returns null for anything that is not TOTP -- HOTP (counter based) is a
     * different flow and is out of scope here.
     */
    fun parseUri(raw: String): OtpEntry? {
        if (!raw.startsWith("otpauth://", ignoreCase = true)) return null
        val rest = raw.substring("otpauth://".length)
        val q = rest.indexOf('?')
        val head = if (q >= 0) rest.substring(0, q) else rest
        val query = if (q >= 0) rest.substring(q + 1) else ""

        val slash = head.indexOf('/')
        val type = if (slash >= 0) head.substring(0, slash) else head
        if (!type.equals("totp", ignoreCase = true)) return null
        // Decoded before the split: an issuer whose name contains an encoded
        // colon must not be cut in half by looking for one in the raw label.
        val label = percentDecode(if (slash >= 0) head.substring(slash + 1) else "")

        val params = parseQuery(query)
        val secret = params["secret"] ?: return null

        // The label is "Issuer:Account"; the issuer may also come as a query
        // parameter, and that one wins (some generators leave the label empty).
        val colon = label.indexOf(':')
        val labelIssuer = if (colon >= 0) label.substring(0, colon) else ""
        val labelAccount = if (colon >= 0) label.substring(colon + 1) else label

        return OtpEntry(
            uuid = UUID.randomUUID(),
            // parseQuery already decoded the values it returns.
            issuer = params["issuer"] ?: labelIssuer,
            account = labelAccount,
            secret = secret.filter { !it.isWhitespace() }.uppercase(Locale.ROOT),
            algorithm = when (params["algorithm"]?.uppercase(Locale.ROOT)) {
                "SHA256" -> OtpAlgorithm.SHA256
                "SHA512" -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            },
            digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6,
            period = params["period"]?.toIntOrNull()?.takeIf { it > 0 } ?: 30
        )
    }

    /**
     * Hand-rolled query parsing. android.net.Uri would be the obvious choice
     * but its desktop stub throws the moment a JVM test touches it.
     */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null
            else part.substring(0, eq).lowercase() to percentDecode(part.substring(eq + 1))
        }.toMap()
    }

    /**
     * Percent decoding that survives multi-byte characters.
     *
     * Decoding one %XX at a time (as this used to) turns each byte of a UTF-8
     * sequence into a character of its own: 中 arrives as %E4%B8%AD and came
     * out as three Latin-1 characters. The bytes are collected first and
     * decoded as UTF-8 once at the end, which is what an issuer like "阿里云"
     * or an account with Chinese in it needs.
     */
    private fun percentDecode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    i += 3
                    continue
                }
            }
            val ch = if (c == '+') ' ' else c
            // Anything already a real character (a code that was not encoded)
            // goes in as its UTF-8 bytes, so it survives the final decode.
            if (ch.code < 0x80) out.write(ch.code) else out.write(ch.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }
}
