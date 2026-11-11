package app.srpass.kykfz01.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppScreen

private val SelectedGreen = Color(0xFF2E7D32)
private val SelectedOnGreen = Color.White

/**
 * Bottom navigation shown on the main authenticated screen. Keeps the password
 * vault, the encrypted notes and the bank cards as three clearly separated
 * sections, all unlocked by the same master key. A compact variant of the
 * Material bar: shorter height, and the whole selected item (icon + label) is
 * wrapped in a green rounded container.
 */
@Composable
fun AppNavBar(
    current: AppScreen,
    onVault: () -> Unit,
    onNotes: () -> Unit,
    onCards: () -> Unit,
    onOtp: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem(
                selected = current is AppScreen.List,
                icon = Icons.Filled.Lock,
                label = "密码库",
                onClick = onVault
            )
            NavItem(
                selected = current is AppScreen.NotesList,
                icon = Icons.Filled.Description,
                label = "笔记",
                onClick = onNotes
            )
            NavItem(
                selected = current is AppScreen.CardsList,
                icon = Icons.Filled.CreditCard,
                label = "银行卡",
                onClick = onCards
            )
            NavItem(
                selected = current is AppScreen.OtpList,
                icon = Icons.Filled.Numbers,
                label = "验证器",
                onClick = onOtp
            )
            NavItem(
                selected = current is AppScreen.Settings,
                icon = Icons.Filled.Settings,
                label = "设置",
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected) SelectedGreen else Color.Transparent
    val contentColor =
        if (selected) SelectedOnGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
