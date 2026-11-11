package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.vault.BankCard
import app.srpass.kykfz01.vault.CardType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsListScreen(viewModel: AppViewModel) {
    val cards by viewModel.cards.collectAsState()
    val hiddenEnabled by viewModel.hiddenSpaceEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var hideTarget by remember { mutableStateOf<BankCard?>(null) }
    val pull = rememberHiddenPull()
    val thresholdPx = rememberPullThresholdPx()

    val filtered = remember(cards, query) {
        if (query.isBlank()) cards
        else cards.filter {
            it.nickname.contains(query, true) ||
                it.bankName.contains(query, true) ||
                it.cardHolder.contains(query, true) ||
                // 两边都去掉非数字再比：卡号是带空格存的，而用户习惯照着卡面上
                // 带空格的样子搜，只处理一边搜不出来。
                it.cardNumber.digitsOnly().contains(query.digitsOnly(), true)
        }
    }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "银行卡"
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newCard() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建卡片")
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
                    placeholder = { Text("搜索银行 / 卡号 / 备注名…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .offset { IntOffset(0, pull.offset.roundToInt()) }
                        .pullToOpenHiddenSpace(
                            pull = pull,
                            enabled = hiddenEnabled,
                            thresholdPx = thresholdPx
                        ) { viewModel.goHiddenSpace(HiddenKind.CARD) },
                    state = pull.listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // An item, not a sibling Box, so the pull still arrives when
                    // there is nothing to scroll.
                    if (filtered.isEmpty()) {
                        emptyStateItem(
                            if (cards.isEmpty()) "还没有银行卡，点击右下角添加" else "没有匹配的结果"
                        )
                    }
                    items(filtered, key = { it.uuid }) { card ->
                        CardCard(
                            card,
                            onOpen = { viewModel.openCard(card.uuid) },
                            onLongPress = if (hiddenEnabled) { { hideTarget = card } } else null
                        )
                    }
                }
            }
            PullHint(progress = (pull.offset / thresholdPx).coerceIn(0f, 1f))
        }
    }

    HiddenToggleDialog(
        name = hideTarget?.let { (it.bankName.ifBlank { it.nickname }).ifBlank { "（未命名卡片）" } },
        hidden = false,
        noun = "卡片",
        onDismiss = { hideTarget = null }
    ) {
        val uuid = hideTarget?.uuid ?: return@HiddenToggleDialog
        scope.launch {
            viewModel.setCardHidden(uuid, true)
            viewModel.showMessage("已移动到隐藏空间")
        }
    }
}

/** Shared with the hidden space, where the long press moves a card back out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CardCard(card: BankCard, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
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
            leadingContent = {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        (card.bankName.ifBlank { card.nickname }).ifBlank { "（未命名卡片）" },
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TypePill(card.type)
                }
            },
            supportingContent = {
                val masked = maskCard(card.cardNumber)
                if (masked.isNotBlank()) {
                    Text(
                        masked,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun TypePill(type: CardType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = if (type == CardType.CREDIT) "信用卡" else "储蓄卡",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Shows the first four and last four digits, dotting out the middle. */
private fun maskCard(number: String): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length <= 4) return number
    val first = digits.take(4)
    val last = digits.takeLast(4)
    return if (digits.length >= 12) "$first •••• •••• $last" else "$first •••• $last"
}
