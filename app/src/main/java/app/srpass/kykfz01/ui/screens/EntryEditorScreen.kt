package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import app.srpass.kykfz01.ui.components.CenterTopBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.DiscardChangesDialog
import app.srpass.kykfz01.ui.components.PasswordGeneratorSheet
import app.srpass.kykfz01.ui.components.PasswordInputField
import app.srpass.kykfz01.ui.util.Categories
import app.srpass.kykfz01.vault.VaultEntry
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(viewModel: AppViewModel, uuid: UUID?, fromHidden: Boolean = false) {
    val scope = rememberCoroutineScope()
    // Snapshot once: this queries the database, which would otherwise re-run on
    // every keystroke-driven recomposition.
    val existing = remember(uuid) { uuid?.let { viewModel.getEntry(it) } }

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.url ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category?.takeIf { it.isNotBlank() } ?: Categories.DEFAULT.first()) }

    var showGen by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    // 连点两下保存会写进两条：第一次点下去到真正落库之间把入口锁住。
    var saving by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val originalCategory = remember(uuid) {
        existing?.category?.takeIf { it.isNotBlank() } ?: Categories.DEFAULT.first()
    }
    // 只有真的改过才拦返回，否则进去看一眼再退出也要多点一次。
    val dirty = remember(title, username, password, url, notes, category) {
        if (existing == null) {
            title.isNotBlank() || username.isNotBlank() || password.isNotEmpty() ||
                url.isNotBlank() || notes.isNotBlank()
        } else {
            title != existing.title || username != existing.username ||
                password != existing.password || url != existing.url ||
                notes != existing.notes || category != originalCategory
        }
    }

    /** Where "back" goes: the hidden space this was opened from, or the list. */
    fun leave() {
        if (fromHidden) viewModel.goHiddenSpace(HiddenKind.PASSWORD) else viewModel.goList()
    }

    /** 返回前先问一句，避免误触一下就把刚输完的内容全丢掉。 */
    fun requestLeave() {
        if (dirty) showDiscard = true else leave()
    }

    fun save() {
        if (saving) return
        if (title.isBlank() && username.isBlank()) {
            viewModel.showMessage("请至少填写标题或账号")
            return
        }
        val entry = VaultEntry(
            uuid = existing?.uuid ?: UUID.randomUUID(),
            title = title.trim(),
            username = username.trim(),
            password = password,
            url = url.trim(),
            notes = notes.trim(),
            category = category,
            // Editing must not quietly un-hide an entry: what
            // was hidden stays hidden, and something created
            // inside the hidden space starts out hidden.
            hidden = existing?.hidden ?: fromHidden
        )
        scope.launch {
            saving = true
            try {
                if (existing == null) viewModel.addEntry(entry)
                else viewModel.updateEntry(entry)
                leave()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.showMessage("保存失败：${e.message ?: "未知错误"}")
            } finally {
                saving = false
            }
        }
    }

    BackHandler {
        if (showGen) showGen = false else requestLeave()
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = if (uuid == null) "新建密码" else "编辑密码",
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PasswordInputField(password, { password = it }, label = "密码")
                OutlinedButton(onClick = { showGen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(" 生成随机密码")
                }
            }

            CategoryDropdown(category, { category = it })
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("网址") }, singleLine = true)
            OutlinedTextField(
                notes, { notes = it },
                Modifier.fillMaxWidth(),
                label = { Text("备注") },
                singleLine = false,
                minLines = 3
            )
        }
    }

    if (showGen) {
        PasswordGeneratorSheet(
            sheetState = sheetState,
            onDismiss = { showGen = false },
            onUse = { password = it; showGen = false }
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDismiss = { showDiscard = false },
            onDiscard = { showDiscard = false; leave() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: String, onSelected: (String) -> Unit) {
    val options = remember(selected) { (Categories.DEFAULT + selected).distinct() }
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelected(opt); expanded = false }
                )
            }
        }
    }
}
