package app.srpass.kykfz01.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class CryptoTest {

    // ---- Argon2Kdf ------------------------------------------------------

    @Test
    fun argon2_isDeterministicForSameInput() {
        val salt = ByteArray(16) { 1 }
        val pw = "password123".toCharArray()
        val a = Argon2Kdf.derive(pw, salt)
        val b = Argon2Kdf.derive(pw, salt)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun argon2_differsWithDifferentSalt() {
        val pw = "password123".toCharArray()
        val a = Argon2Kdf.derive(pw, ByteArray(16) { 1 })
        val b = Argon2Kdf.derive(pw, ByteArray(16) { 2 })
        assertFalse(a.contentEquals(b))
    }

    // ---- AeadCipher -----------------------------------------------------

    @Test
    fun aead_roundTrips() {
        val key = ByteArray(32) { 7 }
        val plaintext = "secret data".toByteArray()
        val sealed = AeadCipher.seal(key, plaintext)
        assertArrayEquals(plaintext, AeadCipher.open(key, sealed))
    }

    @Test
    fun aead_tamperedCiphertextFails() {
        val key = ByteArray(32) { 7 }
        val sealed = AeadCipher.seal(key, "hello".toByteArray())
        val tampered = sealed.copy(
            ciphertext = sealed.ciphertext.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }
        )
        assertThrows(AEADBadTagException::class.java) { AeadCipher.open(key, tampered) }
    }

    @Test
    fun aead_tamperedIvFails() {
        val key = ByteArray(32) { 7 }
        val sealed = AeadCipher.seal(key, "hello".toByteArray())
        val tampered = sealed.copy(
            iv = sealed.iv.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }
        )
        assertThrows(GeneralSecurityException::class.java) { AeadCipher.open(key, tampered) }
    }

    @Test
    fun aead_wrongKeyFails() {
        val sealed = AeadCipher.seal(ByteArray(32) { 1 }, "hello".toByteArray())
        assertThrows(GeneralSecurityException::class.java) {
            AeadCipher.open(ByteArray(32) { 2 }, sealed)
        }
    }

    // ---- RecoveryKey ----------------------------------------------------

    @Test
    fun recoveryKey_isFourGroupsOfEight() {
        val key = RecoveryKey.generate()
        val parts = key.split("-")
        assertEquals(4, parts.size)
        parts.forEach { assertEquals(8, it.length) }
        assertTrue(RecoveryKey.isValid(key))
    }

    @Test
    fun recoveryKey_normalizesConfusables() {
        val key = RecoveryKey.generate()
        // O and I are the most commonly confused glyphs; normalize must fold
        // them back so a hand-copied key still verifies.
        val mangled = key.replace('0', 'O').replace('1', 'I')
        assertTrue(RecoveryKey.isValid(mangled))
    }

    @Test
    fun recoveryKey_rejectsTooShort() {
        assertFalse(RecoveryKey.isValid("short"))
    }

    // ---- MasterKeyManager (password + recovery channels) ----------------

    @Test
    fun masterKey_roundTripWithPassword() {
        val mgr = MasterKeyManager(InMemoryKeyEnvelopeStore())
        mgr.createVault("correct horse battery staple".toCharArray())
        assertTrue(mgr.isUnlocked)

        mgr.lock()
        assertFalse(mgr.isUnlocked)

        assertTrue(mgr.unlockWithPassword("correct horse battery staple".toCharArray()))
        assertTrue(mgr.isUnlocked)
    }

    @Test
    fun masterKey_wrongPasswordFails() {
        val mgr = MasterKeyManager(InMemoryKeyEnvelopeStore())
        mgr.createVault("right-password".toCharArray())
        mgr.lock()
        assertFalse(mgr.unlockWithPassword("wrong-password".toCharArray()))
        assertFalse(mgr.isUnlocked)
    }

    @Test
    fun masterKey_recoveryKeyResetsPassword() {
        val mgr = MasterKeyManager(InMemoryKeyEnvelopeStore())
        val recovery = mgr.createVault("original-password".toCharArray())
        mgr.lock()

        assertTrue(mgr.unlockWithRecoveryKey(recovery))
        assertTrue(mgr.changeMasterPassword("new-password".toCharArray()))
        mgr.lock()

        assertFalse(mgr.unlockWithPassword("original-password".toCharArray()))
        assertTrue(mgr.unlockWithPassword("new-password".toCharArray()))
    }

    @Test
    fun recoveryKey_acceptsLowercaseAndSeparators() {
        val key = RecoveryKey.generate()
        // Hand-copied keys arrive in all shapes; only the alphabet matters.
        val messy = "  ${key.lowercase().replace("-", " ")}  "
        assertTrue(RecoveryKey.isValid(messy))
        assertEquals(RecoveryKey.normalize(key), RecoveryKey.normalize(messy))
    }

    /**
     * Regression test: there is no AndroidKeyStore on the JVM, and building a
     * biometric cipher used to let that provider exception escape all the way
     * to the UI. Both builders must answer null instead.
     */
    @Test
    fun biometricCiphersReturnNullInsteadOfThrowingWithoutKeystore() {
        val mgr = MasterKeyManager(InMemoryKeyEnvelopeStore())
        mgr.createVault("pw".toCharArray())

        assertNull(mgr.createBiometricEncryptCipher())
        assertNull(mgr.createBiometricDecryptCipher())
    }

    @Test
    fun masterKey_tamperedEnvelopeFails() {
        val store = InMemoryKeyEnvelopeStore()
        val mgr = MasterKeyManager(store)
        mgr.createVault("pw".toCharArray())
        mgr.lock()

        val env = store.load(Channel.PASSWORD)!!
        val ct = env.ciphertext.clone()
        ct[0] = (ct[0].toInt() xor 1).toByte()
        store.save(env.copy(ciphertext = ct))

        assertFalse(mgr.unlockWithPassword("pw".toCharArray()))
    }
}
