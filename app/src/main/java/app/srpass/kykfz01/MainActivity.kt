package app.srpass.kykfz01

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import app.srpass.kykfz01.ui.theme.ThemeMode
import app.srpass.kykfz01.ui.util.hasMediaAccess
import app.srpass.kykfz01.ui.util.mediaPermissions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.srpass.kykfz01.ui.AppScreen
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.CameraController
import app.srpass.kykfz01.ui.ImageController
import app.srpass.kykfz01.ui.MicController
import app.srpass.kykfz01.ui.SafController
import app.srpass.kykfz01.ui.settings.SecurityStore
import app.srpass.kykfz01.ui.screens.AboutSettingsScreen
import app.srpass.kykfz01.ui.screens.HiddenSpaceScreen
import app.srpass.kykfz01.ui.screens.AppearanceSettingsScreen
import app.srpass.kykfz01.ui.screens.CreateVaultScreen
import app.srpass.kykfz01.ui.screens.DataSettingsScreen
import app.srpass.kykfz01.ui.screens.EntryDetailScreen
import app.srpass.kykfz01.ui.screens.EntryEditorScreen
import app.srpass.kykfz01.ui.screens.ImageViewerScreen
import app.srpass.kykfz01.ui.screens.NoteEditorScreen
import app.srpass.kykfz01.ui.screens.CardsListScreen
import app.srpass.kykfz01.ui.screens.CardViewScreen
import app.srpass.kykfz01.ui.screens.CardEditorScreen
import app.srpass.kykfz01.ui.screens.OtpListScreen
import app.srpass.kykfz01.ui.screens.OtpEditorScreen
import app.srpass.kykfz01.ui.screens.QrScannerScreen
import app.srpass.kykfz01.ui.screens.RecoveryScreen
import app.srpass.kykfz01.ui.screens.SecuritySettingsScreen
import app.srpass.kykfz01.ui.screens.UnlockScreen
import app.srpass.kykfz01.ui.screens.HomeScreen
import app.srpass.kykfz01.ui.theme.SrPassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Fixed, always-valid (< 65536) request codes for the SAF intents. Using a
 * plain startActivityForResult with these avoids the ActivityResultRegistry's
 * request-code counter entirely -- that counter can reach values larger than
 * 16 bits and throw "Can only use lower 16 bits for requestCode". */
private const val RC_EXPORT = 9001
private const val RC_IMPORT = 9002
private const val RC_MEDIA_PERM = 9003
private const val RC_CAMERA_PERM = 9004
private const val RC_MIC_PERM = 9005

