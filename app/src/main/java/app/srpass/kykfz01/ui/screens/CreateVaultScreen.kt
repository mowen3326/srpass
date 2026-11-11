package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.components.BusyOverlay
import app.srpass.kykfz01.ui.components.PasswordInputField
import app.srpass.kykfz01.ui.util.copyToClipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVaultScreen(viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 跨配置变更保留：旋转屏幕时正在输入的密码不该被清空。
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    // 恢复码与"到哪一步"都在 ViewModel 里：它是建库后唯一一次显示的机会，
    // composable 被重建（旋转）之后必须还在。
    val recoveryKey = viewModel.pendingRecoveryKey

    val busy by viewModel.busy.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.height(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("创建密码库", style = MaterialTheme.typography.headlineSmall)
            Text(
                "设置一个主密码，它将用于加密你所有的密码。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (recoveryKey == null) {
                PasswordInputField(
                    value = password,
                    onValueChange = { password = it },
                    label = "主密码"
                )
                PasswordInputField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "确认主密码"
                )
                Button(
                    onClick = {
                        when {
                            password.length < 8 -> viewModel.showMessage("主密码至少 8 位")
                            password != confirm -> viewModel.showMessage("两次输入不一致")
                            else -> scope.launch {
                                try {
                                    viewModel.createVault(password)
                                    // 主密码用完即弃，不再留在内存里。
                                    password = ""
                                    confirm = ""
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    // 离开页面/旋转会取消协程。这不是建库失败，
                                    // 当成失败会误报，还可能把已落盘的凭证销毁。
                                    throw e
                                } catch (e: Exception) {
                                    viewModel.showMessage("创建失败：${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("创建")
                }
            } else {
                Text(
                    "恢复码（请立即抄写并妥善保存）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "这是你忘记主密码时唯一的找回方式，应用不会保存它。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    recoveryKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = { copyToClipboard(context, "恢复码", recoveryKey) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制恢复码")
                }
                Button(
                    onClick = viewModel::enterVaultAfterRecoveryKeySaved,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("我已安全保存，进入密码库")
                }
            }
        }
    }
    BusyOverlay(busy)
}
