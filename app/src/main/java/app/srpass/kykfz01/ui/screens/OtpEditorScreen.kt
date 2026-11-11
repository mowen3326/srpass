package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.DiscardChangesDialog
import app.srpass.kykfz01.vault.Base32
import app.srpass.kykfz01.vault.OtpAlgorithm
import app.srpass.kykfz01.vault.OtpEntry
import app.srpass.kykfz01.vault.Totp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpEditorScreen(viewModel: AppViewModel, uuid: UUID?, fromHidden: Boolean = false) {
    val scope = rememberCoroutineScope()
    // Snapshot once: re-reading on every keystroke would re-query the vault for
    // no reason and fight the user's edits.
    val existing = remember(uuid) { uuid?.let { viewModel.getOtp(it) } }

    var issuer by rememberSaveable { mutableStateOf(existing?.issuer ?: "") }
    var account by rememberSaveable { mutableStateOf(existing?.account ?: "") }
    var secret by rememberSaveable { mutableStateOf(existing?.secret ?: "") }
    var digits by rememberSaveable { mutableStateOf(existing?.digits ?: 6) }
    var period by rememberSaveable { mutableStateOf(existing?.period ?: 30) }
    // Not exposed as a field: virtually every site issues SHA1, so the only
    // ways in are the entry being edited and a scanned QR. It is carried
    // through both, because dropping it silently downgrades a SHA256/512
    // account to SHA1 and every code it then generates is wrong.
    var algorithm by rememberSaveable { mutableStateOf(existing?.algorithm ?: OtpAlgorithm.SHA1) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    // 连点两下保存/删除会执行两次。
    var working by remember { mutableStateOf(false) }

    // A scan lands here: fill the form in and clear the hand-off so the next
    // visit to this screen starts empty.
    val scanned by viewModel.scannedOtp.collectAsState()
    LaunchedEffect(scanned) {
        scanned?.let { e ->
            issuer = e.issuer
            account = e.account
            secret = e.secret
            digits = e.digits
            period = e.period
            algorithm = e.algorithm
            viewModel.consumeScannedOtp()
        }
    }

    // Drives the live preview of the code the secret currently produces.
    var now by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - (now % 1000))
        }
    }

    /** Where "back" goes: the hidden space this was opened from, or the list. */
    fun leave() {
        if (fromHidden) viewModel.goHiddenSpace(HiddenKind.OTP) else viewModel.goOtpList()
    }

    // 只有真的动过才拦返回，否则进去看一眼再退出也要多点一次。
    val dirty = remember(issuer, account, secret, digits, period, algorithm) {
        if (existing == null) {
            issuer.isNotBlank() || account.isNotBlank() || secret.isNotBlank()
        } else {
            issuer != existing.issuer || account != existing.account ||
                secret != existing.secret || digits != existing.digits ||
                period != existing.period || algorithm != existing.algorithm
        }
    }

    /** 返回前先问一句，免得误触一下把刚填的密钥丢了。 */
    fun requestLeave() {
        if (dirty) showDiscard = true else leave()
    }

    BackHandler { requestLeave() }

    val trimmedSecret = remember(secret) { secret.filter { !it.isWhitespace() } }
    val secretOk = remember(trimmedSecret) {
        trimmedSecret.isEmpty() || Base32.isValid(trimmedSecret)
    }

    fun save() {
        if (working) return
        if (trimmedSecret.isEmpty()) {
            viewModel.showMessage("请填写密钥")
            return
        }
        if (!Base32.isValid(trimmedSecret)) {
            viewModel.showMessage("密钥格式不对，应为 Base32（仅 A-Z 与 2-7）")
            return
        }
        scope.launch {
            working = true
            val entry = OtpEntry(
                uuid = existing?.uuid ?: UUID.randomUUID(),
                issuer = issuer.trim(),
                account = account.trim(),
                secret = trimmedSecret.uppercase(),
                digits = digits,
                period = period,
                algorithm = algorithm,
                // Editing must not quietly un-hide an account: what was hidden
                // stays hidden, and one added inside the hidden space starts
                // out hidden.
                hidden = existing?.hidden ?: fromHidden
            )
            // 保存失败要说出来，否则用户以为改好了，其实原账号一点没变。
            try {
                if (existing == null) viewModel.addOtp(entry) else viewModel.updateOtp(entry)
                leave()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.showMessage("保存失败：${e.message ?: "未知错误"}")
            } finally {
                working = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = if (existing == null) "添加验证器" else "编辑验证器",
                navigationIcon = {
                    TextButton(onClick = { requestLeave() }) { Text("取消") }
                },
                actions = {
                    IconButton(onClick = ::save) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text("服务商（可选）") },
                placeholder = { Text("例如 GitHub") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("账号（可选）") },
                placeholder = { Text("例如 alice@example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("密钥 *") },
                placeholder = { Text("Base32 密钥，如 JBSWY3DPEHPK3PXP") },
                modifier = Modifier.fillMaxWidth(),
                isError = !secretOk,
                supportingText = {
                    Text(if (secretOk) "网站通常会同时给出一串备用密钥，填这里" else "含有非法字符")
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2
            )

            Text("位数", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = digits == 6,
                    onClick = { digits = 6 },
                    label = { Text("6 位") }
                )
                FilterChip(
                    selected = digits == 8,
                    onClick = { digits = 8 },
                    label = { Text("8 位") }
                )
            }

            Text("刷新周期", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = period == 30,
                    onClick = { period = 30 },
                    label = { Text("30 秒") }
                )
                FilterChip(
                    selected = period == 60,
                    onClick = { period = 60 },
                    label = { Text("60 秒") }
                )
            }

            // Live preview: proves the secret is right before saving, instead
            // of the user finding out at the next login.
            if (secretOk && trimmedSecret.isNotEmpty()) {
                // Same window key as the list: recompute only when the code
                // actually changes, not on every tick.
                val previewWindow = now / 1000 / period.coerceAtLeast(1)
                // algorithm 也要进 key：扫码导入一个 SHA256 账号时密钥可能和原来
                // 一样，少了它预览会继续按上一个算法算，看着像"密钥错了"。
                val preview = remember(trimmedSecret, digits, period, algorithm, previewWindow) {
                    Totp.generate(
                        OtpEntry(
                            uuid = UUID.randomUUID(),
                            secret = trimmedSecret,
                            digits = digits,
                            period = period,
                            algorithm = algorithm
                        ),
                        now
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "当前验证码",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            preview,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            OutlinedButton(
                // Tell the scanner which account is being edited, so coming back
                // re-opens *this* editor instead of a blank "add" form.
                onClick = { viewModel.openQrScanner(fromHidden, existing?.uuid) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text(" 扫描二维码")
            }

            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                Text("保存")
            }

            if (existing != null) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Text(" 删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除验证器？") },
            text = { Text("「${existing.displayTitle()}」将被移除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    if (working) return@TextButton
                    scope.launch {
                        working = true
                        try {
                            viewModel.deleteOtp(existing.uuid)
                            leave()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            viewModel.showMessage("删除失败：${e.message ?: "未知错误"}")
                        } finally {
                            working = false
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDismiss = { showDiscard = false },
            onDiscard = { showDiscard = false; leave() }
        )
    }
}
