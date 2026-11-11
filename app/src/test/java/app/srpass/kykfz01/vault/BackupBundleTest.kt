package app.srpass.kykfz01.vault

import app.srpass.kykfz01.crypto.InMemoryKeyEnvelopeStore
import app.srpass.kykfz01.crypto.MasterKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class BackupBundleTest {

    private fun tempVaultFile(): File {
        val f = File.createTempFile("vault", ".kdbx")
        f.deleteOnExit()
        return f
    }

    private fun tempMediaDir(): File {
        val f = File.createTempFile("media", "")
        f.delete()
        f.mkdirs()
        f.deleteOnExit()
        return f
    }

    private fun sampleKey(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun writeThenReadRoundTripsVaultAndEnvelopes() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())

        val store = InMemoryKeyEnvelopeStore()
        val mkm = MasterKeyManager(store)
        mkm.createVault("password".toCharArray())

        val out = ByteArrayOutputStream()
        BackupBundle.write(out, file.inputStream(), mkm.exportableEnvelopes(), tempMediaDir())

        val read = BackupBundle.read(ByteArrayInputStream(out.toByteArray()))
        assertNotNull(read)
        read!!
        assertArrayEquals(file.readBytes(), read.vaultBytes)
        assertEquals(mkm.exportableEnvelopes().size, read.envelopes.size)
    }

    @Test
    fun isBundleDetectsZipSignature() {
        val out = ByteArrayOutputStream()
        BackupBundle.write(out, byteArrayOf(1, 2, 3).inputStream(), emptyList(), tempMediaDir())
        assertTrue(BackupBundle.isBundle(ByteArrayInputStream(out.toByteArray())))
        assertFalse(BackupBundle.isBundle(ByteArrayInputStream(byteArrayOf(0, 1, 2, 3))))
    }

    @Test
    fun readRejectsNonBackupArchive() {
        // A valid ZIP but not our layout: no envelopes entry.
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("something-else"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
        }
        assertNull(BackupBundle.read(ByteArrayInputStream(out.toByteArray())))
    }

    /**
     * The whole point: a backup made on one device must open on another with the
     * original master password / recovery key, even though the new device has
     * its own vault and master key.
     */
    @Test
    fun crossDeviceRestoreOpensWithOriginalPassword() {
        // --- Device A: creates the vault and exports a backup ---
        val storeA = InMemoryKeyEnvelopeStore()
        val mkmA = MasterKeyManager(storeA)
        val recovery = mkmA.createVault("correct horse battery".toCharArray())
        val fileA = tempVaultFile()
        val repoA = VaultRepository(fileA)
        repoA.createVault(mkmA.peekMasterKey()!!)

        val out = ByteArrayOutputStream()
        BackupBundle.write(out, fileA.inputStream(), mkmA.exportableEnvelopes(), tempMediaDir())

        // --- Device B: its own, completely different vault + master key ---
        val fileB = tempVaultFile()
        val storeB = InMemoryKeyEnvelopeStore()
        val mkmB = MasterKeyManager(storeB)
        mkmB.createVault("totally unrelated passphrase".toCharArray())
        val repoB = VaultRepository(fileB)
        repoB.createVault(mkmB.peekMasterKey()!!)

        // --- Import the backup into B ---
        val read = BackupBundle.read(ByteArrayInputStream(out.toByteArray()))!!
        fileB.writeBytes(read.vaultBytes)
        assertTrue(mkmB.restoreEnvelopes(read.envelopes))

        // B now opens with A's password (and recovery key).
        assertTrue(mkmB.unlockWithPassword("correct horse battery".toCharArray()))
        assertTrue(repoB.unlock(mkmB.peekMasterKey()!!))

        assertTrue(mkmB.unlockWithRecoveryKey(recovery))
        assertTrue(repoB.unlock(mkmB.peekMasterKey()!!))
    }

    @Test
    fun mediaFilesRoundTripThroughBackup() {
        // 一个加密媒体文件（磁盘上就是密文 .bin，这里用任意字节模拟）。
        val mediaDir = tempMediaDir()
        val blob = File(mediaDir, "aabbccddee.bin")
        blob.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))

        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        val store = InMemoryKeyEnvelopeStore()
        val mkm = MasterKeyManager(store)
        mkm.createVault("password".toCharArray())

        val out = ByteArrayOutputStream()
        BackupBundle.write(out, file.inputStream(), mkm.exportableEnvelopes(), mediaDir)

        val restoredDir = tempMediaDir()
        val read = BackupBundle.read(ByteArrayInputStream(out.toByteArray()), restoredDir)
        assertNotNull(read)

        val restoredBlob = File(restoredDir, "aabbccddee.bin")
        assertTrue(restoredBlob.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), restoredBlob.readBytes())
    }
}
