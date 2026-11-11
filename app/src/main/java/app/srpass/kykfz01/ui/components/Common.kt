package app.srpass.kykfz01.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Outlined text field whose content can be revealed via a trailing eye icon. */
@Composable
fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "密码",
    readOnly: Boolean = false,
    singleLine: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        readOnly = readOnly,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "隐藏" else "显示"
                )
            }
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
    )
}

/**
 * A dimming overlay with a spinner, shown during long-running crypto work.
 *
 * It swallows touches while visible. Without that, tapping "解锁" again starts
 * a second Argon2id derivation -- at 64 MiB each, a few taps are enough to
 * exhaust the heap on a low-end device.
 */
@Composable
fun BusyOverlay(show: Boolean) {
    if (show) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/** A single labelled field row: label + value on the left, actions on the right. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null
) {
    if (value.isBlank() && trailing == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        trailing?.invoke()
    }
}

/** Maps a category name to a stable (container, onContainer) colour pair. */
@Composable
fun categoryColorPair(category: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val pairs = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    if (category.isBlank()) return scheme.surfaceVariant to scheme.onSurfaceVariant
    val idx = kotlin.math.abs(category.hashCode()) % pairs.size
    return pairs[idx]
}

/** Small rounded pill showing a category, tinted by the category colour. */
@Composable
fun CategoryPill(category: String) {
    if (category.isBlank()) return
    val (bg, fg) = categoryColorPair(category)
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 对话框里的反馈文字。
 *
 * 必须用这个而不是全局 Snackbar：Snackbar 画在屏幕中部，AlertDialog 连遮罩
 * 一起盖在它上面，弹窗里触发的提示等于没弹。
 */
@Composable
fun DialogHint(text: String?, ok: Boolean) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

/**
 * 「放弃修改？」确认框，四个编辑页共用。
 *
 * 返回键、返回箭头、「取消」都先经过它。没有这层确认时，误触一下返回就把刚
 * 输完的一整条密码/笔记丢掉，而且什么提示都没有 —— 新建尤其明显，退出后
 * 库里什么都不会留下。
 */
@Composable
fun DiscardChangesDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("放弃修改？") },
        text = { Text("当前内容还没有保存，退出后会丢失。") },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("放弃", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续编辑") } }
    )
}
