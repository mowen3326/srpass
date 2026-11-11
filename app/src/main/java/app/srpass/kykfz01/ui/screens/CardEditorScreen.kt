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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.DiscardChangesDialog
import app.srpass.kykfz01.ui.components.PasswordInputField
import app.srpass.kykfz01.vault.BankCard
import app.srpass.kykfz01.vault.CardType
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(viewModel: AppViewModel, uuid: UUID?, fromHidden: Boolean = false) {
    val scope = rememberCoroutineScope()
    val existing = remember(uuid) { uuid?.let { viewModel.getCard(it) } }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: CardType.DEBIT) }
    var nickname by rememberSaveable { mutableStateOf(existing?.nickname ?: "") }
    var bankName by rememberSaveable { mutableStateOf(existing?.bankName ?: "") }
    var cardNumber by rememberSaveable { mutableStateOf(existing?.cardNumber ?: "") }
    var cardHolder by rememberSaveable { mutableStateOf(existing?.cardHolder ?: "") }
    var expiry by rememberSaveable { mutableStateOf(existing?.expiry ?: "") }
    var cvv by rememberSaveable { mutableStateOf(existing?.cvv ?: "") }
    var pin6 by rememberSaveable { mutableStateOf(existing?.pin6 ?: "") }
    var olbAccount by rememberSaveable { mutableStateOf(existing?.olbAccount ?: "") }
    var olbPassword by rememberSaveable { mutableStateOf(existing?.olbPassword ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    // 连点两下保存/删除会执行两次；落库前把入口锁住。
    var working by remember { mutableStateOf(false) }

    // 只有真的改过才拦返回，否则进去看一眼再退出也要多点一次。
    val dirty = remember(
        type, nickname, bankName, cardNumber, cardHolder, expiry,
        cvv, pin6, olbAccount, olbPassword, notes
    ) {
        if (existing == null) {
            listOf(
                nickname, bankName, cardNumber, cardHolder, expiry,
                cvv, pin6, olbAccount, olbPassword, notes
            ).any { it.isNotBlank() } || type != CardType.DEBIT
        } else {
            type != existing.type || nickname != existing.nickname ||
                bankName != existing.bankName || cardNumber != existing.cardNumber ||
                cardHolder != existing.cardHolder || expiry != existing.expiry ||
                cvv != existing.cvv || pin6 != existing.pin6 ||
                olbAccount != existing.olbAccount || olbPassword != existing.olbPassword ||
                notes != existing.notes
        }
    }

    /** Where "back" goes: the hidden space this was opened from, or the list. */
    fun leave() {
        if (fromHidden) viewModel.goHiddenSpace(HiddenKind.CARD) else viewModel.goCardsList()
    }

    /** 返回前先问一句，避免误触一下就把刚输完的卡信息全丢掉。 */
    fun requestLeave() {
        if (dirty) showDiscard = true else leave()
    }

    BackHandler { requestLeave() }

    fun save() {
        if (working) return
        if (listOf(
                nickname, bankName, cardNumber, cardHolder, expiry,
                cvv, pin6, olbAccount, olbPassword, notes
            ).all { it.isBlank() }
        ) {
            viewModel.showMessage("请至少填写一项")
            return
        }
        val card = BankCard(
            uuid = existing?.uuid ?: UUID.randomUUID(),
            type = type,
            nickname = nickname.trim(),
            bankName = bankName.trim(),
            cardNumber = cardNumber.trim(),
            cardHolder = cardHolder.trim(),
            expiry = expiry.trim(),
            cvv = cvv.trim(),
            pin6 = pin6.trim(),
            olbAccount = olbAccount.trim(),
            olbPassword = olbPassword.trim(),
            notes = notes.trim(),
            // Editing must not quietly un-hide a card: what was hidden stays
            // hidden, and one created inside the hidden space starts out hidden.
            hidden = existing?.hidden ?: fromHidden
        )
        scope.launch {
            working = true
            // 保存失败一定要说出来：静默失败时用户以为存好了，退出后修改全没了。
            try {
                if (existing == null) viewModel.addCard(card) else viewModel.updateCard(card)
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
                title = if (uuid == null) "新建卡片" else "编辑卡片",
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = { save() }) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "卡片类型",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = type == CardType.CREDIT,
                    onClick = { type = CardType.CREDIT },
                    label = { Text("信用卡") }
                )
                FilterChip(
                    selected = type == CardType.DEBIT,
                    onClick = { type = CardType.DEBIT },
                    label = { Text("储蓄卡") }
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(),
                label = { Text("备注名（如：工资卡）") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(bankName, { bankName = it }, Modifier.fillMaxWidth(),
                label = { Text("银行名称") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(cardNumber, { cardNumber = it }, Modifier.fillMaxWidth(),
                label = { Text("卡号") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(cardHolder, { cardHolder = it }, Modifier.fillMaxWidth(),
                label = { Text("持卡人 / 户名") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(expiry, { expiry = it }, Modifier.fillMaxWidth(),
                label = { Text("有效期（MM/YY）") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            PasswordInputField(cvv, { cvv = it }, label = "安全码")
            Spacer(Modifier.height(12.dp))
            PasswordInputField(pin6, { pin6 = it }, label = "6 位密码")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(olbAccount, { olbAccount = it }, Modifier.fillMaxWidth(),
                label = { Text("网银账号") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            PasswordInputField(olbPassword, { olbPassword = it }, label = "网银密码")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                singleLine = false,
                minLines = 3
            )
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除卡片") },
            text = { Text("确定要删除这张卡片吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    if (working) return@TextButton
                    scope.launch {
                        working = true
                        try {
                            viewModel.deleteCard(uuid ?: return@launch)
                            leave()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            viewModel.showMessage("删除失败：${e.message ?: "未知错误"}")
                        } finally {
                            working = false
                        }
                    }
                }) { Text("删除") }
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
