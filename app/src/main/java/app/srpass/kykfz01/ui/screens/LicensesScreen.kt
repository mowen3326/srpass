package app.srpass.kykfz01.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.components.CenterTopBar

/**
 * 开源许可页：正文来自 APK 内的 assets/licenses/THIRD-PARTY-NOTICES.txt。
 * 放在 APK 里而不是只写仓库文档，是因为 MIT / BSD / OFL 都要求许可文本随分发物一起提供给接收者，
 * 而 APK 就是本应用唯一的分发物。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val text = remember(context) {
        runCatching {
            context.assets.open("licenses/THIRD-PARTY-NOTICES.txt")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "无法读取许可文件：${it.message}" }
    }

    BackHandler { viewModel.goSettingsAbout() }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "开源许可",
                navigationIcon = {
                    IconButton(onClick = viewModel::goSettingsAbout) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
