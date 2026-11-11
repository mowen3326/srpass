package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CategoryPill
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.categoryColorPair
import app.srpass.kykfz01.vault.VaultEntry
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultListScreen(viewModel: AppViewModel) {
    val entries by viewModel.entries.collectAsState()
    val hiddenEnabled by viewModel.hiddenSpaceEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    // rememberSaveable: a rotation should not silently drop what the user typed
    // or the category they filtered down to.
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var hideTarget by remember { mutableStateOf<VaultEntry?>(null) }
    val pull = rememberHiddenPull()
    val thresholdPx = rememberPullThresholdPx()

    val categories = remember(entries) {
        entries.mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
    }

    val filtered = remember(entries, query, selectedCategory) {
        entries.filter { e ->
            val q = query.trim().lowercase()
            val matchQuery = q.isBlank() || listOf(e.title, e.username, e.url, e.notes, e.category)
                .any { it.lowercase().contains(q) }
            val matchCat = selectedCategory == null || e.category == selectedCategory
            matchQuery && matchCat
        }
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "我的密码库"
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
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
                    placeholder = { Text("搜索标题、账号、网址…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("全部") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                        // The list travels with the finger: the only feedback
                        // that a pull is in progress.
                        .offset { IntOffset(0, pull.offset.roundToInt()) }
                        .pullToOpenHiddenSpace(
                            pull = pull,
                            enabled = hiddenEnabled,
                            thresholdPx = thresholdPx
                        ) { viewModel.goHiddenSpace(HiddenKind.PASSWORD) },
                    state = pull.listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // An item, not a sibling Box: the list keeps owning the
                    // scroll even when it is empty, so the pull still arrives --
                    // and it must, or a list that does not pull would give away
                    // that it has nothing hidden.
                    if (filtered.isEmpty()) {
                        emptyStateItem(
                            if (entries.isEmpty()) "还没有任何密码，点击右下角添加" else "没有匹配的结果"
                        )
                    }
                    items(filtered, key = { it.uuid }) { entry ->
                        EntryCard(
                            entry,
                            onOpen = { viewModel.openDetail(entry.uuid) },
                            onLongPress = if (hiddenEnabled) { { hideTarget = entry } } else null
                        )
                    }
                }
            }
            PullHint(progress = (pull.offset / thresholdPx).coerceIn(0f, 1f))
        }
    }

    HiddenToggleDialog(
        name = hideTarget?.title?.ifBlank { "（无标题）" },
        hidden = false,
        noun = "密码",
        onDismiss = { hideTarget = null }
    ) {
        val uuid = hideTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setEntryHidden(uuid, true)
            viewModel.showMessage("已移动到隐藏空间")
        }
    }
}

/** Shared with the hidden space, where the long press moves an entry back out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryCard(entry: VaultEntry, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    val (bg, fg) = categoryColorPair(entry.category)
    val clickModifier =
        if (onLongPress == null) {
            Modifier.clickable(onClick = onOpen)
        } else {
            Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
        }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        ListItem(
            modifier = Modifier.padding(vertical = 4.dp),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (entry.title.firstOrNull() ?: '#').toString().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = fg,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            headlineContent = {
                Text(
                    entry.title.ifBlank { "（无标题）" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                val sub = buildList {
                    if (entry.username.isNotBlank()) add(entry.username)
                    if (entry.url.isNotBlank()) add(entry.url)
                }.joinToString("  ·  ")
                val text = sub.ifBlank { entry.category }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                if (entry.category.isNotBlank()) CategoryPill(entry.category)
                else Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
