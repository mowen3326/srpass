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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.components.BusyOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    // 本地闸门：viewModel.busy 从 IO 线程 emit 到界面生效有一帧的窗口，连点两
    // 下会并发跑两次密钥派生。
    var working by remember { mutableStateOf(false) }
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
            Spacer(Modifier.height(40.dp))
            Icon(
                Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.height(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("使用恢复码", style = MaterialTheme.typography.headlineSmall)
            Text(
                "输入创建密码库时保存的恢复码以解锁。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("恢复码") },
                singleLine = false,
                minLines = 2
            )
            Button(
                onClick = {
                    // 解锁是一次完整的密钥派生，连点会并发跑两遍
                    if (working) return@Button
                    scope.launch {
                        working = true
                        try {
                            if (viewModel.unlockWithRecoveryKey(input.trim())) {
                                viewModel.route()
                            } else {
                                viewModel.showMessage("恢复码无效")
                            }
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !busy && !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (working) "解锁中…" else "解锁")
            }
            // 不能用 route()：它在锁定时会让人停在 Recovery，于是「我只是想起
            // 主密码了」没有回头路，只剩退出应用。
            TextButton(onClick = viewModel::goUnlock) {
                Text("想起主密码了，返回")
            }
        }
    }
    BusyOverlay(busy || working)
}