class MainActivity : FragmentActivity(), SafController, ImageController,
    CameraController, MicController {

    private lateinit var vm: AppViewModel

    /**
     * Screen lock (power button or display timeout) does NOT fire
     * [onUserLeaveHint] -- that hint only covers Home / Recents / switching
     * apps. Without this receiver the vault stayed unlocked across a device
     * lock, so unlocking the phone went straight back into the data with no
     * master password prompt.
     *
     * ACTION_SCREEN_OFF cannot be declared in the manifest on modern Android,
     * so it is registered here. It stays registered for the whole activity
     * lifetime rather than just the resumed window, because the activity is
     * often already stopped by the time the screen goes off.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!::vm.isInitialized) return
            // Same guard as onStop: a SAF picker, a BiometricPrompt or the
            // permission dialog in flight must not be killed mid-flow.
            if (vm.suppressLockOnLeave) return
            vm.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE is user controlled now (Security screen), but it still has
        // to be applied before the first frame: once a frame is produced without
        // the flag its content is already capturable. Read the stored preference
        // directly so this works before the ViewModel exists.
        applyScreenshotBlocking(SecurityStore(this).getBlockScreenshots())

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vm = ViewModelProvider(this).get(AppViewModel::class.java)
        // Let the UI trigger the SAF pickers through this activity, which owns
        // the startActivityForResult / onActivityResult handling.
        vm.safController = this
        vm.imageController = this
        vm.cameraController = this
        vm.micController = this
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Reflect later changes made from the Security screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.blockScreenshots.collect { applyScreenshotBlocking(it) }
            }
        }
        setContent {
            SrPassRoot(vm)
        }
    }

    /**
     * FLAG_SECURE stops screenshots, screen recording and casting, and keeps
     * the vault out of the recent-apps thumbnail. The Security screen decides
     * whether it is on.
     */
    private fun applyScreenshotBlocking(block: Boolean) {
        if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ---- Full-album permission (ImageController) -------------------------
    // Uses a fixed, always-valid request code so we never touch the
    // ActivityResultRegistry counter (which can overflow 16 bits and crash).
    private var pendingPermResult: ((Boolean) -> Unit)? = null

    override fun requestMediaPermission(onResult: (Boolean) -> Unit) {
        if (hasAnyMediaAccess()) {
            onResult(true)
        } else {
            pendingPermResult = onResult
            // The system permission dialog covers our window and stops the
            // activity; without this guard onStop would lock the vault while
            // the user is still answering it.
            if (::vm.isInitialized) vm.suppressLockOnLeave = true
            // API 33+ requests READ_MEDIA_IMAGES (+ partial access on 34+);
            // API 30~32 falls back to READ_EXTERNAL_STORAGE. See
            // MediaPermissionCompat for the split.
            ActivityCompat.requestPermissions(this, mediaPermissions(), RC_MEDIA_PERM)
        }
    }

    /**
     * Full library access, or the Android 14 partial access where the user
     * hand-picked the photos we are allowed to see. Both let the picker open.
     */
    private fun hasAnyMediaAccess(): Boolean = hasMediaAccess(this)

    @Deprecated("Legacy path, but reliable for our fixed-code intents.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // The dialog is gone either way, so the activity may lock again.
        if (::vm.isInitialized) vm.suppressLockOnLeave = false
        when (requestCode) {
            RC_MEDIA_PERM -> {
                val granted = hasAnyMediaAccess()
                // The callback lives on the Activity instance, so it is gone
                // when the activity was recreated (rotation) behind the dialog.
                // Fall back to the ViewModel's flow, which does survive -- the
                // UI would otherwise wait for a result that can never arrive.
                val callback = pendingPermResult
                pendingPermResult = null
                if (callback != null) callback(granted)
                else vm.reportMediaPermissionResult(granted)
            }
            RC_CAMERA_PERM -> {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val callback = pendingCameraPermResult
                pendingCameraPermResult = null
                if (callback != null) callback(granted)
                else vm.reportCameraPermissionResult(granted)
            }
            RC_MIC_PERM -> {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val callback = pendingMicPermResult
                pendingMicPermResult = null
                if (callback != null) callback(granted)
                else vm.reportMicPermissionResult(granted)
            }
        }
    }

    // ---- 麦克风权限（MicController）：只给笔记录音用 ---------------------

    private var pendingMicPermResult: ((Boolean) -> Unit)? = null

    override fun requestMicPermission(onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            pendingMicPermResult = onResult
            // 同其它权限：系统弹窗会盖住窗口并让 Activity 停下，不加这个保护
            // onStop 会在用户还没回答时就把库锁了。
            if (::vm.isInitialized) vm.suppressLockOnLeave = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RC_MIC_PERM
            )
        }
    }

    // ---- Camera permission (CameraController) -----------------------------

    private var pendingCameraPermResult: ((Boolean) -> Unit)? = null

    override fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            pendingCameraPermResult = onResult
            // Same reason as the media permission: the system dialog covers our
            // window and stops the activity, so onStop would lock the vault
            // while the user is still answering it.
            if (::vm.isInitialized) vm.suppressLockOnLeave = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                RC_CAMERA_PERM
            )
        }
    }

    /**
     * 界面重新可见：解锁页据此重新弹起指纹框。
     *
     * 用 onStart 而不是 onResume，是为了和 onStop 成对 —— 解锁页关心的是"看不
     * 看得见"（息屏、切后台），而不是"能不能交互"（系统权限框盖住时 onResume 不
     * 会重来一次，那种情况本就不该重弹指纹框）。
     */
    override fun onStart() {
        super.onStart()
        if (::vm.isInitialized) vm.uiInForeground = true
    }

    /**
     * Back in the foreground: whatever system flow raised the guard is over, so
     * the vault may lock again from here on.
     */
    override fun onResume() {
        super.onResume()
        waitingForSystemFlow = false
        deferredLock?.cancel()
        deferredLock = null
        if (::vm.isInitialized) vm.suppressLockOnLeave = false
    }

    /**
     * How long a raised lock guard is honoured while we are still away.
     *
     * Flows like the permission dialog or the SAF picker do come back, but a
     * trip to the system Settings page can end with the user simply leaving.
     * The guard then stayed raised until the next resume -- and if it never came,
     * the vault stayed decrypted for the rest of the process.
     */
    private val SYSTEM_FLOW_LOCK_DELAY_MS = 5 * 60 * 1000L

    /** Whether we are still waiting for a system flow we started. */
    private var waitingForSystemFlow = false
    private var deferredLock: kotlinx.coroutines.Job? = null

    /**
     * Locks the vault the moment our window is no longer visible.
     *
     * onUserLeaveHint is NOT enough: it only fires for Home / Recents, so
     * moving to another app any other way -- a notification, an incoming call,
     * or an intent we launched ourselves -- used to leave the vault unlocked.
     * onStop covers every path that actually hides the activity.
     *
     * Two cases must not lock:
     *  - configuration changes (rotation, theme, locale, ...), which destroy and
     *    rebuild the activity while the user stays put;
     *  - system flows we started ourselves (SAF picker, BiometricPrompt,
     *    permission dialog), guarded by [AppViewModel.suppressLockOnLeave],
     *    because locking mid-flow would dispose the pending result.
     *
     * The guard is deliberately NOT cleared here: whichever flow raised it owns
     * its lifetime and clears it on completion (onActivityResult, the biometric
     * onSettled callback, onRequestPermissionsResult). Clearing it here would
     * let a second stop -- while the picker is still open -- lock the vault and
     * break the flow anyway.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (!::vm.isInitialized) return
        vm.uiInForeground = false
        if (vm.suppressLockOnLeave) {
            // The guard is raised for a flow that covers our window. Most report
            // a result and clear it; the ones that do not (the system Settings
            // page) must not be able to disable locking forever, so arm a
            // fallback that locks once we have clearly been away too long.
            waitingForSystemFlow = true
            deferredLock?.cancel()
            deferredLock = lifecycleScope.launch {
                delay(SYSTEM_FLOW_LOCK_DELAY_MS)
                if (!waitingForSystemFlow) return@launch
                waitingForSystemFlow = false
                vm.suppressLockOnLeave = false
                vm.lock()
            }
            return
        }
        vm.lock()
    }

    // ---- SAF picker launch (SafController) --------------------------------

    override fun launchExport(filename: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        startActivityForResult(intent, RC_EXPORT)
    }

    override fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/octet-stream", "*/*")
            )
        }
        startActivityForResult(intent, RC_IMPORT)
    }




    @Deprecated("Legacy path, but reliable for our fixed-code SAF intents.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // We are back from the system picker: never leave the vault unlocked
        // due to the leave-hint that fired when the picker opened.
        vm.suppressLockOnLeave = false
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            // On the ViewModel's scope, not the activity's: an export or import
            // takes seconds, and a rotation mid-way used to cancel it silently --
            // no "exported", no "failed", nothing.
            RC_EXPORT -> vm.exportVaultToAsync(uri)
            RC_IMPORT -> vm.importVaultFromAsync(uri)
        }
    }

    override fun onDestroy() {
        // Guarded: unregisterReceiver throws IllegalArgumentException if the
        // receiver was never registered (e.g. onCreate bailed out early).
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }
}

