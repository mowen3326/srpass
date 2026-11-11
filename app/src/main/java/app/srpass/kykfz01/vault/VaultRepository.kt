package app.srpass.kykfz01.vault

import app.srpass.kykfz01.crypto.Hkdf
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.getEntries
import app.keemobile.kotpass.database.getEntryBy
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.database.modifiers.*
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/**
 * KDBX custom field carrying the "hidden" mark on a password entry, a note, a
 * bank card or an authenticator account.
 *
 * Plain rather than Protected, same as "Content": the whole file is already
 * encrypted under the master key, and placed in the item's own entry so a
 * backup carries it automatically.
 */
private const val FIELD_HIDDEN = "Hidden"

private const val VALUE_HIDDEN = "1"
private const val VALUE_VISIBLE = "0"

/** HKDF domain separation for the media encryption key (see [Hkdf]). */
private val MEDIA_KEY_SALT = "srpass-media-key".toByteArray(Charsets.UTF_8)
private val MEDIA_KEY_INFO = "media-encryption".toByteArray(Charsets.UTF_8)

/**
 * A single password record, decoupled from kotpass so the UI never touches the
 * KDBX model directly.
 */
data class VaultEntry(
    val uuid: UUID,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = "",
    val updatedAt: Long = 0L,
    /** 放进隐藏空间的标记，写在自己的 KDBX 条目里，备份/恢复后依然是隐藏的。 */
    val hidden: Boolean = false
)

/**
 * Persists encrypted entries as a KDBX 4.1 file (via kotpass).
 *
 * The KDBX master credential is derived from the in-memory master key (MK):
 * the MK's hex digest becomes the KeePass master password. So the file is
 * encrypted twice -- once by our Argon2id-wrapped MK, once by KeePass's own
 * KDF -- and can only be opened after the MK has been unlocked through one of
 * the three channels in [MasterKeyManager].
 *
 * The KDBX file lives in the app's private storage and is never transmitted.
 *
 * Notes live in a dedicated "Notes" group (separate from password entries).
 * Each note is a KDBX entry whose block list is JSON in a custom field; media
 * (images / audio / drawings) are stored as encrypted files in the adjacent
 * media directory and referenced by [ImageBlock.ref].
 */
class VaultRepository(private val vaultFile: File) {

    private val secureRandom = SecureRandom()
    private var database: KeePassDatabase? = null

    /**
     * Media ids written by [addBinary] that no note references yet. Kept out of
     * [dropOrphanBinaries]' reach while the user is still editing.
     */
    private val pendingBinaries = mutableSetOf<String>()

    /** Encrypted media files live alongside the vault file. */
    private val mediaStore = MediaStore(
        File(vaultFile.parentFile ?: error("vault file has no parent directory"), "media")
    )

