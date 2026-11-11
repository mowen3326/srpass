package app.srpass.kykfz01.vault

import app.srpass.kykfz01.crypto.CryptoConstants
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Content-addressed, encrypted media store.
 *
 * Every blob is encrypted with AES-256-GCM under a key derived from the master
 * key (see [Hkdf]), then written to `media/<id>.bin` where `<id>` is the
 * SHA-256 hex of the *plaintext*. The id doubles as the note's [ImageBlock] /
 * [AudioBlock] / [DrawingBlock] ref, so identical content is stored once and
 * the ref alone never leaks the content.
 *
 * Blobs are written and (for large media) read as streams: the encryption or
 * decryption buffer is a fixed size, so a 50 MB recording never requires the
 * ciphertext and plaintext to coexist in memory.
 */
class MediaStore(private val mediaDir: File) {

    private var key: ByteArray? = null
    private val secureRandom = SecureRandom()

    fun ensureDir() {
        mediaDir.mkdirs()
    }

    /** Installs the derived media key (called on unlock / create). */
    @Synchronized
    fun setKey(mediaKey: ByteArray) {
        key?.fill(0)
        key = mediaKey.copyOf()
    }

    /** Wipes the media key; after this the store cannot read or write. */
    @Synchronized
    fun clearKey() {
        key?.fill(0)
        key = null
    }

    /** Returns a private copy so a concurrent [clearKey] cannot zero it mid-use. */
    private fun requireKey(): ByteArray =
        synchronized(this) { key?.copyOf() ?: error("media store is locked") }

    /**
     * Encrypts [plain] and writes it atomically to `media/<id>.bin`, returning
     * the content id. Identical content is stored only once.
     */
    fun put(plain: ByteArray): String {
        val k = requireKey()
        val mediaId = sha256hex(plain)
        val target = File(mediaDir, "$mediaId.bin")
        if (target.exists()) return mediaId
        mediaDir.mkdirs()

        val iv = ByteArray(CryptoConstants.GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, ALG), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv))
        cipher.updateAAD(mediaId.toByteArray(Charsets.UTF_8))

        val tmp = File(mediaDir, "$mediaId.bin.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(iv)
                var off = 0
                while (off < plain.size) {
                    val len = minOf(BUFFER_SIZE, plain.size - off)
                    val enc = cipher.update(plain, off, len)
                    if (enc != null && enc.isNotEmpty()) out.write(enc)
                    off += len
                }
                out.write(cipher.doFinal()) // GCM tag (16 bytes)
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return mediaId
    }

    /** Reads and decrypts a whole blob. For small media (images). */
    fun get(mediaId: String): ByteArray? {
        val k = requireKey()
        val file = File(mediaDir, "$mediaId.bin")
        if (!file.exists()) return null
        return runCatching {
            FileInputStream(file).use { raw ->
                val iv = ByteArray(CryptoConstants.GCM_IV_BYTES)
                readFully(raw, iv)
                val cipher = decryptCipher(k, iv, mediaId)
                val body = raw.readBytes()
                val tagLen = CryptoConstants.GCM_TAG_BITS / 8
                if (body.size < tagLen) throw EOFException("media blob truncated")
                val ct = body.copyOfRange(0, body.size - tagLen)
                val tag = body.copyOfRange(body.size - tagLen, body.size)
                val first = cipher.update(ct) ?: ByteArray(0)
                val last = cipher.doFinal(tag)
                first + last
            }
        }.getOrNull()
    }

    /**
     * Streams the decrypted bytes into [out] without holding the whole blob in
     * memory. For large media (audio). Returns false on any failure (the output
     * file is deleted in that case).
     */
    fun decryptToFile(mediaId: String, out: File): Boolean {
        val k = requireKey()
        val file = File(mediaDir, "$mediaId.bin")
        if (!file.exists()) return false
        return runCatching {
            FileInputStream(file).use { raw ->
                val iv = ByteArray(CryptoConstants.GCM_IV_BYTES)
                readFully(raw, iv)
                val cipher = decryptCipher(k, iv, mediaId)
                val tagLen = CryptoConstants.GCM_TAG_BITS / 8
                val cipherLen = file.length() - CryptoConstants.GCM_IV_BYTES - tagLen
                val buf = ByteArray(BUFFER_SIZE)
                FileOutputStream(out).use { outStream ->
                    var read = 0L
                    while (read < cipherLen) {
                        val want = minOf(BUFFER_SIZE.toLong(), cipherLen - read).toInt()
                        val n = raw.read(buf, 0, want)
                        if (n < 0) throw EOFException("media blob truncated")
                        val plain = cipher.update(buf, 0, n)
                        if (plain != null && plain.isNotEmpty()) outStream.write(plain)
                        read += n
                    }
                    val tag = ByteArray(tagLen)
                    readFully(raw, tag)
                    cipher.doFinal(tag) // authenticates the whole stream
                }
            }
            true
        }.getOrElse { runCatching { out.delete() }; false }
    }

    fun delete(mediaId: String) {
        runCatching { File(mediaDir, "$mediaId.bin").delete() }
    }

    /** Ids of every stored blob. */
    fun list(): Set<String> =
        mediaDir.listFiles()
            ?.mapNotNull { f -> if (f.name.endsWith(".bin")) f.name.removeSuffix(".bin") else null }
            ?.toSet()
            ?: emptySet()

    /** Deletes every stored blob whose id is not in [used]. */
    fun collectOrphans(used: Set<String>) {
        val files = mediaDir.listFiles() ?: return
        for (f in files) {
            if (!f.isFile || !f.name.endsWith(".bin")) continue
            val id = f.name.removeSuffix(".bin")
            if (id !in used) runCatching { f.delete() }
        }
    }

    private fun decryptCipher(k: ByteArray, iv: ByteArray, mediaId: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(k, ALG), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv))
            updateAAD(mediaId.toByteArray(Charsets.UTF_8))
        }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    private fun sha256hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[b.toInt() shr 4 and 0xF])
            sb.append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ALG = "AES"
        const val BUFFER_SIZE = 64 * 1024
        const val HEX = "0123456789abcdef"
    }
}
