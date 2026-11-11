package app.srpass.kykfz01.crypto

import java.security.SecureRandom
import javax.crypto.Cipher

/**
 * Orchestrates the three unlock channels around a single in-memory master key.
 *
 * The master key (MK) is a random 256-bit secret. It is never persisted; only
 * three *wrapped* copies exist on disk, one per [Channel]. While the vault is
 * unlocked the MK lives in memory only and is wiped by [lock].
 *
 * Design rule: only the master key is ever wrapped. Vault entries are encrypted
 * with the master key, so adding or rotating a credential never requires
 * re-encrypting any stored entry.
 */
class MasterKeyManager(
    private val store: KeyEnvelopeStore,
    private val keyStoreManager: KeyStoreManager = KeyStoreManager()
) {

    @Volatile
    private var masterKey: ByteArray? = null

    /** True only between a successful [unlockWith*] and [lock]. */
    val isUnlocked: Boolean get() = masterKey != null

    /** Whether a vault has been created on this device. */
    @Synchronized
    fun hasVault(): Boolean = store.hasAny()

    /**
     * Creates a fresh vault and returns the recovery key, which the UI must
     * show to the user to copy down. It is the ONLY escape hatch if the master
     * password is forgotten, so it is never persisted here.
     */
    @Synchronized
    fun createVault(masterPassword: CharArray): String {
        // Guard against overwriting an existing vault's envelopes: doing so would
        // strand every historical entry, which is encrypted under the old master
        // key and can never be recovered. The caller (CreateVaultScreen) already
        // checks hasVault(), but a crash/retry/nav-back could reach here again.
        if (hasVault()) {
            throw IllegalStateException("设备上已存在密码库，无法重复创建")
        }
        val mk = randomMasterKey()
        val recoveryKey = RecoveryKey.generate()

        store.save(wrapWithPassphrase(Channel.PASSWORD, mk, masterPassword))
        store.save(wrapWithPassphrase(Channel.RECOVERY, mk, RecoveryKey.toKeyMaterial(recoveryKey)))

        masterKey = mk
        return recoveryKey
    }

    @Synchronized
    fun unlockWithPassword(masterPassword: CharArray): Boolean =
        unlockWithPassphrase(Channel.PASSWORD, masterPassword)

    /**
     * Confirms the master password without touching the current session.
     *
     * Used by the hidden space, which asks "are you still you" while the vault
     * is already open. Assigning [masterKey] here would at best be redundant
     * and at worst harmful: a mistyped password must never be able to change
     * the state of the notes the user is currently looking at. The unwrapped
     * MK is thrown away as soon as the answer is known.
     */
    @Synchronized
    fun verifyMasterPassword(masterPassword: CharArray): Boolean {
        val mk = unpackPassphrase(Channel.PASSWORD, masterPassword)
        mk?.fill(0)
        return mk != null
    }

    @Synchronized
    fun unlockWithRecoveryKey(input: String): Boolean {
        if (!RecoveryKey.isValid(input)) return false
        return unlockWithPassphrase(Channel.RECOVERY, RecoveryKey.toKeyMaterial(input))
    }

    // ---- Biometric channel (Android Keystore) ---------------------------

    @Synchronized
    fun isBiometricEnabled(): Boolean = store.load(Channel.BIOMETRIC) != null

    /**
     * Builds a cipher to wrap the MK under the Keystore key. Hand it to
     * BiometricPrompt; once authentication succeeds, pass the authorised cipher
     * to [enableBiometric].
     *
     * Returns null when the Keystore key cannot be created or read, which
     * includes the case where no Class 3 biometric is enrolled -- generating
     * the key throws there, so callers get a clean null instead of a crash.
     */
    @Synchronized
    fun createBiometricEncryptCipher(): Cipher? {
        if (!keyStoreManager.hasBiometricKey()) {
            runCatching { keyStoreManager.createBiometricKey() }.getOrNull() ?: return null
        }
        return keyStoreManager.createEncryptCipher()
    }

    /**
     * Builds a decrypt cipher, or null if biometric unlock is not usable.
     *
     * Beyond "never enabled", this also covers a Keystore key that went away or
     * was invalidated by a new fingerprint enrolment. The stale envelope is
     * dropped in that case, so the unlock screen stops offering a fingerprint
     * button that would fail (and previously crashed) on tap.
     */
    @Synchronized
    fun createBiometricDecryptCipher(): Cipher? {
        val envelope = store.load(Channel.BIOMETRIC) ?: return null
        val cipher = keyStoreManager.createDecryptCipher(envelope.iv)
        // Only a Keystore key that is really gone justifies throwing the
        // envelope away. A transient failure (the cipher could not be built for
        // some other reason) used to delete it too -- which silently disabled
        // fingerprint unlock on a device where it would have worked again.
        if (cipher == null && !keyStoreManager.hasBiometricKey()) {
            store.clear(Channel.BIOMETRIC)
        }
        return cipher
    }

    /** Persists a Keystore-wrapped copy of the currently unlocked MK. */
    @Synchronized
    fun enableBiometric(cipher: Cipher): Boolean {
        val mk = masterKey ?: return false
        val sealed = AeadCipher.sealWithCipher(cipher, mk, aad(Channel.BIOMETRIC))
        store.save(KeyEnvelope(Channel.BIOMETRIC, sealed.iv, sealed.ciphertext))
        return true
    }

    @Synchronized
    fun unlockWithBiometric(cipher: Cipher): Boolean {
        val mk = unwrapBiometric(cipher) ?: return false
        masterKey = mk
        return true
    }

    /**
     * Same check as [verifyMasterPassword], through the biometric channel: an
     * already-authenticated cipher opens the Keystore-wrapped envelope, and the
     * MK behind it is discarded again instead of being installed.
     */
    @Synchronized
    fun verifyBiometric(cipher: Cipher): Boolean {
        val mk = unwrapBiometric(cipher)
        mk?.fill(0)
        return mk != null
    }

    private fun unwrapBiometric(cipher: Cipher): ByteArray? {
        val envelope = store.load(Channel.BIOMETRIC) ?: return null
        return runCatching {
            AeadCipher.openWithCipher(
                cipher,
                SealedData(envelope.iv, envelope.ciphertext),
                aad(Channel.BIOMETRIC)
            )
        }.getOrNull()
    }

    @Synchronized
    fun disableBiometric() {
        store.clear(Channel.BIOMETRIC)
        keyStoreManager.deleteBiometricKey()
    }

    /**
     * Removes every envelope and the device-bound biometric key, and wipes the
     * in-memory MK. Used to roll back a failed [createVault]: the wrapped
     * credentials must not linger on disk when no usable vault exists, or the
     * next launch would offer to unlock a database that was never written.
     */
    @Synchronized
    fun wipeCredentials() {
        Channel.entries.forEach { store.clear(it) }
        keyStoreManager.deleteBiometricKey()
        masterKey?.fill(0)
        masterKey = null
    }

    // ---- Backup / restore ----------------------------------------------

    /**
     * The wrapped master keys a backup needs: password and recovery.
     *
     * Master password and recovery key are portable -- they only need the user's
     * head -- so taking their envelopes along is what makes a backup restorable
     * on a new phone. The vault file alone is not enough: it is encrypted under
     * the MK, and the MK only ever exists inside these envelopes.
     */
    // 同步：导入回滚要用它取"导入前"的快照，另一个线程可能正在换信封。
    // 少了它，取到的可能是一半旧一半新的信封，回滚就回滚错了。
    @Synchronized
    fun exportableEnvelopes(): List<KeyEnvelope> =
        listOf(Channel.PASSWORD, Channel.RECOVERY).mapNotNull { store.load(it) }

    /**
     * Installs the envelopes from a backup, replacing whatever was here.
     *
     * Only PASSWORD and RECOVERY are accepted. A biometric envelope is useless
     * across devices -- its wrapping key never leaves the old Keystore -- so
     * accepting one would leave a fingerprint button that can only fail.
     *
     * @return false if the backup carried no portable credential. Restoring
     * that would strand the user with a vault nothing can open, so the caller
     * must refuse the import instead.
     */
    @Synchronized
    fun restoreEnvelopes(envelopes: List<KeyEnvelope>): Boolean {
        val portable = envelopes.filter {
            it.channel == Channel.PASSWORD || it.channel == Channel.RECOVERY
        }
        if (portable.isEmpty()) return false

        // Restore before clearing: a crash (or a thrown error from store.save)
        // mid-way must never leave zero envelopes on disk, or the next launch
        // would offer to "create a vault" and overwrite the still-good .kdbx.
        portable.forEach { store.save(it) }

        // Only the biometric channel is dropped, and only because its wrapping
        // key belongs to the device the backup came from. Clearing *every*
        // channel the backup did not carry used to wipe whichever credential
        // was missing -- a backup holding only the password envelope silently
        // destroyed the recovery envelope, i.e. the user's last way back in.
        store.clear(Channel.BIOMETRIC)
        keyStoreManager.deleteBiometricKey()
        return true
    }

    // ---- Password maintenance -------------------------------------------

    /** Re-wraps the MK under a new master password (vault already unlocked). */
    @Synchronized
    fun changeMasterPassword(newMasterPassword: CharArray): Boolean {
        val mk = masterKey ?: return false
        store.save(wrapWithPassphrase(Channel.PASSWORD, mk, newMasterPassword))
        return true
    }

    /**
     * Verifies the recovery key, then re-wraps the MK under a new master
     * password. Biometric envelopes are untouched because the MK is unchanged.
     */
    @Synchronized
    fun resetMasterPassword(recoveryKeyInput: String, newMasterPassword: CharArray): Boolean {
        if (!unlockWithRecoveryKey(recoveryKeyInput)) return false
        return changeMasterPassword(newMasterPassword)
    }

    // ---- Lifecycle ------------------------------------------------------

    /** Wipes the in-memory MK. Call when the app goes to the background. */
    @Synchronized
    fun lock() {
        masterKey?.fill(0)
        masterKey = null
    }

    /**
     * Returns a copy of the MK if unlocked, else null.
     *
     * A copy rather than the live array: the caller may hand it to an IO thread
     * (the repository runs its work off the main thread), and [lock] wiping the
     * original mid-read used to make that read see zeroes.
     */
    @Synchronized
    fun peekMasterKey(): ByteArray? = masterKey?.copyOf()

    // ---- Internals ------------------------------------------------------

    private fun unlockWithPassphrase(channel: Channel, passphrase: CharArray): Boolean {
        val mk = unpackPassphrase(channel, passphrase) ?: return false
        // Wipe whatever MK was here before: re-unlocking (or unlocking through
        // another channel) used to leave the previous key material on the heap.
        val previous = masterKey
        masterKey = mk
        if (previous != null && previous !== mk) previous.fill(0)
        return true
    }

    /**
     * Unwraps the MK from a passphrase envelope, or null if it does not open.
     *
     * The caller owns the returned key and must wipe it once done. Whether the
     * result is *kept* is a separate decision -- see [unlockWithPassphrase]
     * (keep, for unlocking) versus [verifyMasterPassword] (wipe, merely a check).
     */
    private fun unpackPassphrase(channel: Channel, passphrase: CharArray): ByteArray? {
        val envelope = store.load(channel) ?: return null
        val salt = envelope.salt ?: return null
        val kek = Argon2Kdf.derive(passphrase, salt, params = envelope.kdf ?: KdfParams())
        val mk = runCatching {
            AeadCipher.open(kek, SealedData(envelope.iv, envelope.ciphertext), aad(channel))
        }.getOrNull()
        // Wipe the derived key material and the supplied passphrase as soon as
        // they are no longer needed.
        passphrase.fill('\u0000')
        kek.fill(0)
        return mk
    }

    private fun wrapWithPassphrase(channel: Channel, mk: ByteArray, passphrase: CharArray): KeyEnvelope {
        val salt = ByteArray(CryptoConstants.SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val kek = Argon2Kdf.derive(passphrase, salt)
        val sealed = AeadCipher.seal(kek, mk, aad(channel))
        // Wipe the passphrase and derived key material; the MK must be preserved.
        passphrase.fill('\u0000')
        kek.fill(0)
        return KeyEnvelope(channel, sealed.iv, sealed.ciphertext, salt, KdfParams())
    }

    /** Authenticated associated data: binds an envelope to its channel. */
    private fun aad(channel: Channel): ByteArray = channel.id.toByteArray(Charsets.UTF_8)

    private fun randomMasterKey(): ByteArray =
        ByteArray(CryptoConstants.MASTER_KEY_BYTES).also { SecureRandom().nextBytes(it) }
}