@Composable
fun SrPassRoot(viewModel: AppViewModel) {
    val screen by viewModel.screen.collectAsState()
    val locked by viewModel.locked.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Keep the system status-bar / navigation-bar gesture-line colour in sync
    // with the chosen theme, otherwise the line is always white and clashes
    // with a light bottom bar (dark line on light, light line on dark).
    val sysUiActivity = LocalContext.current as android.app.Activity
    LaunchedEffect(themeMode) {
        val window = sysUiActivity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val light = when (themeMode) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.SYSTEM -> {
                val night =
                    sysUiActivity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_NO
            }
        }
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    SrPassTheme(mode = themeMode, dynamicColor = dynamicColor) {
        // No outer Scaffold: each screen brings its own Scaffold + TopAppBar,
        // which already consumes the status-bar inset once. Wrapping in an
        // extra Scaffold would double that inset and leave a blank band on top.
        Box(modifier = Modifier.fillMaxSize()) {
            // Content layer. It stays composed even while locked so work in
            // progress (a note or card being edited) is not thrown away -- the
            // lock overlay below is fully opaque, and FLAG_SECURE already
            // blocks screenshots and the recent-apps thumbnail.
            when (val s = screen) {
                AppScreen.CreateVault -> CreateVaultScreen(viewModel)
                AppScreen.Unlock -> Unit
                AppScreen.Recovery -> RecoveryScreen(viewModel)
                // One branch for every home tab, including Settings: as separate
                // branches Compose tore HomeScreen down and built a new one on
                // each switch, which threw away the pager position along with
                // every list's query and scroll state.
                AppScreen.List,
                AppScreen.Settings,
                AppScreen.NotesList,
                AppScreen.CardsList,
                AppScreen.OtpList -> HomeScreen(viewModel)
                AppScreen.SettingsAppearance -> AppearanceSettingsScreen(viewModel)
                AppScreen.SettingsSecurity -> SecuritySettingsScreen(viewModel)
                AppScreen.SettingsData -> DataSettingsScreen(viewModel)
                AppScreen.SettingsAbout -> AboutSettingsScreen(viewModel)
                is AppScreen.Detail -> EntryDetailScreen(viewModel, s.uuid, s.fromHidden)
                is AppScreen.Editor -> EntryEditorScreen(viewModel, s.uuid, s.fromHidden)
                is AppScreen.Hidden -> HiddenSpaceScreen(viewModel, s.kind)
                is AppScreen.NoteEditor -> NoteEditorScreen(viewModel, s.uuid, s.fromHidden, s.scrollToRef)
                is AppScreen.CardView -> CardViewScreen(viewModel, s.uuid, s.fromHidden)
                is AppScreen.CardEditor -> CardEditorScreen(viewModel, s.uuid, s.fromHidden)
                is AppScreen.OtpEditor -> OtpEditorScreen(viewModel, s.uuid, s.fromHidden)
                is AppScreen.QrScanner -> QrScannerScreen(viewModel, s.fromHidden, s.editingUuid)
                is AppScreen.ImageViewer -> ImageViewerScreen(viewModel, s.ref, s.noteUuid, s.fromHidden)
            }
            // Lock layer, drawn over the content. Recovery is excluded: it is
            // the one screen the user is allowed to use while locked.
            if (locked && screen !is AppScreen.Recovery) {
                UnlockScreen(viewModel)
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.Center),
                snackbar = { data ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = data.visuals.message,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Auto-dismiss after a short, fixed window so the centred
                    // hint does not linger on screen.
                    LaunchedEffect(Unit) {
                        delay(2500)
                        data.dismiss()
                    }
                }
            )
        }
    }
}
