package app.srpass.kykfz01.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.biometric.showBiometric
import app.srpass.kykfz01.ui.biometric.strongBiometricProblem
import app.srpass.kykfz01.ui.components.BusyOverlay
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.DialogHint
import app.srpass.kykfz01.ui.components.PasswordInputField
import app.srpass.kykfz01.ui.util.copyToClipboard
import app.srpass.kykfz01.vault.BankCard
import app.srpass.kykfz01.vault.Note
import app.srpass.kykfz01.vault.OtpEntry
import app.srpass.kykfz01.vault.Totp
import app.srpass.kykfz01.vault.VaultEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What each space is called in its top bar. */
private val HiddenKind.title: String
    get() = when (this) {
        HiddenKind.PASSWORD -> "隐藏的密码"
        HiddenKind.NOTE -> "隐藏笔记"
        HiddenKind.CARD -> "隐藏的银行卡"
        HiddenKind.OTP -> "隐藏的验证器"
    }

/** Word for one item of this kind, used by the confirmation dialogs. */
private val HiddenKind.noun: String
    get() = when (this) {
        HiddenKind.PASSWORD -> "密码"
        HiddenKind.NOTE -> "笔记"
        HiddenKind.CARD -> "卡片"
        HiddenKind.OTP -> "验证器"
    }

/** A TOTP code is useless after its window closes, so the copy expires with it. */
private const val OTP_CLIPBOARD_TTL_MS = 30_000L

/**
 * One of the four hidden spaces: passwords, notes, cards and authenticator
 * accounts each have their own, and [kind] says which this one is.
 *
 * Reaching it costs a credential check even though the vault is already open,
 * and it re-locks the moment the user leaves. Nothing here relies on a second
 * vault or its own file -- the items are the same KDBX entries, marked hidden,
 * so backups keep working exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSpaceScreen(viewModel: AppViewModel, kind: HiddenKind) {
    val unlocked by viewModel.hiddenUnlocked.collectAsState()
    val open = kind in unlocked

    // Leaving is locking: there is deliberately no session to resume from.
    BackHandler { viewModel.exitHiddenSpace(kind) }

    if (!open) {
        // Replacing the list rather than overlaying it: while locked there must
        // be nothing composed below that could reveal hidden content.
        HiddenGate(viewModel, kind)
        return
    }
    when (kind) {
        HiddenKind.PASSWORD -> HiddenPasswordList(viewModel)
        HiddenKind.NOTE -> HiddenNoteList(viewModel)
        HiddenKind.CARD -> HiddenCardList(viewModel)
        HiddenKind.OTP -> HiddenOtpList(viewModel)
    }
}

/**
 * The credential check in front of the space.
 *
 * Same master password (or fingerprint) that unlocked the vault -- no separate
 * secret to set up, remember, lose, or back up. The check only *verifies*: a
 * wrong guess can never change the state of an already-unlocked session.
 */
