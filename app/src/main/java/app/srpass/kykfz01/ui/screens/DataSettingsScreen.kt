package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import app.srpass.kykfz01.ui.components.CenterTopBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import app.srpass.kykfz01.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(viewModel: AppViewModel) {
    BackHandler { viewModel.goSettings() }

    var showImportConfirm by remember { mutableStateOf(false) }

    // Reset the lock-suppression if this screen is disposed while a picker is
    // still open (e.g. a configuration change), so we never get stuck.
    DisposableEffect(Unit) {
        onDispose { viewModel.suppressLockOnLeave = false }
    }

    // The SAF pickers are opened by the activity (see AppViewModel.safController
    // / MainActivity), which uses fixed, always-valid request codes -- so we
    // never exhaust the 16-bit request-code space.

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "导入 / 导出",
                navigationIcon = {
                    IconButton(onClick = viewModel::goSettings) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("导出备份", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "将密码库连同密钥信封一起导出为自包含备份 (.srpass)。文件已用主密钥加密，可安全存放于任意位置；在新手机上用原主密码或恢复密钥即可恢复全部内容。生物识别需在新设备重新启用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            viewModel.suppressLockOnLeave = true
                            viewModel.safController?.launchExport("SrPass-密码库备份.srpass")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Text(" 导出密码库")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("从备份导入", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "选择一个 .srpass 备份以恢复密码库。导入会先清空当前手机上的全部数据（密码、笔记、银行卡、验证器），再用备份完全替换。导入前建议先导出当前数据备份。导入后必须用原备份的主密码或恢复密钥解锁，并重新启用指纹/面容——备份本身已加密，没有密码谁都打不开。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showImportConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Text(" 从备份导入")
                    }
                }
            }

            Text(
                "数据仅保存在本机。备份文件 (.srpass) 已用主密钥加密，可安全存放于任意位置；但它是 SrPass 自有的格式，不能直接用其它 KeePass 客户端打开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入将清空当前数据") },
            text = {
                Text(
                    "导入备份会用所选备份的内容完全替换当前手机上的所有数据" +
                        "（密码、笔记、银行卡、验证器），且不可撤销。\n\n" +
                        "建议先点“导出密码库”备份现有数据。确定继续吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    viewModel.suppressLockOnLeave = true
                    viewModel.safController?.launchImport()
                }) { Text("继续导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }
}