    /** Creates a brand-new empty vault. Overwrites any existing file. */
    @Synchronized
    fun createVault(masterKey: ByteArray) {
        val db = KeePassDatabase.Ver4x.create(
            "SrPass",
            Meta(generator = "SrPass"),
            credentialsFor(masterKey),
            secureRandom
        )
        val dir = vaultFile.parentFile ?: error("vault has no parent directory")
        val tmp = File(dir, "${vaultFile.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                db.encode(out)
                runCatching { out.fd.sync() }
            }
            rotateIntoPlace(tmp, dir)
        } finally {
            tmp.delete()
        }
        database = db
        mediaStore.setKey(deriveMediaKey(masterKey))
        mediaStore.ensureDir()
    }

    /**
     * Puts the freshly encoded [tmp] where the vault belongs, keeping the
     * current file as `.bak`.
     *
     * The order matters. Rotating the current file away *first* used to leave a
     * window in which neither name held a database: if the process died or the
     * rename failed between the two steps, `vault.kdbx` simply did not exist
     * and nothing in the app knew how to get it back. So the new file is moved
     * over the old one first and the previous generation is kept under `.bak`
     * by rename of a copy, and any failure rolls the original back.
     */
    private fun rotateIntoPlace(tmp: File, dir: File) {
        val bak = File(dir, "${vaultFile.name}.bak")
        val hadVault = vaultFile.exists()
        // Snapshot the current generation before it is replaced.
        if (hadVault) {
            bak.delete()
            runCatching { vaultFile.copyTo(bak, overwrite = true) }
        }
        // REPLACE_EXISTING + ATOMIC_MOVE：目标文件本来就在（保存已有库时一定
        // 在），而 File.renameTo 在部分文件系统上（典型是 Windows）覆盖已存在
        // 的文件会直接失败。原子替换在 POSIX 上就是 rename，语义和原来一致。
        val moved = runCatching {
            Files.move(
                tmp.toPath(),
                vaultFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            true
        }.getOrElse {
            // 少数文件系统不支持原子替换（或跨挂载点）：退回非原子移动，
            // 至少保证目标被换掉，而不是原地失败。
            runCatching {
                Files.move(tmp.toPath(), vaultFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.isSuccess
        }
        if (!moved) {
            // Put the previous generation back: without it the vault is gone.
            if (hadVault && !vaultFile.exists() && bak.exists()) {
                runCatching { bak.renameTo(vaultFile) }
            }
            throw IOException("cannot move $tmp into place")
        }
        fsyncDir(dir)
    }

    /**
     * Restores the previous generation when the main file is missing.
     *
     * True when a `.bak` was found and moved back. Without this a crash during
     * [save] left the user with no database at all even though the previous
     * generation was still on disk.
     */
    @Synchronized
    fun recoverFromBackup(): Boolean {
        if (vaultFile.exists()) return false
        val dir = vaultFile.parentFile ?: return false
        val bak = File(dir, "${vaultFile.name}.bak")
        if (!bak.exists()) return false
        return bak.renameTo(vaultFile)
    }

    /** Makes the directory entry itself durable; best effort, never fatal. */
    private fun fsyncDir(dir: File) {
        runCatching { FileInputStream(dir).use { it.fd.sync() } }
    }

    /**
     * Opens the vault with the unlocked MK. Returns false if the file is
     * missing (or unrecoverable) or the MK does not match.
     */
    @Synchronized
    fun unlock(masterKey: ByteArray): Boolean {
        recoverFromBackup()
        if (!vaultFile.exists()) return false
        val ok = openAndRemember(masterKey)
        if (ok) {
            mediaStore.setKey(deriveMediaKey(masterKey))
            mediaStore.ensureDir()
        }
        return ok
    }

    private fun openAndRemember(masterKey: ByteArray): Boolean {
        // A corrupt file and a wrong key look identical to the caller, so log
        // nothing and let the UI report "wrong master password" -- but do not
        // report success when the file could not be parsed.
        val db = runCatching {
            vaultFile.inputStream().use {
                KeePassDatabase.decode(it, credentialsFor(masterKey))
            }
        }.getOrNull() ?: return false
        database = db
        return true
    }

    /**
     * True when [masterKey] can open the vault currently on disk.
     *
     * Unlike [unlock] this leaves [database] untouched, so it can be used to
     * validate a file (for example a freshly imported backup) without
     * disturbing the session that is already open.
     */
    @Synchronized
    fun canOpen(masterKey: ByteArray): Boolean {
        if (!vaultFile.exists()) return false
        return runCatching {
            vaultFile.inputStream().use {
                KeePassDatabase.decode(it, credentialsFor(masterKey))
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Drops the in-memory database handle. The MK is wiped by [MasterKeyManager.lock].
     *
     * [pendingBinaries] deliberately survives: those bytes are already written
     * to disk by [addBinary], and clearing the set here used to hand them to
     * [dropOrphanBinaries] on the next save -- a photo the user had just
     * picked silently disappeared because the vault happened to be locked
     * mid-edit. They are cleared once the edit is committed instead.
     */
    @Synchronized
    fun lock() {
        database = null
        mediaStore.clearKey()
    }

    /**
     * Gives up the protection on binaries that were staged but never used.
     *
     * Called when an editor is abandoned: [dropOrphanBinaries] can then reclaim
     * the staged photo instead of carrying it in every future backup.
     */
    @Synchronized
    fun discardPendingBinaries() {
        pendingBinaries.clear()
        // 库已经锁上就到此为止：没有 database，[dropOrphanBinaries] 收集不到任何
        // 引用（referenced 会是空集），继续往下会当成"全部媒体都是孤儿"清空。
        // 这些暂存的媒体下次解锁后、下一次保存时照样会被回收，这里没有可写的东西。
        if (database == null) return
        dropOrphanBinaries()
    }

    /**
     * One side of the visible / hidden split; see [getNotes] for why the filter
     * belongs in the repository rather than in the UI.
     */
    @Synchronized
    fun getAllEntries(hidden: Boolean = false): List<VaultEntry> {
        val db = database ?: return emptyList()
        // The vault holds one group per module. Only the root group's entries
        // are passwords -- everything else belongs to notes, cards or the
        // authenticator and must not leak into the password list. Add new
        // modules here or their entries will show up as bogus passwords.
        val excluded = setOfNotNull(
            notesGroup()?.uuid,
            cardsGroup()?.uuid,
            otpGroup()?.uuid
        )
        return db.getEntries { true }
            .flatMap { (group, entries) ->
                if (group.uuid in excluded) emptyList()
                else entries.map { it.toVaultEntry() }.filter { it.hidden == hidden }
            }
    }

    @Synchronized
    fun getEntry(uuid: UUID): VaultEntry? =
        database?.getEntryBy { this.uuid == uuid }?.toVaultEntry()

    /** Moves a password entry between the visible list and the hidden space. */
    @Synchronized
    fun setEntryHidden(uuid: UUID, hidden: Boolean) {
        val current = getEntry(uuid) ?: return
        rewriteFields(uuid, current.copy(hidden = hidden).toKeePassFields())
    }

    /**
     * Rewrites one entry's fields without touching its timestamps.
     *
     * [modifyEntry] always restamps TimeData.lastModificationTime, and for
     * password entries, cards and OTPs that stamp *is* the "修改时间" the UI
     * shows and the sort key of the list. Hiding would then float the item to
     * the top of the list it is being taken out of -- a visible trace that
     * something was removed. Replacing the entry inside its group leaves its
     * own [TimeData] alone; only the group is restamped, and nobody shows that.
     *
     * The owning group is found by entry uuid instead of being passed in:
     * passwords normally live in the root group, but a vault imported from
     * another KeePass client can nest them, and a hardcoded group would then
     * silently match nothing.
     */
    private fun rewriteFields(uuid: UUID, fields: EntryFields) {
        val db = database ?: error("vault is locked")
        val groupUuid = findGroupOf(db.content.group, uuid)?.uuid ?: return
        database = db.modifyGroup(groupUuid) {
            copy(entries = entries.map { if (it.uuid == uuid) it.copy(fields = fields) else it })
        }
        save()
    }

    /** Depth-first search for the group holding the entry with this [uuid]. */
    private fun findGroupOf(group: Group, uuid: UUID): Group? {
        if (group.entries.any { it.uuid == uuid }) return group
        for (sub in group.groups) findGroupOf(sub, uuid)?.let { return it }
        return null
    }

    @Synchronized
    fun addEntry(entry: VaultEntry): VaultEntry {
        val db = database ?: error("vault is locked")
        val newEntry = Entry(
            uuid = entry.uuid,
            fields = entry.toKeePassFields()
        )
        database = db.modifyGroup(db.content.group.uuid) {
            copy(entries = entries + newEntry)
        }
        save()
        return entry
    }

    /**
     * Guards the mutating calls below: kotpass' `modifyEntry` / `removeEntry`
     * return the database unchanged when the uuid is unknown, so a write to an
     * entry that no longer exists used to look like a success. Failing loudly
     * is what lets the UI say "保存失败" instead of showing stale data.
     */
    private fun assertKnown(uuid: UUID) {
        val db = database ?: error("vault is locked")
        val known = db.getEntries { true }
            .any { (_, entries) -> entries.any { it.uuid == uuid } }
        if (!known) error("entry $uuid is not in the vault")
    }

    @Synchronized
    fun updateEntry(entry: VaultEntry) {
        assertKnown(entry.uuid)
        val db = database ?: error("vault is locked")
        database = db.modifyEntry(entry.uuid) {
            copy(fields = entry.toKeePassFields())
        }
        save()
    }

    @Synchronized
    fun deleteEntry(uuid: UUID) {
        assertKnown(uuid)
        val db = database ?: error("vault is locked")
        database = db.removeEntry(uuid)
        save()
    }

    // ---- Notes (separate "Notes" group) ---------------------------------

    private fun notesGroup(): Group? =
        database?.content?.group?.groups?.firstOrNull { it.name == "Notes" }

    private fun ensureNotesGroup(): UUID {
        var db = database ?: error("vault is locked")
        val existing = db.content.group.groups.firstOrNull { it.name == "Notes" }
        if (existing != null) return existing.uuid
        val g = Group(uuid = UUID.randomUUID(), name = "Notes")
        db = db.modifyGroup(db.content.group.uuid) { copy(groups = groups + g) }
        database = db
        save()
        return g.uuid
    }

    private fun Note.toFields(): EntryFields = EntryFields(
        mapOf(
            "Title" to EntryValue.Plain(title),
            // 读不懂的内容原样写回去（rawContent 只在解析失败时有值）；
            // 否则一次无关的保存就会把它永久抹掉。
            "Content" to EntryValue.Plain(rawContent ?: blocksToJson(blocks)),
            "UpdatedAt" to EntryValue.Plain(updatedAt.toString()),
            // Written unconditionally, even for visible notes: a note that was
            // hidden must overwrite the previous "1" rather than rely on the
            // reader treating a missing field as "visible".
            FIELD_HIDDEN to EntryValue.Plain(if (hidden) VALUE_HIDDEN else VALUE_VISIBLE)
        )
    )

    private fun Entry.toNote(): Note {
        val title = fields["Title"]?.content ?: ""
        val json = fields["Content"]?.content ?: "[]"
        val ts = fields["UpdatedAt"]?.content?.toLongOrNull() ?: 0L
        // A missing flag means visible: notes written before this feature (or
        // by another KeePass client) stay where the user left them.
        val hidden = fields[FIELD_HIDDEN]?.content == VALUE_HIDDEN
        val parsed = jsonToBlocks(json)
        return Note(
            uuid = uuid,
            title = title,
            updatedAt = ts,
            blocks = parsed ?: emptyList(),
            // 解析不出来就把原文带上，交给下一次保存原样写回。
            rawContent = if (parsed == null) json else null,
            hidden = hidden
        )
    }

    /**
     * One side of the visible / hidden split.
     *
     * The filter belongs here rather than in the UI: any caller that forgets to
     * filter would silently put private notes back on the main list, and that
     * mistake is invisible until the user notices their note was displayed.
     */
    @Synchronized
    fun getNotes(hidden: Boolean): List<Note> {
        val group = notesGroup() ?: return emptyList()
        return group.entries
            .map { it.toNote() }
            .filter { it.hidden == hidden }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Moves a note between the visible list and the hidden space.
     *
     * [Note.updatedAt] is kept as-is on purpose. [updateNote] restamps it, and
     * that would float a just-hidden note to the top of the list it is supposed
     * to disappear from -- a visible trace that something was taken away.
     *
     * The whole field set is rebuilt from the note; media refs live in the
     * note's own JSON, so images and audio survive untouched.
     */
    @Synchronized
    fun setNoteHidden(uuid: UUID, hidden: Boolean) {
        val db = database ?: error("vault is locked")
        val current = getNote(uuid) ?: return
        val target = current.copy(hidden = hidden)
        database = db.modifyEntry(uuid) { copy(fields = target.toFields()) }
        save()
    }

    @Synchronized
    fun getNote(uuid: UUID): Note? =
        notesGroup()?.entries?.firstOrNull { it.uuid == uuid }?.toNote()

    @Synchronized
    fun addNote(note: Note): Note {
        val groupUuid = ensureNotesGroup()
        val db = database ?: error("vault is locked")
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyGroup(groupUuid) {
            copy(
                entries = entries + Entry(
                    uuid = stamped.uuid,
                    fields = stamped.toFields()
                )
            )
        }
        // The note now references what the user picked, so those no longer need
        // pending protection.
        releasePending(stamped)
        save()
        return stamped
    }

    /**
     * 只撤掉这条笔记真正引用上的图片的"暂存保护"。
     *
     * 以前是无条件 clear()：保护集合是全局的，另一篇还在编辑的笔记刚导入、尚
     * 未提交的图片会被一起撤掉保护，下一次 save() 的 [dropOrphanBinaries] 就把
     * 它当孤儿回收，笔记里留下一个取不出内容的图片引用。
     */
    private fun releasePending(note: Note) {
        pendingBinaries -= note.blocks.filterIsInstance<BinaryBlock>().map { it.ref }.toSet()
    }

    @Synchronized
    fun updateNote(note: Note) {
        assertKnown(note.uuid)
        val db = database ?: error("vault is locked")
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyEntry(stamped.uuid) {
            copy(fields = stamped.toFields())
        }
        dropOrphanBinaries()
        releasePending(stamped)
        save()
    }

    @Synchronized
    fun deleteNote(uuid: UUID) {
        assertKnown(uuid)
        val db = database ?: error("vault is locked")
        database = db.removeEntry(uuid)
        dropOrphanBinaries()
        save()
    }

    // ---- Bank cards (separate "Cards" group) ------------------------------

    private fun cardsGroup(): Group? =
        database?.content?.group?.groups?.firstOrNull { it.name == "Cards" }

    private fun ensureCardsGroup(): UUID {
        var db = database ?: error("vault is locked")
        val existing = db.content.group.groups.firstOrNull { it.name == "Cards" }
        if (existing != null) return existing.uuid
        val g = Group(uuid = UUID.randomUUID(), name = "Cards")
        db = db.modifyGroup(db.content.group.uuid) { copy(groups = groups + g) }
        database = db
        save()
        return g.uuid
    }

    private fun BankCard.toFields(): EntryFields = EntryFields(
        mapOf(
            "Title" to EntryValue.Plain(nickname),
            "BankName" to EntryValue.Plain(bankName),
            "CardNumber" to EntryValue.Encrypted(EncryptedValue.fromString(cardNumber)),
            "CardHolder" to EntryValue.Plain(cardHolder),
            "Expiry" to EntryValue.Plain(expiry),
            "CVV" to EntryValue.Encrypted(EncryptedValue.fromString(cvv)),
            "PIN" to EntryValue.Encrypted(EncryptedValue.fromString(pin6)),
            "OLBAccount" to EntryValue.Encrypted(EncryptedValue.fromString(olbAccount)),
            "OLBPassword" to EntryValue.Encrypted(EncryptedValue.fromString(olbPassword)),
            "Notes" to EntryValue.Plain(notes),
            "CardType" to EntryValue.Plain(type.name.lowercase()),
            // Written unconditionally: a card that was hidden must overwrite
            // the previous "1" rather than rely on the reader treating a
            // missing field as "visible".
            FIELD_HIDDEN to EntryValue.Plain(if (hidden) VALUE_HIDDEN else VALUE_VISIBLE)
        )
    )

    private fun Entry.toCard(): BankCard {
        val f = fields
        val isCredit = (f["CardType"]?.content ?: "debit").equals("credit", true)
        return BankCard(
            uuid = uuid,
            type = if (isCredit) CardType.CREDIT else CardType.DEBIT,
            nickname = f["Title"]?.content ?: "",
            bankName = f["BankName"]?.content ?: "",
            cardNumber = f["CardNumber"]?.content ?: "",
            cardHolder = f["CardHolder"]?.content ?: "",
            expiry = f["Expiry"]?.content ?: "",
            cvv = f["CVV"]?.content ?: "",
            pin6 = f["PIN"]?.content ?: "",
            olbAccount = f["OLBAccount"]?.content ?: "",
            olbPassword = f["OLBPassword"]?.content ?: "",
            notes = f["Notes"]?.content ?: "",
            updatedAt = times?.lastModificationTime?.toEpochMilli() ?: 0L,
            // A missing flag means visible: cards written before this feature
            // (or by another KeePass client) stay where the user left them.
            hidden = f[FIELD_HIDDEN]?.content == VALUE_HIDDEN
        )
    }

    /** One side of the visible / hidden split; see [getNotes]. */
    @Synchronized
    fun getAllCards(hidden: Boolean = false): List<BankCard> {
        val group = cardsGroup() ?: return emptyList()
        return group.entries
            .map { it.toCard() }
            .filter { it.hidden == hidden }
            .sortedByDescending { it.updatedAt }
    }

    /** Moves a card between the visible list and the hidden space. */
    @Synchronized
    fun setCardHidden(uuid: UUID, hidden: Boolean) {
        val current = getCard(uuid) ?: return
        rewriteFields(uuid, current.copy(hidden = hidden).toFields())
    }

    @Synchronized
    fun getCard(uuid: UUID): BankCard? =
        cardsGroup()?.entries?.firstOrNull { it.uuid == uuid }?.toCard()

    @Synchronized
    fun addCard(card: BankCard): BankCard {
        val groupUuid = ensureCardsGroup()
        val db = database ?: error("vault is locked")
        val stamped = card.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyGroup(groupUuid) {
            copy(entries = entries + Entry(uuid = stamped.uuid, fields = stamped.toFields()))
        }
        save()
        return stamped
    }

    @Synchronized
    fun updateCard(card: BankCard) {
        assertKnown(card.uuid)
        val db = database ?: error("vault is locked")
        val stamped = card.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyEntry(stamped.uuid) {
            copy(fields = stamped.toFields())
        }
        save()
    }

    @Synchronized
    fun deleteCard(uuid: UUID) {
        assertKnown(uuid)
        val db = database ?: error("vault is locked")
        database = db.removeEntry(uuid)
        save()
    }

    // ---- Authenticator (separate "OTP" group) -----------------------------

    private fun otpGroup(): Group? =
        database?.content?.group?.groups?.firstOrNull { it.name == "OTP" }

    private fun ensureOtpGroup(): UUID {
        var db = database ?: error("vault is locked")
        val existing = db.content.group.groups.firstOrNull { it.name == "OTP" }
        if (existing != null) return existing.uuid
        val g = Group(uuid = UUID.randomUUID(), name = "OTP")
        db = db.modifyGroup(db.content.group.uuid) { copy(groups = groups + g) }
        database = db
        save()
        return g.uuid
    }

    private fun OtpEntry.toFields(): EntryFields = EntryFields(
        mapOf(
            "Title" to EntryValue.Plain(displayTitle()),
            "Issuer" to EntryValue.Plain(issuer),
            "Account" to EntryValue.Plain(account),
            // The shared secret is the whole point of the module: it goes into
            // the KDBX encrypted, exactly like a stored password.
            "Secret" to EntryValue.Encrypted(EncryptedValue.fromString(secret)),
            "Algorithm" to EntryValue.Plain(algorithm.name),
            "Digits" to EntryValue.Plain(digits.toString()),
            "Period" to EntryValue.Plain(period.toString()),
            // Written unconditionally: an account that was hidden must
            // overwrite the previous "1" rather than rely on the reader
            // treating a missing field as "visible".
            FIELD_HIDDEN to EntryValue.Plain(if (hidden) VALUE_HIDDEN else VALUE_VISIBLE)
        )
    )

    private fun Entry.toOtpEntry(): OtpEntry {
        val f = fields
        return OtpEntry(
            uuid = uuid,
            issuer = f["Issuer"]?.content ?: "",
            account = f["Account"]?.content ?: "",
            secret = f["Secret"]?.content ?: "",
            algorithm = runCatching { OtpAlgorithm.valueOf(f["Algorithm"]?.content ?: "") }
                .getOrDefault(OtpAlgorithm.SHA1),
            digits = f["Digits"]?.content?.toIntOrNull() ?: 6,
            period = f["Period"]?.content?.toIntOrNull() ?: 30,
            updatedAt = times?.lastModificationTime?.toEpochMilli() ?: 0L,
            // A missing flag means visible: accounts written before this
            // feature stay where the user left them.
            hidden = f[FIELD_HIDDEN]?.content == VALUE_HIDDEN
        )
    }

    /**
     * One side of the visible / hidden split; see [getNotes].
     *
     * Sorted by issuer so accounts group together and stay easy to scan.
     */
    @Synchronized
    fun getAllOtps(hidden: Boolean = false): List<OtpEntry> {
        val group = otpGroup() ?: return emptyList()
        return group.entries
            .map { it.toOtpEntry() }
            .filter { it.hidden == hidden }
            .sortedBy { it.displayTitle() }
    }

    /** Moves an authenticator account between the visible list and the hidden space. */
    @Synchronized
    fun setOtpHidden(uuid: UUID, hidden: Boolean) {
        val current = getOtp(uuid) ?: return
        rewriteFields(uuid, current.copy(hidden = hidden).toFields())
    }

    @Synchronized
    fun getOtp(uuid: UUID): OtpEntry? =
        otpGroup()?.entries?.firstOrNull { it.uuid == uuid }?.toOtpEntry()

    @Synchronized
    fun addOtp(entry: OtpEntry): OtpEntry {
        val groupUuid = ensureOtpGroup()
        val db = database ?: error("vault is locked")
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyGroup(groupUuid) {
            copy(entries = entries + Entry(uuid = stamped.uuid, fields = stamped.toFields()))
        }
        save()
        return stamped
    }

    @Synchronized
    fun updateOtp(entry: OtpEntry) {
        assertKnown(entry.uuid)
        val db = database ?: error("vault is locked")
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        database = db.modifyEntry(stamped.uuid) {
            copy(fields = stamped.toFields())
        }
        save()
    }

    @Synchronized
    fun deleteOtp(uuid: UUID) {
        assertKnown(uuid)
        val db = database ?: error("vault is locked")
        database = db.removeEntry(uuid)
        save()
    }

    /**
     * Stores media bytes in the encrypted media store and returns their
     * content-addressed id, which becomes the note's [ImageBlock] / [AudioBlock]
     * / [DrawingBlock] ref. The bytes land in a media file immediately; the
     * note that will reference them is still open, so the id is recorded in
     * [pendingBinaries] -- see [dropOrphanBinaries] for why that matters.
     */
    @Synchronized
    fun addBinary(bytes: ByteArray): String {
        val id = mediaStore.put(bytes)
        pendingBinaries += id
        return id
    }

    /** Reads and decrypts a whole media blob (images). */
    @Synchronized
    fun getBinary(ref: String): ByteArray? = mediaStore.get(ref)

    /** Streams a media blob to [out] without holding it in memory (audio). */
    fun decryptMediaToFile(ref: String, out: File): Boolean =
        mediaStore.decryptToFile(ref, out)

    /**
     * Deletes media files that no note references any more.
     *
     * The referenced set is rebuilt from the notes' own block lists, so it only
     * ever touches media. [pendingBinaries] holds back media that was just
     * written but whose note is still being edited.
     */
    private fun dropOrphanBinaries() {
        val referenced = notesGroup()?.entries
            ?.flatMap { it.toNote().blocks }
            ?.filterIsInstance<BinaryBlock>()
            ?.map { it.ref }
            ?.toSet()
            ?: emptySet()
        mediaStore.collectOrphans(referenced + pendingBinaries)
    }

    /**
     * Writes the in-memory database to disk atomically.
     *
     * Encoding straight into vault.kdbx would truncate the live file first. Any
     * failure after that -- an exception from encode() (disk full, or OOM on a
     * vault full of photos), or the process dying mid-write -- leaves a
     * half-written database that can never be opened again, taking every
     * password, note, card and OTP with it.
     *
     * So: encode into a temp file, fsync it, rotate the current file to .bak,
     * then rename the temp over the original. A crash now leaves either the old
     * file or the new one intact, never a partial one, and the previous
     * generation survives as .bak.
     *
     * Unlike the old version this never silently does nothing: a locked vault
     * is a programming error, and swallowing it used to discard the user's
     * write while the UI reported success.
     */
    @Synchronized
    fun save() {
        val db = database ?: error("vault is locked")
        val dir = vaultFile.parentFile ?: error("vault file has no parent directory")
        val tmp = File(dir, "${vaultFile.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                db.encode(out)
                // fsync can throw SyncFailedException on some filesystems / Android
                // builds even though every byte is already in the OS page cache
                // (the rename below still moves a complete file). A failed
                // durability flush must not discard the write nor crash the
                // caller, so swallow it -- the data is intact in the temp file.
                runCatching { out.fd.sync() }
            }
            rotateIntoPlace(tmp, dir)
        } finally {
            // No-op once the rename succeeded; cleans up on any failure path.
            tmp.delete()
        }
    }

    private fun credentialsFor(masterKey: ByteArray): Credentials {
        val passphrase = masterKey.toHexString()
        return Credentials.from(EncryptedValue.fromString(passphrase))
    }

    /** Derives the media encryption key from the master key (fixed domain separation). */
    private fun deriveMediaKey(masterKey: ByteArray): ByteArray =
        Hkdf.derive(masterKey, MEDIA_KEY_SALT, MEDIA_KEY_INFO)

    private fun Entry.toVaultEntry(): VaultEntry {
        val f = fields
        return VaultEntry(
            uuid = uuid,
            title = f["Title"]?.content ?: "",
            username = f["UserName"]?.content ?: "",
            password = f["Password"]?.content ?: "",
            url = f["URL"]?.content ?: "",
            notes = f["Notes"]?.content ?: "",
            category = f["Category"]?.content ?: "",
            updatedAt = times?.lastModificationTime?.toEpochMilli() ?: 0L,
            // A missing flag means visible: entries written before this feature
            // (or by another KeePass client) stay where the user left them.
            hidden = f[FIELD_HIDDEN]?.content == VALUE_HIDDEN
        )
    }

    private fun VaultEntry.toKeePassFields(): EntryFields =
        EntryFields(
            mapOf(
                "Title" to EntryValue.Plain(title),
                "UserName" to EntryValue.Plain(username),
                // Password is stored protected (encrypted at rest inside the KDBX).
                "Password" to EntryValue.Encrypted(EncryptedValue.fromString(password)),
                "URL" to EntryValue.Plain(url),
                "Notes" to EntryValue.Plain(notes),
                "Category" to EntryValue.Plain(category),
                // Written unconditionally: an entry that was hidden must
                // overwrite the previous "1" rather than rely on the reader
                // treating a missing field as "visible".
                FIELD_HIDDEN to EntryValue.Plain(if (hidden) VALUE_HIDDEN else VALUE_VISIBLE)
            )
        )

    /**
     * Hex under a fixed locale: `String.format` follows the default locale, and
     * under some locales the digits are not ASCII -- which would make the
     * passphrase derived here differ from the one the same vault was written
     * with after a language change.
     */
    private fun ByteArray.toHexString(): String =
        joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
