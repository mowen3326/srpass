package app.srpass.kykfz01.crypto

import android.content.Context
import java.util.Base64

/** Persistence for [KeyEnvelope]s. */
interface KeyEnvelopeStore {
    fun save(envelope: KeyEnvelope)
    fun load(channel: Channel): KeyEnvelope?
    fun clear(channel: Channel)
    fun hasAny(): Boolean
}

/**
 * Stores envelopes in a private SharedPreferences file.
 *
 * Everything written here is already ciphertext, so no extra encryption layer
 * is needed. The file lives in the app's private sandbox, and both cloud backup
 * and device transfer are disabled at the manifest level, so these values
 * cannot leave the device.
 */
class SharedPrefsKeyEnvelopeStore(context: Context) : KeyEnvelopeStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(envelope: KeyEnvelope) {
        val c = envelope.channel.id
        val editor = prefs.edit()
            .putString(field(c, FIELD_IV), envelope.iv.encode())
            .putString(field(c, FIELD_CIPHERTEXT), envelope.ciphertext.encode())

        envelope.salt?.let { editor.putString(field(c, FIELD_SALT), it.encode()) }
        envelope.kdf?.let { editor.putString(field(c, FIELD_KDF), it.encode()) }

        // commit(), not apply(): envelopes are the only way back into the
        // vault, so they must survive a process kill that happens immediately
        // after a create / password change.
        editor.commit()
    }

    override fun load(channel: Channel): KeyEnvelope? {
        val c = channel.id
        val iv = prefs.getString(field(c, FIELD_IV), null)?.decode() ?: return null
        val ciphertext =
            prefs.getString(field(c, FIELD_CIPHERTEXT), null)?.decode() ?: return null

        return KeyEnvelope(
            channel = channel,
            iv = iv,
            ciphertext = ciphertext,
            salt = prefs.getString(field(c, FIELD_SALT), null)?.decode(),
            kdf = KdfParams.decode(prefs.getString(field(c, FIELD_KDF), null))
        )
    }

    override fun clear(channel: Channel) {
        val c = channel.id
        // commit() keeps this in step with [KeyStoreManager.deleteBiometricKey],
        // which takes effect immediately. With apply() a process kill right
        // after disabling biometrics could leave the envelope on disk while the
        // Keystore key is already gone -- and the unlock screen would then
        // offer a fingerprint button that cannot work.
        prefs.edit()
            .remove(field(c, FIELD_IV))
            .remove(field(c, FIELD_CIPHERTEXT))
            .remove(field(c, FIELD_SALT))
            .remove(field(c, FIELD_KDF))
            .commit()
    }

    /**
     * Whether any envelope exists at all.
     *
     * Deliberately judged by the stored keys, not by [load]: `load` returns null
     * when a value fails to decode, so a corrupt or oddly-padded entry read as
     * "no vault here" -- and the app then offered to *create* one, which would
     * have overwritten a perfectly recoverable database.
     */
    override fun hasAny(): Boolean =
        prefs.all.keys.any { it.startsWith("env_") && it.endsWith("_$FIELD_CIPHERTEXT") }

    private fun field(channel: String, name: String) = "env_${channel}_$name"

    private fun ByteArray.encode(): String = Base64.getEncoder().encodeToString(this)

    /**
     * Tolerant decode: accepts the standard alphabet and the MIME flavour
     * (which ignores the trailing newline older builds wrote), and falls back
     * to the URL-safe alphabet before giving up.
     */
    private fun String.decode(): ByteArray? =
        runCatching { Base64.getDecoder().decode(this) }.getOrNull()
            ?: runCatching { Base64.getMimeDecoder().decode(this) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(this) }.getOrNull()

    private companion object {
        const val PREFS_NAME = "srpass_key_envelopes"
        const val FIELD_IV = "iv"
        const val FIELD_CIPHERTEXT = "ct"
        const val FIELD_SALT = "salt"
        const val FIELD_KDF = "kdf"
    }
}
