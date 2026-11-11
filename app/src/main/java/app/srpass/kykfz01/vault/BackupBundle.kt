package app.srpass.kykfz01.vault

import app.srpass.kykfz01.crypto.Channel
import app.srpass.kykfz01.crypto.CryptoConstants
import app.srpass.kykfz01.crypto.KeyEnvelope
import app.srpass.kykfz01.crypto.KdfParams
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A portable backup: the encrypted vault **plus** the wrapped master keys.
 *
 * The vault file on its own is not enough to move to another phone. Its KeePass
 * credential is the master key itself, and the master key never exists outside
 * the envelopes -- those live in SharedPreferences and are not part of the
 * .kdbx. Shipping both together is what lets the original master password and
 * recovery key open the vault again after a restore.
 *
 * The biometric envelope is deliberately excluded: its wrapping key lives in
 * the device Keystore and cannot travel, so it always has to be re-enabled on
 * the new device.
 *
 * Layout is a ZIP (streamed, so a vault full of photos never has to be held in
 * memory twice) holding:
 *   - `vault.kdbx`      the encrypted database, byte for byte
 *   - `envelopes.json`  the PASSWORD / RECOVERY envelopes
 *   - `media/`          the encrypted media blobs, byte for byte (still encrypted)
 */
object BackupBundle {

    private const val VAULT_ENTRY = "vault.kdbx"
    private const val ENVELOPES_ENTRY = "envelopes.json"
    private const val MEDIA_DIR = "media"

    private const val FORMAT = "srpass-backup"
    private const val FORMAT_VERSION = 1

    /** ZIP local-file-header signature, used to tell new backups from old ones. */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** KDBX magic: KeePass writes these two words at offset 0 of every database. */
    private val KDBX_MAGIC = byteArrayOf(
        0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte(),
        0x67, 0xFB.toByte(), 0x4B, 0xB5.toByte()
    )

    data class Backup(
        val vaultBytes: ByteArray,
        val envelopes: List<KeyEnvelope>
    )

