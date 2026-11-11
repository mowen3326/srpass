package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.vault.Note
import app.srpass.kykfz01.vault.formatNoteTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(viewModel: AppViewModel) {
    val notes by viewModel.notes.collectAsState()
    val hiddenEnabled by viewModel.hiddenSpaceEnabled.collectAsState()
    val scope = rememberCoroutineScope()

    // rememberSaveable: a rotation should not silently drop what the user typed.
    var query by rememberSaveable { mutableStateOf("") }
    var hideTarget by remember { mutableStateOf<Note?>(null) }
    val pull = rememberHiddenPull()
    val thresholdPx = rememberPullThresholdPx()

    val filtered = remember(notes, query) {
        if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, true) || it.preview().contains(query, true)
        }
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "笔记"
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openNoteEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建笔记")
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
                    placeholder = { Text("搜索笔记…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )

                NoteList(
                    notes = filtered,
                    listState = pull.listState,
                    emptyText = if (notes.isEmpty()) "还没有笔记，点击右下角添加" else "没有匹配的结果",
                    modifier = Modifier
                        .weight(1f)
                        // The list travels with the finger: the only feedback
                        // that a pull is in progress.
                        .offset { IntOffset(0, pull.offset.roundToInt()) }
                        .pullToOpenHiddenSpace(
                            pull = pull,
                            enabled = hiddenEnabled,
                            thresholdPx = thresholdPx
                        ) { viewModel.goHiddenSpace(HiddenKind.NOTE) },
                    onOpen = { viewModel.openNoteEditor(it.uuid) },
                    // Hiding without anywhere to reach the note again would read
                    // as losing it, so the gesture only exists while the hidden
                    // space does.
                    onLongPress = if (hiddenEnabled) { { hideTarget = it } } else null
                )
            }
            PullHint(progress = (pull.offset / thresholdPx).coerceIn(0f, 1f))
        }
    }

    HiddenToggleDialog(
        name = hideTarget?.title?.ifBlank { "（无标题）" },
        hidden = false,
        noun = "笔记",
        onDismiss = { hideTarget = null }
    ) {
        val uuid = hideTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setNoteHidden(uuid, true)
            viewModel.showMessage("已移动到隐藏空间")
        }
    }
}

/**
 * The list body, shared with the hidden space.
 *
 * The empty state is an *item* rather than a sibling Box on purpose: either way
 * the LazyColumn keeps owning the scroll, which is what lets the pull gesture
 * still be delivered when there is nothing to scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteList(
    notes: List<Note>,
    emptyText: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onOpen: (Note) -> Unit,
    /** Null turns the long press off entirely; see the caller for why. */
    onLongPress: ((Note) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        if (notes.isEmpty()) emptyStateItem(emptyText)
        items(notes, key = { it.uuid }) { note ->
            NoteCard(
                note,
                onOpen = { onOpen(note) },
                onLongPress = onLongPress?.let { handler -> { handler(note) } }
            )
        }
    }
}

/** Shared with the hidden space, where the long press moves a note back out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(note: Note, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    val clickModifier =
        if (onLongPress == null) {
            Modifier.clickable(onClick = onOpen)
        } else {
            Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
        }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Long press on the card itself is how a note moves in or out of
            // the hidden space -- no toolbar menu to discover.
            .then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Text(
                    note.title.ifBlank { "（无标题）" }
                )
            },
            supportingContent = {
                Column {
                    val p = note.preview()
                    if (p.isNotBlank()) {
                        Text(
                            p,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (note.updatedAt > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatNoteTime(note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        )
    }
}
