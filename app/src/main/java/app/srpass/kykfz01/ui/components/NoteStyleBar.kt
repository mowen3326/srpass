package app.srpass.kykfz01.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.ui.util.sysFontFamily
import app.srpass.kykfz01.vault.Span
import app.srpass.kykfz01.vault.SysFont

/** 底部工具栏里可展开的二级面板。 */
private enum class StylePanel { FONT, COLOR }

/** 字号档位：null 表示跟随正文（16sp）。 */
private val FONT_SIZES = listOf(
    null to "正文", 14 to "小", 18 to "大", 22 to "更大", 28 to "标题"
)

/** 文字颜色：null = 跟随主题。 */
private val TEXT_COLORS = listOf(
    null to "默认",
    0xFF000000L to "黑", 0xFF757575L to "灰", 0xFFD32F2FL to "红",
    0xFFEF6C00L to "橙", 0xFF2E7D32L to "绿", 0xFF1565C0L to "蓝", 0xFF6A1B9AL to "紫"
)

/** 高亮背景色：null = 无高亮。 */
private val HIGHLIGHTS = listOf(
    null to "无",
    0xFFFFF59DL to "黄", 0xFFB9F6CAL to "绿",
    0xFFB3E5FCL to "蓝", 0xFFF8BBD0L to "粉", 0xFFFFE0B2L to "橙"
)

/** 段落级别：0 正文，1..3 标题。 */
private val HEADINGS = listOf(
    0 to "正文", 1 to "标题1", 2 to "标题2", 3 to "标题3"
)

/**
 * 笔记编辑器的底部控制栏。
 *
 * 二级面板是**内嵌展开**的而不是 ModalBottomSheet：BottomSheet 会收起键盘并
 * 抢走输入框焦点，每改一次颜色都要重新点回正文，体验很差。
 *
 * 键盘/手势条的避让**不由本控件负责**：调用方把"正文 + 本栏"放进一个竖向布局
 * （正文 `weight(1f)`，本栏排在最后），再把整块区域套上 `ime ∪ navigationBars`
 * 的 inset padding。这样本栏天然贴在键盘上沿、中间没有缝，正文也只占剩下的
 * 高度 —— 不用测量高度，更不会出现"面板盖住正文"。
 *
 * [onPanelOpen] 在二级面板展开/收起时回调，调用方据此把正在编辑的那块顶回可视区。
 */