    /**
     * Writes a backup. [vault] and every media file are streamed straight
     * through, so peak memory stays proportional to the ZIP buffer rather than
     * to the vault or media size.
     */
    fun write(output: OutputStream, vault: InputStream, envelopes: List<KeyEnvelope>, mediaDir: File) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENVELOPES_ENTRY))
            zip.write(envelopesToJson(envelopes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(VAULT_ENTRY))
            vault.copyTo(zip)
            zip.closeEntry()

            val files = mediaDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".bin") }
                ?: emptyList()
            for (f in files) {
                zip.putNextEntry(ZipEntry("$MEDIA_DIR/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a backup written by [write]. Media entries are streamed into
     * [mediaOutDir] when provided (still encrypted on disk).
     *
     * Returns null when the archive is not a SrPass backup or carries no usable
     * envelope -- restoring one of those would leave the user with a vault that
     * no credential on this device can open.
     */
    fun read(input: InputStream, mediaOutDir: File? = null): Backup? {
        var vaultBytes: ByteArray? = null
        var envelopes: List<KeyEnvelope>? = null

        runCatching {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == ENVELOPES_ENTRY ->
                            envelopes = jsonToEnvelopes(zip.readBytes().toString(Charsets.UTF_8))
                        entry.name == VAULT_ENTRY -> vaultBytes = zip.readBytes()
                        mediaOutDir != null && entry.name.startsWith("$MEDIA_DIR/") ->
                            extractMedia(zip, entry.name.removePrefix("$MEDIA_DIR/"), mediaOutDir)
                    }
                    zip.closeEntry()
                }
            }
        }.getOrNull() ?: return null

        val bytes = vaultBytes
        val envs = envelopes
        if (bytes == null || envs.isNullOrEmpty()) return null
        if (!looksLikeKdbx(bytes)) return null

        return Backup(bytes, envs)
    }

    /** Streams one media entry into the media directory (content-addressed). */
    private fun extractMedia(input: InputStream, name: String, dir: File) {
        // The id is a hex string, never a path: refuse anything with a slash.
        if (name.isBlank() || !name.endsWith(".bin") || name.contains('/')) return
        dir.mkdirs()
        val target = File(dir, name)
        if (target.exists() && target.length() > 0L) return // same content already present
        val tmp = File(dir, "$name.tmp")
        try {
            FileOutputStream(tmp).use { input.copyTo(it) }
            tmp.renameTo(target)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * True if [source] starts with a ZIP signature, i.e. it is a bundle rather
     * than a bare .kdbx exported by an older build.
     *
     * The stream is rewound afterwards so it can still be read in full.
     */
    fun isBundle(source: InputStream): Boolean {
        val buffered = source as? BufferedInputStream ?: source.buffered()
        buffered.mark(ZIP_MAGIC.size)
        val head = ByteArray(ZIP_MAGIC.size)
        // read() may return fewer bytes than asked for. Taking a short read as
        // "not a bundle" made a perfectly good bundle fall through to the bare
        // .kdbx path and fail to import.
        var filled = 0
        while (filled < head.size) {
            val read = runCatching { buffered.read(head, filled, head.size - filled) }.getOrDefault(-1)
            if (read <= 0) break
            filled += read
        }
        buffered.reset()
        return filled == ZIP_MAGIC.size && head.contentEquals(ZIP_MAGIC)
    }

    /** Cheap sanity check on the database header, so a wrong file fails here
     *  instead of at unlock time. */
    fun looksLikeKdbx(bytes: ByteArray): Boolean {
        if (bytes.size < KDBX_MAGIC.size) return false
        return KDBX_MAGIC.indices.all { bytes[it] == KDBX_MAGIC[it] }
    }

    // ---- envelope (de)serialisation -------------------------------------

    private fun envelopesToJson(envelopes: List<KeyEnvelope>): String {
        val arr = JSONArray()
        envelopes.forEach { env ->
            arr.put(
                JSONObject().apply {
                    put("channel", env.channel.id)
                    put("iv", b64(env.iv))
                    put("ct", b64(env.ciphertext))
                    env.salt?.let { put("salt", b64(it)) }
                    env.kdf?.let { put("kdf", it.encode()) }
                }
            )
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", FORMAT_VERSION)
            put("envelopes", arr)
        }.toString()
    }

    private fun jsonToEnvelopes(json: String): List<KeyEnvelope> {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) return emptyList()

        val arr = root.optJSONArray("envelopes") ?: return emptyList()
        val result = mutableListOf<KeyEnvelope>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val channel = Channel.entries.firstOrNull { it.id == o.optString("channel") }
                ?: continue
            // Biometric envelopes are never restored: their Keystore key is
            // bound to the device that created them.
            if (channel == Channel.BIOMETRIC) continue

            // Every field is mandatory for PASSWORD / RECOVERY envelopes: salt
            // feeds Argon2, iv sizes the GCM nonce, ct is the ciphertext. A
            // missing or corrupt field means the envelope can never open the
            // vault, so it is dropped rather than carried along to strand the
            // user with no working credential.
            val salt = o.optString("salt").takeIf { it.isNotEmpty() }?.let(::unb64) ?: continue
            val iv = o.optString("iv").takeIf { it.isNotEmpty() }?.let(::unb64)
                ?.takeIf { it.size == CryptoConstants.GCM_IV_BYTES } ?: continue
            val ct = o.optString("ct").takeIf { it.isNotEmpty() }?.let(::unb64) ?: continue
            val kdf = KdfParams.decode(o.optString("kdf").takeIf { it.isNotEmpty() }) ?: continue

            result += KeyEnvelope(channel, iv, ct, salt, kdf)
        }
        return result
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray? = runCatching {
        Base64.getMimeDecoder().decode(text)
    }.getOrNull()
}