@Composable
private fun HiddenGate(viewModel: AppViewModel, kind: HiddenKind) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val busy by viewModel.busy.collectAsState()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Read once: whether the Keystore key exists cannot change while this
    // screen is up, and asking per recomposition touches it on every keystroke.
    val biometricReady = remember { viewModel.isBiometricEnabled() }
    // Set before the first prompt is launched, so a rotation -- or any other
    // recomposition -- cannot queue a second one on top of the running dialog.
    var biometricOffered by rememberSaveable { mutableStateOf(false) }

    fun launchBiometric() {
        if (activity == null) {
            viewModel.showMessage("当前环境不支持生物识别")
            return
        }
        activity.strongBiometricProblem()?.let {
            viewModel.showMessage(it)
            return
        }
        val cipher = viewModel.createBiometricDecryptCipher()
        if (cipher == null) {
            viewModel.showMessage("指纹不可用，请使用主密码")
            return
        }
        // The system prompt counts as leaving the app; without this the vault
        // would lock underneath the dialog.
        viewModel.suppressLockOnLeave = true
        activity.showBiometric(
            cipher = cipher,
            title = "验证身份",
            subtitle = "验证指纹以查看隐藏${kind.noun}",
            onSuccess = { authorised ->
                if (viewModel.verifyBiometric(authorised)) {
                    viewModel.unlockHiddenSpace(kind)
                } else {
                    viewModel.showMessage("验证失败，请使用主密码")
                }
            },
            onError = { viewModel.showMessage(it) },
            onSettled = { viewModel.suppressLockOnLeave = false }
        )
    }

    // With a fingerprint enrolled it is the faster route, so it is offered
    // straight away rather than waited for behind a button. Once per visit
    // only: repeating it after a cancelled prompt would trap the user in a
    // dialog they just dismissed. The button below stays available either way.
    LaunchedEffect(biometricReady) {
        if (biometricReady && !biometricOffered) {
            biometricOffered = true
            launchBiometric()
        }
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "隐藏空间",
                navigationIcon = {
                    IconButton(onClick = { viewModel.exitHiddenSpace(kind) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text("验证身份后查看隐藏${kind.noun}", style = MaterialTheme.typography.titleMedium)
            Text(
                "使用打开密码库的主密码或指纹，不会另外要求一套新密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            PasswordInputField(
                password,
                { password = it; error = null },
                label = "主密码"
            )
            DialogHint(error, false)
            Button(
                onClick = {
                    scope.launch {
                        if (viewModel.verifyMasterPassword(password)) {
                            password = ""
                            viewModel.unlockHiddenSpace(kind)
                        } else {
                            error = "密码不正确"
                        }
                    }
                },
                enabled = password.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("验证") }

            if (biometricReady) {
                OutlinedButton(
                    onClick = { launchBiometric() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(" 使用指纹验证")
                }
            }
        }
    }
    // One Argon2id derivation per attempt: a few taps must not queue up several
    // simultaneous ones, which is how this class of app dies on low-end phones.
    BusyOverlay(busy)
}

/**
 * Frame every hidden list shares: search box, the list itself, and a "new"
 * button that -- like everything created in here -- starts out hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenSpaceScaffold(
    viewModel: AppViewModel,
    kind: HiddenKind,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    fabLabel: String,
    onFab: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            CenterTopBar(
                title = kind.title,
                navigationIcon = {
                    IconButton(onClick = { viewModel.exitHiddenSpace(kind) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFab) {
                Icon(Icons.Filled.Add, contentDescription = fabLabel)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenPasswordList(viewModel: AppViewModel) {
    val entries by viewModel.hiddenEntries.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<VaultEntry?>(null) }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { e ->
            listOf(e.title, e.username, e.url, e.notes, e.category)
                .any { it.contains(query, true) }
        }
    }

    HiddenSpaceScaffold(
        viewModel = viewModel,
        kind = HiddenKind.PASSWORD,
        query = query,
        onQueryChange = { query = it },
        placeholder = "搜索隐藏密码…",
        fabLabel = "新建隐藏密码",
        onFab = { viewModel.openEditor(fromHidden = true) }
    ) {
        if (filtered.isEmpty()) {
            emptyStateItem(if (entries.isEmpty()) "隐藏空间是空的" else "没有匹配的结果")
        }
        items(filtered, key = { it.uuid }) { entry ->
            EntryCard(
                entry,
                onOpen = { viewModel.openDetail(entry.uuid, fromHidden = true) },
                onLongPress = { restoreTarget = entry }
            )
        }
    }

    HiddenToggleDialog(
        name = restoreTarget?.title?.ifBlank { "（无标题）" },
        hidden = true,
        noun = HiddenKind.PASSWORD.noun,
        onDismiss = { restoreTarget = null }
    ) {
        val uuid = restoreTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setEntryHidden(uuid, false)
            viewModel.showMessage("已移出隐藏空间")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenNoteList(viewModel: AppViewModel) {
    val notes by viewModel.hiddenNotes.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<Note?>(null) }

    val filtered = remember(notes, query) {
        if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, true) || it.preview().contains(query, true)
        }
    }

    HiddenSpaceScaffold(
        viewModel = viewModel,
        kind = HiddenKind.NOTE,
        query = query,
        onQueryChange = { query = it },
        placeholder = "搜索隐藏笔记…",
        fabLabel = "新建隐藏笔记",
        onFab = { viewModel.openNoteEditor(fromHidden = true) }
    ) {
        if (filtered.isEmpty()) {
            emptyStateItem(if (notes.isEmpty()) "隐藏空间是空的" else "没有匹配的结果")
        }
        items(filtered, key = { it.uuid }) { note ->
            NoteCard(
                note,
                onOpen = { viewModel.openNoteEditor(note.uuid, fromHidden = true) },
                onLongPress = { restoreTarget = note }
            )
        }
    }

    HiddenToggleDialog(
        name = restoreTarget?.title?.ifBlank { "（无标题）" },
        hidden = true,
        noun = HiddenKind.NOTE.noun,
        onDismiss = { restoreTarget = null }
    ) {
        val uuid = restoreTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setNoteHidden(uuid, false)
            viewModel.showMessage("已移出隐藏空间")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenCardList(viewModel: AppViewModel) {
    val cards by viewModel.hiddenCards.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<BankCard?>(null) }

    val filtered = remember(cards, query) {
        if (query.isBlank()) cards
        else cards.filter {
            it.nickname.contains(query, true) ||
                it.bankName.contains(query, true) ||
                it.cardHolder.contains(query, true) ||
                it.cardNumber.digitsOnly().contains(query.digitsOnly(), true)
        }
    }

    HiddenSpaceScaffold(
        viewModel = viewModel,
        kind = HiddenKind.CARD,
        query = query,
        onQueryChange = { query = it },
        placeholder = "搜索隐藏卡片…",
        fabLabel = "新建隐藏卡片",
        onFab = { viewModel.newCard(fromHidden = true) }
    ) {
        if (filtered.isEmpty()) {
            emptyStateItem(if (cards.isEmpty()) "隐藏空间是空的" else "没有匹配的结果")
        }
        items(filtered, key = { it.uuid }) { card ->
            CardCard(
                card,
                onOpen = { viewModel.openCard(card.uuid, fromHidden = true) },
                onLongPress = { restoreTarget = card }
            )
        }
    }

    HiddenToggleDialog(
        name = restoreTarget?.let {
            (it.bankName.ifBlank { it.nickname }).ifBlank { "（未命名卡片）" }
        },
        hidden = true,
        noun = HiddenKind.CARD.noun,
        onDismiss = { restoreTarget = null }
    ) {
        val uuid = restoreTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setCardHidden(uuid, false)
            viewModel.showMessage("已移出隐藏空间")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenOtpList(viewModel: AppViewModel) {
    val otps by viewModel.hiddenOtps.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<OtpEntry?>(null) }

    // One clock drives the whole list. A timer per row would drift apart and
    // cost a coroutine per account; sleeping to the next whole second keeps
    // every countdown ticking on the beat.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay(1000 - (current % 1000))
        }
    }

    val filtered = remember(otps, query) {
        if (query.isBlank()) otps
        else otps.filter {
            it.issuer.contains(query, true) || it.account.contains(query, true)
        }
    }

    HiddenSpaceScaffold(
        viewModel = viewModel,
        kind = HiddenKind.OTP,
        query = query,
        onQueryChange = { query = it },
        placeholder = "搜索隐藏验证器…",
        fabLabel = "添加隐藏验证器",
        onFab = { viewModel.openOtpEditor(fromHidden = true) }
    ) {
        if (filtered.isEmpty()) {
            emptyStateItem(if (otps.isEmpty()) "隐藏空间是空的" else "没有匹配的账号")
        }
        items(filtered, key = { it.uuid }) { entry ->
            OtpRow(
                entry = entry,
                now = now,
                onCopy = {
                    copyToClipboard(
                        context,
                        "验证码",
                        Totp.generate(entry, now),
                        OTP_CLIPBOARD_TTL_MS
                    )
                },
                onEdit = { viewModel.openOtpEditor(entry.uuid, fromHidden = true) },
                onLongPress = { restoreTarget = entry }
            )
        }
    }

    HiddenToggleDialog(
        name = restoreTarget?.displayTitle(),
        hidden = true,
        noun = HiddenKind.OTP.noun,
        onDismiss = { restoreTarget = null }
    ) {
        val uuid = restoreTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setOtpHidden(uuid, false)
            viewModel.showMessage("已移出隐藏空间")
        }
    }
}
