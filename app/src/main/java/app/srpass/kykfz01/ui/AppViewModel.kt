package app.srpass.kykfz01.ui

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.srpass.kykfz01.crypto.MasterKeyManager
import app.srpass.kykfz01.crypto.SharedPrefsKeyEnvelopeStore
import app.srpass.kykfz01.ui.settings.SecurityStore
import app.srpass.kykfz01.ui.settings.ThemeStore
import app.srpass.kykfz01.ui.theme.ThemeMode
import app.srpass.kykfz01.vault.BackupBundle
import app.srpass.kykfz01.vault.BankCard
import app.srpass.kykfz01.vault.Note
import app.srpass.kykfz01.vault.OtpEntry
import app.srpass.kykfz01.vault.VaultEntry
import app.srpass.kykfz01.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.crypto.Cipher

/**
 * Implemented by the activity so the UI can ask it to open the system SAF
 * pickers. The activity owns the startActivityForResult / onActivityResult
 * handling with fixed, always-valid request codes, keeping us clear of the
 * ActivityResultRegistry request-code counter.
 */
interface SafController {
    fun launchExport(filename: String)
    fun launchImport()
}

/**
 * Implemented by the activity so the UI can ask it to request the full-album
 * (READ_MEDIA_IMAGES) permission using a fixed, always-valid request code --
 * keeping us clear of the ActivityResultRegistry request-code counter that can
 * overflow 16 bits and crash with "Can only use lower 16 bits for requestCode".
 */
interface ImageController {
    fun requestMediaPermission(onResult: (Boolean) -> Unit)
}

/**
 * CAMERA, requested through the activity with the same fixed-request-code
 * approach as [ImageController] so the ActivityResultRegistry counter is never
 * involved. Needed only for live QR scanning.
 */
interface CameraController {
    fun requestCameraPermission(onResult: (Boolean) -> Unit)
}

/**
 * RECORD_AUDIO，用和 [ImageController] 一样的固定 request code 向 Activity 申请。
 *
 * 笔记录音是**唯一**用到麦克风的地方：录制过程完全在本地完成，音频自始至终
 * 存在 KDBX 加密库里。
 */
interface MicController {
    fun requestMicPermission(onResult: (Boolean) -> Unit)
}

/**
 * The four hidden spaces.
 *
 * Independent on purpose: each one is reached from its own list and costs its
 * own credential check, so proving who you are to see hidden cards does not
 * also open hidden passwords, and vice versa.
 */
enum class HiddenKind { PASSWORD, NOTE, CARD, OTP }

/** Top-level navigation destination. */
sealed interface AppScreen {
    data object CreateVault : AppScreen
    data object Unlock : AppScreen
    data object Recovery : AppScreen
    data object List : AppScreen
    data object Settings : AppScreen
    data object SettingsAppearance : AppScreen
    data object SettingsSecurity : AppScreen
    data object SettingsData : AppScreen
    data object SettingsAbout : AppScreen
    data object SettingsLicenses : AppScreen
    /** [fromHidden] 从隐藏空间打开/新建：返回要回到那个隐藏空间，不能落回公开列表。 */
    data class Detail(val uuid: UUID, val fromHidden: Boolean = false) : AppScreen
    data class Editor(val uuid: UUID?, val fromHidden: Boolean = false) : AppScreen
    data object NotesList : AppScreen
    /**
     * 隐藏空间：密码、笔记、银行卡、验证器各有自己那一个，从哪个列表下拉进来
     * 就进哪一个。页面本身在没验证过时只画验证门，所以直接跳进来是安全的。
     */
    data class Hidden(val kind: HiddenKind) : AppScreen
    /**
     * [fromHidden] 标记这条笔记是从隐藏空间打开/新建的，返回目标要跟着回去，
     * 而不是落到普通笔记列表（那等于把隐藏内容的去向暴露出去）。
     */
    /**
     * [scrollToRef] 是"回到笔记时滚到哪张图旁边"：从全屏看图返回时带上它，
     * 否则返回等于重开一页，滚动位置归零，看了一半还得从头翻。
     */
    data class NoteEditor(
        val uuid: UUID?,
        val fromHidden: Boolean = false,
        val scrollToRef: String? = null
    ) : AppScreen
    data object CardsList : AppScreen
    data class CardView(val uuid: UUID, val fromHidden: Boolean = false) : AppScreen
    data class CardEditor(val uuid: UUID?, val fromHidden: Boolean = false) : AppScreen
    data object OtpList : AppScreen
    data class OtpEditor(val uuid: UUID?, val fromHidden: Boolean = false) : AppScreen
    /**
     * [fromHidden] 扫码是从隐藏空间里发起的：扫完要回到隐藏空间那个编辑器，
     * 否则新建的账号会被当成公开账号写出去（编辑器靠它决定 hidden 初值）。
     *
     * [editingUuid] 是从哪个验证器编辑器发起的（新建时为 null）。必须传回来：
     * 以前一律用 null 回编辑器，编辑已有账号时扫完一次码，保存就变成"新建"，
     * 原账号密钥没改成、列表里多一条重复的。
     */
    data class QrScanner(
        val fromHidden: Boolean = false,
        val editingUuid: java.util.UUID? = null
    ) : AppScreen
    data class ImageViewer(
        val ref: String,
        val noteUuid: UUID,
        val fromHidden: Boolean = false
    ) : AppScreen
}

