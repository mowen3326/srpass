package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import app.srpass.kykfz01.ui.components.CenterTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.biometric.showBiometric
import app.srpass.kykfz01.ui.biometric.strongBiometricProblem
import app.srpass.kykfz01.ui.components.BusyOverlay
import app.srpass.kykfz01.ui.components.PasswordInputField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var biometricOn by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    var showPwDialog by remember { mutableStateOf(false) }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    // 本地闸门。只靠 viewModel.busy 不够：它是在 IO 线程里 emit 的，到界面生效
    // 要跨一次调度加一帧，连点两下时第二次照样进得来 —— 而它用的是已经被第一
    // 次轮换掉的旧密钥。
    var working by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsState()
    val blockScreenshots by viewModel.blockScreenshots.collectAsState()
    val hiddenSpaceEnabled by viewModel.hiddenSpaceEnabled.collectAsState()

    BackHandler {
        if (showPwDialog) showPwDialog = false else viewModel.goSettings()
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "安全",
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
            Text("生物识别", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (biometricOn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("生物识别已启用", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.disableBiometric()
                        biometricOn = false
                    }) { Text("关闭") }
                }
            } else {
                Button(
                    onClick = {
                        if (activity == null) {
                            viewModel.showMessage("当前环境不支持生物识别")
                            return@Button
                        }
                        // Check the sensor first. Creating the Keystore key
                        // throws when nothing is enrolled, so doing it before
                        // this check used to crash instead of explaining.
                        activity.strongBiometricProblem()?.let {
                            viewModel.showMessage(it)
                            return@Button
                        }
                        val cipher = viewModel.createBiometricEncryptCipher()
                        if (cipher == null) {
                            viewModel.showMessage("无法创建生物识别密钥，请确认已录入指纹或面容")
                            return@Button
                        }
                        viewModel.suppressLockOnLeave = true
                        activity.showBiometric(
                            cipher = cipher,
                            title = "启用生物识别",
                            subtitle = "验证指纹或面容以绑定生物识别",
                            onSuccess = { c ->
                                if (viewModel.enableBiometric(c)) {
                                    biometricOn = true
                                    viewModel.showMessage("已启用生物识别")
                                } else {
                                    viewModel.showMessage("启用失败")
                                }
                            },
                            onError = { viewModel.showMessage(it) },
                            onSettled = { viewModel.suppressLockOnLeave = false }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(" 启用生物识别")
                }
            }

            Text("屏幕安全", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("禁止截屏与录屏", modifier = Modifier.weight(1f))
                Switch(
                    checked = blockScreenshots,
                    onCheckedChange = viewModel::setBlockScreenshots
                )
            }
            Text(
                "开启后APP内将不可截屏和录屏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("隐藏空间", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("开启隐藏空间", modifier = Modifier.weight(1f))
                Switch(
                    checked = hiddenSpaceEnabled,
                    onCheckedChange = viewModel::setHiddenSpaceEnabled
                )
            }
            Text(
                "开启后密码、笔记、银行卡、验证器均可长按隐藏，在对应列表顶部下拉进入各自的隐藏空间，每次进入都要验证一次。\n再次关闭后已隐藏的内容不会删除，只是不再显示入口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("主密码", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { showPwDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("修改主密码")
            }
            OutlinedButton(onClick = viewModel::lock, modifier = Modifier.fillMaxWidth()) {
                Text("锁定密码库")
            }
        }
    }

    if (showPwDialog) {
        AlertDialog(
            onDismissRequest = { showPwDialog = false },
            title = { Text("修改主密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 先验证当前主密码：改主密码会让旧主密码当场失效，不验一道的
                    // 话，任何拿到已解锁手机的人都能把主人锁在外面。
                    PasswordInputField(currentPw, { currentPw = it }, label = "当前主密码")
                    PasswordInputField(newPw, { newPw = it }, label = "新主密码")
                    PasswordInputField(confirmPw, { confirmPw = it }, label = "确认新主密码")
                }
            },
            confirmButton = {
                // 连点会并发改主密码，第二次用的可能是已经失效的旧密钥，所以
                // 这里既要禁用按钮，也要在点击入口再拦一道（禁用是异步生效的）。
                TextButton(
                    enabled = !busy && !working,
                    onClick = {
                    if (working) return@TextButton
                    when {
                        newPw.length < 8 -> viewModel.showMessage("主密码至少 8 位")
                        newPw != confirmPw -> viewModel.showMessage("两次输入不一致")
                        else -> scope.launch {
                            working = true
                            try {
                                if (viewModel.changePassword(currentPw, newPw)) {
                                    showPwDialog = false
                                    currentPw = ""
                                    newPw = ""
                                    confirmPw = ""
                                    viewModel.showMessage("主密码已更新")
                                } else {
                                    viewModel.showMessage("当前主密码不正确，未做任何改动")
                                }
                            } finally {
                                working = false
                            }
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPwDialog = false }) { Text("取消") }
            }
        )
    }

    BusyOverlay(busy || working)
}
