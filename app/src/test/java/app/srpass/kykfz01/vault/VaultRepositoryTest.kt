package app.srpass.kykfz01.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class VaultRepositoryTest {

    private fun tempVaultFile(): File {
        val f = File.createTempFile("srpass-vault", ".kdbx")
        f.deleteOnExit()
        return f
    }

    private fun sampleKey(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun createAndUnlock() {
        val repo = VaultRepository(tempVaultFile())
        repo.createVault(sampleKey())
        assertTrue(repo.unlock(sampleKey()))
    }

    @Test
    fun wrongKeyFailsToUnlock() {
        val file = tempVaultFile()
        VaultRepository(file).createVault(sampleKey())
        val wrong = VaultRepository(file)
        assertFalse(wrong.unlock(ByteArray(32) { (it + 1).toByte() }))
    }

    @Test
    fun addEntrySurvivesReopen() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val entry = VaultEntry(
            uuid = UUID.randomUUID(),
            title = "GitHub",
            username = "me@example.com",
            password = "s3cr3t-pw",
            url = "https://github.com",
            notes = "work account"
        )
        repo.addEntry(entry)

        // Re-open from disk and confirm the entry persisted.
        val reopened = VaultRepository(file)
        assertTrue(reopened.unlock(sampleKey()))
        val all = reopened.getAllEntries()
        assertEquals(1, all.size)
        assertEquals("GitHub", all[0].title)
        assertEquals("me@example.com", all[0].username)
        assertEquals("s3cr3t-pw", all[0].password)
        assertEquals("https://github.com", all[0].url)
        assertEquals("work account", all[0].notes)
    }

    @Test
    fun updateAndDeleteEntry() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val entry = VaultEntry(
            uuid = UUID.randomUUID(),
            title = "Original",
            password = "old"
        )
        repo.addEntry(entry)

        repo.updateEntry(entry.copy(title = "Renamed", password = "new"))
        val reopened = VaultRepository(file)
        reopened.unlock(sampleKey())
        assertEquals("Renamed", reopened.getEntry(entry.uuid)?.title)
        assertEquals("new", reopened.getEntry(entry.uuid)?.password)

        reopened.deleteEntry(entry.uuid)
        val after = VaultRepository(file)
        after.unlock(sampleKey())
        assertNull(after.getEntry(entry.uuid))
        assertEquals(0, after.getAllEntries().size)
    }

    /**
     * Guards the backup-import check: [VaultRepository.canOpen] must tell us
     * whether a key opens the file on disk, without replacing (or closing) an
     * already open session.
     */
    @Test
    fun canOpenValidatesKeyWithoutDisturbingSession() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        assertTrue(repo.unlock(sampleKey()))

        assertTrue(repo.canOpen(sampleKey()))
        assertFalse(repo.canOpen(ByteArray(32) { (it + 1).toByte() }))

        // Still the session we opened, not the throwaway decode.
        assertTrue(repo.getAllEntries().isEmpty())
    }

    /**
     * Regression test: a photo the user just picked sits in the binary pool
     * while the note meant to reference it is still open in the editor. Saving
     * (or deleting) a *different* note used to reclaim it, so the image was
     * gone by the time the user pressed save.
     */
    @Test
    fun pendingBinarySurvivesEditingAnotherNote() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val first = Note(uuid = UUID.randomUUID(), title = "First")
        repo.addNote(first)

        val imageBytes = "not-really-a-jpeg".toByteArray()
        val ref = repo.addBinary(imageBytes)

        // Saving an unrelated note must not reclaim the pending image.
        repo.addNote(Note(uuid = UUID.randomUUID(), title = "Second"))
        assertArrayEquals(imageBytes, repo.getBinary(ref))

        // Committing the reference keeps it.
        repo.updateNote(first.copy(blocks = listOf(ImageBlock(ref, "image/*"))))
        assertArrayEquals(imageBytes, repo.getBinary(ref))

        // Dropping the reference does clean it up.
        repo.updateNote(first.copy(blocks = emptyList()))
        assertNull(repo.getBinary(ref))
    }

    @Test
    fun addCardSurvivesReopen() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val card = BankCard(
            uuid = UUID.randomUUID(),
            type = CardType.CREDIT,
            nickname = "工资卡",
            bankName = "招商银行",
            cardNumber = "6225880123456789",
            cardHolder = "张三",
            expiry = "12/28",
            cvv = "123",
            pin6 = "123456",
            olbAccount = "zhangsan",
            olbPassword = "olb-pass",
            notes = "备注"
        )
        repo.addCard(card)

        val reopened = VaultRepository(file)
        assertTrue(reopened.unlock(sampleKey()))
        val all = reopened.getAllCards()
        assertEquals(1, all.size)
        val got = all[0]
        assertEquals(CardType.CREDIT, got.type)
        assertEquals("工资卡", got.nickname)
        assertEquals("招商银行", got.bankName)
        // Sensitive fields survive the encrypted round-trip intact.
        assertEquals("6225880123456789", got.cardNumber)
        assertEquals("张三", got.cardHolder)
        assertEquals("12/28", got.expiry)
        assertEquals("123", got.cvv)
        assertEquals("123456", got.pin6)
        assertEquals("zhangsan", got.olbAccount)
        assertEquals("olb-pass", got.olbPassword)
        assertEquals("备注", got.notes)
    }

    @Test
    fun cardsAreExcludedFromPasswordList() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())
        repo.addCard(BankCard(uuid = UUID.randomUUID(), bankName = "某行", cardNumber = "1111"))

        val reopened = VaultRepository(file)
        reopened.unlock(sampleKey())
        assertEquals(0, reopened.getAllEntries().size)
        assertEquals(1, reopened.getAllCards().size)
    }

    /**
     * Hidden notes live in the same group and the same KDBX file, marked with a
     * custom field. Keeping them there is what leaves backups untouched, so the
     * mark has to be part of what survives a reopen.
     */
    @Test
    fun hiddenMarkSurvivesReopen() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val visible = Note(uuid = UUID.randomUUID(), title = "公开")
        val secret = Note(uuid = UUID.randomUUID(), title = "私密")
        repo.addNote(visible)
        repo.addNote(secret)
        repo.setNoteHidden(secret.uuid, hidden = true)

        val reopened = VaultRepository(file)
        assertTrue(reopened.unlock(sampleKey()))
        assertEquals(listOf("公开"), reopened.getNotes(hidden = false).map { it.title })
        assertEquals(listOf("私密"), reopened.getNotes(hidden = true).map { it.title })
        assertTrue(reopened.getNote(secret.uuid)?.hidden == true)
        assertTrue(reopened.getNote(visible.uuid)?.hidden == false)
    }

    /**
     * Regression guard: hiding must not bump [Note.updatedAt].
     *
     * Hiding is a storage change, not a content change. Re-stamping would float
     * the note to the top of the very list it is supposed to disappear from, so
     * anyone looking over the user's shoulder sees that something was removed.
     */
    @Test
    fun setNoteHiddenKeepsTimestamp() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val added = repo.addNote(Note(uuid = UUID.randomUUID(), title = "One"))
        repo.setNoteHidden(added.uuid, hidden = true)
        assertEquals(added.updatedAt, repo.getNotes(hidden = true).single().updatedAt)

        repo.setNoteHidden(added.uuid, hidden = false)
        assertEquals(added.updatedAt, repo.getNotes(hidden = false).single().updatedAt)
    }

    /** Moving a note into the hidden space must not cost it its images. */
    @Test
    fun hiddenNoteKeepsItsImages() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val note = repo.addNote(Note(uuid = UUID.randomUUID(), title = "带图"))
        val imageBytes = "not-really-a-jpeg".toByteArray()
        val ref = repo.addBinary(imageBytes)
        repo.updateNote(note.copy(blocks = listOf(ImageBlock(ref, "image/*"))))

        repo.setNoteHidden(note.uuid, hidden = true)
        assertArrayEquals(imageBytes, repo.getBinary(ref))
        assertTrue(repo.getNotes(hidden = true).single().hasImage())

        // And coming back out again.
        repo.setNoteHidden(note.uuid, hidden = false)
        assertArrayEquals(imageBytes, repo.getBinary(ref))
        assertTrue(repo.getNotes(hidden = false).single().hasImage())
    }

    /**
     * Same contract as notes, for the three other kinds: the mark lives in the
     * item's own entry, so it survives a reopen (and a backup), and hiding it
     * must not cost it the encrypted fields.
     */
    @Test
    fun hiddenMarkSurvivesReopenForPasswordsCardsAndOtps() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        repo.addEntry(VaultEntry(uuid = UUID.randomUUID(), title = "公开密码", password = "visible"))
        val secretEntry =
            repo.addEntry(VaultEntry(uuid = UUID.randomUUID(), title = "私密密码", password = "secret"))
        repo.setEntryHidden(secretEntry.uuid, hidden = true)

        repo.addCard(BankCard(uuid = UUID.randomUUID(), bankName = "公开行", cardNumber = "1111", cvv = "111"))
        val secretCard =
            repo.addCard(BankCard(uuid = UUID.randomUUID(), bankName = "秘密行", cardNumber = "2222", cvv = "222"))
        repo.setCardHidden(secretCard.uuid, hidden = true)

        repo.addOtp(OtpEntry(uuid = UUID.randomUUID(), issuer = "GitHub", secret = "JBSWY3DPEHPK3PXP"))
        val secretOtp =
            repo.addOtp(OtpEntry(uuid = UUID.randomUUID(), issuer = "Bank", secret = "JBSWY3DPEHPK3PXQ"))
        repo.setOtpHidden(secretOtp.uuid, hidden = true)

        // A reopen is the cheapest stand-in for "backed up and restored": the
        // mark has to come back with the entry, not live in app state.
        val reopened = VaultRepository(file)
        assertTrue(reopened.unlock(sampleKey()))

        assertEquals(listOf("公开密码"), reopened.getAllEntries(hidden = false).map { it.title })
        assertEquals(listOf("私密密码"), reopened.getAllEntries(hidden = true).map { it.title })
        assertEquals("secret", reopened.getAllEntries(hidden = true).single().password)

        assertEquals(listOf("公开行"), reopened.getAllCards(hidden = false).map { it.bankName })
        assertEquals(listOf("秘密行"), reopened.getAllCards(hidden = true).map { it.bankName })
        assertEquals("2222", reopened.getAllCards(hidden = true).single().cardNumber)
        assertEquals("222", reopened.getAllCards(hidden = true).single().cvv)

        assertEquals(listOf("GitHub"), reopened.getAllOtps(hidden = false).map { it.displayTitle() })
        assertEquals(listOf("Bank"), reopened.getAllOtps(hidden = true).map { it.displayTitle() })
        assertEquals("JBSWY3DPEHPK3PXQ", reopened.getAllOtps(hidden = true).single().secret)
    }

    /**
     * Where this differs from notes: passwords, cards and OTPs take their
     * "修改时间" from the entry's KDBX timestamp, which [VaultRepository] would
     * otherwise restamp on every write. Hiding would then float the item to the
     * top of the list it is being taken out of -- a visible trace.
     */
    @Test
    fun setHiddenKeepsTimestampForPasswordsCardsAndOtps() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val entry = repo.addEntry(VaultEntry(uuid = UUID.randomUUID(), title = "密码"))
        val entryStamp = repo.getEntry(entry.uuid)!!.updatedAt
        repo.setEntryHidden(entry.uuid, hidden = true)
        assertEquals(entryStamp, repo.getAllEntries(hidden = true).single().updatedAt)
        repo.setEntryHidden(entry.uuid, hidden = false)
        assertEquals(entryStamp, repo.getAllEntries(hidden = false).single().updatedAt)

        val card = repo.addCard(BankCard(uuid = UUID.randomUUID(), bankName = "行"))
        val cardStamp = repo.getCard(card.uuid)!!.updatedAt
        repo.setCardHidden(card.uuid, hidden = true)
        assertEquals(cardStamp, repo.getAllCards(hidden = true).single().updatedAt)
        repo.setCardHidden(card.uuid, hidden = false)
        assertEquals(cardStamp, repo.getAllCards(hidden = false).single().updatedAt)

        val otp = repo.addOtp(OtpEntry(uuid = UUID.randomUUID(), issuer = "GitHub", secret = "JBSWY3DPEHPK3PXP"))
        val otpStamp = repo.getOtp(otp.uuid)!!.updatedAt
        repo.setOtpHidden(otp.uuid, hidden = true)
        assertEquals(otpStamp, repo.getAllOtps(hidden = true).single().updatedAt)
        repo.setOtpHidden(otp.uuid, hidden = false)
        assertEquals(otpStamp, repo.getAllOtps(hidden = false).single().updatedAt)
    }

    /**
     * The two sides must never overlap: an item is either in the list or in the
     * hidden space, so hiding it cannot silently leave a copy behind in the
     * visible one.
     */
    @Test
    fun visibleAndHiddenListsNeverOverlap() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val entry = repo.addEntry(VaultEntry(uuid = UUID.randomUUID(), title = "密码"))
        val card = repo.addCard(BankCard(uuid = UUID.randomUUID(), bankName = "行"))
        val otp = repo.addOtp(OtpEntry(uuid = UUID.randomUUID(), issuer = "GitHub", secret = "JBSWY3DPEHPK3PXP"))

        repo.setEntryHidden(entry.uuid, hidden = true)
        repo.setCardHidden(card.uuid, hidden = true)
        repo.setOtpHidden(otp.uuid, hidden = true)

        assertTrue(repo.getAllEntries(hidden = false).isEmpty())
        assertTrue(repo.getAllCards(hidden = false).isEmpty())
        assertTrue(repo.getAllOtps(hidden = false).isEmpty())
        assertEquals(1, repo.getAllEntries(hidden = true).size)
        assertEquals(1, repo.getAllCards(hidden = true).size)
        assertEquals(1, repo.getAllOtps(hidden = true).size)
    }

    /**
     * Regression guard for the rich note editor.
     *
     * Audio and drawings live in the binary pool exactly like images do, so the
     * "still in use" census has to count them too. When it only looked at
     * [ImageBlock], saving any other note reclaimed them: the note kept its ref
     * and silently lost the recording / drawing for good.
     *
     * The reopen matters: a fresh [VaultRepository] has no "pending" binaries
     * protecting anything, so this only passes if the note itself claims them.
     */
    @Test
    fun audioAndDrawingSurviveSavingAnotherNote() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val imageBytes = ByteArray(64) { 1 }
        val audioBytes = ByteArray(64) { 2 }
        val drawingBytes = ByteArray(64) { 3 }
        val imageRef = repo.addBinary(imageBytes)
        val audioRef = repo.addBinary(audioBytes)
        val drawingRef = repo.addBinary(drawingBytes)

        repo.addNote(
            Note(
                uuid = UUID.randomUUID(),
                title = "带媒体的笔记",
                blocks = listOf(
                    TextBlock("正文"),
                    ImageBlock(imageRef, "image/*"),
                    AudioBlock(audioRef, 42_000L),
                    DrawingBlock(drawingRef, "{}")
                )
            )
        )

        // Reopen: pending binaries are gone, so only the note's own refs count.
        val reopened = VaultRepository(file)
        assertTrue(reopened.unlock(sampleKey()))
        reopened.addNote(Note(uuid = UUID.randomUUID(), title = "另一条", blocks = listOf(TextBlock("x"))))

        assertArrayEquals(imageBytes, reopened.getBinary(imageRef))
        assertArrayEquals(audioBytes, reopened.getBinary(audioRef))
        assertArrayEquals(drawingBytes, reopened.getBinary(drawingRef))
    }

    /** Deleting the reference does still reclaim the media (no leak). */
    @Test
    fun droppingAudioAndDrawingReclaimsThem() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val audioBytes = ByteArray(64) { 2 }
        val audioRef = repo.addBinary(audioBytes)
        val note = repo.addNote(
            Note(uuid = UUID.randomUUID(), title = "录音笔记", blocks = listOf(AudioBlock(audioRef, 1_000L)))
        )
        assertArrayEquals(audioBytes, repo.getBinary(audioRef))

        repo.updateNote(note.copy(blocks = listOf(TextBlock("只剩文字"))))
        assertNull(repo.getBinary(audioRef))
    }

    /**
     * Rich text is stored inside the note's JSON, so a styled paragraph has to
     * come back out of the vault with its spans intact.
     */
    @Test
    fun styledTextSurvivesReopen() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val spans = listOf(Span(0, 2, bold = true, color = 0xFFD32F2FL, size = 22))
        val note = Note(
            uuid = UUID.randomUUID(),
            title = "富文本",
            blocks = listOf(TextBlock("加粗正文", spans = spans))
        )
        repo.addNote(note)

        val reopened = VaultRepository(file)
        reopened.unlock(sampleKey())
        val blocks = reopened.getNote(note.uuid)!!.blocks
        assertEquals("加粗正文", (blocks.single() as TextBlock).text)
        assertEquals(spans, (blocks.single() as TextBlock).spans)
    }

    /**
     * 放弃编辑时，这次暂存却没用上的媒体要被回收 —— 这正是 discardPendingBinaries
     * 存在的意义，删掉 save() 后它应该照常工作。
     */
    @Test
    fun discardPendingBinariesReclaimsStagedMedia() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val stagedRef = repo.addBinary("unused-image".toByteArray())
        assertArrayEquals("unused-image".toByteArray(), repo.getBinary(stagedRef))

        repo.discardPendingBinaries()
        assertNull(repo.getBinary(stagedRef))
    }

    @Test
    fun discardPendingBinariesKeepsReferencedMedia() {
        val file = tempVaultFile()
        val repo = VaultRepository(file)
        repo.createVault(sampleKey())
        repo.unlock(sampleKey())

        val usedRef = repo.addBinary("used-image".toByteArray())
        repo.addNote(Note(uuid = UUID.randomUUID(), title = "有图", blocks = listOf(ImageBlock(usedRef, "image/*"))))

        val stagedRef = repo.addBinary("unused-image-2".toByteArray())

        repo.discardPendingBinaries()

        assertArrayEquals("used-image".toByteArray(), repo.getBinary(usedRef))
        assertNull(repo.getBinary(stagedRef))
    }
}
