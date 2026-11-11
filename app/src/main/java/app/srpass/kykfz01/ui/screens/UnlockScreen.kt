package app.srpass.kykfz01.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.biometric.showBiometric
import app.srpass.kykfz01.ui.biometric.strongBiometricProblem
import app.srpass.kykfz01.ui.components.BusyOverlay
import app.srpass.kykfz01.ui.components.PasswordInputField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    // Re-read on every entry: an envelope can disappear between visits, for
    // example when a new fingerprint invalidated the Keystore key and the
    // previous attempt to build a cipher dropped it.
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    LaunchedEffect(Unit) { biometricEnabled = viewModel.isBiometricEnabled() }

    // 本地闸门：viewModel.busy 从 IO 线程 emit 到界面生效有一帧窗口，连点两下
    // 会并发跑两次 Argon2id 派生，或弹出两个系统指纹框。
    var pwBusy by remember { mutableStateOf(false) }
    var bioBusy by remember { mutableStateOf(false) }

    // Starts a biometric prompt. Shared by the button and the auto-prompt below.
    fun startBiometric() {
        // 进页面自动弹一次，这时再点按钮就是第二个系统框了
        if (bioBusy) return
        bioBusy = true
        if (activity == null) {
            bioBusy = false
            viewModel.showMessage("当前环境不支持生物识别")
            return
        }
        // Ask the sensor before building the cipher: if the Keystore key was
        // invalidated by a new fingerprint, this reports it and disables the
        // button instead of letting the call throw.
        activity.strongBiometricProblem()?.let {
            bioBusy = false
            viewModel.showMessage(it)
            return
        }
        val cipher = viewModel.createBiometricDecryptCipher()
        if (cipher == null) {
            bioBusy = false
            biometricEnabled = false
            viewModel.showMessage("生物识别已失效，请用主密码解锁后重新启用")
            return
        }
        // The system dialog can fire onUserLeaveHint; without this the vault
        // would lock itself mid-authentication.
        viewModel.suppressLockOnLeave = true
        activity.showBiometric(
            cipher = cipher,
            title = "生物识别解锁",
            subtitle = "验证指纹或面容以解锁密码库",
            onSuccess = { c ->
                scope.launch {
                    if (viewModel.unlockWithBiometric(c)) viewModel.route()
                    else viewModel.showMessage("解锁失败")
                }
            },
            onError = { viewModel.showMessage(it) },
            onSettled = {
                bioBusy = false
                viewModel.suppressLockOnLeave = false
            }
        )
    }

    // 自动弹指纹框：进解锁页弹一次，并且每次从后台回到前台都再弹一次。
    //
    // 它必须跟着「是否在前台」（ViewModel 里，跨组合存活）走，不能跟着组合走：
    // 息屏时是 onStop 里的 lock() 把解锁页推出来的，那一刻界面已经在后台，弹出
    // 去的系统指纹框既看不见也收不到回调（BiometricPrompt 不保证回调 onSettled），
    // bioBusy 就永远卡在 true —— 指纹按钮变灰、自动弹框也不再触发，只剩密码一条
    // 路。而解锁页自己的组合正是那一刻才建立，根本收不到此前那次 ON_STOP，所以
    // 在界面里用 remember 记「是否离开过」必然记不住。回到前台时先松开闸门再弹。
    LaunchedEffect(viewModel.uiInForeground) {
        if (!viewModel.uiInForeground) return@LaunchedEffect
        bioBusy = false
        viewModel.suppressLockOnLeave = false
        if (viewModel.isBiometricEnabled()) startBiometric()
    }

    var password by remember { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()

    // While locked, the screen the user came from is still composed underneath
    // this one and its BackHandler is still registered. Registering here --
    // after the content layer -- puts us first in line, so Back cannot drive a
    // screen that is not supposed to be reachable.
    BackHandler { /* no-op: the vault is locked */ }

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
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.height(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("解锁 SrPass", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            PasswordInputField(
                value = password,
                onValueChange = { password = it },
                label = "主密码"
            )
            Button(
                onClick = {
                    if (pwBusy) return@Button
                    scope.launch {
                        pwBusy = true
                        try {
                            if (viewModel.unlockWithPassword(password)) {
                                viewModel.route()
                            } else {
                                viewModel.showMessage("主密码错误")
                            }
                        } finally {
                            pwBusy = false
                        }
                    }
                },
                enabled = !busy && !pwBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pwBusy) "解锁中…" else "解锁")
            }

            if (biometricEnabled) {
                OutlinedButton(
                    onClick = { startBiometric() },
                    enabled = !bioBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(" 使用指纹")
                }
            }

            TextButton(onClick = viewModel::openRecovery) {
                Text("忘记密码？使用恢复码")
            }
        }
    }
    // 这里刻意不含 bioBusy：指纹框是系统 UI，本来就盖在页面上，我们再蒙一层
    // 转圈只会让「框没了但闸门卡住」从「少一个入口」升级成「整页不可用」。
    BusyOverlay(busy || pwBusy)
}
