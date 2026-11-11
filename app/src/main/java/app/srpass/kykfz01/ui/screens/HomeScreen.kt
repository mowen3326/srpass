package app.srpass.kykfz01.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppScreen
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.components.AppNavBar
import app.srpass.kykfz01.ui.screens.CardsListScreen
import app.srpass.kykfz01.ui.screens.SettingsScreen

/**
 * Hosts the two main authenticated sections -- the password vault and the
 * encrypted notes -- inside a horizontally swipeable pager. The bottom
 * navigation bar selects / reflects the current page, and swiping updates the
 * shared [AppViewModel.homeTab] so navigation (e.g. "打开笔记") can jump to the
 * correct page.
 */
@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val homeTab by viewModel.homeTab.collectAsState()
    val pagerState = rememberPagerState(initialPage = homeTab, pageCount = { 5 })
    val scope = rememberCoroutineScope()
    // The programmatic scroll (animateScrollToPage) currently in flight, if any.
    // Guards the swipe -> homeTab sync below so an intermediate page crossed
    // during a 0->2 jump can't reset homeTab and abort the animation.
    //
    // It is the Job itself and not a boolean: switching tabs twice in a row
    // cancels the first scroll, and its `finally` used to clear the flag of the
    // scroll that had just replaced it -- the second animation was then read as
    // a user swipe, which re-targeted it to whatever page it happened to be
    // passing and left the pager on the wrong tab.
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(homeTab) {
        if (pagerState.currentPage != homeTab) {
            val job = scope.launch { pagerState.animateScrollToPage(homeTab) }
            scrollJob = job
            try {
                job.join()
            } finally {
                if (scrollJob === job) scrollJob = null
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (scrollJob == null && pagerState.currentPage != viewModel.homeTab.value) {
            viewModel.setHomeTab(pagerState.currentPage)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppNavBar(
                current = when (pagerState.currentPage) {
                    0 -> AppScreen.List
                    1 -> AppScreen.NotesList
                    2 -> AppScreen.CardsList
                    3 -> AppScreen.OtpList
                    else -> AppScreen.Settings
                },
                onVault = viewModel::goList,
                onNotes = viewModel::goNotesList,
                onCards = viewModel::goCardsList,
                onOtp = viewModel::goOtpList,
                onSettings = viewModel::goSettings
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> VaultListScreen(viewModel)
                1 -> NotesListScreen(viewModel)
                2 -> CardsListScreen(viewModel)
                3 -> OtpListScreen(viewModel)
                4 -> SettingsScreen(viewModel)
            }
        }
    }
}
