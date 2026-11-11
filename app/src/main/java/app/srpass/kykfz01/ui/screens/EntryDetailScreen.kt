package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CategoryPill
import app.srpass.kykfz01.ui.components.DetailRow
import app.srpass.kykfz01.ui.components.categoryColorPair
import app.srpass.kykfz01.ui.util.copyToClipboard
import app.srpass.kykfz01.vault.formatNoteTime
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(viewModel: AppViewModel, uuid: UUID, fromHidden: Boolean = false) {
    val entries by viewModel.entries.collectAsState()
    // Hidden entries never reach the public list, so a detail opened from the
    // hidden space has to look in both.
    val hiddenEntries by viewModel.hiddenEntries.collectAsState()
    val entry = remember(entries, hiddenEntries, uuid) {
        (entries + hiddenEntries).firstOrNull { it.uuid == uuid }
    }
    val locked by viewModel.locked.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reveal by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    /** Where "back" goes: the hidden space this was opened from, or the list. */
    fun leave() {
        if (fromHidden) viewModel.goHiddenSpace(HiddenKind.PASSWORD) else viewModel.goList()
    }

    BackHandler {
        if (showDelete) showDelete = false else leave()
    }

    if (entry == null) {
        // Locking clears the entry lists on purpose, yet this screen stays
        // mounted underneath the unlock overlay. Bailing out to the home list
        // here would throw the user out of the detail they were reading on
        // every lock; the entry is back as soon as the vault is unlocked and
        // the lists are refreshed again.
        if (!locked) {
            androidx.compose.runtime.LaunchedEffect(Unit) { leave() }
        }
        return
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "密码详情",
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openEditor(entry.uuid, fromHidden) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
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
            // ---- Header card: avatar + title + username + category ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (bg, fg) = categoryColorPair(entry.category)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (entry.title.firstOrNull() ?: '#').toString().uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = fg,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title.ifBlank { "（无标题）" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.username.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entry.category.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            CategoryPill(entry.category)
                        }
                    }
                }
            }

            // ---- Fields card: rows divided by separators --------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (entry.username.isNotBlank()) {
                        DetailRow("账号", entry.username, trailing = { CopyButton(entry.username, "账号") })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                    val shown = if (reveal) entry.password else "•".repeat(entry.password.length.coerceAtLeast(1))
                    DetailRow("密码", shown, trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (reveal) "隐藏" else "显示"
                                )
                            }
                            CopyButton(entry.password, "密码")
                        }
                    })
                    if (entry.url.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        DetailRow("网址", entry.url, trailing = { CopyButton(entry.url, "网址") })
                    }
                    if (entry.notes.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        DetailRow("备注", entry.notes)
                    }
                }
            }

            if (entry.updatedAt > 0) {
                Text(
                    "修改时间：${formatNoteTime(entry.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除条目") },
            text = { Text("确定要删除「${entry.title.ifBlank { "该条目" }}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        try {
                            viewModel.deleteEntry(uuid)
                            leave()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            viewModel.showMessage("删除失败：${e.message ?: "未知错误"}")
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CopyButton(text: String, label: String) {
    val context = LocalContext.current
    IconButton(
        onClick = { copyToClipboard(context, label, text) },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = "复制$label", modifier = Modifier.size(18.dp))
    }
}