/**
 * Single owner of the crypto + vault singletons and the in-app navigation
 * state. UI collects [screen] and renders the matching screen.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        /** Name of the KDBX file inside the app's private storage. */
        const val VAULT_FILE_NAME = "vault.kdbx"

        /**
         * 单份音频 / 涂鸦入库上限。
         *
         * 媒体是独立加密文件、按需流式读写的，不再参与整库编码，所以这个上限
         * 只约束"单个文件解密 / 播放的内存峰值"，放宽到 50MB。
         */
        const val MAX_MEDIA_BYTES = 50 * 1024 * 1024

        /** 图片入库的最长边：超过就按 2 的幂采样降下来。 */
        const val IMAGE_MAX_DIM = 1920

        /** 图片入库的 JPEG 质量。 */
        const val IMAGE_JPEG_QUALITY = 82

        /**
         * "压完反而比原图大"时，回退到"直接存原字节"的体积上限。
         *
         * 超过这个数就不再去读原字节了 —— 那说明原图大到不该整张进内存，
         * 而压缩结果本来就是我们要的小图。
         */
        const val KEEP_ORIGINAL_MAX_BYTES = 8 * 1024 * 1024

        /**
         * 笔记图片的解码缓存上限。
         *
         * 只管"同一个页面反复取同一张图别重复解码"（旋转、来回翻、列表缩略图
         * 和正文各取一次）。它**压不住**一次性全部解码的峰值 —— bitmap 被界面
         * 引用着，淘汰掉缓存里的那份也不会被 GC。峰值靠 [getNoteImage] 按屏幕
         * 宽度解码来控制。
         */
        const val NOTE_IMAGE_CACHE_BYTES = 64 * 1024 * 1024
    }

    private val vaultFile = File(app.filesDir, VAULT_FILE_NAME)
    private val repo = VaultRepository(vaultFile)
    /** 媒体加密文件目录（与 vaultFile 同目录）。 */
    private val mediaDir = File(app.filesDir, "media")
    /**
     * The envelope SharedPreferences file and its import snapshot. Snapshotting
     * the whole file is what lets a crashed import be recovered on the next
     * launch -- see [recoverImportIfNeeded] and [importVaultFrom]. The name must
     * stay in sync with SharedPrefsKeyEnvelopeStore.PREFS_NAME.
     */
    private val envelopePrefsFile = File(
        app.filesDir.parentFile ?: app.filesDir,
        "shared_prefs/srpass_key_envelopes.xml"
    )
    private val envelopeBackupFile = File(
        app.filesDir.parentFile ?: app.filesDir,
        "shared_prefs/srpass_key_envelopes.import-backup"
    )
    private val mkm = MasterKeyManager(SharedPrefsKeyEnvelopeStore(app))
    private val themeStore = ThemeStore(app)
    private val securityStore = SecurityStore(app)

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Unlock)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    /**
     * A vault exists on disk but its master key is not in memory.
     *
     * Deliberately kept apart from [screen]: locking used to call [route],
     * which navigated back to [AppScreen.Unlock] and destroyed whatever the
     * user was in the middle of editing. Now the screen stays where it is and
     * this flag is what raises an opaque unlock overlay on top of it.
     */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _homeTab = MutableStateFlow(0)
    val homeTab: StateFlow<Int> = _homeTab.asStateFlow()

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    /**
     * Items parked in a hidden space, one list per [HiddenKind]. Each is
     * populated only while that kind is unlocked: keeping private items off
     * these lists costs nothing (they are a filtered read away) and means
     * nothing sits in memory until asked for.
     */
    private val _hiddenEntries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val hiddenEntries: StateFlow<List<VaultEntry>> = _hiddenEntries.asStateFlow()

    private val _hiddenNotes = MutableStateFlow<List<Note>>(emptyList())
    val hiddenNotes: StateFlow<List<Note>> = _hiddenNotes.asStateFlow()

    private val _hiddenCards = MutableStateFlow<List<BankCard>>(emptyList())
    val hiddenCards: StateFlow<List<BankCard>> = _hiddenCards.asStateFlow()

    private val _hiddenOtps = MutableStateFlow<List<OtpEntry>>(emptyList())
    val hiddenOtps: StateFlow<List<OtpEntry>> = _hiddenOtps.asStateFlow()

    /**
     * Which hidden spaces may be shown right now; see [unlockHiddenSpace].
     *
     * A set rather than one flag: the four spaces unlock independently, so a
     * credential check in front of one says nothing about the others.
     */
    private val _hiddenUnlocked = MutableStateFlow<Set<HiddenKind>>(emptySet())
    val hiddenUnlocked: StateFlow<Set<HiddenKind>> = _hiddenUnlocked.asStateFlow()

    /**
     * Master switch for the whole feature: it arms the pull gesture and the
     * long-press "hide" action in all four lists at once. One switch because
     * the four spaces are the same mechanism, and closing it closes all four.
     */
    private val _hiddenSpaceEnabled = MutableStateFlow(securityStore.getHiddenSpaceEnabled())
    val hiddenSpaceEnabled: StateFlow<Boolean> = _hiddenSpaceEnabled.asStateFlow()

    private val _cards = MutableStateFlow<List<BankCard>>(emptyList())
    val cards: StateFlow<List<BankCard>> = _cards.asStateFlow()

    private val _otps = MutableStateFlow<List<OtpEntry>>(emptyList())
    val otps: StateFlow<List<OtpEntry>> = _otps.asStateFlow()

    /**
     * Hand-off from the QR scanner back to the editor.
     *
     * Navigation here is a single [screen] value rather than a back stack, so
     * the scanner cannot "pop" with a result -- it drops the parsed entry here
     * and the editor consumes it once. One-shot on purpose: a later visit to
     * the editor must not re-fill the form with a stale scan.
     */
    private val _scannedOtp = MutableStateFlow<OtpEntry?>(null)
    val scannedOtp: StateFlow<OtpEntry?> = _scannedOtp.asStateFlow()

    fun onOtpScanned(entry: OtpEntry) { _scannedOtp.value = entry }
    fun consumeScannedOtp() { _scannedOtp.value = null }

    /**
     * One flag, but counted: several operations can be in flight at once
     * (a photo import while a password change is still saving), and a single
     * boolean let whichever finished first drop the overlay for both.
     */
    private val busyDepth = java.util.concurrent.atomic.AtomicInteger(0)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun beginBusy() {
        if (busyDepth.incrementAndGet() == 1) _busy.tryEmit(true)
    }

    private fun endBusy() {
        if (busyDepth.decrementAndGet() <= 0) {
            busyDepth.set(0)
            _busy.tryEmit(false)
        }
    }

    /**
     * 每上锁一次就 +1 的世代号。
     *
     * 解锁要先跑几秒 Argon2，这段时间里用户完全可能息屏或切后台触发 [lock]。
     * 没有世代号时，那次"迟到"的解锁照样会打开密码库并把界面切进列表 —— 等于
     * 息屏一次之后不用再验证就能看密码。解锁结束时比对世代号，变了就作废。
     */
    private val lockEpoch = java.util.concurrent.atomic.AtomicInteger(0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _themeMode = MutableStateFlow(themeStore.get())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(themeStore.getDynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /**
     * Mirrors FLAG_SECURE on the main window, which the user now controls from
     * the Security screen. Defaults to on -- see [SecurityStore].
     */
    private val _blockScreenshots = MutableStateFlow(securityStore.getBlockScreenshots())
    val blockScreenshots: StateFlow<Boolean> = _blockScreenshots.asStateFlow()

    init {
        abandonUnfinishedCreate()
        route()
        // Off the main thread: this touches the vault file and the snapshot
        // directory, and on a cold start it used to run before the first frame.
        viewModelScope.launch(Dispatchers.IO) {
            if (recoverImportIfNeeded()) {
                withContext(Dispatchers.Main.immediate) { route() }
            }
        }
    }

    /**
     * Clears up a create that never finished.
     *
     * [crypto.MasterKeyManager.createVault] writes the key envelopes *before*
     * the vault file exists. Killing the process in between (or a throw on the
     * write) leaves a phone that reports "a vault exists" while there is no
     * file to open: neither openable nor re-creatable. The only honest way out
     * is to drop the orphaned envelopes and offer a fresh create.
     */
    private fun abandonUnfinishedCreate() {
        if (!mkm.hasVault()) return
        if (vaultFile.exists()) return
        // A previous generation on disk is preferred over starting over.
        if (repo.recoverFromBackup()) return
        mkm.wipeCredentials()
    }

    // ---- Navigation -------------------------------------------------------

    /** True when a vault exists on disk but its master key is not in memory. */
    private fun syncLocked() {
        _locked.value = mkm.hasVault() && !mkm.isUnlocked
    }

    fun route() {
        syncLocked()
        if (!mkm.hasVault()) {
            _screen.value = AppScreen.CreateVault
            return
        }
        if (!mkm.isUnlocked) {
            // Recovery stays put: it is the one screen the user may use while
            // the vault is locked. Everything else falls back to the unlock
            // entry, which the overlay in SrPassRoot renders.
            if (_screen.value !is AppScreen.Recovery) _screen.value = AppScreen.Unlock
            return
        }
        refreshEntries()
        refreshNotes()
        refreshCards()
        refreshOtps()
        // Only an entry screen (unlock / create / recovery) moves on to the
        // home list. Unlocking after a lock must return the user to whatever
        // they were doing -- a half-edited note or card is still there.
        // 恢复码还没确认抄下时不许离开建库页：它是唯一一次显示机会（应用不保存
        // 它），被上锁/解锁这一轮切走就等于永久丢失。
        if (pendingRecoveryKey != null) return
        val current = _screen.value
        if (current is AppScreen.Unlock ||
            current is AppScreen.CreateVault ||
            current is AppScreen.Recovery
        ) {
            _screen.value = AppScreen.List
        }
    }

    // Home tab order: 0 vault, 1 notes, 2 cards, 3 authenticator, 4 settings.
    /**
     * 从恢复码页退回主密码解锁页。
     *
     * route() 在锁定时会让人停在 Recovery（这是有意的，恢复码页本来就是给忘了
     * 主密码的人用的），但那样一来「我只是一时想起来了」就没有回头路：返回键
     * 直接退出、route() 又原地不动。这是那条唯一的回边。
     */
    fun goUnlock() { _screen.value = AppScreen.Unlock }

    fun goSettings() { _homeTab.value = 4; _screen.value = AppScreen.Settings }
    fun goSettingsAppearance() { _screen.value = AppScreen.SettingsAppearance }
    fun goSettingsSecurity() { _screen.value = AppScreen.SettingsSecurity }
    fun goSettingsData() { _screen.value = AppScreen.SettingsData }
    fun goSettingsAbout() { _screen.value = AppScreen.SettingsAbout }
    fun goSettingsLicenses() { _screen.value = AppScreen.SettingsLicenses }
    fun goList() { _homeTab.value = 0; _screen.value = AppScreen.List }
    fun openDetail(uuid: UUID, fromHidden: Boolean = false) {
        _screen.value = AppScreen.Detail(uuid, fromHidden)
    }
    fun openEditor(uuid: UUID? = null, fromHidden: Boolean = false) {
        _screen.value = AppScreen.Editor(uuid, fromHidden)
    }
    fun openRecovery() { _screen.value = AppScreen.Recovery }
    fun goNotesList() { _homeTab.value = 1; _screen.value = AppScreen.List }
    fun openNoteEditor(
        uuid: UUID? = null,
        fromHidden: Boolean = false,
        scrollToRef: String? = null
    ) {
        _screen.value = AppScreen.NoteEditor(uuid, fromHidden, scrollToRef)
    }
    fun openImageViewer(ref: String, noteUuid: UUID, fromHidden: Boolean = false) {
        _screen.value = AppScreen.ImageViewer(ref, noteUuid, fromHidden)
    }

    /**
     * Enters one of the four hidden spaces. The screen itself renders the
     * credential gate while that kind is not in [hiddenUnlocked], so
     * navigating here is safe even if verification has not happened.
     */
    fun goHiddenSpace(kind: HiddenKind) {
        // With the master switch off there is no space to enter: land on the
        // list this kind belongs to rather than leaving the user on a screen
        // that no longer has anywhere to go back to.
        if (!_hiddenSpaceEnabled.value) goTabFor(kind) else _screen.value = AppScreen.Hidden(kind)
    }

    /**
     * Leaves one hidden space and re-locks it, landing on the list it belongs
     * to (a hidden card returns to the cards tab, not to the password list).
     *
     * The user asked for a check every time, so there is deliberately no
     * "still within the grace period" shortcut: leaving costs one more check.
     * Only this one space closes -- the other three keep their own state.
     */
    fun exitHiddenSpace(kind: HiddenKind) {
        lockHidden(kind)
        goTabFor(kind)
    }

    /** The home tab a hidden space is reached from. */
    private fun goTabFor(kind: HiddenKind) {
        when (kind) {
            HiddenKind.PASSWORD -> goList()
            HiddenKind.NOTE -> goNotesList()
            HiddenKind.CARD -> goCardsList()
            HiddenKind.OTP -> goOtpList()
        }
    }

    fun goCardsList() { _homeTab.value = 2; _screen.value = AppScreen.List }
    fun openCard(uuid: UUID, fromHidden: Boolean = false) {
        _screen.value = AppScreen.CardView(uuid, fromHidden)
    }
    fun newCard(fromHidden: Boolean = false) {
        _screen.value = AppScreen.CardEditor(null, fromHidden)
    }
    fun openCardEditor(uuid: UUID? = null, fromHidden: Boolean = false) {
        _screen.value = AppScreen.CardEditor(uuid, fromHidden)
    }
    fun goOtpList() { _homeTab.value = 3; _screen.value = AppScreen.List }
    fun openOtpEditor(uuid: UUID? = null, fromHidden: Boolean = false) {
        _screen.value = AppScreen.OtpEditor(uuid, fromHidden)
    }
    fun openQrScanner(fromHidden: Boolean = false, editingUuid: java.util.UUID? = null) {
        _screen.value = AppScreen.QrScanner(fromHidden, editingUuid)
    }
    fun setHomeTab(tab: Int) { _homeTab.value = tab }

    // ---- Theme ------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        themeStore.set(mode)
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        themeStore.setDynamicColor(enabled)
        _dynamicColor.value = enabled
    }

    fun setBlockScreenshots(enabled: Boolean) {
        securityStore.setBlockScreenshots(enabled)
        _blockScreenshots.value = enabled
    }

    // ---- Messages ---------------------------------------------------------

    fun showMessage(msg: String) { _message.value = msg }
    fun clearMessage() { _message.value = null }

    /**
     * Set by screens that launch an in-app system picker (SAF import/export)
     * so [android.app.Activity.onUserLeaveHint] does not lock the vault while
     * that picker is showing -- opening SAF also fires onUserLeaveHint, which
     * would otherwise lock the app and dispose the result launcher mid-pick.
     */
    var suppressLockOnLeave: Boolean = false

    /**
     * 界面是否真的在前台（Activity 至少 STARTED），由 MainActivity 的
     * onStart / onStop 维护。
     *
     * 放 ViewModel 而不是解锁页里，是因为解锁页往往是 onStop 里的 [lock] 才推
     * 出来的：它的组合那一刻才建立，收不到此前那次 ON_STOP，靠界面内的 remember
     * 记「是否离开过」必然记不住 —— 闸门松不开，指纹入口就永久灰掉。
     */
    var uiInForeground by mutableStateOf(true)

    // ---- Vault creation / unlock -----------------------------------------

    /**
     * 建库刚生成的恢复码。
     *
     * 放在 ViewModel 而不是 remember：旋转屏幕会重建 composable，而恢复码之后
     * 再也显示不出来（应用不保存它），丢了就是永久丢。用户确认保存后清掉。
     */
    var pendingRecoveryKey by mutableStateOf<String?>(null)
        private set

    /** 用户已确认抄下恢复码 —— 之后不再显示。 */
    fun consumeRecoveryKey() { pendingRecoveryKey = null }

    suspend fun createVault(password: String): String = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val recoveryKey = mkm.createVault(password.toCharArray())
            // 立刻落到 ViewModel 里，中间不再经过任何可取消的步骤：恢复码只显示
            // 这一次，应用不保存它，切一次主线程被取消就永远拿不回来了。
            pendingRecoveryKey = recoveryKey
            val mk = mkm.peekMasterKey() ?: error("master key missing after create")
            try {
                repo.createVault(mk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消不是建库失败：库可能已经落盘了，凭证不能在这里销毁。
                throw e
            } catch (e: Throwable) {
                // Never leave "envelopes on disk but no vault": a failed create
                // would otherwise let the user lock themselves out. Roll the
                // credentials back so the next launch starts clean.
                mkm.wipeCredentials()
                pendingRecoveryKey = null
                throw e
            } finally {
                mk.fill(0)
            }
            recoveryKey
        } finally {
            endBusy()
        }
    }

    suspend fun unlockWithPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val epoch = lockEpoch.get()
            if (mkm.unlockWithPassword(password.toCharArray())) openVault(epoch)
            else false
        } finally {
            endBusy()
        }
    }

    suspend fun unlockWithRecoveryKey(input: String): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val epoch = lockEpoch.get()
            if (mkm.unlockWithRecoveryKey(input)) openVault(epoch)
            else false
        } finally {
            endBusy()
        }
    }

    suspend fun unlockWithBiometric(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val epoch = lockEpoch.get()
            if (mkm.unlockWithBiometric(cipher)) openVault(epoch)
            else false
        } finally {
            endBusy()
        }
    }

    /**
     * Opens the KDBX file with the master key that was just unwrapped.
     *
     * A channel can succeed while the file itself refuses to open: the file may
     * be missing, truncated, or (after a backup import) encrypted under a
     * different master key. Treating that as success used to leave the app in a
     * half-unlocked state -- the UI showed the vault list backed by no database,
     * and the first write threw "vault is locked". So on failure we roll the
     * master key back to locked and report false, which keeps [route] on the
     * unlock screen.
     */
    /** 进主界面前必须先把恢复码收掉：这一步过去之后它不再显示。 */
    fun enterVaultAfterRecoveryKeySaved() {
        pendingRecoveryKey = null
        route()
    }

    /**
     * @param epoch 发起解锁时看到的世代号。派生的那几秒里若发生过 [lock]，这次
     * 解锁结果作废：界面已经显示锁定，绝不能再把解密好的数据库挂回内存。
     */
    private fun openVault(epoch: Int): Boolean {
        if (lockEpoch.get() != epoch) {
            mkm.lock()
            return false
        }
        val mk = mkm.peekMasterKey() ?: return false
        try {
            if (repo.unlock(mk)) {
                // 真能打开，说明刚导入的库和信封是配套的：那张留着兜底的快照
                // 可以扔了。
                clearImportSnapshot()
                return true
            }
            mkm.lock()
            return false
        } finally {
            // peek 出来的是副本，用完必须自己擦；否则明文主密钥会一直躺在堆上。
            mk.fill(0)
        }
    }

    /**
     * 扔掉导入前留下的回滚快照。
     *
     * 只在 [openVault] 成功之后调用 —— 能打开就证明"新库 + 新信封"是自洽的。
     * 快照还在的时候重启，[recoverImportIfNeeded] 会把密码库还原成导入前的样子。
     */
    private fun clearImportSnapshot() {
        runCatching {
            File(vaultFile.parentFile, "$VAULT_FILE_NAME.import-backup").delete()
            envelopeBackupFile.delete()
        }
    }

    // ---- Biometric management --------------------------------------------

    fun isBiometricEnabled(): Boolean = mkm.isBiometricEnabled()
    fun createBiometricDecryptCipher(): Cipher? = mkm.createBiometricDecryptCipher()
    fun createBiometricEncryptCipher(): Cipher? = mkm.createBiometricEncryptCipher()
    fun enableBiometric(cipher: Cipher): Boolean = mkm.enableBiometric(cipher)
    fun disableBiometric() = mkm.disableBiometric()

    // ---- Master password --------------------------------------------------

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean =
        withContext(Dispatchers.IO) {
            beginBusy()
            try {
                // 改主密码是应用里权限最高的动作：进隐藏空间、开指纹都要重新验
                // 一次身份，这个不能是例外 —— 否则拿到已解锁手机的人不用知道
                // 主密码就能把它换掉，旧主密码当场失效。
                if (!mkm.verifyMasterPassword(currentPassword.toCharArray())) return@withContext false
                mkm.changeMasterPassword(newPassword.toCharArray())
            } finally {
                endBusy()
            }
        }

    /**
     * Kills the whole session: master key, decrypted lists and every hidden
     * space at once.
     */
    fun lock() {
        // 先作废所有在途解锁：这一步之后完成的解锁结果一律不认。
        lockEpoch.incrementAndGet()
        // 忙碌计数一并清零：解锁跑到一半被息屏打断时，那一次 beginBusy 有可能
        // 等不到配对的 endBusy，busyDepth 就永远不归零 —— 解锁页会一直转圈、
        // 密码和指纹两个按钮全灰。既然已经回到锁定态，在途的忙碌就没有意义了。
        busyDepth.set(0)
        _busy.tryEmit(false)
        mkm.lock()
        repo.lock()
        _entries.value = emptyList()
        _notes.value = emptyList()
        _cards.value = emptyList()
        _otps.value = emptyList()
        // The hidden spaces must not outlive the foreground session: whoever
        // comes back to the phone has to prove who they are again.
        lockAllHidden()
        // A scan result outlives the session otherwise: unlock the vault again
        // and the OTP editor would fill itself with whatever was scanned before
        // the lock, ready to be saved as a brand new account.
        _scannedOtp.value = null
        // Hidden *destinations* are the exception to "stay mounted": the unlock
        // overlay would otherwise reveal hidden content the moment it lifts,
        // one verification short of what the user asked for. Normal screens
        // keep their editing context.
        hiddenOrigin(_screen.value)?.let { kind ->
            _homeTab.value = homeTabFor(kind)
            _screen.value = AppScreen.List
        }
        // No route() here on purpose: the current screen stays mounted under
        // the unlock overlay, so a note or card being edited is still there
        // once the vault is unlocked again. Only the key is dropped.
        syncLocked()
    }

    /**
     * Which hidden space [screen] belongs to, or null when it is not hidden
     * content. Used both to decide whether locking has to move the user out and
     * to know which list to move them back to.
     */
    private fun hiddenOrigin(screen: AppScreen): HiddenKind? = when (screen) {
        is AppScreen.Hidden -> screen.kind
        is AppScreen.Detail -> HiddenKind.PASSWORD.takeIf { screen.fromHidden }
        is AppScreen.Editor -> HiddenKind.PASSWORD.takeIf { screen.fromHidden }
        is AppScreen.NoteEditor -> HiddenKind.NOTE.takeIf { screen.fromHidden }
        is AppScreen.ImageViewer -> HiddenKind.NOTE.takeIf { screen.fromHidden }
        is AppScreen.CardView -> HiddenKind.CARD.takeIf { screen.fromHidden }
        is AppScreen.CardEditor -> HiddenKind.CARD.takeIf { screen.fromHidden }
        is AppScreen.OtpEditor -> HiddenKind.OTP.takeIf { screen.fromHidden }
        else -> null
    }

    private fun homeTabFor(kind: HiddenKind): Int = when (kind) {
        HiddenKind.PASSWORD -> 0
        HiddenKind.NOTE -> 1
        HiddenKind.CARD -> 2
        HiddenKind.OTP -> 3
    }

    // ---- Entry CRUD -------------------------------------------------------

    suspend fun addEntry(entry: VaultEntry) = withContext(Dispatchers.IO) {
        repo.addEntry(entry)
        refreshEntries()
    }

    suspend fun updateEntry(entry: VaultEntry) = withContext(Dispatchers.IO) {
        repo.updateEntry(entry)
        refreshEntries()
    }

    suspend fun deleteEntry(uuid: UUID) = withContext(Dispatchers.IO) {
        repo.deleteEntry(uuid)
        refreshEntries()
    }

    fun getEntry(uuid: UUID): VaultEntry? = repo.getEntry(uuid)

    // ---- Note CRUD --------------------------------------------------------

    /** Visible notes and (when allowed) hidden ones are refreshed together:
     *  hiding a note changes what both lists must show. */
    private fun refreshNotes() {
        _notes.value = repo.getNotes(hidden = false)
        _hiddenNotes.value = if (isHiddenUnlocked(HiddenKind.NOTE)) {
            repo.getNotes(hidden = true)
        } else {
            emptyList()
        }
    }

    /** Reloads one kind's lists; see [refreshNotes] for why both sides go together. */
    private fun refreshKind(kind: HiddenKind) {
        when (kind) {
            HiddenKind.PASSWORD -> refreshEntries()
            HiddenKind.NOTE -> refreshNotes()
            HiddenKind.CARD -> refreshCards()
            HiddenKind.OTP -> refreshOtps()
        }
    }

    /**
     * 丢弃这次编辑里导入却没用上的图片。
     *
     * 编辑器放弃修改时调用：那些字节已经写进密码库了，不主动回收就会一直跟着
     * 每一次备份和导出走。
     */
    fun discardPendingBinaries() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.discardPendingBinaries()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 以前整个 runCatching 掉：清不掉时用户以为回收了，那些字节其实
                // 一直跟着每次备份和导出走，而这个函数存在的唯一目的就是不让它走。
                showMessage("清理未使用的图片失败：${e.message ?: "未知错误"}")
            }
        }
    }

    suspend fun addNote(note: Note) = withContext(Dispatchers.IO) {
        repo.addNote(note)
        refreshNotes()
    }

    suspend fun updateNote(note: Note) = withContext(Dispatchers.IO) {
        repo.updateNote(note)
        refreshNotes()
    }

    suspend fun deleteNote(uuid: UUID) = withContext(Dispatchers.IO) {
        repo.deleteNote(uuid)
        refreshNotes()
    }

    fun getNote(uuid: UUID): Note? = repo.getNote(uuid)

    // ---- Hidden notes -------------------------------------------------------

    /**
     * Checks the master password without disturbing the open vault.
     *
     * Runs on Dispatchers.IO: this repeats the Argon2id derivation, which is a
     * few seconds of work and must never sit on the main thread.
     */
    suspend fun verifyMasterPassword(masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            mkm.verifyMasterPassword(masterPassword.toCharArray())
        } finally {
            endBusy()
        }
    }

    /** Checks an already-authenticated BiometricPrompt cipher; see [verifyMasterPassword]. */
    fun verifyBiometric(cipher: Cipher): Boolean = mkm.verifyBiometric(cipher)

    /** Whether [kind]'s hidden space is currently open. */
    fun isHiddenUnlocked(kind: HiddenKind): Boolean = kind in _hiddenUnlocked.value

    /**
     * Opens one hidden space after a successful check, and only then loads that
     * kind's hidden items -- no list is kept in memory while locked, and passing
     * the check for one kind leaves the other three closed.
     */
    /**
     * @return whether the space was actually opened.
     *
     * The vault can lock itself (screen off, app backgrounded) during the
     * several seconds the password check takes, and returning silently then
     * left the user staring at the gate they had just passed with no hint that
     * anything went wrong.
     */
    fun unlockHiddenSpace(kind: HiddenKind): Boolean {
        if (!mkm.isUnlocked || !_hiddenSpaceEnabled.value) {
            showMessage("密码库已锁定，请重新解锁后再进入隐藏空间")
            return false
        }
        _hiddenUnlocked.value = _hiddenUnlocked.value + kind
        refreshKind(kind)
        return true
    }

    /** Closes one hidden space and drops whatever it had decrypted. */
    private fun lockHidden(kind: HiddenKind) {
        _hiddenUnlocked.value = _hiddenUnlocked.value - kind
        clearHiddenList(kind)
    }

    /**
     * Closes every hidden space at once. Used when the vault locks and when the
     * master switch is turned off: in both cases nothing hidden may stay
     * reachable.
     */
    private fun lockAllHidden() {
        _hiddenUnlocked.value = emptySet()
        HiddenKind.entries.forEach(::clearHiddenList)
    }

    private fun clearHiddenList(kind: HiddenKind) {
        when (kind) {
            HiddenKind.PASSWORD -> _hiddenEntries.value = emptyList()
            HiddenKind.NOTE -> _hiddenNotes.value = emptyList()
            HiddenKind.CARD -> _hiddenCards.value = emptyList()
            HiddenKind.OTP -> _hiddenOtps.value = emptyList()
        }
    }

    suspend fun setNoteHidden(uuid: UUID, hidden: Boolean) = withContext(Dispatchers.IO) {
        repo.setNoteHidden(uuid, hidden)
        refreshNotes()
    }

    suspend fun setEntryHidden(uuid: UUID, hidden: Boolean) = withContext(Dispatchers.IO) {
        repo.setEntryHidden(uuid, hidden)
        refreshEntries()
    }

    suspend fun setCardHidden(uuid: UUID, hidden: Boolean) = withContext(Dispatchers.IO) {
        repo.setCardHidden(uuid, hidden)
        refreshCards()
    }

    suspend fun setOtpHidden(uuid: UUID, hidden: Boolean) = withContext(Dispatchers.IO) {
        repo.setOtpHidden(uuid, hidden)
        refreshOtps()
    }

    /**
     * The master switch for the whole feature. Turning it off also closes every
     * space that happens to be open: leaving one unlocked behind a switch that
     * says "hidden spaces are off" would be a lie the user cannot see.
     */
    fun setHiddenSpaceEnabled(enabled: Boolean) {
        securityStore.setHiddenSpaceEnabled(enabled)
        _hiddenSpaceEnabled.value = enabled
        if (!enabled) lockAllHidden()
    }

    // ---- Card CRUD --------------------------------------------------------

    /** Visible cards and (when allowed) hidden ones; see [refreshNotes]. */
    private fun refreshCards() {
        _cards.value = repo.getAllCards(hidden = false)
        _hiddenCards.value = if (isHiddenUnlocked(HiddenKind.CARD)) {
            repo.getAllCards(hidden = true)
        } else {
            emptyList()
        }
    }

    suspend fun addCard(card: BankCard) = withContext(Dispatchers.IO) {
        repo.addCard(card)
        refreshCards()
    }

    suspend fun updateCard(card: BankCard) = withContext(Dispatchers.IO) {
        repo.updateCard(card)
        refreshCards()
    }

    suspend fun deleteCard(uuid: UUID) = withContext(Dispatchers.IO) {
        repo.deleteCard(uuid)
        refreshCards()
    }

    fun getCard(uuid: UUID): BankCard? = repo.getCard(uuid)

    // ---- Authenticator (OTP) CRUD -----------------------------------------

    /** Visible accounts and (when allowed) hidden ones; see [refreshNotes]. */
    private fun refreshOtps() {
        _otps.value = repo.getAllOtps(hidden = false)
        _hiddenOtps.value = if (isHiddenUnlocked(HiddenKind.OTP)) {
            repo.getAllOtps(hidden = true)
        } else {
            emptyList()
        }
    }

    suspend fun addOtp(entry: OtpEntry) = withContext(Dispatchers.IO) {
        repo.addOtp(entry)
        refreshOtps()
    }

    suspend fun updateOtp(entry: OtpEntry) = withContext(Dispatchers.IO) {
        repo.updateOtp(entry)
        refreshOtps()
    }

    suspend fun deleteOtp(uuid: UUID) = withContext(Dispatchers.IO) {
        repo.deleteOtp(uuid)
        refreshOtps()
    }

    fun getOtp(uuid: UUID): OtpEntry? = repo.getOtp(uuid)

    /**
     * Reads an image from a content URI, compresses it so the encrypted vault
     * stays small, then stores it inside the KDBX binary pool.
     * Returns a stable ref for the note's [app.srpass.kykfz01.vault.ImageBlock].
     *
     * 原图多大都能进来：不再有"太大就取消"这一说，因为入库的永远是采样加重
     * 编码之后的小图。解码走 [android.content.res.AssetFileDescriptor]，不把
     * 原文件整张读进堆 —— 几十 MB 的原图压完也就几百 KB。
     */
    suspend fun importImageToVault(uri: Uri): String? = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val cr = getApplication<Application>().contentResolver
            val packed = runCatching { packImageFromUri(cr, uri) }.getOrNull()
                ?: runCatching { packImageFromStream(cr, uri) }.getOrNull()
                ?: return@withContext null
            repo.addBinary(packed)
        } finally {
            endBusy()
        }
    }

    /**
     * Same destination as [importImageToVault], just fed bytes instead of a URI:
     * compress, then into the KDBX binary pool so a backup carries the image.
     *
     * 同样不设上限：字节已经在内存里了，拒掉也没省下什么，压小再入库才是正解。
     */
    suspend fun importImageBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val packed = packImage(bytes) ?: return@withContext null
            repo.addBinary(packed)
        } finally {
            endBusy()
        }
    }

    /**
     * Imports an image that came off the clipboard.
     *
     * The source stays a string because that is what the HTML carried:
     * `data:image/...;base64,...` (self-contained, always readable), or a
     * `content://` / `file://` pointer whose provider may refuse to hand the
     * bytes over - that case returns null so the caller can ask for permission
     * or tell the user to save the picture first.
     */
    suspend fun importClipboardImage(src: String): String? {
        val s = src.trim()
        if (s.startsWith("data:", ignoreCase = true)) {
            val b64 = s.substringAfter("base64,", "")
            if (b64.isBlank()) return null
            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
            return importImageBytes(bytes)
        }
        if (s.startsWith("content://", ignoreCase = true) || s.startsWith("file://", ignoreCase = true)) {
            return runCatching { importImageToVault(Uri.parse(s)) }.getOrNull()
        }
        return null
    }

    /** 读剪贴板里的图被拒时用得上：申请媒体权限，结果直接回调。 */
    fun requestMediaPermissionThen(onResult: (Boolean) -> Unit) {
        val controller = imageController
        if (controller == null) onResult(false) else controller.requestMediaPermission(onResult)
    }

    /**
     * Decrypts a note image from the KDBX binary pool into a bitmap.
     *
     * Sampled, not full-size: a 48MP photo decodes to ~190MB of ARGB and took
     * the whole app down with it.
     */
    /** 笔记图片解码缓存：key 是 "ref@maxDim"，同一张图的不同尺寸各存一份。 */
    private val noteImageCache = object : LruCache<String, Bitmap>(NOTE_IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 解码并发压到 2。
     *
     * 一篇笔记里的图片是**一起**进组合树的（正文用的是可滚动 Column，不是
     * LazyColumn），同时发十几个解码会把 IO 线程池占满，首屏一卡就是几百毫秒。
     */
    private val imageDecodeDispatcher = Dispatchers.IO.limitedParallelism(2)

    /** 屏幕宽度（物理像素）：正文里的图铺满一列只需要这么宽。 */
    private fun screenWidthPx(): Int =
        getApplication<Application>().resources.displayMetrics.widthPixels

    /**
     * 取一张笔记图片。
     *
     * [maxDim] = 0 表示"按屏幕宽度解码"。正文里的图只是 fillMaxWidth 地铺满一
     * 列，按 2048 解出来是白白多花三四倍内存（2000×1500 是 12MB，屏幕宽那份只
     * 有 3.5MB）—— 而一篇笔记的图是**一次性全部解码并一直持有**的，十来张就能
     * 吃掉一百多 MB。真正需要高清的只有全屏看图那里，由调用方显式传更大的尺寸。
     */
    suspend fun getNoteImage(ref: String, maxDim: Int = 0): Bitmap? =
        withContext(imageDecodeDispatcher) {
            val dim = if (maxDim > 0) maxDim else screenWidthPx()
            val key = "$ref@$dim"
            noteImageCache.get(key)?.let { return@withContext it }
            val bytes = repo.getBinary(ref) ?: return@withContext null
            val bmp = decodeSampled(bytes, dim) ?: return@withContext null
            // 单张就吃掉缓存四分之一的不进缓存：它会把别的图全挤出去，缓存等于白建。
            if (bmp.byteCount <= NOTE_IMAGE_CACHE_BYTES / 4) noteImageCache.put(key, bmp)
            bmp
        }

    /**
     * 把一条录音流式解密到缓存目录，返回可直接交给 [MediaPlayer] 播放的文件。
     *
     * 解密是流式的，50MB 的录音也不会让密文和明文同时驻留内存；已缓存的
     * 文件直接复用。
     */
    suspend fun getNoteAudioFile(ref: String): File? = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "note_audio").apply { mkdirs() }
        val f = File(dir, "${ref.filter { it.isLetterOrDigit() }}.m4a")
        if (f.exists() && f.length() > 0L) return@withContext f
        if (repo.decryptMediaToFile(ref, f)) f else null
    }

    /**
     * 把一张笔记图片保存到系统相册（无损：直接写库里的解密字节，不重压缩）。
     *
     * MIME 从字节魔数判断：入库时有透明通道存 PNG、否则 JPEG。Android 10+
     * 走 MediaStore 写入，无需存储权限。
     */
    suspend fun saveNoteImageToGallery(ref: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = repo.getBinary(ref)
        if (bytes == null) {
            showMessage("图片读取失败")
            return@withContext false
        }
        val isPng = bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        val mime = if (isPng) "image/png" else "image/jpeg"
        val ext = if (isPng) "png" else "jpg"

        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SrPass_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            showMessage("无法写入相册")
            return@withContext false
        }
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (ok) {
            showMessage("已保存到相册")
        } else {
            runCatching { resolver.delete(uri, null, null) }
            showMessage("保存失败")
        }
        ok
    }

    /**
     * 把一段录音存进二进制池。
     *
     * 入库前卡一次大小：KDBX 的二进制是整体编码进文件的（[VaultRepository.save]
     * 里就写着"vault 满是照片时 encode 会 OOM"），而音频比照片大得多——AAC
     * 96kbps 一分钟约 700KB，随手录十分钟就能撑爆一次保存。
     */
    suspend fun importAudioToVault(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_MEDIA_BYTES) {
            withContext(Dispatchers.Main) { showMessage("音频太大（>${MAX_MEDIA_BYTES / 1024 / 1024}MB），已取消") }
            return@withContext null
        }
        beginBusy()
        try {
            repo.addBinary(bytes)
        } finally {
            endBusy()
        }
    }

    /** 把涂鸦导出的 PNG 存进二进制池（同样卡大小）。 */
    suspend fun importDrawingToVault(png: ByteArray): String? = withContext(Dispatchers.IO) {
        if (png.size > MAX_MEDIA_BYTES) {
            withContext(Dispatchers.Main) { showMessage("涂鸦太大，已取消") }
            return@withContext null
        }
        beginBusy()
        try {
            repo.addBinary(png)
        } finally {
            endBusy()
        }
    }

    /** 单份媒体入库上限（见 [importAudioToVault]）。 */

    /** Decodes with the smallest power-of-two sample that keeps [maxDim]. */
    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val biggest = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = if (biggest > 0) {
            var s = 1
            while (biggest / s > maxDim) s = s shl 1
            s
        } else 1
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    /**
     * 从 content URI 一路压到入库字节，全程不把原文件读进内存。
     *
     * 返回 null = 这个 URI 拿不到文件描述符（虚拟文件之类），调用方退回"读成
     * 字节再压"那条路。
     */
    private fun packImageFromUri(cr: ContentResolver, uri: Uri): ByteArray? {
        val desc = cr.openAssetFileDescriptor(uri, "r") ?: return null
        return desc.use { afd ->
            val fd = afd.fileDescriptor
            val sample = sampleSize { BitmapFactory.decodeFileDescriptor(fd, null, it) }
                ?: return null
            val bmp = BitmapFactory.decodeFileDescriptor(
                fd, null, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            val orientation = runCatching {
                afd.createInputStream().use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            encodePacked(bmp, orientation, afd.length) { readOriginal(cr, uri, afd.length) }
        }
    }

    /**
     * 拿不到文件描述符时的退路（网盘里的虚拟文件）。
     *
     * 开三次流 —— 读头定采样率、读 EXIF、按采样率解码 —— 也不把整张原图读进
     * 内存。那条路（`readBytes()`）留着只会让一张几百 MB 的原图把堆撑爆。
     */
    private fun packImageFromStream(cr: ContentResolver, uri: Uri): ByteArray? {
        val open = { runCatching { cr.openInputStream(uri) }.getOrNull() }
        val sample = open().use { stream ->
            stream?.let { s -> sampleSize { BitmapFactory.decodeStream(s, null, it) } }
        } ?: return null
        val orientation = open().use { stream ->
            stream?.let { s ->
                runCatching {
                    ExifInterface(s).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }
        val bmp = open().use { stream ->
            stream?.let { s ->
                BitmapFactory.decodeStream(
                    s, null, BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        } ?: return null
        // 这条路拿不到原图长度，也就无从保证原图不大 —— 一律只存压过的那份。
        return encodePacked(bmp, orientation, -1L) { null }
    }

    /**
     * 回读原字节：只有确认它不大才读。
     *
     * [size] 是压缩前拿到的长度；未知（-1）或超过上限时返回 null，一张几百 MB
     * 的原图就不会因为"顺手读一下"被整张拉进堆。
     */
    private fun readOriginal(cr: ContentResolver, uri: Uri, size: Long): ByteArray? {
        if (size <= 0 || size > KEEP_ORIGINAL_MAX_BYTES) return null
        return runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }

    /** 字节版：采样 → 摆正 → 重编码。认不出的图原样存，压不出来返回 null。 */
    private fun packImage(bytes: ByteArray): ByteArray? {
        val sample = sampleSize { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
            ?: return bytes
        val bmp = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return bytes
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return encodePacked(bmp, orientation, bytes.size.toLong()) { bytes }
    }

    /**
     * 只读文件头定采样率，不解码整图。采样率是 2 的幂，所以结果落在
     * [IMAGE_MAX_DIM / 2, IMAGE_MAX_DIM] 之间，不会像早先 highestOneBit 那样
     * 向上取整后反而超过 1920。
     */
    private fun sampleSize(boundsOf: (BitmapFactory.Options) -> Unit): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        boundsOf(bounds)
        // 坏图或不支持的格式会让 bounds 停在 -1，交给调用方原样存。
        val biggest = maxOf(bounds.outWidth, bounds.outHeight)
        if (biggest <= 0) return null
        var s = 1
        while (biggest / s > IMAGE_MAX_DIM) s = s shl 1
        return s
    }

    /**
     * 摆正方向 → 选格式压 → 压完比原图还大就留原图。
     *
     * [originalSize] 是压缩前的字节数（-1 = 拿不到）；[originalBytes] 只在真
     * 要留原图时才调用，所以大图不会因为"顺手读一下"被拉进内存。
     */
    private fun encodePacked(
        bmp: Bitmap,
        orientation: Int,
        originalSize: Long,
        originalBytes: () -> ByteArray?
    ): ByteArray? {
        val rotated = applyOrientation(bmp, orientation)
        val encoded = encodeBitmap(rotated)
        // 旋转过的不能留原字节 —— 原字节还是躺着的。
        val keepOriginal = encoded != null && rotated === bmp &&
            originalSize > 0 && originalSize <= KEEP_ORIGINAL_MAX_BYTES.toLong() &&
            encoded.size.toLong() >= originalSize
        val result = when {
            keepOriginal -> originalBytes() ?: encoded
            encoded != null -> encoded
            else -> originalBytes()
        }
        if (rotated !== bmp) rotated.recycle()
        bmp.recycle()
        // null = 压不出来又拿不到原字节：宁可不入库，也不存一张打不开的空图。
        return result
    }

    /**
     * 有透明通道的必须走 PNG：JPEG 没有 alpha，透明区会被压成黑底。其余一律
     * JPEG —— 照片压 PNG 会大一个数量级。
     */
    private fun encodeBitmap(bmp: Bitmap): ByteArray? {
        val keepAlpha = hasTransparency(bmp)
        encode(bmp, if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
            ?.let { return it }
        // 首选格式压不出来（配置不支持等），换另一个再试一次。
        return encode(bmp, if (keepAlpha) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG)
    }

    private fun encode(bmp: Bitmap, format: Bitmap.CompressFormat): ByteArray? {
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else IMAGE_JPEG_QUALITY
        val out = ByteArrayOutputStream()
        val ok = runCatching { bmp.compress(format, quality, out) }.getOrDefault(false)
        return if (ok && out.size() > 0) out.toByteArray() else null
    }

    /** 抽样看有没有真的透明像素 —— 光看 hasAlpha() 说明不了什么。 */
    private fun hasTransparency(bmp: Bitmap): Boolean {
        if (!bmp.hasAlpha()) return false
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        // 密度取 128×128：再密就是白跑，再疏会漏掉一小块透明区（那块会变黑）。
        val rowStep = maxOf(1, h / 128)
        val colStep = maxOf(1, w / 128)
        for (y in 0 until h step rowStep) {
            runCatching { bmp.getPixels(row, 0, w, 0, y, w, 1) }.getOrElse { return false }
            var x = 0
            while (x < w) {
                if ((row[x] ushr 24) != 255) return true
                x += colStep
            }
        }
        return false
    }

    /**
     * 按 EXIF 方向摆正。
     *
     * 多数手机的照片像素是横躺着的，方向只写在 EXIF 里；BitmapFactory 不看
     * EXIF，重编码又把 EXIF 丢掉 —— 不摆正的话竖拍照片入库后就是躺着的。
     */
    private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bmp
        }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }.getOrNull() ?: bmp
    }

    // ---- Vault file / import ----------------------------------------------

    fun getVaultFile(): File = vaultFile

    // Lets the UI ask the activity to open the system SAF pickers. The activity
    // owns the startActivityForResult / onActivityResult handling with fixed,
    // always-valid request codes (see MainActivity), so we never touch the
    // ActivityResultRegistry request-code counter.
    var safController: SafController? = null


    // Lets the UI ask the activity to request full-album (READ_MEDIA_IMAGES)
    // permission with a fixed, always-valid request code (see MainActivity),
    // avoiding the ActivityResultRegistry request-code counter.
    var imageController: ImageController? = null

    /**
     * Outcome of the last [requestMediaPermission] call.
     *
     * Delivered as a flow rather than through a callback: the activity can be
     * recreated (rotation) while the system permission dialog is on screen, and
     * a lambda captured from the destroyed instance would never be invoked --
     * leaving the UI waiting forever.
     */
    private val _mediaPermissionResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val mediaPermissionResult: SharedFlow<Boolean> = _mediaPermissionResult.asSharedFlow()

    fun requestMediaPermission() {
        val controller = imageController
        if (controller == null) _mediaPermissionResult.tryEmit(false)
        else controller.requestMediaPermission { _mediaPermissionResult.tryEmit(it) }
    }

    /**
     * Delivers a permission outcome whose Activity-side callback was lost.
     *
     * The callback is a field on the Activity, so it dies with the instance --
     * rotating the screen behind the permission dialog left the UI waiting for
     * a result that could never arrive. The flow survives; this is the path the
     * Activity falls back to when it finds no callback.
     */
    fun reportMediaPermissionResult(granted: Boolean) {
        _mediaPermissionResult.tryEmit(granted)
    }

    var cameraController: CameraController? = null

    /** Same flow shape as [mediaPermissionResult], for the same reason. */
    private val _cameraPermission = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val cameraPermission: SharedFlow<Boolean> = _cameraPermission.asSharedFlow()

    fun requestCameraPermission() {
        val controller = cameraController
        if (controller == null) _cameraPermission.tryEmit(false)
        else controller.requestCameraPermission { _cameraPermission.tryEmit(it) }
    }

    /** See [reportMediaPermissionResult]. */
    fun reportCameraPermissionResult(granted: Boolean) {
        _cameraPermission.tryEmit(granted)
    }

    var micController: MicController? = null

    /** Same flow shape as [mediaPermissionResult], for the same reason. */
    private val _micPermission = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val micPermission: SharedFlow<Boolean> = _micPermission.asSharedFlow()

    fun requestMicPermission() {
        val controller = micController
        if (controller == null) _micPermission.tryEmit(false)
        else controller.requestMicPermission { _micPermission.tryEmit(it) }
    }

    /** See [reportMediaPermissionResult]. */
    fun reportMicPermissionResult(granted: Boolean) {
        _micPermission.tryEmit(granted)
    }



    /**
     * Writes a self-contained backup to a user-chosen destination. Runs on a
     * background dispatcher; returns whether the write succeeded.
     *
     * The backup carries the wrapped master keys alongside the vault, because
     * the vault alone cannot be opened anywhere else: its KeePass credential is
     * the master key, and the master key only ever exists inside those
     * envelopes. See [BackupBundle].
     */
    suspend fun exportVaultTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            val envelopes = mkm.exportableEnvelopes()
            if (envelopes.isEmpty()) {
                showMessage("没有可导出的密钥，请先创建密码库")
                return@withContext false
            }
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                    getVaultFile().inputStream().use { vault ->
                        BackupBundle.write(os, vault, envelopes, mediaDir)
                    }
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        } finally {
            endBusy()
        }
    }

    /**
     * Restores a backup the user picked.
     *
     * Only the self-contained [BackupBundle] is accepted: a ZIP carrying the
     * vault plus the password/recovery envelopes. That is what makes a backup
     * restorable on a different phone -- the vault alone is useless because its
     * KeePass credential is the master key, and the master key only ever exists
     * wrapped inside those envelopes. So we swap the vault file and our envelopes
     * for the backup's, and drop biometric (its Keystore key is bound to the old
     * device).
     *
     * The new phone still has to prove it owns the backup: importing only puts
     * the wrapped master key in place. The in-memory session is wiped and the
     * user must re-unlock with the backup's own password or recovery key before
     * any entry is readable.
     */
    /**
     * 从 SAF 回来的导出/导入走 ViewModel 作用域，不用 Activity 的。
     *
     * 这两个操作要跑几秒，期间完全可能旋转屏幕；用 lifecycleScope 时配置变更会
     * 把 Job 取消，结果既没有「已导出」也没有「导出失败」，而导入被打断更危险。
     */
    fun exportVaultToAsync(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { exportVaultTo(uri) }.getOrDefault(false)
            showMessage(if (ok) "已导出密码库备份" else "导出失败")
        }
    }

    fun importVaultFromAsync(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { importVaultFrom(uri) }.getOrDefault(false)
            showMessage(if (ok) "已从备份导入，请用备份的主密码解锁" else "导入失败")
        }
    }

    suspend fun importVaultFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val backup = readBackup(uri) ?: return@withContext false
        applyBackup(backup)
    }

    /** 真正执行导入：换信封 → 装密码库 → 锁定。任何一步失败都回滚到导入前。 */
    private suspend fun applyBackup(backup: BackupBundle.Backup): Boolean = withContext(Dispatchers.IO) {
        beginBusy()
        try {
            // Snapshot the current vault and envelopes so a failure leaves the
            // user exactly where they started -- still unlocked, working vault.
            val snapshot = File(vaultFile.parentFile, "$VAULT_FILE_NAME.import-backup")
            // Whether a vault existed is not the same as whether it could be
            // snapshotted. Conflating the two let a failed copy fall through to
            // the rollback below, which then *deleted* the current vault -- the
            // one file the whole import was supposed to be undoable back to.
            val existedBefore = vaultFile.exists()
            val hadVault = runCatching {
                if (existedBefore) {
                    vaultFile.copyTo(snapshot, overwrite = true)
                    true
                } else false
            }.getOrDefault(false)
            if (existedBefore && !hadVault) {
                showMessage("无法备份当前密码库，已取消导入")
                return@withContext false
            }
            val oldEnvelopes = mkm.exportableEnvelopes()

            // 先把内存里的数据库句柄丢掉。否则任何还在途的 save() 会在几秒后
            // 拿着旧库往刚换上来的 vault.kdbx 里整库覆写一次 —— 旧内容配新信封，
            // 两个密钥都打不开，且不可回滚。断了句柄之后它们只会失败。
            repo.lock()

            // Also snapshot the envelope SharedPreferences file so a crash between
            // swapping envelopes and installing the vault can be recovered on the
            // next launch (the live vault file is snapshotted separately above).
            val envSnap = envelopeBackupFile
            runCatching {
                if (envelopePrefsFile.exists()) envelopePrefsFile.copyTo(envSnap, overwrite = true)
            }

            // Swap in the backup's envelopes FIRST, while the live vault file is
            // still untouched. A crash mid-import can then only ever be rolled
            // back (the .import-backup snapshot plus the in-memory oldEnvelopes),
            // never a "new vault + old key" dead end that cannot be unlocked.
            if (!runCatching { mkm.restoreEnvelopes(backup.envelopes) }.getOrDefault(false)) {
                restoreSnapshot(snapshot, existedBefore)
                runCatching { envSnap.delete() }
                showMessage("该备份缺少可用的密钥信封，已取消导入")
                return@withContext false
            }

            // Now install the imported vault via temp file + fsync + rename.
            // On failure roll both the vault file and the envelopes back.
            val written = runCatching {
                val dir = vaultFile.parentFile ?: error("vault has no parent directory")
                val tmp = File(dir, "${vaultFile.name}.import-tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(backup.vaultBytes)
                    runCatching { out.fd.sync() }
                }
                // rename() replaces the target atomically: deleting first used
                // to open a window in which no vault file existed at all. The
                // delete is only a fallback for filesystems that refuse to
                // rename over an existing file.
                if (!tmp.renameTo(vaultFile)) {
                    if (vaultFile.exists() && !vaultFile.delete()) {
                        throw IOException("cannot clear current vault for import")
                    }
                    if (!tmp.renameTo(vaultFile)) throw IOException("cannot install imported vault")
                }
            }.isSuccess
            if (!written) {
                if (existedBefore) snapshot.copyTo(vaultFile, overwrite = true) else vaultFile.delete()
                // A throw from the rollback itself must not mask the real
                // failure, and must not crash the import either.
                runCatching { mkm.restoreEnvelopes(oldEnvelopes) }
                snapshot.delete()
                runCatching { envSnap.delete() }
                // 内存句柄在开头已经丢掉了，这里必须整体锁一次，否则界面显示已解锁
                // 而库已经写不动。
                lock()
                showMessage("写入备份失败，已恢复到导入前的密码库，请重新解锁")
                return@withContext false
            }

            // 快照故意留着，等第一次成功解锁再删（见 [clearImportSnapshot]）。
            //
            // 导入无法验证"新库能被这批信封打开"：信封里的主密钥要等用户输入主
            // 密码才解得出。以前这里直接删快照，一旦备份是截断的、或者库与信封
            // 不配套，用户就既没有旧库也没有能开新库的密钥。留着快照，最坏情况是
            // 下次启动 [recoverImportIfNeeded] 把它还原成导入前的样子。
            // Wipe the in-memory key, clear every list, and sync _locked so the
            // unlock overlay reappears. (Previously _locked stayed false, stranding
            // the user on the data screen with no way to unlock, and _cards/_otps
            // were left showing the old vault.)
            lock()
            true
        } finally {
            endBusy()
        }
    }

    /**
     * Reads a picked .srpass file into a [BackupBundle.Backup].
     *
     * Only the self-contained bundle is accepted -- it carries both the vault and
     * the password/recovery envelopes, which is what makes it restorable on any
     * phone. Returns null (after telling the user why) for an unreadable file or
     * anything that is not a valid backup.
     */
    private suspend fun readBackup(uri: Uri): BackupBundle.Backup? {
        val cr = getApplication<Application>().contentResolver
        return cr.openInputStream(uri)?.buffered()?.use { stream ->
            if (!BackupBundle.isBundle(stream)) {
                showMessage("这不是有效的 .srpass 备份文件")
                return null
            }
            BackupBundle.read(stream, mediaDir) ?: run {
                showMessage("备份文件已损坏或格式不正确")
                return null
            }
        } ?: run {
            showMessage("无法读取所选文件")
            null
        }
    }

    /** Puts the pre-import vault file back, or removes it if there was none. */
    private fun restoreSnapshot(snapshot: File, hadVault: Boolean) {
        runCatching {
            if (hadVault) snapshot.copyTo(vaultFile, overwrite = true) else vaultFile.delete()
            snapshot.delete()
        }
    }

    /**
     * Crash recovery for an import that never finished committing. importVaultFrom
     * snapshots the live vault file before touching anything, so if that snapshot
     * is still on disk the vault was never swapped in -- restore the pre-import
     * vault and the (also snapshotted) envelopes and tell the user to re-import.
     *
     * Only the vault snapshot gates recovery: once the vault file itself has been
     * replaced the import is effectively complete, so a stray envelope snapshot
     * left behind by a crash between the two deletes is just discarded.
     */
    private fun recoverImportIfNeeded(): Boolean {
        val snap = File(vaultFile.parentFile, "$VAULT_FILE_NAME.import-backup")
        if (!snap.exists()) {
            runCatching { envelopeBackupFile.delete() }
            return false
        }
        runCatching {
            snap.copyTo(vaultFile, overwrite = true)
            if (envelopeBackupFile.exists()) {
                envelopeBackupFile.copyTo(envelopePrefsFile, overwrite = true)
            }
        }
        snap.delete()
        envelopeBackupFile.delete()
        showMessage("检测到上次导入未完成，已恢复到导入前的密码库，请重新导入或解锁")
        return true
    }

    /** Visible entries and (when allowed) hidden ones; see [refreshNotes]. */
    private fun refreshEntries() {
        _entries.value = repo.getAllEntries(hidden = false)
        _hiddenEntries.value = if (isHiddenUnlocked(HiddenKind.PASSWORD)) {
            repo.getAllEntries(hidden = true)
        } else {
            emptyList()
        }
    }
}
