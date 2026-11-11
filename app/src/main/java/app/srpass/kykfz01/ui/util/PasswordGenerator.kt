package app.srpass.kykfz01.ui.util

import java.security.SecureRandom

/** Cryptographically-random password generator used by the entry editor. */
object PasswordGenerator {

    private val rng = SecureRandom()

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"

    fun generate(
        length: Int = 16,
        upper: Boolean = true,
        lower: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true
    ): String {
        val pool = buildString {
            if (upper) append(UPPER)
            if (lower) append(LOWER)
            if (digits) append(DIGITS)
            if (symbols) append(SYMBOLS)
        }
        if (pool.isEmpty() || length <= 0) return ""
        return (1..length).joinToString("") { pool[rng.nextInt(pool.length)].toString() }
    }
}
