package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.util.copyToClipboard
import app.srpass.kykfz01.vault.OtpEntry
import app.srpass.kykfz01.vault.Totp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A TOTP code is useless after its window closes, so the copy expires with it. */
private const val OTP_CLIPBOARD_TTL_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpListScreen(viewModel: AppViewModel) {
    val otps by viewModel.otps.collectAsState()
    val hiddenEnabled by viewModel.hiddenSpaceEnabled.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var hideTarget by remember { mutableStateOf<OtpEntry?>(null) }
    val pull = rememberHiddenPull()
    val thresholdPx = rememberPullThresholdPx()

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

    Scaffold(
        topBar = { CenterTopBar(title = "验证器") },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openOtpEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加验证器")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("搜索服务商 / 账号…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .offset { IntOffset(0, pull.offset.roundToInt()) }
                        .pullToOpenHiddenSpace(
                            pull = pull,
                            enabled = hiddenEnabled,
                            thresholdPx = thresholdPx
                        ) { viewModel.goHiddenSpace(HiddenKind.OTP) },
                    state = pull.listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // An item, not a sibling Box, so the pull still arrives when
                    // there is nothing to scroll.
                    if (filtered.isEmpty()) {
                        emptyStateItem(
                            if (otps.isEmpty()) "还没有验证器账号\n点右下角添加，或扫码导入"
                            else "没有匹配的账号"
                        )
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
                            onEdit = { viewModel.openOtpEditor(entry.uuid) },
                            // Tapping copies the code, so hiding rides on the
                            // long press instead of stealing the tap.
                            onLongPress = if (hiddenEnabled) { { hideTarget = entry } } else null
                        )
                    }
                }
            }
            PullHint(progress = (pull.offset / thresholdPx).coerceIn(0f, 1f))
        }
    }

    HiddenToggleDialog(
        name = hideTarget?.displayTitle(),
        hidden = false,
        noun = "验证器",
        onDismiss = { hideTarget = null }
    ) {
        val uuid = hideTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setOtpHidden(uuid, true)
            viewModel.showMessage("已移动到隐藏空间")
        }
    }
}

/** Shared with the hidden space, where the long press moves an account back out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OtpRow(
    entry: OtpEntry,
    now: Long,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val remaining = Totp.remainingSeconds(entry, now)
    val progress = Totp.progress(entry, now)
    // A code only changes when its window rolls over, so derive it once per
    // window instead of recomputing the HMAC on every tick (29 of every 30
    // computations were being thrown away).
    val window = now / 1000 / entry.period.coerceAtLeast(1)
    val code = remember(entry, window) { Totp.generate(entry, now) }
    // Last few seconds: warn so the user does not type a code that is about
    // to rotate out from under them.
    val expiring = remaining <= 5
    val accent =
        if (expiring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongPress == null) {
                        Modifier.clickable(onClick = onCopy)
                    } else {
                        Modifier.combinedClickable(onClick = onCopy, onLongClick = onLongPress)
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Countdown ring: how much of the current window is left.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 3.dp
                )
                Text(
                    remaining.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (expiring) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    ),
                    color = if (expiring) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制验证码")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
        }
    }
}