@Composable
fun NoteStyleBar(
    /** 光标/选区处当前的属性，用来点亮按钮。 */
    current: Span,
    /** 当前段落级别，用来点亮"标题"。 */
    currentLevel: Int = 0,
    onPickLevel: (Int) -> Unit = {},
    /** false 时（没有可作用的文本块）样式按钮全部置灰。 */
    styleEnabled: Boolean,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrike: () -> Unit,
    onPickFontSize: (Int?) -> Unit,
    onPickFont: (String?) -> Unit,
    onPickColor: (Long?) -> Unit,
    onPickHighlight: (Long?) -> Unit,
    onInsertImage: () -> Unit,
    /**
     * 粘贴剪贴板里的图片（连同原文顺序一起还原）。
     *
     * 必须有这个显式入口：输入法的粘贴键是直接 commit 纯文本，绕过了 Compose
     * 的粘贴回调，事件拦截接不住，图片只能靠这个按钮进来。
     */
    onPasteImage: (() -> Unit)? = null,
    /** 剪贴板里确实有图才点亮，否则点了没反应反而像坏了。 */
    pasteImageEnabled: Boolean = false,
    onInsertDrawing: (() -> Unit)? = null,
    onInsertAudio: (() -> Unit)? = null,
    /** 二级面板展开 / 收起。 */
    onPanelOpen: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var panel by remember { mutableStateOf<StylePanel?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    LaunchedEffect(panel) { onPanelOpen(panel != null) }

    Surface(
        // 手势条这一截由底栏自己吃掉：Surface 的背景会连 padding 一起画，
        // 所以底栏的底色一直铺到屏幕底，下面不会留一条空白。
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        // 用 surface + 零 tonalElevation：避免 M3 按高度把主色（紫色）混进来，
        // 这样浅色就是浅灰、深色就是深灰，跟随系统主题。
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedVisibility(
                visible = panel != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                when (panel) {
                    StylePanel.FONT -> FontPanel(
                        currentSize = current.size,
                        currentFont = current.font,
                        currentLevel = currentLevel,
                        onPickFontSize = onPickFontSize,
                        onPickFont = onPickFont,
                        onPickLevel = onPickLevel
                    )
                    StylePanel.COLOR -> ColorPanel(
                        currentColor = current.color,
                        currentBg = current.bg,
                        onPickColor = onPickColor,
                        onPickHighlight = onPickHighlight
                    )
                    null -> Unit
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyleIcon(
                    active = panel == StylePanel.FONT,
                    enabled = styleEnabled,
                    onClick = { panel = if (panel == StylePanel.FONT) null else StylePanel.FONT }
                ) {
                    Icon(Icons.Filled.FontDownload, contentDescription = "字体")
                }
                StyleIcon(
                    active = panel == StylePanel.COLOR,
                    enabled = styleEnabled,
                    onClick = { panel = if (panel == StylePanel.COLOR) null else StylePanel.COLOR }
                ) {
                    Icon(Icons.Filled.FormatColorText, contentDescription = "颜色")
                }
                VerticalBar()
                StyleIcon(active = current.bold, enabled = styleEnabled, onClick = onToggleBold) {
                    Icon(Icons.Filled.FormatBold, contentDescription = "加粗")
                }
                StyleIcon(active = current.italic, enabled = styleEnabled, onClick = onToggleItalic) {
                    Icon(Icons.Filled.FormatItalic, contentDescription = "斜体")
                }
                StyleIcon(
                    active = current.underline,
                    enabled = styleEnabled,
                    onClick = onToggleUnderline
                ) {
                    Icon(Icons.Filled.FormatUnderlined, contentDescription = "下划线")
                }
                StyleIcon(active = current.strike, enabled = styleEnabled, onClick = onToggleStrike) {
                    Icon(Icons.Filled.FormatStrikethrough, contentDescription = "删除线")
                }
                VerticalBar()
                StyleIcon(active = false, enabled = true, onClick = onInsertImage) {
                    Icon(Icons.Filled.Image, contentDescription = "插入图片")
                }
                if (onInsertAudio != null) {
                    StyleIcon(active = false, enabled = true, onClick = onInsertAudio) {
                        Icon(Icons.Filled.Mic, contentDescription = "录制音频")
                    }
                }
                if (onInsertDrawing != null) {
                    StyleIcon(active = false, enabled = true, onClick = onInsertDrawing) {
                        Icon(Icons.Filled.Brush, contentDescription = "插入涂鸦")
                    }
                }
                if (onPasteImage != null) {
                    // 始终可点：预检测在后台会被系统挡掉而误判成"没图"，真正确
                    // 的时刻是用户点下去的这一下，那时窗口一定在前台。
                    StyleIcon(active = pasteImageEnabled, enabled = true, onClick = onPasteImage) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴剪贴板里的图片")
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalBar() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StyleIcon(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            // 关键：工具栏按钮**不能成为焦点**，否则点一次"加粗"就把焦点从
            // 正文输入框上抢走 —— 键盘收起、光标位置丢失，样式还不知道该
            // 作用在谁的身上。
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}

/** [SysFont] → 面板上显示的名字。 */
private fun fontLabel(font: SysFont): String = when (font) {
    SysFont.DEFAULT -> "默认"
    SysFont.THIN -> "极细"
    SysFont.LIGHT -> "细体"
    SysFont.MEDIUM -> "中等"
    SysFont.BLACK -> "特粗"
    SysFont.CONDENSED -> "窄体"
    SysFont.CONDENSED_MEDIUM -> "窄中等"
    SysFont.SMALLCAPS -> "小型大写"
    SysFont.SERIF -> "衬线"
    SysFont.SERIF_MONO -> "衬线等宽"
    SysFont.MONO -> "等宽"
    SysFont.CASUAL -> "休闲"
    SysFont.CURSIVE -> "手写"
}

/** 字号 + 段落级别 + 系统字体面板。 */
@Composable
private fun FontPanel(
    currentSize: Int?,
    currentFont: String?,
    currentLevel: Int,
    onPickFontSize: (Int?) -> Unit,
    onPickFont: (String?) -> Unit,
    onPickLevel: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 段落级别：这一段是正文还是几级标题。
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HEADINGS.forEach { (level, label) ->
                Chip(
                    text = label,
                    selected = currentLevel == level,
                    onClick = { onPickLevel(level) },
                    textSize = 14.sp
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.FormatSize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            FONT_SIZES.forEach { (size, label) ->
                Chip(
                    text = label,
                    selected = currentSize == size,
                    onClick = { onPickFontSize(size) },
                    textSize = (size ?: 16).sp
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.FontDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            SysFont.entries.forEach { font ->
                Chip(
                    text = fontLabel(font),
                    selected = (currentFont ?: SysFont.DEFAULT.key) == font.key,
                    onClick = { onPickFont(if (font == SysFont.DEFAULT) null else font.key) },
                    fontFamily = sysFontFamily(font.key)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/** 文字色 + 高亮面板。 */
@Composable
private fun ColorPanel(
    currentColor: Long?,
    currentBg: Long?,
    onPickColor: (Long?) -> Unit,
    onPickHighlight: (Long?) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("文字颜色", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TEXT_COLORS.forEach { (value, label) ->
                Swatch(
                    color = value?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
                    label = label,
                    selected = currentColor == value,
                    isDefault = value == null,
                    onClick = { onPickColor(value) }
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("高亮", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            HIGHLIGHTS.forEach { (value, label) ->
                Swatch(
                    color = value?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant,
                    label = label,
                    selected = currentBg == value,
                    isDefault = value == null,
                    onClick = { onPickHighlight(value) }
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontFamily: FontFamily? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = textSize, fontFamily = fontFamily)
    }
}

@Composable
private fun Swatch(
    color: Color,
    label: String,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isDefault) {
                Text(
                    "×",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
